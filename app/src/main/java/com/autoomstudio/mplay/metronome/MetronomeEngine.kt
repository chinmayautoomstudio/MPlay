package com.autoomstudio.mplay.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/** A beat that has just become audible: [beatInBar] counts from 0, where 0 is the accented first beat. */
data class BeatTick(val index: Long, val beatInBar: Int, val beatsPerBar: Int)

/**
 * Plays the clicks on its own low-latency [AudioTrack], separate from the music player, so the two mix at the
 * system level (PRD section 5). Beats are placed by counting frames ([BeatClock]); no timers decide when a click
 * sounds. [onBeat] runs on a background thread when a beat reaches the speaker, for the visual indicator (MT7).
 */
class MetronomeEngine(private val onBeat: (BeatTick) -> Unit) {
    private val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 }
        ?: FALLBACK_RATE
    private val sounds = ClickSounds(sampleRate)

    @Volatile private var settings = MetronomeSettings()

    @Volatile private var muted = false

    @Volatile private var running = false

    /** Picked up by the writer before its next block; [Unalign] goes back to the set tempo. */
    private val pendingAlignment = AtomicReference<Any?>(null)

    @Volatile private var output: AudioTrack? = null
    private var writer: Thread? = null
    private var watcher: Thread? = null

    val isRunning: Boolean get() = running

    val outputSampleRate: Int get() = sampleRate

    fun update(newSettings: MetronomeSettings) {
        settings = newSettings
    }

    /** Silences the clicks without stopping the beat, for example during a call (MT16). */
    fun setMuted(value: Boolean) {
        muted = value
    }

    /**
     * Puts the clicks on a song's beats: song beat 0 is the accented first beat, and the set tempo is ignored until
     * [unalign]. [alignment] is in this run's output frames, so it must come from [heardFrame] after [start].
     */
    fun align(alignment: Alignment) {
        pendingAlignment.set(alignment)
    }

    fun unalign() {
        pendingAlignment.set(Unalign)
    }

    /** Which output frame is reaching the speaker now; null when stopped. */
    fun heardFrame(): HeardFrame? {
        val track = output ?: return null
        val timestamp = AudioTimestamp()
        return try {
            if (track.getTimestamp(timestamp)) {
                HeardFrame(timestamp.framePosition, timestamp.nanoTime)
            } else {
                // No timestamp until audio flows; the head position is close enough to start with.
                HeardFrame(track.playbackHeadPosition.toLong() and 0xFFFFFFFFL, System.nanoTime())
            }
        } catch (e: IllegalStateException) {
            null
        }
    }

    @Synchronized
    fun start() {
        if (running) return
        val track = buildTrack() ?: return
        pendingAlignment.set(null)
        output = track
        running = true
        val scheduled = ArrayDeque<ScheduledBeat>()
        writer = Thread({ writeLoop(track, scheduled) }, "MetronomeWriter").apply { start() }
        watcher = Thread({ watchLoop(track, scheduled) }, "MetronomeBeats").apply { start() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        writer?.join(JOIN_TIMEOUT_MS)
        watcher?.join(JOIN_TIMEOUT_MS)
        writer = null
        watcher = null
        output = null
    }

    private fun buildTrack(): AudioTrack? = try {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(max(minBuffer, BLOCK_FRAMES * Float.SIZE_BYTES * 2))
            .build()
    } catch (e: Exception) {
        Log.e(TAG, "Could not open the metronome output", e)
        null
    }

    private fun writeLoop(track: AudioTrack, scheduled: ArrayDeque<ScheduledBeat>) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var current = settings
        val clock = BeatClock(sampleRate, current.bpm.toDouble())
        val block = FloatArray(BLOCK_FRAMES)
        var written = 0L
        var barStartBeat = 0L
        var beatsPerBar = current.beatsPerBar
        // The click still sounding from an earlier block, and how far into it we are.
        var tail: FloatArray? = null
        var tailPosition = 0
        var tailGain = 0f
        try {
            track.play()
            while (running) {
                val latest = settings
                when (val change = pendingAlignment.getAndSet(null)) {
                    is Alignment -> clock.align(change.anchorFrame, change.framesPerBeat, 0, written)
                    Unalign -> if (clock.aligned) {
                        clock.unalign(latest.bpm.toDouble())
                        // Keep the bar where the song's numbering had it.
                        barStartBeat = clock.beatIndex - Math.floorMod(clock.beatIndex, beatsPerBar.toLong())
                    }
                }
                if (latest.bpm != current.bpm && !clock.aligned) clock.setBpm(latest.bpm.toDouble())
                current = latest
                block.fill(0f)
                tail?.let { sound ->
                    tailPosition = mix(block, 0, sound, tailPosition, tailGain)
                    if (tailPosition >= sound.size) tail = null
                }
                clock.beatsIn(written, BLOCK_FRAMES) { offset, beat ->
                    if (current.beatsPerBar != beatsPerBar) {
                        beatsPerBar = current.beatsPerBar
                        barStartBeat = beat
                    }
                    val beatInBar = if (clock.aligned) {
                        Math.floorMod(beat, beatsPerBar.toLong()).toInt()
                    } else {
                        Math.floorMod(beat - barStartBeat, beatsPerBar.toLong()).toInt()
                    }
                    val accented = current.accent && beatInBar == 0
                    val sound = sounds.get(current.sound, accented)
                    val gain = if (muted) 0f else current.volume
                    tailPosition = mix(block, offset, sound, 0, gain)
                    tail = sound.takeIf { tailPosition < it.size }
                    tailGain = gain
                    synchronized(scheduled) {
                        scheduled.addLast(ScheduledBeat(written + offset, BeatTick(beat, beatInBar, beatsPerBar)))
                    }
                }
                var offset = 0
                while (offset < BLOCK_FRAMES && running) {
                    val result = track.write(block, offset, BLOCK_FRAMES - offset, AudioTrack.WRITE_BLOCKING)
                    if (result < 0) {
                        Log.w(TAG, "AudioTrack write failed: $result")
                        running = false
                        break
                    }
                    offset += result
                }
                written += BLOCK_FRAMES
            }
        } catch (e: Exception) {
            Log.e(TAG, "Metronome output failed", e)
            running = false
        } finally {
            output = null
            runCatching { track.pause() }
            runCatching { track.flush() }
            track.release()
        }
    }

    /** Reports each beat when the playback head reaches its frame, so the flash matches what is heard. */
    private fun watchLoop(track: AudioTrack, scheduled: ArrayDeque<ScheduledBeat>) {
        while (running) {
            val head = runCatching { track.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrDefault(-1L)
            var due: BeatTick? = null
            synchronized(scheduled) {
                while (scheduled.isNotEmpty() && scheduled.first().frame <= head) due = scheduled.removeFirst().tick
            }
            due?.let(onBeat)
            try {
                Thread.sleep(WATCH_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    /** Adds [sound] from [from] into [block] at [at]; returns the position in [sound] it reached. */
    private fun mix(block: FloatArray, at: Int, sound: FloatArray, from: Int, gain: Float): Int {
        var source = from
        var target = at
        while (target < block.size && source < sound.size) {
            block[target] += sound[source] * gain
            target++
            source++
        }
        return source
    }

    private class ScheduledBeat(val frame: Long, val tick: BeatTick)

    private object Unalign

    private companion object {
        const val TAG = "MetronomeEngine"
        const val FALLBACK_RATE = 48_000
        const val BLOCK_FRAMES = 256
        const val WATCH_INTERVAL_MS = 4L
        const val JOIN_TIMEOUT_MS = 500L
    }
}
