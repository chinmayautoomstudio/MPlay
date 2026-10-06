package com.autoomstudio.mplay.separation.worker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.autoomstudio.mplay.separation.pipeline.SeparationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

sealed interface SeparatorOutcome {
    data object Done : SeparatorOutcome

    data object Cancelled : SeparatorOutcome

    data class Failed(val error: SeparationError, val message: String?) : SeparatorOutcome
}

/** Main-process side of [SeparatorService]. One instance per worker run; [close] unbinds and ends the process. */
class SeparatorClient(private val context: Context) : AutoCloseable {

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var service: Messenger? = null
    private var connection: ServiceConnection? = null
    @Volatile private var pending: CompletableDeferred<SeparatorOutcome>? = null

    suspend fun connect() {
        if (service != null) return
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { bind() } ?: Log.w(TAG, "Timed out binding the separator")
    }

    private suspend fun bind() {
        suspendCancellableCoroutine { continuation ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                    if (connection !== this) return
                    service = Messenger(binder)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onServiceDisconnected(name: ComponentName?) = onDied()

                override fun onBindingDied(name: ComponentName?) = onDied()

                override fun onNullBinding(name: ComponentName?) = onDied()

                private fun onDied() {
                    if (connection !== this) return
                    service = null
                    // The process died mid-song; almost always the low-memory killer.
                    pending?.complete(SeparatorOutcome.Failed(SeparationError.OutOfMemory, "Separator process died"))
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
            connection = conn
            val bound = context.bindService(
                Intent(context, SeparatorService::class.java),
                conn,
                Context.BIND_AUTO_CREATE,
            )
            if (!bound && continuation.isActive) continuation.resume(Unit)
            continuation.invokeOnCancellation { close() }
        }
    }

    /** Runs one song. Cancelling the calling coroutine asks the service to stop without waiting for it. */
    suspend fun separate(
        uri: Uri,
        vocals: File,
        instrumental: File,
        onProgress: (fraction: Float, remainingMs: Long?, cooling: Boolean) -> Unit,
    ): SeparatorOutcome {
        val target = service ?: reconnect()
            ?: return SeparatorOutcome.Failed(SeparationError.ModelFailed, "Could not start the separator")
        val result = CompletableDeferred<SeparatorOutcome>()
        pending = result
        val reply = Messenger(
            Handler(Looper.getMainLooper()) { msg ->
                when (msg.what) {
                    SeparatorProtocol.MSG_PROGRESS -> {
                        val data = msg.data
                        val remaining = if (data.containsKey(SeparatorProtocol.KEY_REMAINING_MS)) {
                            data.getLong(SeparatorProtocol.KEY_REMAINING_MS)
                        } else {
                            null
                        }
                        onProgress(
                            data.getFloat(SeparatorProtocol.KEY_FRACTION),
                            remaining,
                            data.getBoolean(SeparatorProtocol.KEY_COOLING),
                        )
                    }
                    SeparatorProtocol.MSG_DONE -> result.complete(SeparatorOutcome.Done)
                    SeparatorProtocol.MSG_FAILED -> result.complete(failureOf(msg.data))
                }
                true
            },
        )
        val request = Message.obtain(null, SeparatorProtocol.MSG_SEPARATE).apply {
            replyTo = reply
            data = Bundle().apply {
                putString(SeparatorProtocol.KEY_URI, uri.toString())
                putString(SeparatorProtocol.KEY_VOCALS, vocals.absolutePath)
                putString(SeparatorProtocol.KEY_INSTRUMENTAL, instrumental.absolutePath)
            }
        }
        try {
            target.send(request)
            return result.await()
        } catch (_: RemoteException) {
            return SeparatorOutcome.Failed(SeparationError.OutOfMemory, "Separator process died")
        } catch (e: CancellationException) {
            cancel()
            throw e
        } finally {
            pending = null
        }
    }

    /**
     * The `:separator` process died (usually the low-memory killer on the previous song). Binds a fresh one so the
     * rest of the queue still runs; the song that was running when it died has already failed.
     */
    private suspend fun reconnect(): Messenger? {
        Log.i(TAG, "Separator is gone; starting it again")
        close()
        connect()
        return service
    }

    /** Asks the running song to stop; [separate] then returns [SeparatorOutcome.Cancelled]. */
    fun cancel() {
        try {
            service?.send(Message.obtain(null, SeparatorProtocol.MSG_CANCEL))
        } catch (_: RemoteException) {
        }
    }

    private fun failureOf(data: Bundle): SeparatorOutcome {
        if (data.getBoolean(SeparatorProtocol.KEY_CANCELLED)) return SeparatorOutcome.Cancelled
        val error = SeparationError.entries.firstOrNull { it.name == data.getString(SeparatorProtocol.KEY_ERROR) }
            ?: SeparationError.Unknown
        return SeparatorOutcome.Failed(error, data.getString(SeparatorProtocol.KEY_MESSAGE))
    }

    override fun close() {
        val conn = connection ?: return
        connection = null
        service = null
        mainHandler.post {
            try {
                context.unbindService(conn)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    private companion object {
        const val TAG = "SeparatorClient"
        const val CONNECT_TIMEOUT_MS = 20_000L
    }
}
