package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet
import java.io.InputStream

/**
 * Lector de CSV propio: detecta el separador (`;`, tabulador o `,`), respeta las comillas (también con saltos de
 * línea y `""`) y quita el BOM. Devuelve una sola hoja; todas las filas se rellenan hasta el ancho de la más ancha.
 */
object CsvReader {

    /** Lee el flujo completo (UTF-8) como una hoja llamada [name]. */
    fun read(input: InputStream, name: String): ImportSheet {
        val bytes = readBoundedBytes(input, ImportLimits.MAX_FILE_BYTES, "El archivo supera 5 MB")
        return parse(String(bytes, Charsets.UTF_8), name)
    }

    /** Parte un texto CSV en una hoja. Solo lanza [ImportFileException] si supera el límite de filas o columnas. */
    fun parse(text: String, name: String): ImportSheet {
        val clean = text.removePrefix("﻿")
        val separator = detectSeparator(clean)
        val records = splitRecords(clean, separator).dropLastWhile { it.isEmpty() }
        if (records.size > ImportLimits.MAX_ROWS) {
            throw ImportFileException("La hoja «$name» tiene más de ${ImportLimits.MAX_ROWS} filas")
        }
        val width = records.maxOfOrNull { it.size } ?: 0
        if (width > ImportLimits.MAX_COLUMNS) {
            throw ImportFileException("La hoja «$name» tiene más de ${ImportLimits.MAX_COLUMNS} columnas")
        }
        return ImportSheet(
            name = name,
            rows = records.map { row -> List(width) { index -> row.getOrElse(index) { "" } } },
        )
    }

    /** Separador que más aparece fuera de comillas en las primeras líneas; en empate gana `;`, luego tabulador. */
    private fun detectSeparator(text: String): Char {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(10).joinToString("\n")
        val counts = linkedMapOf(';' to 0, '\t' to 0, ',' to 0)
        var inQuotes = false
        for (char in sample) {
            if (char == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes && counts.containsKey(char)) {
                counts[char] = counts.getValue(char) + 1
            }
        }
        val best = counts.maxByOrNull { it.value }
        return if (best == null || best.value == 0) ',' else best.key
    }

    /** Divide el texto en registros y campos. Una línea vacía es un registro sin campos. */
    private fun splitRecords(text: String, separator: Char): List<List<String>> {
        val records = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun endRecord() {
            if (fields.isEmpty() && field.isEmpty()) {
                records.add(emptyList())
            } else {
                fields.add(field.toString())
                records.add(fields)
            }
            fields = ArrayList()
            field.clear()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                inQuotes && char == '"' -> inQuotes = false
                inQuotes -> field.append(char)
                char == '"' -> inQuotes = true
                char == separator -> {
                    fields.add(field.toString())
                    field.clear()
                }
                char == '\r' || char == '\n' -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    endRecord()
                }
                else -> field.append(char)
            }
            index++
        }
        if (fields.isNotEmpty() || field.isNotEmpty()) endRecord()
        return records
    }
}
