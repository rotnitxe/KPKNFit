package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Lector de hojas de cálculo OpenDocument (.ods): un ZIP con content.xml, leído con las protecciones de [XlsxReader].
 * Cada `table:table` es una hoja con su nombre. Las repeticiones de filas y de columnas se respetan, pero solo se
 * materializa lo que tiene valor: una columna vacía repetida no se expande. Porcentajes («25%»), horas («2:00») y
 * decimales salen como texto legible; lo demás, el texto de la celda. Los topes son los de [ImportLimits].
 */
object OdsReader {
    private const val CONTENT = "content.xml"
    private const val MAX_SPACES = 100
    private val DURATION = Regex("""(-?)P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?""")

    /** Hojas del libro en su orden. Lanza [ImportFileException] si no es una hoja de cálculo o si supera los topes. */
    fun read(bytes: ByteArray): List<ImportSheet> {
        val entries = OfficeXml.entries(bytes, ARCHIVE_TOO_LARGE) ?: throw notOds()
        val content = entries[CONTENT] ?: throw notOds()
        val spreadsheet = OfficeXml.parse(content, "El libro contiene XML no válido")
            .getElementsByTagNameNS("*", "spreadsheet").item(0) ?: throw notOds()
        val tables = OfficeXml.childElements(spreadsheet).filter { OfficeXml.name(it) == "table" }
        if (tables.size > ImportLimits.MAX_SHEETS) {
            throw ImportFileException("El libro tiene más de ${ImportLimits.MAX_SHEETS} hojas")
        }
        return tables.mapIndexed { index, table ->
            val name = OfficeXml.attribute(table, "name")?.takeIf { it.isNotBlank() } ?: "Hoja ${index + 1}"
            ImportSheet(name = name, rows = readTable(name, table))
        }
    }

    /**
     * Filas con datos de una tabla. La última fila y la última columna con valor marcan el tamaño, como en [XlsxReader];
     * las filas y columnas vacías repetidas se cuentan sin materializarse.
     */
    private fun readTable(name: String, table: Element): List<List<String>> {
        val values = HashMap<Int, Map<Int, String>>()
        var rowIndex = 0
        var maxRow = -1
        var maxCol = -1
        for (row in tableRows(table)) {
            val repeat = repeatOf(row, "number-rows-repeated", ImportLimits.MAX_ROWS + 1)
            val cells = HashMap<Int, String>()
            var col = 0
            for (cell in rowCells(row)) {
                val span = repeatOf(cell, "number-columns-repeated", ImportLimits.MAX_COLUMNS + 1)
                val text = cellText(cell)
                if (text.isNotBlank()) {
                    if (col + span > ImportLimits.MAX_COLUMNS) {
                        throw ImportFileException("La hoja «$name» tiene más de ${ImportLimits.MAX_COLUMNS} columnas")
                    }
                    for (c in col until col + span) cells[c] = text
                    maxCol = maxOf(maxCol, col + span - 1)
                }
                col += span
            }
            if (cells.isNotEmpty()) {
                if (rowIndex + repeat > ImportLimits.MAX_ROWS) {
                    throw ImportFileException("La hoja «$name» tiene más de ${ImportLimits.MAX_ROWS} filas")
                }
                for (r in rowIndex until rowIndex + repeat) values[r] = cells
                maxRow = maxOf(maxRow, rowIndex + repeat - 1)
            }
            // Con el tope, un hueco más largo que el límite sigue sin dejar pasar datos (se rechazan al llegar).
            rowIndex = minOf(rowIndex + repeat, ImportLimits.MAX_ROWS + 1)
        }
        if (maxRow < 0) return emptyList()
        val width = maxCol + 1
        return (0..maxRow).map { r -> List(width) { c -> values[r]?.get(c).orEmpty() } }
    }

    /** Filas de una tabla en orden, también dentro de grupos o de cabeceras de filas. */
    private fun tableRows(parent: Element): List<Element> = OfficeXml.childElements(parent).flatMap { child ->
        when (OfficeXml.name(child)) {
            "table-row" -> listOf(child)
            "table-row-group", "table-header-rows", "table-rows" -> tableRows(child)
            else -> emptyList()
        }
    }

