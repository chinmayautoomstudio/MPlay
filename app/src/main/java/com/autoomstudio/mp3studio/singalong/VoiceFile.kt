package com.autoomstudio.mp3studio.singalong

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Random access to a recorded take: raw mono 16-bit little-endian PCM at 44.1 kHz (SA15). */
class VoiceFile(file: File) : Closeable {
    private val access = RandomAccessFile(file, "r")
    private var bytes = ByteArray(0)

    val frames: Long = access.length() / BYTES_PER_FRAME

    /** Fills [out] with [count] frames starting at [start]; frames outside the take are silence. */
    fun read(start: Long, count: Int, out: FloatArray) {
        out.fill(0f, 0, count)
        val from = maxOf(start, 0L)
        val to = minOf(start + count, frames)
        if (from >= to) return
        val length = (to - from).toInt()
        if (bytes.size < length * BYTES_PER_FRAME) bytes = ByteArray(length * BYTES_PER_FRAME)
        access.seek(from * BYTES_PER_FRAME)
        access.readFully(bytes, 0, length * BYTES_PER_FRAME)
        val shorts = ByteBuffer.wrap(bytes, 0, length * BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val offset = (from - start).toInt()
        for (i in 0 until length) out[offset + i] = shorts.get(i) / 32768f
    }

    override fun close() = access.close()

    companion object {
        const val BYTES_PER_FRAME = 2
    }
}
