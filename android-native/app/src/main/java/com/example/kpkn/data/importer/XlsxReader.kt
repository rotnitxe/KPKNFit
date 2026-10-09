package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/**
 * Lector de .xlsx propio con ZIP y DOM del JDK (sin librerías nuevas). Lee el nombre y el orden de las hojas,
 * las cadenas compartidas (también con texto enriquecido), las celdas en línea, números y booleanos. Las celdas
 * vacías quedan como "". Si el libro no es un Excel válido o supera [ImportLimits], lanza [ImportFileException].
 */
object XlsxReader {
    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val MAX_COLUMN_SCAN = 1_000_000
    private val DECIMAL = Regex("""-?\d{1,30}(?:\.\d{1,30})?""")

    /** Devuelve las hojas en el orden del libro. */
    fun read(input: InputStream): List<ImportSheet> {
        val entries = readEntries(input)
        val workbookXml = entries[WORKBOOK] ?: throw notExcel()
        val sheetElements = parseXml(workbookXml).getElementsByTagNameNS("*", "sheet").elements()
        if (sheetElements.size > ImportLimits.MAX_SHEETS) {
            throw ImportFileException("El libro tiene más de ${ImportLimits.MAX_SHEETS} hojas")
        }
        val targets = relationshipTargets(entries[WORKBOOK_RELS])
        val shared = sharedStrings(entries[SHARED_STRINGS])
        return sheetElements.mapIndexed { index, sheet ->
            val name = sheet.getAttribute("name").ifBlank { "Hoja ${index + 1}" }
            val target = attributeNamed(sheet, "id")?.let { targets[it] }
            val path = target?.let { resolvePath(it) } ?: "xl/worksheets/sheet${index + 1}.xml"
            ImportSheet(name = name, rows = readSheet(name, entries[path], shared))
        }
    }

    /** Lee todas las entradas del ZIP con un tope de bytes descomprimidos. No cierra el flujo del llamador. */
    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val entries = HashMap<String, ByteArray>()
        var budget = ImportLimits.MAX_UNCOMPRESSED_BYTES
        val zip = ZipInputStream(BoundedInputStream(input, ImportLimits.MAX_FILE_BYTES, "El archivo supera 5 MB"))
        zip.use {
            try {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val bytes = readBoundedBytes(zip, budget, "El libro descomprimido es demasiado grande")
                    budget -= bytes.size
                    entries[entry.name] = bytes
                }
            } catch (error: IOException) {
                throw notExcel()
            }
        }
        return entries
    }

    private fun relationshipTargets(xml: ByteArray?): Map<String, String> {
        if (xml == null) return emptyMap()
        return parseXml(xml).getElementsByTagNameNS("*", "Relationship").elements()
            .associate { it.getAttribute("Id") to it.getAttribute("Target") }
    }

    private fun resolvePath(target: String): String =
        if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"

    private fun sharedStrings(xml: ByteArray?): List<String> {
        if (xml == null) return emptyList()
        return parseXml(xml).getElementsByTagNameNS("*", "si").elements().map { richText(it) }
    }

    private fun readSheet(name: String, xml: ByteArray?, shared: List<String>): List<List<String>> {
        if (xml == null) return emptyList()
        val values = HashMap<Int, HashMap<Int, String>>()
        var maxRow = -1
        var maxCol = -1
        parseXml(xml).getElementsByTagNameNS("*", "row").elements().forEachIndexed { position, row ->
            val rowIndex = row.getAttribute("r").toIntOrNull()?.minus(1)?.takeIf { it >= 0 } ?: position
            var nextCol = 0
            childElements(row, "c").forEach { cell ->
                val col = columnIndex(cell.getAttribute("r")) ?: nextCol
                nextCol = col + 1
                val value = cellValue(cell, shared)
                if (value.isNotBlank()) {
                    values.getOrPut(rowIndex) { HashMap() }[col] = value
                    maxRow = maxOf(maxRow, rowIndex)
                    maxCol = maxOf(maxCol, col)
                }
            }
        }
        if (maxRow < 0) return emptyList()
        if (maxRow + 1 > ImportLimits.MAX_ROWS) {
            throw ImportFileException("La hoja «$name» tiene más de ${ImportLimits.MAX_ROWS} filas")
        }
        if (maxCol + 1 > ImportLimits.MAX_COLUMNS) {
            throw ImportFileException("La hoja «$name» tiene más de ${ImportLimits.MAX_COLUMNS} columnas")
        }
        val width = maxCol + 1
        return (0..maxRow).map { row ->
            val cells = values[row]
            List(width) { col -> cells?.get(col).orEmpty() }
        }
    }

    private fun cellValue(cell: Element, shared: List<String>): String {
        if (cell.getAttribute("t") == "inlineStr") {
            return childElements(cell, "is").firstOrNull()?.let { richText(it) }.orEmpty()
        }
        val raw = childElements(cell, "v").firstOrNull()?.textContent ?: return ""
        return when (cell.getAttribute("t")) {
            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "str" -> raw
            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
            "e" -> ""
            else -> plainNumber(raw)
        }
    }

    /** Texto de un nodo `si` o `is`: sus `t` y los de sus `r`; la fonética `rPh` no cuenta. */
    private fun richText(node: Node): String {
        val out = StringBuilder()
        collectText(node, out)
        return out.toString()
    }

    private fun collectText(node: Node, out: StringBuilder) {
        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            when (localName(child)) {
                "t" -> out.append(child.textContent)
                "r", "is", "si" -> collectText(child, out)
            }
        }
    }

    /** Número tal como lo escribe Excel («80.0» → «80»); cualquier otra cosa se devuelve tal cual. */
    private fun plainNumber(raw: String): String {
        val text = raw.trim()
        if (!DECIMAL.matches(text)) return text
        return BigDecimal(text).stripTrailingZeros().toPlainString()
    }

    /** Índice 0-based de columna a partir de una referencia como «B3». Null si no hay letras. */
    private fun columnIndex(reference: String): Int? {
        var column = 0
        var letters = 0
        for (char in reference) {
            if (!char.isLetter()) break
            column = minOf(column * 26 + (char.uppercaseChar() - 'A' + 1), MAX_COLUMN_SCAN)
            letters++
        }
        return if (letters == 0) null else column - 1
    }

    private fun attributeNamed(element: Element, name: String): String? {
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            if (attribute.localName == name || attribute.nodeName == name) return attribute.nodeValue
        }
        return null
    }

    private fun childElements(parent: Node, tag: String): List<Element> {
        val children = parent.childNodes
        return (0 until children.length).mapNotNull { index ->
            (children.item(index) as? Element)?.takeIf { localName(it) == tag }
        }
    }

    private fun localName(node: Node): String = node.localName ?: node.nodeName.substringAfter(':')

    private fun NodeList.elements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }

    /** DOM con espacios de nombres y sin entidades externas ni DTD (el XML de un libro no las necesita). */
    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        secure(factory)
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (error: Exception) {
            throw ImportFileException("El libro contiene XML no válido")
        }
    }

    private fun secure(factory: DocumentBuilderFactory) {
        val features = mapOf(
            XMLConstants.FEATURE_SECURE_PROCESSING to true,
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        )
        for ((feature, value) in features) {
            try {
                factory.setFeature(feature, value)
            } catch (error: Exception) {
                // Esta implementación de XML no admite la opción: se siguen aplicando las demás.
            }
        }
    }

    private fun notExcel(): ImportFileException = ImportFileException("No es un libro de Excel (.xlsx) válido")
}
