package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet

/**
 * Lector de texto para .md y .txt, como los programas de Liftosaur en Markdown. Si el texto tiene bloques cercados
 * («```») con series × repeticiones, solo se leen esos bloques; si no, el texto entero. Cada línea no vacía es una fila:
 * una tabla Markdown da una celda por columna (sin la línea separadora), los encabezados y las viñetas pierden su marca,
 * el énfasis se quita y « / » separa celdas. Todo va en una sola hoja con [name] y con los topes de [ImportLimits].
 */
object TextProgramReader {
    private val NOTATION = Regex("""\d+\s*[x×]\s*\d+""", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val LIST_MARK = Regex("""^(?:[-*+]|\d+\.)\s+""")
    private val EMPHASIS = Regex("""[*_`]+""")
    private val RULE = Regex("""-{3,}|\*{3,}|_{3,}""")

    /** Lee [text] como una hoja llamada [name]. Lanza [ImportFileException] si pasa los topes de filas o columnas. */
    fun read(text: String, name: String): ImportSheet {
        val lines = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val notated = fencedBlocks(lines).filter { block -> block.any { NOTATION.containsMatchIn(it) } }
        val source = if (notated.isNotEmpty()) notated.flatten() else lines.filterNot { it.trim().startsWith("```") }
        val rows = source.mapNotNull { rowOf(it) }
        if (rows.size > ImportLimits.MAX_ROWS) {
            throw ImportFileException("La hoja «$name» tiene más de ${ImportLimits.MAX_ROWS} filas")
        }
        val width = rows.maxOfOrNull { it.size } ?: 0
        if (width > ImportLimits.MAX_COLUMNS) {
            throw ImportFileException("La hoja «$name» tiene más de ${ImportLimits.MAX_COLUMNS} columnas")
        }
        return ImportSheet(name = name, rows = rows.map { row -> List(width) { row.getOrElse(it) { "" } } })
    }

    /** Líneas de cada bloque cercado, sin la valla. Un bloque sin cerrar llega hasta el final del texto. */
    private fun fencedBlocks(lines: List<String>): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var open: MutableList<String>? = null
        for (line in lines) {
            val current = open
            val trimmed = line.trim()
            if (current == null) {
                if (trimmed.startsWith("```")) open = mutableListOf()
            } else if (trimmed.isNotEmpty() && trimmed.all { it == '`' }) {
                blocks.add(current)
                open = null
            } else {
                current.add(line)
            }
        }
        open?.let { blocks.add(it) }
        return blocks
    }

    /** Celdas de una línea, o null si no aporta fila (vacía, separadora de tabla, regla horizontal o sin celdas). */
    private fun rowOf(line: String): List<String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || RULE.matches(trimmed)) return null
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
