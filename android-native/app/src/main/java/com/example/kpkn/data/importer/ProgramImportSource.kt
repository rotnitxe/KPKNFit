package com.example.kpkn.data.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.kpkn.data.importer.ocr.OcrImportSource
import com.example.kpkn.domain.training.importer.ImportSheet
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val ODS_MIME = "application/vnd.oasis.opendocument.spreadsheet"
private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val UNREADABLE_MESSAGE = "No pudimos leer este archivo. Prueba a exportarlo como .xlsx o .csv."

/** Bytes que se miran para decidir el tipo de un archivo; las firmas más largas (WebP y HEIC) caben en ellos. */
private const val HEAD_BYTES = 16

private val PDF_SIGNATURE = "%PDF".toByteArray(Charsets.US_ASCII)
private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val OLE_SIGNATURE = byteArrayOf(
    0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
)
private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val RIFF_SIGNATURE = "RIFF".toByteArray(Charsets.US_ASCII)
private val WEBP_SIGNATURE = "WEBP".toByteArray(Charsets.US_ASCII)
private val FTYP_SIGNATURE = "ftyp".toByteArray(Charsets.US_ASCII)

/** Marcas de HEIC y HEIF, el formato de foto del teléfono. */
private val HEIF_BRANDS = listOf("heic", "heix", "hevc", "hevx", "mif1", "msf1").map { it.toByteArray(Charsets.US_ASCII) }

/** Tipos que ofrece el selector de «Elegir archivo»: hojas de cálculo (xlsx y ods), Word, CSV, texto, Markdown, PDF y fotos. */
val PROGRAM_IMPORT_MIME_TYPES: Array<String> = arrayOf(
    XLSX_MIME,
    ODS_MIME,
    DOCX_MIME,
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "text/markdown",
    "application/pdf",
    "image/*",
)

/** Qué camino toma un archivo elegido: OCR de un PDF, OCR de una foto o el lector de hojas y de texto. */
internal enum class ImportKind { PDF, IMAGE, OTHER }

/**
 * Lee las hojas de un archivo elegido. Corre en IO. El tipo sale de su firma y, sin firma conocida, de un MIME de imagen:
 * un PDF se lee con OCR ([OcrImportSource.readPdf], con [onProgress] por página) y una foto con
 * [OcrImportSource.readImage]; lo demás pasa por [ImportFileReader] con el tope de 5 MB. Los topes son 20 MB para un PDF y
 * 15 MB para una foto, y se comprueban recorriendo el archivo sin cargarlo en memoria. [cacheDir] es donde se copia un PDF
 * que el proveedor no deja abrir directamente. Los errores salen con el texto que ve la persona; un fallo inesperado de
 * lectura se resume en «No pudimos leer…».
 */
suspend fun readSheets(
    contentResolver: ContentResolver,
    uri: Uri,
    mime: String?,
    cacheDir: File? = null,
    onProgress: (page: Int, pages: Int) -> Unit = { _, _ -> },
): Result<List<ImportSheet>> = withContext(Dispatchers.IO) {
    try {
        val fileName = fileNameOf(contentResolver, uri)
        val title = ImportFileReader.titleOf(fileName)
        val peeked = contentResolver.openInputStream(uri)?.use { input -> peekWithinLimits(input, mime) }
            ?: return@withContext Result.failure(ImportFileException(UNREADABLE_MESSAGE))
        val sheets = when (peeked.kind) {
            ImportKind.PDF -> OcrImportSource.readPdf(contentResolver, uri, title, cacheDir, onProgress)
            ImportKind.IMAGE -> OcrImportSource.readImage(contentResolver, uri, title)
            ImportKind.OTHER -> ImportFileReader.read(checkNotNull(peeked.bytes), fileName, mime)
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
    withContext(Dispatchers.IO) { ImportFileReader.titleOf(fileNameOf(contentResolver, uri)) }

/** Nombre visible del archivo con su extensión; cadena vacía si el proveedor no lo da. */
private fun fileNameOf(contentResolver: ContentResolver, uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty()

/** Tipo de un archivo por su cabecera: un PDF por «%PDF»; una foto por su firma o, sin firma conocida, por un MIME de imagen. */
internal fun importKindOf(head: ByteArray, mime: String?): ImportKind = when {
    head.startsWithAt(0, PDF_SIGNATURE) -> ImportKind.PDF
    isImageHeader(head) -> ImportKind.IMAGE
    // Un ZIP o un OLE son hojas o documentos, nunca una foto, aunque el proveedor diga otra cosa.
    head.startsWithAt(0, ZIP_SIGNATURE) || head.startsWithAt(0, OLE_SIGNATURE) -> ImportKind.OTHER
    mime?.startsWith("image/") == true -> ImportKind.IMAGE
    else -> ImportKind.OTHER
}

private fun isImageHeader(head: ByteArray): Boolean =
    head.startsWithAt(0, JPEG_SIGNATURE) ||
        head.startsWithAt(0, PNG_SIGNATURE) ||
        (head.startsWithAt(0, RIFF_SIGNATURE) && head.startsWithAt(8, WEBP_SIGNATURE)) ||
        (head.startsWithAt(4, FTYP_SIGNATURE) && HEIF_BRANDS.any { head.startsWithAt(8, it) })

private fun ByteArray.startsWithAt(offset: Int, signature: ByteArray): Boolean =
    size >= offset + signature.size && signature.indices.all { this[offset + it] == signature[it] }

/** Lo que se sabe de un archivo tras mirar su cabecera: su tipo y, para una hoja o un texto, sus bytes. */
internal class Peeked(val kind: ImportKind, val bytes: ByteArray?)

/**
 * Mira la cabecera y comprueba el tope del tipo. Un PDF o una foto se recorren sin guardarlos, porque se leen después con
 * su uri; lo demás se lee entero, con el tope de 5 MB.
 */
internal fun peekWithinLimits(input: InputStream, mime: String?): Peeked {
    val stream = PushbackInputStream(input, HEAD_BYTES)
    val head = stream.readHead(HEAD_BYTES)
    stream.unread(head)
    val kind = importKindOf(head, mime)
    return when (kind) {
        ImportKind.PDF -> {
            checkSizeWithin(stream, ImportLimits.MAX_PDF_BYTES, OcrImportSource.PDF_TOO_LARGE_MESSAGE)
            Peeked(kind, bytes = null)
        }
        ImportKind.IMAGE -> {
            checkSizeWithin(stream, ImportLimits.MAX_IMAGE_BYTES, OcrImportSource.IMAGE_TOO_LARGE_MESSAGE)
            Peeked(kind, bytes = null)
        }
        ImportKind.OTHER -> Peeked(
            kind,
            bytes = readBoundedBytes(stream, ImportLimits.MAX_FILE_BYTES, ImportFileReader.TOO_LARGE_MESSAGE),
        )
    }
}

/** Los primeros [count] bytes del flujo (menos si el archivo es más corto). */
private fun InputStream.readHead(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var filled = 0
    while (filled < count) {
        val bytesRead = read(buffer, filled, count - filled)
        if (bytesRead < 0) break
        filled += bytesRead
    }
    return buffer.copyOf(filled)
}

/** Recorre el flujo sin guardarlo: si pasa de [limit] bytes, lanza [ImportFileException] con [tooLargeMessage]. */
private fun checkSizeWithin(input: InputStream, limit: Long, tooLargeMessage: String) {
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return
        total += read
        if (total > limit) throw ImportFileException(tooLargeMessage)
    }
}
