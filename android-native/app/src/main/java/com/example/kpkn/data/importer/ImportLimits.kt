package com.example.kpkn.data.importer

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream

/** Error con mensaje para el usuario: el archivo no se puede leer o supera los límites del importador. */
class ImportFileException(message: String) : Exception(message)

/** Límites defensivos del importador de programas (archivos de entrenamiento, no hojas masivas). */
object ImportLimits {
    /** Tamaño máximo de una hoja, un CSV o un texto en disco (el PDF y la foto tienen sus propios topes). */
    const val MAX_FILE_BYTES: Long = 5L * 1024 * 1024

    /** Tamaño máximo de un PDF que se lee con OCR. */
    const val MAX_PDF_BYTES: Long = 20L * 1024 * 1024

    /** Tamaño máximo de una foto (JPEG, PNG, WebP o HEIC) que se lee con OCR. */
    const val MAX_IMAGE_BYTES: Long = 15L * 1024 * 1024

    /** Páginas de un PDF que se leen con OCR; con más, se leen estas y la hoja lo dice. */
    const val MAX_OCR_PAGES: Int = 30

    /** Lado largo máximo de una foto antes del OCR (la rotación EXIF se aplica después). */
    const val MAX_OCR_IMAGE_SIDE: Int = 2_000

    /** Tope de bytes descomprimidos de un .xlsx (protege frente a archivos comprimidos maliciosos). */
    const val MAX_UNCOMPRESSED_BYTES: Long = 16L * 1024 * 1024

    const val MAX_SHEETS: Int = 20
    const val MAX_ROWS: Int = 2_000
    const val MAX_COLUMNS: Int = 40
}

/** Lee el flujo entero; si pasa de [limit] bytes lanza [ImportFileException] con [tooLargeMessage]. */
internal fun readBoundedBytes(input: InputStream, limit: Long, tooLargeMessage: String): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw ImportFileException(tooLargeMessage)
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/** Envoltorio que corta la lectura en cuanto supera el tope. No cierra el flujo del llamador. */
internal class BoundedInputStream(
    input: InputStream,
    private val limit: Long,
    private val tooLargeMessage: String,
) : FilterInputStream(input) {
    private var total = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) count(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) count(read)
        return read
    }

    override fun close() {
        // El flujo lo cierra quien lo abrió; aquí solo se libera el lector ZIP.
    }

    private fun count(bytes: Int) {
        total += bytes
        if (total > limit) throw ImportFileException(tooLargeMessage)
    }
}
