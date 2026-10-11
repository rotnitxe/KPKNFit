package com.example.kpkn.data.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Error con mensaje para el usuario: el archivo no se puede leer o supera los límites del importador. */
class ImportFileException(message: String) : Exception(message)

/** Límites defensivos del importador de programas (archivos de entrenamiento, no hojas masivas). */
object ImportLimits {
    /** Tamaño máximo del archivo en disco. */
    const val MAX_FILE_BYTES: Long = 5L * 1024 * 1024

    /** Tope de bytes descomprimidos que se guardan de un libro (hojas, cadenas y estilos); protege la memoria del teléfono. */
    const val MAX_UNCOMPRESSED_BYTES: Long = 32L * 1024 * 1024

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
