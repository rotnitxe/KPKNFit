package com.example.kpkn.data.importer

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream

/** Error con mensaje para el usuario: el archivo no se puede leer o supera los límites del importador. */
class ImportFileException(message: String) : Exception(message)

/** Límites defensivos del importador de programas (archivos de entrenamiento, no hojas masivas). */
object ImportLimits {
    /** Tamaño máximo del archivo en disco. */
    const val MAX_FILE_BYTES: Long = 5L * 1024 * 1024

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