    /** Celdas de una fila; una celda cubierta por una combinación ocupa su columna. */
    private fun rowCells(row: Element): List<Element> = OfficeXml.childElements(row).filter {
        OfficeXml.name(it) == "table-cell" || OfficeXml.name(it) == "covered-table-cell"
    }

    /** Veces que se repite una fila o una celda (al menos 1), con [cap] como tope para no expandir de más. */
    private fun repeatOf(element: Element, attribute: String, cap: Int): Int =
        (OfficeXml.attribute(element, attribute)?.toLongOrNull() ?: 1L).coerceIn(1L, cap.toLong()).toInt()

    /** Texto de una celda según su tipo de valor; sin un valor que convertir, el texto de sus párrafos. */
    private fun cellText(cell: Element): String {
        val value = OfficeXml.attribute(cell, "value")
        val converted = when (OfficeXml.attribute(cell, "value-type")) {
            "float", "currency" -> value?.let { plainDecimal(it) }
            "percentage" -> value?.let { percent(it) }
            "time" -> OfficeXml.attribute(cell, "time-value")?.let { duration(it) }
            "boolean" -> OfficeXml.attribute(cell, "boolean-value")?.let { if (it == "true") "TRUE" else "FALSE" }
            else -> null
        }
        return converted ?: paragraphs(cell)
    }

    /** Número sin ceros sobrantes («80.50» → «80.5»). */
    private fun plainDecimal(value: String): String? = value.trim().toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()

    /** Porcentaje: el valor por cien con «%» («0.25» → «25%»). */
    private fun percent(value: String): String? =
        value.trim().toBigDecimalOrNull()?.movePointRight(2)?.stripTrailingZeros()?.toPlainString()?.plus("%")

    /** Duración ISO («PT0H2M0S») como «m:ss» o «h:mm:ss»; null si no es una duración positiva. */
    private fun duration(value: String): String? {
        val match = DURATION.matchEntire(value.trim()) ?: return null
        if (match.groupValues[1].isNotEmpty()) return null
        val days = match.groupValues[2].toLongOrNull() ?: 0L
        val hours = match.groupValues[3].toLongOrNull() ?: 0L
        val minutes = match.groupValues[4].toLongOrNull() ?: 0L
        val seconds = match.groupValues[5].toDoubleOrNull() ?: 0.0
        val total = days * 86_400 + hours * 3_600 + minutes * 60 + Math.round(seconds)
        val ss = (total % 60).toString().padStart(2, '0')
        return if (total < 3_600) {
            "${total / 60}:$ss"
        } else {
            "${total / 3_600}:${(total / 60 % 60).toString().padStart(2, '0')}:$ss"
        }
    }

    /** Texto de los párrafos de una celda, unidos con espacio. */
    private fun paragraphs(cell: Element): String =
        textBlocks(cell).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    /** Párrafos y encabezados de un nodo (también dentro de listas), en orden. */
    private fun textBlocks(parent: Element): List<String> = OfficeXml.childElements(parent).flatMap { child ->
        when (OfficeXml.name(child)) {
            "p", "h" -> listOf(spanText(child))
            "list", "list-item" -> textBlocks(child)
            else -> emptyList()
        }
    }

    /** Texto de un nodo: sus nodos de texto, con `text:s` como espacios y tabulaciones o saltos como un espacio. */
    private fun spanText(node: Element): String {
        val out = StringBuilder()
        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                when (OfficeXml.name(child)) {
                    "s" -> out.append(" ".repeat(OfficeXml.attribute(child, "c")?.toIntOrNull()?.coerceIn(1, MAX_SPACES) ?: 1))
                    "tab", "line-break" -> out.append(' ')
                    // Comentarios y notas al pie no son el contenido de la celda.
                    "annotation", "annotation-end", "note" -> Unit
                    else -> out.append(spanText(child))
                }
            } else if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                out.append(child.nodeValue.orEmpty())
            }
        }
        return out.toString()
    }

    private fun notOds(): ImportFileException = ImportFileException("No es una hoja de cálculo OpenDocument (.ods) válida")

    private const val ARCHIVE_TOO_LARGE = "El archivo descomprimido es demasiado grande"
}
