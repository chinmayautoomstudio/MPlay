package com.autoomstudio.mplay.separation.dsp

/** Converts interleaved audio with any channel count to interleaved stereo. */
object ChannelMixer {
    private const val SURROUND_GAIN = 0.70710677f
    private const val SURROUND_NORM = 1f / (1f + 2 * SURROUND_GAIN)

    /**
     * Mono is duplicated, stereo copied. 5.1 (Android's FL FR FC LFE BL BR order) is downmixed so the
     * centre channel, where vocals usually sit, is kept. Other layouts keep their first two channels, as Demucs does.
     */
    fun toStereo(input: FloatArray, channels: Int, frames: Int, output: FloatArray) {
        require(channels >= 1 && output.size >= frames * 2)
        when (channels) {
            1 -> for (i in 0 until frames) {
                val x = input[i]
                output[2 * i] = x
                output[2 * i + 1] = x
            }
            2 -> System.arraycopy(input, 0, output, 0, frames * 2)
            6 -> for (i in 0 until frames) {
                val b = i * 6
                val centre = input[b + 2] * SURROUND_GAIN
                output[2 * i] = (input[b] + centre + input[b + 4] * SURROUND_GAIN) * SURROUND_NORM
                output[2 * i + 1] = (input[b + 1] + centre + input[b + 5] * SURROUND_GAIN) * SURROUND_NORM
            }
            else -> for (i in 0 until frames) {
                output[2 * i] = input[i * channels]
                output[2 * i + 1] = input[i * channels + 1]
            }
        }
    }
}
