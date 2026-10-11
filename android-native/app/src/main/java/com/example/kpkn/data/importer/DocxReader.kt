package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Lector de documentos Word (.docx): un ZIP con word/document.xml. Todo va en una sola hoja con el nombre [title]:
 * cada párrafo con texto es una fila de una celda (sus `w:t` unidos), y cada tabla da sus filas, con una celda por
 * `w:tc` (los párrafos de la celda unidos con espacio). Una celda que continúa una combinación vertical va vacía, y una
 * combinada en horizontal ocupa una sola celda. Los topes son los de [ImportLimits].
 */
object DocxReader {
    private const val DOCUMENT = "word/document.xml"

    /** Lee el cuerpo del documento en orden. Lanza [ImportFileException] si no es un .docx o supera los topes. */
    fun read(bytes: ByteArray, title: String): ImportSheet {
        val entries = OfficeXml.entries(bytes, "El archivo descomprimido es demasiado grande") ?: throw notDocx()
        val xml = entries[DOCUMENT] ?: throw notDocx()
        val body = OfficeXml.parse(xml, "El documento contiene XML no válido").getElementsByTagNameNS("*", "body").item(0)
        val rows = body?.let { bodyRows(it) }.orEmpty()
        if (rows.size > ImportLimits.MAX_ROWS) {
            throw ImportFileException("La hoja «$title» tiene más de ${ImportLimits.MAX_ROWS} filas")
        }
        val width = rows.maxOfOrNull { it.size } ?: 0
        if (width > ImportLimits.MAX_COLUMNS) {
            throw ImportFileException("La hoja «$title» tiene más de ${ImportLimits.MAX_COLUMNS} columnas")
        }
        return ImportSheet(name = title, rows = rows.map { row -> List(width) { row.getOrElse(it) { "" } } })
    }

    /** Filas del cuerpo en orden: párrafos con texto y filas de tablas. Los bloques de contenido (`w:sdt`) se abren. */
    private fun bodyRows(container: Node): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        for (child in OfficeXml.childElements(container)) {
            when (OfficeXml.name(child)) {
                "p" -> paragraphText(child).takeIf { it.isNotEmpty() }?.let { rows.add(listOf(it)) }
                "tbl" -> rows.addAll(tableRows(child))
                "sdt", "sdtContent" -> rows.addAll(bodyRows(child))
            }
        }
        return rows
    }

    /** Una fila por `w:tr`, con una celda por `w:tc`. */
    private fun tableRows(table: Element): List<List<String>> =
        OfficeXml.childElements(table).filter { OfficeXml.name(it) == "tr" }.map { row ->
            OfficeXml.childElements(row).filter { OfficeXml.name(it) == "tc" }.map { cellText(it) }
        }

    /** Texto de una celda: sus párrafos unidos con espacio, también los de tablas anidadas. */
    private fun cellText(cell: Element): String {
        if (isVerticalContinuation(cell)) return ""
        val paragraphs = cell.getElementsByTagNameNS("*", "p")
        return (0 until paragraphs.length)
            .mapNotNull { paragraphs.item(it) as? Element }
            .map { paragraphText(it) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    /** Una celda es continuación si su combinación vertical no empieza en ella (`w:vMerge` sin `restart`). */
    private fun isVerticalContinuation(cell: Element): Boolean {
        val properties = OfficeXml.childElements(cell).firstOrNull { OfficeXml.name(it) == "tcPr" } ?: return false
        val merge = OfficeXml.childElements(properties).firstOrNull { OfficeXml.name(it) == "vMerge" } ?: return false
        return OfficeXml.attribute(merge, "val") != "restart"
    }

    /** Texto de un párrafo: sus `w:t` unidos; una tabulación o un salto (`w:tab`, `w:br`) cuenta como espacio. */
    private fun paragraphText(paragraph: Element): String {
        val out = StringBuilder()
        collectText(paragraph, out)
        return out.toString().trim()
    }

    private fun collectText(node: Element, out: StringBuilder) {
        for (child in OfficeXml.childElements(node)) {
            when (OfficeXml.name(child)) {
                "t" -> out.append(child.textContent)
                "tab", "br", "cr" -> out.append(' ')
                // Las propiedades de párrafo traen sus propias tabulaciones (tabulados), que no son texto.
                "pPr", "rPr" -> Unit
                else -> collectText(child, out)
            }
        }
    }

    private fun notDocx(): ImportFileException = ImportFileException("No es un documento de Word (.docx) válido")
}
