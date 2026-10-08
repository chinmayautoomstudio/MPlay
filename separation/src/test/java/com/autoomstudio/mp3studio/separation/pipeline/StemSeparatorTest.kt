package com.autoomstudio.mp3studio.separation.pipeline

import com.autoomstudio.mp3studio.separation.dsp.DemucsSpectrogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.sin

class StemSeparatorTest {

    private val totalFrames = 900_000 // about 3.5 segments
    private val segment = DemucsSpectrogram.SEGMENT

    @Test
    fun spectralVocalsRoundTripToTheInput() {
        val sink = CollectingSink()
        StemSeparator(SpectralVocalsModel()).separate(source(), sink)
        assertTrue(sink.finished)
        assertEquals(totalFrames, sink.frames)
        // The spectral branch is attenuated at segment edges (see DemucsSpectrogramTest); the triangular
        // weights keep that small inside the track, while the track's own first and last edge are skipped.
        val edge = DemucsSpectrogram.PAD
        for (i in edge until totalFrames - edge step 37) {
            assertEquals("vocals L $i", left(i), sink.vocals[2 * i], 5e-3f)
            assertEquals("vocals R $i", right(i), sink.vocals[2 * i + 1], 5e-3f)
            // The track mean is restored once per stem; this signal's mean is close to its DC offset.
            assertEquals("instrumental $i", MEAN, sink.instrumental[2 * i], 2e-3f)
        }
    }

    @Test
    fun timeBranchOfOtherSourcesFormsTheInstrumental() {
        val sink = CollectingSink()
        StemSeparator(WaveformDrumsModel(), overlap = 0.1).separate(source(), sink)
        assertEquals(totalFrames, sink.frames)
        for (i in 0 until totalFrames step 41) {
            assertEquals("instrumental L $i", left(i), sink.instrumental[2 * i], 1e-4f)
            assertEquals("instrumental R $i", right(i), sink.instrumental[2 * i + 1], 1e-4f)
            assertEquals("vocals $i", MEAN, sink.vocals[2 * i], 1e-4f)
        }
    }

    @Test
    fun reportsProgressPerSegment() {
        val progress = ArrayList<SeparationProgress>()
        StemSeparator(WaveformDrumsModel()).separate(source(), CollectingSink(), onProgress = { progress += it })
        val segments = SegmentPlan(totalFrames.toLong(), segment, 0.25).count
        assertEquals(segments, progress.last().segmentsTotal)
        assertEquals(1f, progress.last().fraction, 0f)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.fraction >= a.fraction })
    }

    @Test
    fun cancellationStopsBeforeFinishing() {
        val sink = CollectingSink()
        var segmentsRun = 0
        val model = object : SeparationModel by WaveformDrumsModel() {
            override fun run(mix: FloatBuffer, mag: FloatBuffer, spec: FloatBuffer, wave: FloatBuffer) {
                segmentsRun++
            }
        }
        try {
            StemSeparator(model).separate(source(), sink, isCancelled = { segmentsRun >= 1 })
            fail("expected cancellation")
        } catch (_: SeparationCancelledException) {
        }
        assertEquals(1, segmentsRun)
        assertTrue(!sink.finished)
    }

    @Test(expected = SeparationException::class)
    fun emptySourceFails() {
        StemSeparator(WaveformDrumsModel()).separate({ }, CollectingSink())
    }

    private fun left(i: Int) = (0.4 * sin(2 * PI * 440 * i / 44100.0) + MEAN).toFloat()
    private fun right(i: Int) = (0.3 * sin(2 * PI * 660 * i / 44100.0 + 1) + MEAN).toFloat()

    /** Emits the test signal in uneven blocks. */
    private fun source() = PcmSource { consumer ->
        var offset = 0
        var block = 1000
        val buffer = FloatArray(10_000)
        while (offset < totalFrames) {
            val frames = minOf(block, totalFrames - offset)
            for (i in 0 until frames) {
                buffer[2 * i] = left(offset + i)
                buffer[2 * i + 1] = right(offset + i)
            }
            consumer(buffer, frames)
            offset += frames
            block = block * 3 % 4999 + 1
        }
    }

    private class CollectingSink : StemSink {
        val vocals = FloatArray(2_000_000)
        val instrumental = FloatArray(2_000_000)
        var frames = 0
        var finished = false

        override fun write(vocals: FloatArray, instrumental: FloatArray, frames: Int) {
            System.arraycopy(vocals, 0, this.vocals, this.frames * 2, frames * 2)
            System.arraycopy(instrumental, 0, this.instrumental, this.frames * 2, frames * 2)
            this.frames += frames
        }

        override fun finish() {
            finished = true
        }
    }

    /** Puts the whole input spectrogram into the vocals source, as a perfect separator of an a-cappella track would. */
    private class SpectralVocalsModel : SeparationModel {
        override fun run(mix: FloatBuffer, mag: FloatBuffer, spec: FloatBuffer, wave: FloatBuffer) {
            val size = mag.capacity()
            for (i in 0 until spec.capacity()) spec.put(i, 0f)
            for (i in 0 until size) spec.put(3 * size + i, mag.get(i))
            for (i in 0 until wave.capacity()) wave.put(i, 0f)
        }

        override fun close() = Unit
    }

    /** Returns the input waveform as the drums' time-branch output and nothing else. */
    private class WaveformDrumsModel : SeparationModel {
        override fun run(mix: FloatBuffer, mag: FloatBuffer, spec: FloatBuffer, wave: FloatBuffer) {
            for (i in 0 until spec.capacity()) spec.put(i, 0f)
            for (i in 0 until wave.capacity()) wave.put(i, if (i < mix.capacity()) mix.get(i) else 0f)
        }

        override fun close() = Unit
    }

    private companion object {
        const val MEAN = 0.05f
    }
}
