package com.example.kpkn.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import kotlin.coroutines.coroutineContext

/** The private server path is immutable by SHA256. No download token or signed public URL is stored. */
internal class ResumablePrivateDownload(private val open: suspend (offset: Long) -> HttpURLConnection) {
    suspend fun download(partial: File, bytes: Long, sha256: String) = withContext(Dispatchers.IO) {
        require(bytes in 1..MediaFiles.MAX_BYTES && sha256.matches(Regex("[a-f0-9]{64}")))
        OwnedStorageTasks.stream { registerCancel ->
            check(partial.parentFile!!.isDirectory || partial.parentFile!!.mkdirs() || partial.parentFile!!.isDirectory)
            if (partial.exists() && (partial.length() > bytes || partial.length() == bytes && MediaFiles.hash(partial) != sha256))
                check(partial.delete())
            if (partial.isFile && partial.length() == bytes && MediaFiles.hash(partial) == sha256) return@stream
            val offset = if (partial.isFile) partial.length() else 0L
            val connection = open(offset)
            registerCancel { connection.disconnect() }
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            try {
                coroutineContext.ensureActive()
                val status = connection.responseCode
                val start = when (status) {
                    HttpURLConnection.HTTP_OK -> 0L // A server may ignore Range; restart instead of appending.
                    HttpURLConnection.HTTP_PARTIAL -> {
                        val range = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                            ?: throw IOException("Invalid private download range")
                        val (first, last, total) = range.destructured
                        if (first.toLong() != offset || last.toLong() != bytes - 1 || total.toLong() != bytes)
                            throw IOException("Private download range changed")
                        offset
                    }
                    else -> throw IOException(if (status in setOf(401, 403)) "Private download authorization failed" else "Private download failed")
                }
                val remaining = bytes - start
                if (connection.contentLengthLong >= 0 && connection.contentLengthLong != remaining)
                    throw IOException("Private download size changed")
                connection.inputStream.use { input -> RandomAccessFile(partial, "rw").use { output ->
                    output.setLength(start)
                    output.seek(start)
                    var copied = 0L
                    val buffer = ByteArray(64 * 1024)
                    try {
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (copied + read > remaining) throw IOException("Private download exceeded its bound")
                            output.write(buffer, 0, read)
                            copied += read
                        }
                        if (copied != remaining) throw EOFException("Private download interrupted")
                    } finally { output.fd.sync() }
                } }
                coroutineContext.ensureActive()
                if (partial.length() != bytes || MediaFiles.hash(partial) != sha256) {
                    check(partial.delete())
                    throw IOException("Private download integrity failed")
                }
            } finally { connection.disconnect() }
        }
    }
}
