package com.example.kpkn.data.importer

import android.content.ContentResolver
import android.net.Uri
import com.example.kpkn.domain.training.importer.ImportDocumentType
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private bounded copy of an imported photo/PDF, retained only while its durable editor draft exists. */
internal class ImportSourceMediaStore(filesDir: File) {
    private val directory = File(filesDir, "import-source-evidence")

    suspend fun retain(contentResolver: ContentResolver, uri: Uri, type: ImportDocumentType): String? =
        retainSource(type) { output, maxBytes ->
            val input = contentResolver.openInputStream(uri) ?: return@retainSource false
            input.use { source ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) return@retainSource false
                    output.write(buffer, 0, count)
                }
            }
            true
        }

    suspend fun retainBytes(bytes: ByteArray?, type: ImportDocumentType): String? =
        retainSource(type) { output, maxBytes ->
            if (bytes == null || bytes.isEmpty() || bytes.size > maxBytes) return@retainSource false
            output.write(bytes)
            true
        }

    private suspend fun retainSource(
        type: ImportDocumentType,
        write: suspend (FileOutputStream, Long) -> Boolean,
    ): String? {
        val maxBytes = when (type) {
            ImportDocumentType.PDF, ImportDocumentType.URL_PDF -> ImportLimits.MAX_PDF_BYTES
            ImportDocumentType.IMAGE, ImportDocumentType.URL_IMAGE -> ImportLimits.MAX_IMAGE_BYTES
            else -> return null
        }
        val extension = if (type == ImportDocumentType.PDF || type == ImportDocumentType.URL_PDF) "pdf" else "image"
        val id = UUID.randomUUID().toString()
        val target = File(directory, "$id.$extension")
        val temporary = File(directory, "$id.tmp")
        return try {
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                try {
                    FileOutputStream(temporary).use { output ->
                        if (!write(output, maxBytes)) return@withContext null
                        output.fd.sync()
                    }
                    currentCoroutineContext().ensureActive()
                    if (temporary.length() !in 1..maxBytes || !temporary.renameTo(target)) {
                        return@withContext null
                    }
                    id
                } finally {
                    temporary.delete()
                }
            }
        } catch (cancel: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { target.delete() }
            throw cancel
        } catch (_: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { target.delete() }
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
