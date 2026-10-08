package com.autoomstudio.mp3studio.spike

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import com.autoomstudio.mp3studio.separation.android.AacStemSink
import com.autoomstudio.mp3studio.separation.android.Accelerator
import com.autoomstudio.mp3studio.separation.android.MediaPcmSource
import com.autoomstudio.mp3studio.separation.android.ModelMapper
import com.autoomstudio.mp3studio.separation.android.ModelOptions
import com.autoomstudio.mp3studio.separation.android.ModelSource
import com.autoomstudio.mp3studio.separation.android.OrtDemucsModel
import com.autoomstudio.mp3studio.separation.pipeline.StemSeparator
import com.autoomstudio.mp3studio.separation.pipeline.StemSink
import java.io.File

data class SpikeConfig(
    val threads: Int,
    val overlap: Double,
    val accelerator: Accelerator,
    /** Null for the whole song. */
    val seconds: Int?,
    /** Repeats the run until this many minutes have passed, to watch heat and slowdown. */
    val soakMinutes: Int?,
)

/** One measured run of the real pipeline, logged line by line and appended to results.csv. */
class SpikeRunner(private val context: Context, private val log: (String) -> Unit) {
    private val probe = DeviceProbe(context)
    private val outDir = context.getExternalFilesDir(null)!!

    fun run(uri: Uri, config: SpikeConfig, isCancelled: () -> Boolean) {
        log(probe.description())
        val source = modelSource() ?: run {
            log("No model. Push it with: adb push htdemucs.onnx ${File(outDir, ModelSource.DEFAULT_NAME)}")
            return
        }
        log("Model: $source")
        val loadStart = SystemClock.elapsedRealtime()
        val model = OrtDemucsModel.create(ModelMapper.map(context, source), ModelOptions(config.threads, config.accelerator))
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        log("Model loaded in $loadMs ms; memory peak/now ${probe.memoryMb()} MB")

        model.use {
            val separator = StemSeparator(model, config.overlap)
            val soakEnd = config.soakMinutes?.let { SystemClock.elapsedRealtime() + it * 60_000L }
            var round = 1
            do {
                runOnce(separator, uri, config, round, loadMs, isCancelled)
                round++
            } while (soakEnd != null && SystemClock.elapsedRealtime() < soakEnd && !isCancelled())
        }
    }

    private fun runOnce(
        separator: StemSeparator,
        uri: Uri,
        config: SpikeConfig,
        round: Int,
        loadMs: Long,
        isCancelled: () -> Boolean,
    ) {
        val name = displayName(uri)
        val batteryStart = probe.batteryPercent()
        val tempStart = probe.batteryTempC()
        var maxThermal = probe.thermalStatus()
        val segmentMs = ArrayList<Long>()
        var lastSegmentAt = SystemClock.elapsedRealtime()
        val maxFrames = config.seconds?.let { it * MediaPcmSource.TARGET_RATE.toLong() }

        val vocalsFile = File(outDir, "$name.vocals.m4a")
        val instrumentalFile = File(outDir, "$name.instrumental.m4a")
        val aac = AacStemSink(vocalsFile, instrumentalFile)
        val vocalsRef = File(outDir, "$name.vocals.f32")
        val instrumentalRef = File(outDir, "$name.instrumental.f32")
        val comparison = if (vocalsRef.isFile && instrumentalRef.isFile) {
            ReferenceComparison(aac, vocalsRef, instrumentalRef)
        } else {
            null
        }
        val sink: StemSink = comparison ?: aac
        var framesOut = 0L
        val counting = object : StemSink {
            override fun write(vocals: FloatArray, instrumental: FloatArray, frames: Int) {
                sink.write(vocals, instrumental, frames)
                framesOut += frames
            }

            override fun finish() = sink.finish()
        }

        log("Round $round: $name, $config")
        val start = SystemClock.elapsedRealtime()
        try {
            separator.separate(MediaPcmSource(context, uri, maxFrames), counting, isCancelled) { progress ->
                if (progress.segmentsDone > segmentMs.size) {
                    val now = SystemClock.elapsedRealtime()
                    segmentMs += now - lastSegmentAt
                    lastSegmentAt = now
                    maxThermal = maxOf(maxThermal, probe.thermalStatus())
                    log(
                        "  segment ${progress.segmentsDone}/${progress.segmentsTotal}: ${segmentMs.last()} ms, " +
                            "mem ${probe.memoryMb().first} MB, thermal ${probe.thermalStatus()}, " +
                            "${probe.batteryTempC()} C",
                    )
                } else if (progress.segmentsDone == 0) {
                    lastSegmentAt = SystemClock.elapsedRealtime()
                    log("  decode pass done in ${lastSegmentAt - start} ms")
                }
            }
        } catch (e: Exception) {
            aac.abort()
            log("  failed: $e")
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - start
        val audioMs = framesOut * 1000 / MediaPcmSource.TARGET_RATE
        val rtf = elapsed.toDouble() / audioMs.coerceAtLeast(1)
        val (peakMb, _) = probe.memoryMb()
        val firstSegments = segmentMs.take(3).average()
        val lastSegments = segmentMs.takeLast(3).average()
        log(
            "  done: ${elapsed / 1000.0} s for ${audioMs / 1000.0} s of audio (${"%.2f".format(rtf)}x real time), " +
                "peak $peakMb MB, battery $batteryStart% -> ${probe.batteryPercent()}%, " +
                "$tempStart C -> ${probe.batteryTempC()} C, max thermal $maxThermal, " +
                "segment time first ${firstSegments.toLong()} ms / last ${lastSegments.toLong()} ms",
        )
        comparison?.let { log("  ${it.summary()}") }
        log("  stems: ${vocalsFile.path}, ${instrumentalFile.path}")
        appendCsv(
            listOf(
                probe.description().replace(',', ';'), name, round, config.threads, config.overlap, config.accelerator,
                audioMs, elapsed, "%.3f".format(rtf), loadMs, peakMb, batteryStart, probe.batteryPercent(),
                tempStart, probe.batteryTempC(), maxThermal, firstSegments.toLong(), lastSegments.toLong(),
                comparison?.summary()?.replace(',', ';') ?: "",
            ),
        )
    }

    private fun modelSource(): ModelSource? {
        val pushed = File(outDir, ModelSource.DEFAULT_NAME)
        return if (pushed.isFile) ModelSource.FileModel(pushed) else ModelSource.find(context)
    }

    private fun appendCsv(values: List<Any>) {
        val file = File(outDir, "results.csv")
        if (!file.exists()) {
            file.writeText(
                "device,song,round,threads,overlap,accelerator,audio_ms,elapsed_ms,rtf,load_ms,peak_mb," +
                    "battery_start,battery_end,temp_start,temp_end,max_thermal,first_segment_ms,last_segment_ms,sdr\n",
            )
        }
        file.appendText(values.joinToString(",") + "\n")
    }

    private fun displayName(uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: "song"
        return name.substringBeforeLast('.')
    }
}
