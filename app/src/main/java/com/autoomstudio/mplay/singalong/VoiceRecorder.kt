package com.autoomstudio.mplay.singalong

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10

/**
 * Captures the voice as mono 44.1 kHz 16-bit PCM into a file (SA15), preferring an unprocessed source so the
 * phone's call-style noise suppression doesn't mangle singing, and the built-in or wired mic over Bluetooth (SA5).
 */
class VoiceRecorder(private val context: Context, private val onError: () -> Unit) {

    @Volatile private var running = false
    private var thread: Thread? = null

    /** Peak level of the last block on a -60 to 0 dB scale mapped to 0 to 1, for the meter (SA8). */
    @Volatile var level = 0f
        private set

    @Volatile var frames = 0L
        private set

    /** [SystemClock.elapsedRealtime] when the first block arrived, roughly when sound started being captured. */
    @Volatile var startedAtMs = 0L
        private set

    /** Returns false when the microphone can't be opened. */
    @SuppressLint("MissingPermission")
    fun start(file: File): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val record = open() ?: return false
        preferredInput(context)?.let(record::setPreferredDevice)
        val output = try {
            BufferedOutputStream(FileOutputStream(file), WRITE_BUFFER_BYTES)
        } catch (e: Exception) {
            Log.e(TAG, "Could not create $file", e)
            record.release()
            return false
        }
        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Could not start recording", e)
            record.release()
            output.close()
            return false
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            output.close()
            return false
        }
        frames = 0
        level = 0f
        startedAtMs = SystemClock.elapsedRealtime()
        running = true
        thread = Thread({ loop(record, output) }, "VoiceRecorder").apply { start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
        level = 0f
    }

    private fun loop(record: AudioRecord, output: BufferedOutputStream) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val shorts = ShortArray(BLOCK_FRAMES)
        val bytes = ByteBuffer.allocate(BLOCK_FRAMES * 2).order(ByteOrder.LITTLE_ENDIAN)
        var first = true
        var failed = false
        try {
            while (running) {
                val read = record.read(shorts, 0, shorts.size)
                if (read < 0) {
                    Log.w(TAG, "AudioRecord read failed: $read")
                    failed = true
                    break
                }
                if (read == 0) continue
                if (first) {
                    startedAtMs = SystemClock.elapsedRealtime() - read * 1000L / SAMPLE_RATE
                    first = false
                }
                bytes.clear()
                var peak = 0
                for (i in 0 until read) {
                    bytes.putShort(shorts[i])
                    peak = maxOf(peak, abs(shorts[i].toInt()))
                }
                output.write(bytes.array(), 0, read * 2)
                frames += read
                level = meterLevel(peak / 32768f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            failed = true
        } finally {
            runCatching { record.stop() }
            record.release()
            runCatching { output.close() }
        }
        if (failed) {
            running = false
            onError()
        }
    }

    @SuppressLint("MissingPermission")
    private fun open(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return null
        val buffer = maxOf(minBuffer * 4, BLOCK_FRAMES * 2 * 4)
        for (source in sources()) {
            val record = try {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer)
            } catch (e: Exception) {
                Log.w(TAG, "Source $source unavailable", e)
                continue
            }
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }

    private fun sources(): List<Int> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return listOfNotNull(
            MediaRecorder.AudioSource.UNPROCESSED.takeIf { unprocessed },
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
    }

    companion object {
        private const val TAG = "VoiceRecorder"
        const val SAMPLE_RATE = 44_100
        private const val BLOCK_FRAMES = 1_024
        private const val WRITE_BUFFER_BYTES = 64 * 1024
        private const val JOIN_TIMEOUT_MS = 1_000L

        private const val METER_FLOOR_DB = -60f

        /** Maps a linear peak (0 to 1) onto the meter, so quiet singing still moves it. */
        fun meterLevel(peak: Float): Float {
            if (peak <= 0f) return 0f
            val db = 20f * log10(peak)
            return ((db - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f)
        }

        private val wiredInputs = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET)

        /** A wired headset mic if one is plugged in, else the phone's own mic. */
        fun preferredInput(context: Context): AudioDeviceInfo? {
            val inputs = context.getSystemService(AudioManager::class.java)
                ?.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .orEmpty()
            return inputs.firstOrNull { it.type in wiredInputs }
                ?: inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        }
    }
}
