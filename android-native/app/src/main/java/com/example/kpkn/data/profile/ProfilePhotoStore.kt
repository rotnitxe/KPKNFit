package com.example.kpkn.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Keeps the avatar portable and independent from a gallery/provider URI. */
object ProfilePhotoStore {
    const val STORAGE_TOKEN = "profile/avatar.jpg"

    private const val DIRECTORY = "profile"
    private const val FILE_NAME = "avatar.jpg"
    private const val MAX_DIMENSION = 512
    private const val MAX_ENCODED_BYTES = 512 * 1024

    fun saveFromUri(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo leer la imagen")
        return saveNormalizedBytes(context, bytes)
    }

    fun saveBase64(context: Context, encoded: String): String {
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .getOrElse { error("La imagen del respaldo no es válida") }
        return saveNormalizedBytes(context, bytes)
    }

    fun readBase64(context: Context, token: String?): String? {
        val file = resolve(context, token) ?: return null
        if (!file.isFile || file.length() > MAX_ENCODED_BYTES) return null
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    fun loadBitmap(context: Context, token: String?): Bitmap? =
        resolve(context, token)?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun delete(context: Context) {
        resolve(context, STORAGE_TOKEN)?.delete()
        File(context.applicationContext.filesDir, DIRECTORY).takeIf(File::isDirectory)?.let { directory ->
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    private fun saveNormalizedBytes(context: Context, sourceBytes: ByteArray): String {
        require(sourceBytes.isNotEmpty()) { "La imagen está vacía" }
        require(sourceBytes.size <= MAX_ENCODED_BYTES * 16) { "La imagen del respaldo es demasiado grande" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El archivo no contiene una imagen válida" }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_DIMENSION * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val source = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, options)
            ?: error("El archivo no contiene una imagen válida")
        val normalized = normalize(source)
        val encoded = encodeWithinLimit(normalized)
        val directory = File(context.applicationContext.filesDir, DIRECTORY).apply { mkdirs() }
        val target = File(directory, FILE_NAME)
        val temporary = File(directory, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { it.write(encoded) }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        if (normalized !== source) normalized.recycle()
        source.recycle()
        return STORAGE_TOKEN
    }

    private fun normalize(source: Bitmap): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= MAX_DIMENSION) return source
        val scale = MAX_DIMENSION.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun encodeWithinLimit(bitmap: Bitmap): ByteArray {
        var quality = 88
        var result: ByteArray
        do {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                "No se pudo comprimir la imagen"
            }
            result = output.toByteArray()
            quality -= 8
        } while (result.size > MAX_ENCODED_BYTES && quality >= 40)
        require(result.size <= MAX_ENCODED_BYTES) { "La imagen es demasiado grande" }
        return result
    }

    private fun resolve(context: Context, token: String?): File? {
        if (token.isNullOrBlank()) return null
        val normalized = token.replace('\\', '/')
        if (normalized != STORAGE_TOKEN) return null
        return File(context.applicationContext.filesDir, STORAGE_TOKEN)
    }
}
