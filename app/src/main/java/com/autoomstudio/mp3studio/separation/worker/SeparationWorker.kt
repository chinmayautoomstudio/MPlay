package com.autoomstudio.mp3studio.separation.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.account.AuthState
import com.autoomstudio.mp3studio.data.stems.JobError
import com.autoomstudio.mp3studio.data.stems.JobState
import com.autoomstudio.mp3studio.data.stems.PauseReason
import com.autoomstudio.mp3studio.data.stems.SeparationJobEntity
import com.autoomstudio.mp3studio.separation.StorageEstimate
import com.autoomstudio.mp3studio.separation.pipeline.HeatLevel
import com.autoomstudio.mp3studio.separation.pipeline.SeparationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Works through the separation queue one song at a time (AI10) as a foreground service, in the main process.
 * The model itself runs in [SeparatorService] in `:separator`; this worker only coordinates, so the database and
 * WorkManager stay single-process.
 */
class SeparationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as MPlayApp).container
    private val repository get() = container.stemRepository
    private val notifications = SeparationNotifications(context)
    private val thermal = ThermalMonitor(context)

    private var currentJobId: Long? = null
    private var done = 0
    private var failed = 0

    override suspend fun doWork(): Result {
        repository.recoverInterrupted()
        // Signed out: leave the queue as it is; sign-in schedules the worker again.
        if (container.authRepository.awaitReady() !is AuthState.SignedIn) return Result.success()
        val first = repository.nextQueued() ?: return Result.success()
        notifications.ensureChannel()
        notifications.cancelResult()
        try {
            setForeground(foregroundInfo(first, fraction = null, remainingMs = null))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: Android 12+ started us while the app was in the background.
            Log.w(TAG, "Could not start in the foreground", e)
            repository.setQueuedPauseReason(PauseReason.NeedsApp)
            notifications.showNeedsApp()
            return Result.success()
        }

        val client = SeparatorClient(applicationContext)
        return try {
            client.connect()
            processQueue(client)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                currentJobId?.let { repository.requeue(it, stopPauseReason()) }
            }
            throw e
        } finally {
            client.close()
            if (!isStopped) notifications.showFinished(done, failed)
        }
    }

    private suspend fun processQueue(client: SeparatorClient): Result {
        while (true) {
            val settings = container.appSettings.separationSettings.first()
            DeviceConditions.pauseReason(applicationContext, settings, thermal, HeatLevel.Hot)?.let { return pause(it) }
            if (!container.authRepository.isSignedIn) return Result.success()
            val job = repository.nextQueued() ?: return Result.success()
            if (!repository.markRunning(job.id)) continue
            repository.setQueuedPauseReason(null)
            currentJobId = job.id
            val pausedBy = runJob(job, client)
            currentJobId = null
            if (pausedBy != null) return pause(pausedBy)
        }
    }

    /** Heat retries after the backoff; charging and battery wait for WorkManager's constraints. */
    private suspend fun pause(reason: PauseReason): Result {
        Log.i(TAG, "Pausing: $reason")
        repository.setQueuedPauseReason(reason)
        return if (reason == PauseReason.Heat) {
            Result.retry()
        } else {
            container.separationBackend.schedule()
            Result.success()
        }
    }

    /** Returns the reason when the song was stopped to pause the queue; the job is then back in the queue. */
    @OptIn(FlowPreview::class)
    private suspend fun runJob(job: SeparationJobEntity, client: SeparatorClient): PauseReason? {
        if (repository.freeBytes() < StorageEstimate.requiredFreeBytes(job.durationMs)) {
            fail(job, JobError.LowStorage)
            return null
        }
        val uri = Uri.parse(job.sourceUri)
        val workDir = repository.workDir(job.id)
        val vocals = File(workDir, VOCALS_FILE)
        val instrumental = File(workDir, INSTRUMENTAL_FILE)
        val started = System.nanoTime()
        var pausedBy: PauseReason? = null

        val outcome = coroutineScope {
            val progress = MutableStateFlow<Progress?>(null)
            val reporter = launch {
                progress.filterNotNull().sample(PROGRESS_INTERVAL_MS).collect { (fraction, remaining, cooling) ->
                    repository.updateProgress(job.id, fraction, remaining, PauseReason.Heat.takeIf { cooling })
                    try {
                        setForeground(foregroundInfo(job, fraction, remaining, cooling))
                    } catch (_: IllegalStateException) {
                    }
                }
            }
            val cancelWatcher = launch {
                repository.observeJobState(job.id).first { it != JobState.Running }
                client.cancel()
            }
            val conditionWatcher = launch {
                val settings = container.appSettings.separationSettings
                while (true) {
                    delay(CONDITION_CHECK_MS)
                    val reason = DeviceConditions.pauseReason(
                        applicationContext,
                        settings.first(),
                        thermal,
                        HeatLevel.Critical,
                    )
                    if (reason != null) {
                        pausedBy = reason
                        client.cancel()
                        break
                    }
                }
            }
            val result = client.separate(uri, vocals, instrumental) { fraction, remaining, cooling ->
                progress.value = Progress(fraction, remaining, cooling)
            }
            reporter.cancel()
            cancelWatcher.cancel()
            conditionWatcher.cancel()
            result
        }

        val seconds = (System.nanoTime() - started) / 1e9
        when (outcome) {
            SeparatorOutcome.Done -> try {
                val fingerprint = withContext(Dispatchers.IO) { repository.fingerprint(uri) }
                repository.commit(job, workDir, vocals, instrumental, fingerprint)
                done++
                val speed = realTime(job, seconds)
                Log.i(TAG, "Separated job ${job.id} in %.1f s (%.2f x real time)".format(seconds, speed))
                container.appSettings.recordSeparationSpeed(speed)
            } catch (e: Exception) {
                Log.e(TAG, "Could not store stems for job ${job.id}", e)
                workDir.deleteRecursively()
                fail(job, if (repository.freeBytes() < LOW_SPACE_BYTES) JobError.LowStorage else JobError.Unknown)
            }
            SeparatorOutcome.Cancelled -> {
                workDir.deleteRecursively()
                pausedBy?.let { repository.requeue(job.id, it) }
            }
            is SeparatorOutcome.Failed -> {
                Log.w(TAG, "Job ${job.id} failed: ${outcome.error} ${outcome.message}")
                workDir.deleteRecursively()
                fail(job, outcome.error.toJobError())
            }
        }
        return pausedBy
    }

    private suspend fun fail(job: SeparationJobEntity, error: JobError) {
        repository.markFailed(job.id, error)
        failed++
    }

    private data class Progress(val fraction: Float, val remainingMs: Long?, val cooling: Boolean)

    private suspend fun foregroundInfo(
        job: SeparationJobEntity,
        fraction: Float?,
        remainingMs: Long?,
        cooling: Boolean = false,
    ): ForegroundInfo {
        val waiting = repository.queuedCount()
        val notification = notifications.progress(job.id, job.title, fraction, remainingMs, waiting, cooling)
        val id = SeparationNotifications.PROGRESS_ID
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(id, notification)
        }
    }

    private fun stopPauseReason(): PauseReason = when (stopReason) {
        WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT -> PauseReason.TimeLimit
        WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> PauseReason.Charging
        WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> PauseReason.Battery
        else -> PauseReason.Interrupted
    }

    private fun realTime(job: SeparationJobEntity, seconds: Double): Double =
        if (job.durationMs <= 0) 0.0 else seconds / (job.durationMs / 1000.0)

    private fun SeparationError.toJobError(): JobError = when (this) {
        SeparationError.SourceMissing -> JobError.SourceMissing
        SeparationError.UnsupportedFormat -> JobError.UnsupportedFormat
        SeparationError.CorruptFile -> JobError.CorruptFile
        SeparationError.OutOfMemory -> JobError.OutOfMemory
        SeparationError.ModelUnavailable -> JobError.ModelUnavailable
        SeparationError.ModelFailed -> JobError.ModelFailed
        SeparationError.Storage -> JobError.LowStorage
        SeparationError.Unknown -> JobError.Unknown
    }

    companion object {
        const val UNIQUE_NAME = "separation"
        private const val TAG = "SeparationWorker"
        private const val VOCALS_FILE = "vocals.m4a"
        private const val INSTRUMENTAL_FILE = "instrumental.m4a"
        private const val PROGRESS_INTERVAL_MS = 1_000L
        private const val CONDITION_CHECK_MS = 15_000L
        private const val LOW_SPACE_BYTES = 20L * 1024 * 1024
    }
}
