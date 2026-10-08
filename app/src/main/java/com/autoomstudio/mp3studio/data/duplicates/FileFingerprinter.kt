package com.autoomstudio.mp3studio.data.duplicates

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.autoomstudio.mp3studio.data.model.Song
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * A cheap content fingerprint: the file size plus a hash of its first and last [CHUNK_BYTES].
 * Enough to recognise exact copies without reading whole files.
 */
class FileFingerprinter(private val contentResolver: ContentResolver) {

    /** Null when the file can't be read. */
    fun fingerprint(song: Song): String? = fingerprint(song.uri)

    /** Null when the file can't be read. */
    fun fingerprint(uri: Uri): String? = try {
        contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream -> hash(stream.channel) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not fingerprint $uri", e)
        null
    }

    private fun hash(channel: FileChannel): String {
        val size = channel.size()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(size).array())
        val buffer = ByteBuffer.allocate(CHUNK_BYTES)
        digestRange(channel, 0L, minOf(size, CHUNK_BYTES.toLong()), buffer, digest)
        if (size > CHUNK_BYTES) {
            val tailStart = maxOf(CHUNK_BYTES.toLong(), size - CHUNK_BYTES)
            digestRange(channel, tailStart, size - tailStart, buffer, digest)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun digestRange(
        channel: FileChannel,
        start: Long,
        length: Long,
        buffer: ByteBuffer,
        digest: MessageDigest,
    ) {
        var position = start
        val end = start + length
        while (position < end) {
            buffer.clear()
            buffer.limit(minOf(buffer.capacity().toLong(), end - position).toInt())
            val read = channel.read(buffer, position)
            if (read <= 0) break
            buffer.flip()
            digest.update(buffer)
            position += read
        }
    }

    private companion object {
        const val TAG = "FileFingerprinter"
        const val CHUNK_BYTES = 64 * 1024
    }
}
