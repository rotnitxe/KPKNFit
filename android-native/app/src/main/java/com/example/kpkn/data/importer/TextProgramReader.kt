package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet

/**
 * Lector de texto para archivos y texto pegado, incluidos programas en Markdown. Conserva el contenido fuera de bloques
 * cercados; las vallas son marcas de formato. Cada línea no vacía es una fila:
 * una tabla Markdown da una celda por columna (sin la línea separadora), los encabezados y las viñetas pierden su marca,
 * el énfasis se quita y « / » separa celdas. Todo va en una sola hoja con [name]; lo que pasa de los topes de [ImportLimits]
 * (2000 filas y 40 columnas) se recorta con notas, como en xlsx.
 */
object TextProgramReader {
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val LIST_MARK = Regex("""^(?:[-*+]|\d+\.)\s+""")
    private val EMPHASIS = Regex("""[*_`]+""")
    private val RULE = Regex("""-{3,}|\*{3,}|_{3,}""")

    /** Lee [text] como una hoja llamada [name], recortada a los topes con notas. */
    fun read(text: String, name: String): ImportSheet {
        val lines = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val allRows = lines.mapIndexedNotNull { index, line ->
            if (line.trim().startsWith("```")) null else rowOf(line)?.let { (index + 1) to it }
        }
        val rows = allRows.take(ImportLimits.MAX_ROWS)
        val columnsCut = rows.any { (_, row) -> row.drop(ImportLimits.MAX_COLUMNS).any { it.isNotBlank() } }
        val rowsCut = allRows.size > rows.size
        val width = minOf(rows.maxOfOrNull { it.second.size } ?: 0, ImportLimits.MAX_COLUMNS)
        return ImportSheet(
            name = name,
            rows = rows.map { (_, row) -> List(width) { row.getOrElse(it) { "" } } },
            notes = listOfNotNull(TrimNotes.COLUMNS.takeIf { columnsCut }, TrimNotes.ROWS.takeIf { rowsCut }),
            sourceLineNumbers = rows.map { it.first },
        )
    }

    /** Celdas de una línea, o null si no aporta fila (vacía, separadora de tabla, regla horizontal o sin celdas). */
    private fun rowOf(line: String): List<String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || RULE.matches(trimmed)) return null
        if ('\t' in line) return line.split('\t').map { clean(it) }
        if (trimmed.startsWith("|")) {
            if (trimmed.all { it in "|:- " }) return null
            return trimmed.removePrefix("|").removeSuffix("|").split('|').map { clean(it) }
                .takeIf { cells -> cells.any { it.isNotEmpty() } }
        }
        val text = clean(LIST_MARK.replace(HEADING.replace(trimmed, ""), ""))
        return text.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }
    }

    private fun clean(text: String): String = EMPHASIS.replace(text, "").trim()
}
