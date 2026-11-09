package com.example.kpkn.data.importer

import android.content.ContentResolver
import android.net.Uri
import com.example.kpkn.domain.training.importer.ImportDocumentType
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private bounded copy of an imported photo/PDF, retained only while its durable editor draft exists. */
internal class ImportSourceMediaStore(filesDir: File) {
    private val directory = File(filesDir, "import-source-evidence")

    suspend fun retain(contentResolver: ContentResolver, uri: Uri, type: ImportDocumentType): String? = withContext(Dispatchers.IO) {
        val maxBytes = when (type) {
            ImportDocumentType.PDF, ImportDocumentType.URL_PDF -> ImportLimits.MAX_PDF_BYTES
            ImportDocumentType.IMAGE, ImportDocumentType.URL_IMAGE -> ImportLimits.MAX_IMAGE_BYTES
            else -> return@withContext null
        }
        val extension = if (type == ImportDocumentType.PDF || type == ImportDocumentType.URL_PDF) "pdf" else "image"
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val target = File(directory, "$id.$extension")
        val temporary = File(directory, "$id.tmp")
        try {
            val input = contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxBytes) { "El documento original supera el límite permitido." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            check(temporary.isFile && temporary.length() in 1..maxBytes) { "No se pudo conservar el original." }
            check(temporary.renameTo(target)) { "No se pudo conservar el original." }
            id
        } catch (_: Exception) {
            temporary.delete()
            target.delete()
            null
        }
    }

    suspend fun retainBytes(bytes: ByteArray?, type: ImportDocumentType): String? = withContext(Dispatchers.IO) {
        val maxBytes = when (type) {
            ImportDocumentType.PDF, ImportDocumentType.URL_PDF -> ImportLimits.MAX_PDF_BYTES
            ImportDocumentType.IMAGE, ImportDocumentType.URL_IMAGE -> ImportLimits.MAX_IMAGE_BYTES
            else -> return@withContext null
        }
        if (bytes == null || bytes.isEmpty() || bytes.size.toLong() > maxBytes) return@withContext null
        val extension = if (type == ImportDocumentType.PDF || type == ImportDocumentType.URL_PDF) "pdf" else "image"
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val target = File(directory, "$id.$extension")
        val temporary = File(directory, "$id.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                ByteArrayInputStream(bytes).use { it.copyTo(output) }
                output.fd.sync()
            }
            check(temporary.isFile && temporary.length() in 1..maxBytes) { "No se pudo conservar el original." }
            check(temporary.renameTo(target)) { "No se pudo conservar el original." }
            id
        } catch (_: Exception) {
            temporary.delete()
            target.delete()
            null
        }
    }

    fun fileFor(id: String?, type: ImportDocumentType): File? {
        if (id == null || !UUID_PATTERN.matches(id)) return null
        val extension = when (type) {
            ImportDocumentType.PDF, ImportDocumentType.URL_PDF -> "pdf"
            ImportDocumentType.IMAGE, ImportDocumentType.URL_IMAGE -> "image"
            else -> return null
        }
        return File(directory, "$id.$extension").takeIf { it.isFile }
    }

    fun delete(id: String?) {
        if (id == null || !UUID_PATTERN.matches(id)) return
        File(directory, "$id.pdf").delete()
        File(directory, "$id.image").delete()
        File(directory, "$id.tmp").delete()
    }

    fun retainOnly(id: String?) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (id == null || !file.name.startsWith("$id.")) file.delete()
        }
    }

    private companion object {
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
