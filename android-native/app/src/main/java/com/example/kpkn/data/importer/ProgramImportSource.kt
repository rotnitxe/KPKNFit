package com.example.kpkn.data.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.kpkn.domain.training.importer.ImportSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val ODS_MIME = "application/vnd.oasis.opendocument.spreadsheet"
private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val UNREADABLE_MESSAGE = "No pudimos leer este archivo. Prueba a exportarlo como .xlsx o .csv."

/** Tipos que ofrece el selector de «Elegir archivo»: hojas de cálculo (xlsx y ods), Word, CSV, texto, Markdown y PDF. */
val PROGRAM_IMPORT_MIME_TYPES: Array<String> = arrayOf(
    XLSX_MIME,
    ODS_MIME,
    DOCX_MIME,
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "text/markdown",
    "application/pdf",
)

/**
 * Lee las hojas de un archivo elegido con [ImportFileReader], que elige el lector por firma y extensión. Corre en IO.
 * El tope de 5 MB y los errores salen con el texto que ve la persona; un fallo inesperado de lectura se resume en
 * «No pudimos leer…».
 */
suspend fun readSheets(contentResolver: ContentResolver, uri: Uri, mime: String?): Result<List<ImportSheet>> =
    withContext(Dispatchers.IO) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { input ->
                readBoundedBytes(input, ImportLimits.MAX_FILE_BYTES, ImportFileReader.TOO_LARGE_MESSAGE)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: ImportFileException) {
            return@withContext Result.failure(error)
        } catch (error: Exception) {
            null
        } ?: return@withContext Result.failure(ImportFileException(UNREADABLE_MESSAGE))
        try {
            Result.success(ImportFileReader.read(bytes, fileNameOf(contentResolver, uri), mime))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: ImportFileException) {
            Result.failure(error)
        } catch (error: Exception) {
            Result.failure(ImportFileException(UNREADABLE_MESSAGE))
        }
    }

/** Título sugerido para un programa leído de [uri]: su nombre sin extensión (vacío si el proveedor no lo da). */
suspend fun programTitleOf(contentResolver: ContentResolver, uri: Uri): String =
    withContext(Dispatchers.IO) { ImportFileReader.titleOf(fileNameOf(contentResolver, uri)) }

/** Nombre visible del archivo con su extensión; cadena vacía si el proveedor no lo da. */
private fun fileNameOf(contentResolver: ContentResolver, uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty()
