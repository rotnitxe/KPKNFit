package com.example.kpkn.data.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

object BoundedInput {
    const val MAX_JSON_BYTES = 64L * 1024 * 1024
    const val MAX_AVATAR_BYTES = 8L * 1024 * 1024
    const val MAX_DATABASE_BYTES = 512L * 1024 * 1024

    fun copy(input: InputStream, output: OutputStream, limit: Long): Long {
        require(limit >= 0)
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            total += count
            require(total <= limit) { "Import exceeds the permitted size" }
            output.write(buffer, 0, count)
        }
    }
    fun bytes(input: InputStream, limit: Long): ByteArray = ByteArrayOutputStream().use { output ->
        copy(input, output, limit)
        output.toByteArray()
    }
}
