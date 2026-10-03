package com.autoomstudio.mplay.playback.lofi

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs [LofiEffect] inside the player's audio sink. It stays active for every supported format and
 * toggles only the effect's wet amount, so switching never reconfigures or flushes the sink.
 */
@OptIn(UnstableApi::class)
class LofiAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var enabled = false
    private var effect: LofiEffect? = null
    private var scratch = FloatArray(0)

    /** Safe from any thread; the playback thread picks it up on the next buffer. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val format = inputAudioFormat
        val effect = effect ?: return passThrough(inputBuffer)
        effect.enabled = enabled
        if (effect.isBypassed) return passThrough(inputBuffer)

        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(size)
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val sampleCount = size / if (isFloat) 4 else 2
        if (scratch.size < sampleCount) scratch = FloatArray(sampleCount)
        val samples = scratch

        if (isFloat) {
            for (i in 0 until sampleCount) samples[i] = input.getFloat()
        } else {
            for (i in 0 until sampleCount) samples[i] = input.getShort() / SHORT_SCALE
        }
        effect.process(samples, sampleCount / format.channelCount)
        if (isFloat) {
            for (i in 0 until sampleCount) output.putFloat(samples[i])
        } else {
            for (i in 0 until sampleCount) {
                output.putShort((samples[i] * SHORT_SCALE).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        val format = inputAudioFormat
        effect = if (format.channelCount > 0 && format.sampleRate > 0) {
            LofiEffect(format.sampleRate, format.channelCount, startEnabled = enabled)
        } else {
            null
        }
    }

    override fun onReset() {
        effect = null
        scratch = FloatArray(0)
    }

    private fun passThrough(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer)
        output.flip()
    }

    private companion object {
        const val SHORT_SCALE = 32768f
    }
}
