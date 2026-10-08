package com.autoomstudio.mp3studio.separation.android

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import com.autoomstudio.mp3studio.separation.dsp.DemucsSpectrogram
import com.autoomstudio.mp3studio.separation.pipeline.SeparationError
import com.autoomstudio.mp3studio.separation.pipeline.SeparationException
import com.autoomstudio.mp3studio.separation.pipeline.SeparationModel
import java.nio.ByteBuffer
import java.nio.FloatBuffer

enum class Accelerator { Cpu, Xnnpack, Nnapi }

data class ModelOptions(
    val threads: Int = DEFAULT_THREADS,
    val accelerator: Accelerator = Accelerator.Cpu,
) {
    companion object {
        const val DEFAULT_THREADS = 4
    }
}

/** HT-Demucs on ONNX Runtime. Inputs and outputs are bound to the caller's direct buffers, so nothing is copied. */
class OrtDemucsModel private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) : SeparationModel {

    override fun run(mix: FloatBuffer, mag: FloatBuffer, spec: FloatBuffer, wave: FloatBuffer) {
        try {
            OnnxTensor.createTensor(env, mix.rewound(), MIX_SHAPE).use { mixTensor ->
                OnnxTensor.createTensor(env, mag.rewound(), MAG_SHAPE).use { magTensor ->
                    OnnxTensor.createTensor(env, spec.rewound(), SPEC_SHAPE).use { specTensor ->
                        OnnxTensor.createTensor(env, wave.rewound(), WAVE_SHAPE).use { waveTensor ->
                            session.run(
                                mapOf(INPUT_MIX to mixTensor, INPUT_MAG to magTensor),
                                mapOf(OUTPUT_SPEC to specTensor, OUTPUT_WAVE to waveTensor),
                            ).close()
                        }
                    }
                }
            }
        } catch (e: OrtException) {
            throw e.toSeparationException()
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        private const val INPUT_MIX = "mix"
        private const val INPUT_MAG = "mag"
        private const val OUTPUT_SPEC = "spec"
        private const val OUTPUT_WAVE = "wave"
        private const val SEGMENT = DemucsSpectrogram.SEGMENT.toLong()
        private const val FREQS = DemucsSpectrogram.FREQS.toLong()
        private val FRAMES = DemucsSpectrogram.framesFor(DemucsSpectrogram.SEGMENT).toLong()
        private val MIX_SHAPE = longArrayOf(1, 2, SEGMENT)
        private val MAG_SHAPE = longArrayOf(1, 4, FREQS, FRAMES)
        private val SPEC_SHAPE = longArrayOf(1, 4, 4, FREQS, FRAMES)
        private val WAVE_SHAPE = longArrayOf(1, 4, 2, SEGMENT)

        /** [model] should be a mapped buffer from [ModelMapper]. */
        fun create(model: ByteBuffer, options: ModelOptions = ModelOptions()): OrtDemucsModel {
            val env = OrtEnvironment.getEnvironment()
            try {
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Measured on one segment: pattern planning plus the arena peak near 3.9 GB, without them 2.4 GB.
                    setMemoryPatternOptimization(false)
                    setCPUArenaAllocator(false)
                    when (options.accelerator) {
                        Accelerator.Cpu -> setIntraOpNumThreads(options.threads)
                        Accelerator.Xnnpack -> {
                            addXnnpack(mapOf("intra_op_num_threads" to options.threads.toString()))
                            // XNNPACK runs its own thread pool; ORT's would compete with it.
                            setIntraOpNumThreads(1)
                        }
                        Accelerator.Nnapi -> {
                            addNnapi()
                            setIntraOpNumThreads(options.threads)
                        }
                    }
                }
                return sessionOptions.use { OrtDemucsModel(env, env.createSession(model, it)) }
            } catch (e: OrtException) {
                throw e.toSeparationException()
            }
        }

        private fun FloatBuffer.rewound(): FloatBuffer = apply { clear() }

        private fun OrtException.toSeparationException(): SeparationException {
            val text = message.orEmpty()
            val error = if (text.contains("alloc", ignoreCase = true) || text.contains("memory", ignoreCase = true)) {
                SeparationError.OutOfMemory
            } else {
                SeparationError.ModelFailed
            }
            return SeparationException(error, "ONNX Runtime: $text", this)
        }
    }
}
