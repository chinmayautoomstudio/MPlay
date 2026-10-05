package com.autoomstudio.mplay.separation.pipeline

import com.autoomstudio.mplay.separation.dsp.DemucsSpectrogram

/**
 * Separates a track into vocals and instrumental the way Demucs' `apply_model(shifts=0, split=True)` does,
 * but streaming: a first decode pass measures whole-track statistics, the second feeds segments to the
 * model and passes each stretch of output to the sink as soon as no later segment overlaps it.
 * Memory stays bounded by a few segments regardless of track length.
 */
class StemSeparator(
    model: SeparationModel,
    private val overlap: Double = DEFAULT_OVERLAP,
    private val clock: () -> Long = System::nanoTime,
) {
    private val processor = DemucsSegmentProcessor(model)
    private val segment = DemucsSpectrogram.SEGMENT

    /** Throws [SeparationCancelledException] soon after [isCancelled] turns true. */
    fun separate(
        source: PcmSource,
        sink: StemSink,
        isCancelled: () -> Boolean = { false },
        onProgress: (SeparationProgress) -> Unit = {},
    ) {
        val stats = TrackStats()
        source.read { block, frames ->
            if (isCancelled()) throw SeparationCancelledException()
            stats.add(block, frames)
        }
        val (mean, std) = stats.result()
        val plan = SegmentPlan(stats.frames, segment, overlap)
        if (plan.count == 0) throw SeparationException(SeparationError.CorruptFile, "No audio decoded")
        onProgress(SeparationProgress(DECODE_SHARE, 0, plan.count, null))

        val input = StereoWindow(plan.totalFrames, segment * 2)
        val output = OverlapAdd(plan, mean, std, sink)
        val windowLeft = FloatArray(segment)
        val windowRight = FloatArray(segment)
        var next = 0
        val started = clock()

        fun runSegment(index: Int) {
            if (isCancelled()) throw SeparationCancelledException()
            input.read(plan.windowStart(index), segment, windowLeft, windowRight)
            processor.process(windowLeft, windowRight)
            output.add(index, processor)
            input.discardBefore(plan.inputNeededFrom(index))
            val done = index + 1
            val elapsed = clock() - started
            val remainingMs = elapsed / done * (plan.count - done) / 1_000_000
            val fraction = DECODE_SHARE + (1 - DECODE_SHARE - FINISH_SHARE) * done / plan.count
            onProgress(SeparationProgress(fraction, done, plan.count, remainingMs))
        }

        source.read { block, frames ->
            if (isCancelled()) throw SeparationCancelledException()
            input.append(block, frames, mean, std)
            while (next < plan.count && input.end >= minOf(plan.windowStart(next) + segment, plan.totalFrames)) {
                runSegment(next++)
            }
        }
        // A decoder that returns fewer frames the second time leaves the rest silent instead of failing.
        while (next < plan.count) runSegment(next++)
        sink.finish()
        onProgress(SeparationProgress(1f, plan.count, plan.count, 0))
    }

    companion object {
        const val DEFAULT_OVERLAP = 0.25
        private const val DECODE_SHARE = 0.05f
        private const val FINISH_SHARE = 0.01f
    }
}

/** Weighted overlap-add of segment outputs, emitting finished frames denormalized and interleaved. */
internal class OverlapAdd(
    private val plan: SegmentPlan,
    private val mean: Float,
    private val std: Float,
    private val sink: StemSink,
) {
    private val size = plan.segment
    private val vocalsLeft = FloatArray(size)
    private val vocalsRight = FloatArray(size)
    private val instrumentalLeft = FloatArray(size)
    private val instrumentalRight = FloatArray(size)
    private val weights = FloatArray(size)
    private var vocalsOut = FloatArray(0)
    private var instrumentalOut = FloatArray(0)

    /** Absolute position of index 0 of the accumulators. */
    private var start = 0L

    fun add(index: Int, result: DemucsSegmentProcessor) {
        val offset = plan.offset(index)
        val length = plan.length(index)
        val trim = (plan.segment - length) / 2
        val base = (offset - start).toInt()
        for (i in 0 until length) {
            val w = plan.weight(i)
            val p = base + i
            vocalsLeft[p] += w * result.vocalsLeft[trim + i]
            vocalsRight[p] += w * result.vocalsRight[trim + i]
            instrumentalLeft[p] += w * result.instrumentalLeft[trim + i]
            instrumentalRight[p] += w * result.instrumentalRight[trim + i]
            weights[p] += w
        }
        emitUntil(plan.finalBefore(index))
    }

    private fun emitUntil(position: Long) {
        val frames = (position - start).toInt()
        if (frames <= 0) return
        if (vocalsOut.size < frames * 2) {
            vocalsOut = FloatArray(frames * 2)
            instrumentalOut = FloatArray(frames * 2)
        }
        for (i in 0 until frames) {
            val scale = std / weights[i]
            vocalsOut[2 * i] = vocalsLeft[i] * scale + mean
            vocalsOut[2 * i + 1] = vocalsRight[i] * scale + mean
            instrumentalOut[2 * i] = instrumentalLeft[i] * scale + mean
            instrumentalOut[2 * i + 1] = instrumentalRight[i] * scale + mean
        }
        sink.write(vocalsOut, instrumentalOut, frames)
        shift(frames)
        start = position
    }

    private fun shift(frames: Int) {
        for (array in arrayOf(vocalsLeft, vocalsRight, instrumentalLeft, instrumentalRight, weights)) {
            System.arraycopy(array, frames, array, 0, size - frames)
            array.fill(0f, size - frames, size)
        }
    }
}
