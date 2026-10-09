package com.example.kpkn.data.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.kpkn.domain.training.importer.ImportSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val TOO_LARGE_MESSAGE = "El archivo es demasiado grande (máx. 5 MB)."
private const val UNREADABLE_MESSAGE = "No pudimos leer este archivo. Prueba a exportarlo como .xlsx o .csv."

/** Tipos que ofrece el selector de «Elegir archivo»: Excel, CSV y texto. */
val PROGRAM_IMPORT_MIME_TYPES: Array<String> = arrayOf(
    XLSX_MIME,
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
)

/**
 * Lee las hojas de un archivo elegido: .xlsx con [XlsxReader]; csv o texto con [CsvReader]. Corre en IO. El tope de 5 MB y
 * los errores salen con el texto que ve la persona; un fallo inesperado de lectura se resume en «No pudimos leer…».
 */
suspend fun readSheets(contentResolver: ContentResolver, uri: Uri, mime: String?): Result<List<ImportSheet>> =
    withContext(Dispatchers.IO) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { input ->
                readBoundedBytes(input, ImportLimits.MAX_FILE_BYTES, TOO_LARGE_MESSAGE)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: ImportFileException) {
            return@withContext Result.failure(error)
        } catch (error: Exception) {
            null
        } ?: return@withContext Result.failure(ImportFileException(UNREADABLE_MESSAGE))
        val name = fileTitleOf(contentResolver, uri)
        try {
            val sheets = if (mime == XLSX_MIME || bytes.startsWithZipSignature()) {
                XlsxReader.read(ByteArrayInputStream(bytes))
            } else {
                listOf(CsvReader.parse(String(bytes, Charsets.UTF_8), name))
            }
            Result.success(sheets)
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
    withContext(Dispatchers.IO) { fileTitleOf(contentResolver, uri) }

/** Nombre visible del archivo sin extensión; cadena vacía si no hay nombre. */
private fun fileTitleOf(contentResolver: ContentResolver, uri: Uri): String {
    val name = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty()
    return name.substringBeforeLast('.', missingDelimiterValue = name).trim()
}

/** Un .xlsx es un ZIP: empieza por «PK». Sirve cuando el proveedor del archivo no da el tipo MIME. */
private fun ByteArray.startsWithZipSignature(): Boolean =
    size >= 2 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte()
