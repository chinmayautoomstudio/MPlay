package com.autoomstudio.mplay.separation.pipeline

import java.io.IOException
import java.nio.FloatBuffer

/** The whole track as interleaved stereo float at 44.1 kHz. Each [read] decodes from the start again. */
fun interface PcmSource {
    /** Calls [consumer] with consecutive blocks; the array is reused after the call returns. */
    fun read(consumer: (interleaved: FloatArray, frames: Int) -> Unit)
}

/** Receives finished stem audio in order, as interleaved stereo float at 44.1 kHz. */
interface StemSink {
    fun write(vocals: FloatArray, instrumental: FloatArray, frames: Int)

    /** Called once after the last [write]; the outputs must be complete when it returns. */
    fun finish()
}

/**
 * The exported HT-Demucs graph for one segment (see tools/export_htdemucs.py).
 * [mix] is `[2][SEGMENT]`, [mag] `[4][2048][336]`; writes [spec] `[4 sources][4][2048][336]` and [wave]
 * `[4 sources][2][SEGMENT]`. Sources are drums, bass, other, vocals.
 */
interface SeparationModel : AutoCloseable {
    fun run(mix: FloatBuffer, mag: FloatBuffer, spec: FloatBuffer, wave: FloatBuffer)
}

enum class SeparationError {
    SourceMissing,
    UnsupportedFormat,
    CorruptFile,
    OutOfMemory,
    ModelUnavailable,
    ModelFailed,
    Storage,
    Unknown,
}

class SeparationException(
    val error: SeparationError,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class SeparationCancelledException : Exception("Separation cancelled")

data class SeparationProgress(
    /** 0..1 over the whole job. */
    val fraction: Float,
    val segmentsDone: Int,
    val segmentsTotal: Int,
    /** Null until at least one segment has been timed. */
    val remainingMs: Long?,
)
