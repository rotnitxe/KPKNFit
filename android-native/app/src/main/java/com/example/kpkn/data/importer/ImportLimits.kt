package com.example.kpkn.data.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal

internal fun String.boundedImportDecimal(): BigDecimal? {
    val raw = trim()
    if (raw.length > 256) return null
    return raw.toBigDecimalOrNull()?.takeIf { it.scale() in -100..100 && it.precision() - it.scale() <= 30 }
}

/** Error con mensaje para el usuario: el archivo no se puede leer o supera los límites del importador. */
class ImportFileException(message: String) : Exception(message)

/** Límites defensivos del importador de programas (archivos de entrenamiento, no hojas masivas). */
object ImportLimits {
    const val MAX_ZIP_ENTRIES = 2_048
    const val MAX_DOM_DEPTH = 128
    const val MAX_PASTED_TEXT_CHARS = com.example.kpkn.domain.training.importer.MAX_IMPORT_TEXT_CHARS
    /** Tamaño máximo de una hoja, un CSV o un texto en disco (el PDF y la foto tienen sus propios topes). */
    const val MAX_FILE_BYTES: Long = 5L * 1024 * 1024

    /** Tope de bytes descomprimidos que se guardan de un libro (hojas, cadenas y estilos); protege la memoria del teléfono. */
    const val MAX_UNCOMPRESSED_BYTES: Long = 32L * 1024 * 1024

    /** Tamaño máximo de un PDF que se lee con OCR. */
    const val MAX_PDF_BYTES: Long = 20L * 1024 * 1024

    /** Tamaño máximo de una foto (JPEG, PNG, WebP o HEIC) que se lee con OCR. */
    const val MAX_IMAGE_BYTES: Long = 15L * 1024 * 1024

    /** Páginas de un PDF que se leen con OCR; con más, se leen estas y la hoja lo dice. */
    const val MAX_OCR_PAGES: Int = 30

    /** Lado largo máximo de una foto antes del OCR (la rotación EXIF se aplica después). */
    const val MAX_OCR_IMAGE_SIDE: Int = 2_000

    /** Tope de bytes descomprimidos que se leen y se descartan (imágenes, cálculo...); protege frente a las bombas ZIP. */
    const val MAX_DISCARDED_BYTES: Long = 128L * 1024 * 1024

    /** Hojas visibles que se leen; las de más se recortan con una nota. */
    const val MAX_SHEETS: Int = 20

    /** Filas que se leen de cada hoja; las de más se recortan con una nota. */
    const val MAX_ROWS: Int = 2_000

    /** Columnas que se leen de cada hoja; las de más se recortan con una nota. */
    const val MAX_COLUMNS: Int = 40
}

/**
 * Notas de lo que un lector recortó de una hoja o de un libro. Van en `ImportSheet.notes` y el analizador las convierte
 * en avisos de hoja (`SHEET_NOTE`).
 */
internal object TrimNotes {
    val COLUMNS: String = "Solo se leyeron las primeras ${ImportLimits.MAX_COLUMNS} columnas."
    val ROWS: String = "Solo se leyeron las primeras ${ImportLimits.MAX_ROWS} filas."

    /** Nota del libro: cuántas hojas visibles tiene y cuántas se leen. */
    fun sheets(visible: Int): String = "El libro tiene $visible hojas; se leyeron las ${ImportLimits.MAX_SHEETS} primeras."
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
