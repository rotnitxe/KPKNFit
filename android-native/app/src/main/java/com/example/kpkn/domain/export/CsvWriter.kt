package com.example.kpkn.domain.export

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigDecimal

sealed interface CsvCell {
    data class Text(val value: String) : CsvCell
    data class Number(val value: Double) : CsvCell
    data object Empty : CsvCell
}

/** RFC 4180, UTF-8 con BOM y números independientes de la configuración regional. */
object CsvWriter {
    private val numericText = Regex("[+-]?(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)(?:[eE][+-]?[0-9]+)?")
    fun bytes(headers: List<String>, rows: Iterable<List<CsvCell>>): ByteArray = ByteArrayOutputStream().also { write(it, headers, rows) }.toByteArray()
    fun write(output: OutputStream, headers: List<String>, rows: Iterable<List<CsvCell>>) {
        require(headers.isNotEmpty())
        output.write(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        fun line(values: List<String>) { output.write((values.joinToString(",") { quote(it) } + "\r\n").toByteArray(Charsets.UTF_8)) }
        line(headers.map(::protect))
        rows.forEach { cells ->
            require(cells.size == headers.size) { "La fila no coincide con la cabecera CSV." }
            line(cells.map { cell -> when (cell) {
                is CsvCell.Text -> protect(cell.value)
                is CsvCell.Number -> { require(cell.value.isFinite()) { "Valor numérico no finito." }; BigDecimal.valueOf(cell.value).stripTrailingZeros().toPlainString() }
                CsvCell.Empty -> ""
            } })
        }
    }
    private fun protect(value: String): String {
        val prefix = value.trimStart { it.isWhitespace() || it == '\uFEFF' }
        val numeric = numericText.matches(prefix.trim())
        return if (prefix.firstOrNull() in listOf('=', '+', '-', '@') && !numeric) "'$value" else value
    }
    private fun quote(value: String): String = if (value.any { it == ',' || it == ';' || it == '\t' || it == '"' || it == '\r' || it == '\n' })
        "\"${value.replace("\"", "\"\"") }\"" else value
}
