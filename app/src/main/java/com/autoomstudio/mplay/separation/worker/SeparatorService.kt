package com.autoomstudio.mplay.separation.worker

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.util.Log
import com.autoomstudio.mplay.separation.android.ModelMapper
import com.autoomstudio.mplay.separation.android.ModelOptions
import com.autoomstudio.mplay.separation.android.ModelSource
import com.autoomstudio.mplay.separation.android.OrtDemucsModel
import com.autoomstudio.mplay.separation.android.separateToFiles
import com.autoomstudio.mplay.separation.pipeline.SeparationCancelledException
import com.autoomstudio.mplay.separation.pipeline.SeparationError
import com.autoomstudio.mplay.separation.pipeline.SeparationException
import com.autoomstudio.mplay.separation.pipeline.StemSeparator
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the model in the `:separator` process, so a native crash or the low-memory killer takes down only this
 * process and never playback. Bound by [SeparatorClient] for the length of one worker run; the model stays loaded
 * between songs of that run.
 */
class SeparatorService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private var separator: StemSeparator? = null

    private val messenger = Messenger(
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    SeparatorProtocol.MSG_SEPARATE -> start(Bundle(msg.data), msg.replyTo)
                    SeparatorProtocol.MSG_CANCEL -> cancelled.set(true)
                }
            }
        },
    )

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun start(request: Bundle, replyTo: Messenger?) {
        if (replyTo == null) return
        if (!busy.compareAndSet(false, true)) {
            reply(replyTo, failure(SeparationError.Unknown, "A separation is already running"))
            return
        }
        cancelled.set(false)
        executor.execute {
            try {
                separate(request, replyTo)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun separate(request: Bundle, replyTo: Messenger) {
        val result = try {
            val uri = Uri.parse(request.getString(SeparatorProtocol.KEY_URI).orEmpty())
            val vocals = File(request.getString(SeparatorProtocol.KEY_VOCALS).orEmpty())
            val instrumental = File(request.getString(SeparatorProtocol.KEY_INSTRUMENTAL).orEmpty())
            separator().separateToFiles(
                context = this,
                uri = uri,
                vocals = vocals,
                instrumental = instrumental,
                isCancelled = cancelled::get,
                onProgress = { progress ->
                    reply(
                        replyTo,
                        Message.obtain(null, SeparatorProtocol.MSG_PROGRESS).apply {
                            data = Bundle().apply {
                                putFloat(SeparatorProtocol.KEY_FRACTION, progress.fraction)
                                progress.remainingMs?.let { putLong(SeparatorProtocol.KEY_REMAINING_MS, it) }
                            }
                        },
                    )
                },
            )
            Message.obtain(null, SeparatorProtocol.MSG_DONE)
        } catch (_: SeparationCancelledException) {
            Message.obtain(null, SeparatorProtocol.MSG_FAILED).apply {
                data = Bundle().apply { putBoolean(SeparatorProtocol.KEY_CANCELLED, true) }
            }
        } catch (e: SeparationException) {
            Log.w(TAG, "Separation failed: ${e.error}", e)
            failure(e.error, e.message)
        } catch (e: OutOfMemoryError) {
            separator = null
            failure(SeparationError.OutOfMemory, e.message)
        } catch (e: Exception) {
            Log.e(TAG, "Separation failed", e)
            failure(SeparationError.Unknown, e.message)
        }
        reply(replyTo, result)
    }

    private fun separator(): StemSeparator = separator ?: run {
        val source = ModelSource.find(this)
            ?: throw SeparationException(SeparationError.ModelUnavailable, "No model installed")
        val started = System.nanoTime()
        val model = OrtDemucsModel.create(ModelMapper.map(this, source), ModelOptions())
        Log.i(TAG, "Model loaded in ${(System.nanoTime() - started) / 1_000_000} ms from $source")
        StemSeparator(model).also { separator = it }
    }

    private fun failure(error: SeparationError, message: String?): Message =
        Message.obtain(null, SeparatorProtocol.MSG_FAILED).apply {
            data = Bundle().apply {
                putString(SeparatorProtocol.KEY_ERROR, error.name)
                putString(SeparatorProtocol.KEY_MESSAGE, message)
            }
        }

    private fun reply(replyTo: Messenger, message: Message) {
        try {
            replyTo.send(message)
        } catch (_: RemoteException) {
            // The worker is gone; stop working for nobody.
            cancelled.set(true)
        }
    }

    override fun onDestroy() {
        cancelled.set(true)
        super.onDestroy()
        // The ONNX session holds hundreds of megabytes of native memory, and a running inference can't be
        // interrupted safely; ending the process releases both at once. Nothing else lives in this process.
        Process.killProcess(Process.myPid())
    }

    private companion object {
        const val TAG = "SeparatorService"
    }
}
