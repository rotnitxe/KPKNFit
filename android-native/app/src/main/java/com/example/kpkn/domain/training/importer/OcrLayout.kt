package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Una palabra leída por OCR con su caja, en píxeles de su página (el origen está arriba a la izquierda). */
data class OcrWord(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Una página leída por OCR: su tamaño en píxeles y sus palabras. */
data class OcrPage(
    val width: Int,
    val height: Int,
    val words: List<OcrWord>,
)

/**
 * Reconstruye las filas y las celdas de un texto leído por OCR a partir de la posición de sus palabras, sin Android.
 * Las palabras forman filas por su altura, cada fila se parte en celdas por sus huecos y las celdas se alinean en
 * columnas por los bordes izquierdos que se repiten en la página. Así una tabla «Ejercicio | Series | Reps» sale
 * alineada y el analizador encuentra su cabecera.
 */
object OcrLayout {
    /** Una columna solo cuenta si tiene celdas en, al menos, tantas filas de la página. */
    private const val MIN_ROWS_PER_COLUMN = 3

    /** Tolerancia para agrupar bordes y para asignar celdas a columnas: el 2 % del ancho de la página. */
    private const val COLUMN_TOLERANCE_RATIO = 0.02

    /** Filas de todas las páginas, en orden, cada una con sus celdas. */
    fun reconstruct(pages: List<OcrPage>): List<List<String>> = pages.flatMap { rowsOfPage(it) }

    /** Páginas que hacen falta, como mínimo, para que una fila repetida sea un mueble de página. */
    private const val FURNITURE_MIN_PAGES = 3

    /** Letras que debe tener el texto de una fila para poder ser un mueble de página. */
    private const val FURNITURE_MIN_LETTERS = 3

    /** Filas unidas de varias páginas y, para cada página, el índice de su primera fila (tras quitar los muebles). */
    data class Joined(val rows: List<List<String>>, val pageStarts: List<Int>)

    /**
     * Une las filas de varias páginas quitando los muebles de página. Un mueble es una fila de las 2 primeras o de las 2
     * últimas con texto de su página que, sin cifras ni signos, se repite en el 40 % de las páginas o más (y en 3 como
     * mínimo). Nunca lo son las filas con contenido ([ProgramImportParser.pageContentRows]): estructura, notación, %,
     * datos de tabla, nombres de prescripciones sueltas y títulos encima de ejercicios.
     */
    fun joinPages(pages: List<List<List<String>>>): Joined {
        val candidates = pages.map { page -> furnitureCandidates(page) }
        val pagesWithKey = HashMap<String, Int>()
        for ((page, positions) in pages.zip(candidates)) {
            for (key in positions.map { furnitureKey(page[it]) }.filter { it.isNotEmpty() }.toSet()) {
                pagesWithKey[key] = (pagesWithKey[key] ?: 0) + 1
            }
        }
        val furniture = pagesWithKey.filter { (_, count) ->
            count >= FURNITURE_MIN_PAGES && count * 5 >= pages.size * 2
        }.keys
        val rows = ArrayList<List<String>>()
        val pageStarts = ArrayList<Int>()
        for ((page, positions) in pages.zip(candidates)) {
            pageStarts += rows.size
            page.forEachIndexed { index, row ->
                val mark = index in positions && furnitureKey(row) in furniture
                if (!mark) rows += row
            }
        }
        return Joined(rows, pageStarts)
    }

    /** Filas de una página que pueden ser muebles: las 2 primeras y las 2 últimas con texto, menos las que tienen contenido. */
    private fun furnitureCandidates(page: List<List<String>>): Set<Int> {
        val filled = page.indices.filter { index -> page[index].any { it.isNotBlank() } }
        val edges = (filled.take(2) + filled.takeLast(2)).toSet()
        return edges - ProgramImportParser.pageContentRows(page)
    }

    /**
     * Texto de una fila para comparar páginas: minúsculas, sin cifras ni signos. Una fila con menos de 3 letras («4x 15-12-10-8»
     * queda en «x») no es un mueble: es una prescripción que se repite por estar en cada página.
     */
    private fun furnitureKey(row: List<String>): String {
        val key = ExerciseMatchLexicon.normalize(row.joinToString(" "))
            .split(' ')
            .map { word -> word.filter { it.isLetter() } }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return if (key.count { it.isLetter() } >= FURNITURE_MIN_LETTERS) key else ""
    }

    private fun rowsOfPage(page: OcrPage): List<List<String>> {
        val words = page.words.mapNotNull { word ->
            val text = word.text.trim()
            if (text.isEmpty() || isNoise(text)) null else word.copy(text = text)
        }
        if (words.isEmpty()) return emptyList()
        val wordHeight = max(1.0, median(words.map { (it.bottom - it.top).toDouble() }))
        val charWidth = max(1.0, words.sumOf { (it.right - it.left).toDouble() } / words.sumOf { it.text.length })
        val cellRows = groupRows(words, wordHeight).map { cellsOf(it, wordHeight, charWidth) }
        val tolerance = max(1.0, COLUMN_TOLERANCE_RATIO * page.width)
        val anchors = columnAnchors(cellRows, tolerance)
        return cellRows.map { cells -> placeInColumns(cells, anchors, tolerance) }
    }

    /** Una sola marca que no es letra ni cifra («|», «•», «-») no es texto del programa. */
    private fun isNoise(text: String): Boolean = text.length == 1 && !text[0].isLetterOrDigit()

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun centerY(word: OcrWord): Double = (word.top + word.bottom) / 2.0

    /**
     * Filas en orden vertical, cada una de izquierda a derecha. Una palabra entra en la fila abierta si su centro dista
     * menos de media altura de palabra del centro de esa fila; si no, abre otra.
     */
    private fun groupRows(words: List<OcrWord>, wordHeight: Double): List<List<OcrWord>> {
        val rows = mutableListOf<MutableList<OcrWord>>()
        var openCenterSum = 0.0
        for (word in words.sortedBy { centerY(it) }) {
            val center = centerY(word)
            val open = rows.lastOrNull()
            if (open != null && abs(center - openCenterSum / open.size) < 0.5 * wordHeight) {
                open.add(word)
                openCenterSum += center
            } else {
                rows.add(mutableListOf(word))
                openCenterSum = center
            }
        }
        return rows.map { row -> row.sortedBy { it.left } }
    }

    /** Una celda de una fila: sus palabras unidas con espacio y el borde izquierdo de la primera. */
    private class Cell(val left: Int, val text: String)

    /**
     * Celdas de una fila: un hueco entre palabras mayor que max(1 px, 2 alturas, 2,5 caracteres) abre una celda nueva.
     */
    private fun cellsOf(row: List<OcrWord>, wordHeight: Double, charWidth: Double): List<Cell> {
        val gap = max(1.0, max(2 * wordHeight, 2.5 * charWidth))
        val groups = mutableListOf<MutableList<OcrWord>>()
        var previous: OcrWord? = null
        for (word in row) {
            val last = previous
            if (last == null || (word.left - last.right).toDouble() > gap) {
                groups.add(mutableListOf(word))
            } else {
                groups.last().add(word)
            }
            previous = word
        }
        return groups.map { group -> Cell(left = group.first().left, text = group.joinToString(" ") { it.text }) }
    }

    /**
     * Anclas de columna: los bordes izquierdos de todas las celdas de la página, agrupados a una distancia de [tolerance].
     * Solo cuenta un grupo con celdas en al menos [MIN_ROWS_PER_COLUMN] filas; su ancla es el promedio de sus bordes.
     */
    private fun columnAnchors(rows: List<List<Cell>>, tolerance: Double): List<Int> {
        val edges = rows.flatMapIndexed { rowIndex, cells -> cells.map { cell -> cell.left to rowIndex } }
            .sortedBy { it.first }
        val anchors = mutableListOf<Int>()
        val group = mutableListOf<Pair<Int, Int>>()
        fun closeGroup() {
            if (group.map { it.second }.distinct().size >= MIN_ROWS_PER_COLUMN) {
                anchors.add(group.map { it.first }.average().roundToInt())
            }
            group.clear()
        }
        for (edge in edges) {
            if (group.isNotEmpty() && (edge.first - group.last().first).toDouble() > tolerance) closeGroup()
            group.add(edge)
        }
        if (group.isNotEmpty()) closeGroup()
        return anchors
    }

    /**
     * Cada celda va a la última ancla que no queda a la derecha de su borde (con la tolerancia), o a la primera si no hay
     * ninguna. Las columnas sin celda quedan vacías; dos celdas en la misma columna se unen con espacio. Sin anclas, la
     * fila sale con sus celdas tal cual.
     */
    private fun placeInColumns(cells: List<Cell>, anchors: List<Int>, tolerance: Double): List<String> {
        if (anchors.isEmpty()) return cells.map { it.text }
        val row = MutableList(anchors.size) { "" }
        for (cell in cells) {
            val index = anchors.indexOfLast { anchor -> anchor.toDouble() <= cell.left + tolerance }.coerceAtLeast(0)
            row[index] = if (row[index].isEmpty()) cell.text else "${row[index]} ${cell.text}"
        }
        return row
    }
}
