package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/**
 * Lector de .xlsx propio con ZIP, DOM y SAX del JDK (sin librerías nuevas). Lee el nombre y el orden de las hojas
 * visibles (las ocultas no se devuelven), las cadenas compartidas (también con texto enriquecido), las celdas en línea,
 * números y booleanos. El formato de la celda decide el texto del número: porcentaje («65%») y hora o duración («2:00»);
 * lo demás sale como siempre. Las celdas vacías quedan como "".
 *
 * Recorta en vez de rechazar: más de [ImportLimits.MAX_COLUMNS] columnas, más de [ImportLimits.MAX_ROWS] filas o más de
 * [ImportLimits.MAX_SHEETS] hojas visibles dejan las primeras, con una nota en [ImportSheet.notes]. Lanza
 * [ImportFileException] si el libro no es válido, si pasa de 5 MB o si supera los topes contra bombas ZIP.
 */
object XlsxReader {
    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val STYLES = "xl/styles.xml"
    private val PARTS = setOf(WORKBOOK, WORKBOOK_RELS, SHARED_STRINGS, STYLES)
    private val HIDDEN_STATES = setOf("hidden", "veryHidden")
    private const val TOO_LARGE = "El libro descomprimido es demasiado grande"
    private const val INVALID_XML = "El libro contiene XML no válido"
    private const val MAX_COLUMN_SCAN = 1_000_000
    private val DECIMAL = Regex("""-?\d{1,30}(?:\.\d{1,30})?""")
    private val QUOTED = Regex("\"[^\"]*\"")
    private val ESCAPED = Regex("""\\.""")
    private val BRACKET = Regex("""\[[^\]]*]""")
    private val ELAPSED = Regex("""\[(?:h+|m+|s+)]""")

    /** Opciones de seguridad de los analizadores XML: sin DTD ni entidades externas (el XML de un libro no las necesita). */
    private val SECURITY_FEATURES: Map<String, Boolean> = mapOf(
        XMLConstants.FEATURE_SECURE_PROCESSING to true,
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
    )

    /** Una hoja del libro: su nombre, la parte del ZIP donde están sus celdas y si está oculta. */
    private class SheetRef(val name: String, val path: String, val hidden: Boolean)

    /** Filas leídas de una hoja y las notas de lo que se recortó. */
    private class SheetRead(val rows: List<List<String>>, val notes: List<String>)

    /** Tipo de formato de un número: general, porcentaje u hora. */
    private enum class NumberKind { PLAIN, PERCENT, TIME }

    /** Hojas visibles del libro en su orden, con sus notas de recorte. Lanza [ImportFileException] si no es un libro. */
    fun read(input: InputStream): List<ImportSheet> {
        val bytes = readBoundedBytes(input, ImportLimits.MAX_FILE_BYTES, "El archivo supera 5 MB")
        // Primera pasada: el libro, sus relaciones, las cadenas y los estilos, que son pequeños.
        val parts = OfficeXml.entries(bytes, TOO_LARGE) { it in PARTS } ?: throw notExcel()
        val workbookXml = parts[WORKBOOK] ?: throw notExcel()
        val sheets = workbookSheets(workbookXml, relationshipTargets(parts[WORKBOOK_RELS]))
        val visible = sheets.filterNot { it.hidden }
        val chosen = visible.take(ImportLimits.MAX_SHEETS)
        if (chosen.isEmpty()) return emptyList()
        // Segunda pasada: solo los XML de las hojas visibles que se leen; las ocultas y las sobrantes no se guardan.
        val wanted = chosen.mapTo(HashSet<String>()) { it.path }
        val sheetParts = OfficeXml.entries(bytes, TOO_LARGE) { it in wanted } ?: throw notExcel()
        val shared = sharedStrings(parts[SHARED_STRINGS])
        val formats = cellFormats(parts[STYLES])
        return chosen.mapIndexed { index, sheet ->
            val sheetRead = readSheet(sheetParts[sheet.path], shared, formats)
            val bookNote = TrimNotes.sheets(visible.size).takeIf { index == 0 && visible.size > ImportLimits.MAX_SHEETS }
            ImportSheet(name = sheet.name, rows = sheetRead.rows, notes = sheetRead.notes + listOfNotNull(bookNote))
        }
    }

    /** Hojas del libro en su orden. Sin nombre, una hoja se llama «Hoja N» con su posición en el libro. */
    private fun workbookSheets(xml: ByteArray, targets: Map<String, String>): List<SheetRef> =
        parseXml(xml).getElementsByTagNameNS("*", "sheet").elements().mapIndexed { index, sheet ->
            val name = sheet.getAttribute("name").ifBlank { "Hoja ${index + 1}" }
            val target = attributeNamed(sheet, "id")?.let { targets[it] }
            val path = target?.let { resolvePath(it) } ?: "xl/worksheets/sheet${index + 1}.xml"
            SheetRef(name, path, hidden = sheet.getAttribute("state") in HIDDEN_STATES)
        }

    private fun relationshipTargets(xml: ByteArray?): Map<String, String> {
        if (xml == null) return emptyMap()
        return parseXml(xml).getElementsByTagNameNS("*", "Relationship").elements()
            .associate { it.getAttribute("Id") to it.getAttribute("Target") }
    }

    private fun resolvePath(target: String): String =
        if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"

    /** Cadenas compartidas, en orden; el texto enriquecido se une y la fonética no cuenta. */
    private fun sharedStrings(xml: ByteArray?): List<String> {
        if (xml == null) return emptyList()
        val handler = SharedStringsHandler()
        saxParse(xml, handler)
        return handler.strings
    }

    /** Tipo de formato de cada estilo de celda (`cellXfs`), según sus `numFmts` y los ids integrados de Excel. */
    private fun cellFormats(xml: ByteArray?): List<NumberKind> {
        if (xml == null) return emptyList()
        val document = parseXml(xml)
        val custom = HashMap<Int, String>()
        for (format in document.getElementsByTagNameNS("*", "numFmt").elements()) {
            format.getAttribute("numFmtId").toIntOrNull()?.let { custom[it] = format.getAttribute("formatCode") }
        }
        val styles = document.getElementsByTagNameNS("*", "cellXfs").elements().firstOrNull() ?: return emptyList()
        return childElements(styles, "xf").map { xf ->
            val id = xf.getAttribute("numFmtId").toIntOrNull() ?: 0
            kindOf(id, custom[id])
        }
    }

    /** Formato personalizado si lo hay; si no, los ids integrados: 9 y 10 son porcentajes, 18–21 y 45–47 horas. */
    private fun kindOf(id: Int, code: String?): NumberKind = when {
        code != null -> kindOfCode(code)
        id == 9 || id == 10 -> NumberKind.PERCENT
        id in 18..21 || id in 45..47 -> NumberKind.TIME
        else -> NumberKind.PLAIN
    }

    /** Porcentaje si hay un «%» fuera de comillas; hora si tiene h, s o [h] y ni y ni d (que serían fechas). */
    private fun kindOfCode(code: String): NumberKind {
        val unquoted = code.replace(QUOTED, "").replace(ESCAPED, "").lowercase()
        val elapsed = BRACKET.findAll(unquoted).any { ELAPSED.matches(it.value) }
        val plain = unquoted.replace(BRACKET, "")
        return when {
            plain.contains('%') -> NumberKind.PERCENT
            plain.contains('y') || plain.contains('d') -> NumberKind.PLAIN
            elapsed || plain.contains('h') || plain.contains('s') -> NumberKind.TIME
            else -> NumberKind.PLAIN
        }
    }

    /** Filas de una hoja y sus notas de recorte. Una hoja sin XML en el ZIP está vacía. */
    private fun readSheet(xml: ByteArray?, shared: List<String>, formats: List<NumberKind>): SheetRead {
        if (xml == null) return SheetRead(emptyList(), emptyList())
        val handler = SheetHandler(shared, formats)
        saxParse(xml, handler) { handler.stopped }
        val notes = listOfNotNull(
            TrimNotes.COLUMNS.takeIf { handler.columnsCut },
            TrimNotes.ROWS.takeIf { handler.rowsCut },
        )
        return SheetRead(handler.rows(), notes)
    }

    /**
     * Recorre [xml] con SAX seguro. Un corte que [stopped] da por hecho no es un error; cualquier otro fallo del XML sí.
     */
    private fun saxParse(xml: ByteArray, handler: DefaultHandler, stopped: () -> Boolean = { false }) {
        try {
            saxFactory().newSAXParser().parse(ByteArrayInputStream(xml), handler)
        } catch (error: Exception) {
            if (!stopped()) throw ImportFileException(INVALID_XML)
        }
    }

    private fun saxFactory(): SAXParserFactory {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        for ((feature, value) in SECURITY_FEATURES) {
            try {
                factory.setFeature(feature, value)
            } catch (error: Exception) {
                // Esta implementación de XML no admite la opción: se siguen aplicando las demás.
            }
        }
        return factory
    }

    /** Número tal como lo escribe Excel («80.0» → «80»); cualquier otra cosa se devuelve tal cual. */
    private fun plainNumber(raw: String): String {
        val text = raw.trim()
        if (!DECIMAL.matches(text)) return text
        return BigDecimal(text).stripTrailingZeros().toPlainString()
    }

    /** Texto de un número según su formato; si el formato no encaja con el valor, sale como siempre. */
    private fun numberText(raw: String, kind: NumberKind): String = when (kind) {
        NumberKind.PERCENT -> percentText(raw) ?: plainNumber(raw)
        NumberKind.TIME -> clockText(raw) ?: plainNumber(raw)
        NumberKind.PLAIN -> plainNumber(raw)
    }

    /** Porcentaje: el valor por cien con «%», sin ceros sobrantes («0.725» → «72.5%»). */
    private fun percentText(raw: String): String? = raw.trim().toBigDecimalOrNull()
        ?.movePointRight(2)
        ?.round(MathContext(15))
        ?.stripTrailingZeros()
        ?.toPlainString()
        ?.plus("%")

    /** Hora o duración: la fracción de día en segundos redondeados; «m:ss» o, desde una hora, «h:mm:ss». Null si no es positiva. */
    private fun clockText(raw: String): String? {
        val days = raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val seconds = Math.round(days * 86_400.0)
        val ss = (seconds % 60).toString().padStart(2, '0')
        return if (seconds < 3_600) {
            "${seconds / 60}:$ss"
        } else {
            "${seconds / 3_600}:${(seconds / 60 % 60).toString().padStart(2, '0')}:$ss"
        }
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

    /** Nombre local de un elemento SAX, sin prefijo. */
    private fun nameOf(localName: String?, qName: String?): String =
        localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')

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

    /** DOM con espacios de nombres y sin entidades externas ni DTD, para el libro y sus relaciones (son pequeños). */
    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        for ((feature, value) in SECURITY_FEATURES) {
            try {
                factory.setFeature(feature, value)
            } catch (error: Exception) {
                // Esta implementación de XML no admite la opción: se siguen aplicando las demás.
            }
        }
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (error: Exception) {
            throw ImportFileException(INVALID_XML)
        }
    }

    private fun notExcel(): ImportFileException = ImportFileException("No es un libro de Excel (.xlsx) válido")

    /** Texto de un nodo `si` o `is` por SAX: sus `t`, también los de `r`; la fonética `rPh` no cuenta. */
    private class RichText {
        private val out = StringBuilder()
        private var phonetic = 0
        private var capturing = false

        fun reset() {
            out.setLength(0)
            phonetic = 0
            capturing = false
        }

        fun start(name: String) {
            when (name) {
                "rPh" -> phonetic++
                "t" -> capturing = phonetic == 0
            }
        }

        fun end(name: String) {
            when (name) {
                "rPh" -> phonetic--
                "t" -> capturing = false
            }
        }

        fun characters(chars: CharArray, start: Int, length: Int) {
            if (capturing) out.append(chars, start, length)
        }

        fun text(): String = out.toString()
    }

    /** Cadenas compartidas por SAX: el texto de cada `si`, en orden. */
    private class SharedStringsHandler : DefaultHandler() {
        val strings = ArrayList<String>()
        private val text = RichText()
        private var inItem = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = nameOf(localName, qName)
            if (name == "si") {
                inItem = true
                text.reset()
            }
            if (inItem) text.start(name)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (!inItem) return
            val name = nameOf(localName, qName)
            text.end(name)
            if (name == "si") {
                strings += text.text()
                inItem = false
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inItem) text.characters(ch, start, length)
        }
    }

    /**
     * Celdas de una hoja por SAX. Guarda las de las primeras filas y columnas. Al encontrar datos en la fila tope+1 deja
     * de leer (lanza un [SAXException] propio que [stopped] reconoce); los datos en columnas de más se ignoran.
     */
    private class SheetHandler(
        private val shared: List<String>,
        private val formats: List<NumberKind>,
    ) : DefaultHandler() {
        private val values = HashMap<Int, HashMap<Int, String>>()
        private val raw = StringBuilder()
        private val inlineText = RichText()
        private var maxRow = -1
        private var maxCol = -1
        private var rowPosition = -1
        private var rowIndex = 0
        private var nextCol = 0
        private var col = 0
        private var style: Int? = null
        private var type = ""
        private var inCell = false
        private var inValue = false
        private var inInline = false

        /** Hay datos en columnas de más de [ImportLimits.MAX_COLUMNS]; no se guardan. */
        var columnsCut = false
            private set

        /** Hay datos en filas de más de [ImportLimits.MAX_ROWS]; la lectura se corta ahí. */
        var rowsCut = false
            private set

        /** La lectura se cortó a propósito al pasar del tope de filas. */
        var stopped = false
            private set

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = nameOf(localName, qName)
            when (name) {
                "row" -> {
                    rowPosition++
                    rowIndex = attributes.getValue("r")?.toIntOrNull()?.minus(1)?.takeIf { it >= 0 } ?: rowPosition
                    nextCol = 0
                }
                "c" -> {
                    inCell = true
                    col = columnIndex(attributes.getValue("r").orEmpty()) ?: nextCol
                    nextCol = col + 1
                    style = attributes.getValue("s")?.toIntOrNull()
                    type = attributes.getValue("t").orEmpty()
                    raw.setLength(0)
                    inlineText.reset()
                }
                "v" -> if (inCell) {
                    inValue = true
                    raw.setLength(0)
                }
                "is" -> if (inCell) inInline = true
            }
            if (inInline) inlineText.start(name)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inValue) raw.append(ch, start, length)
            if (inInline) inlineText.characters(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = nameOf(localName, qName)
            if (inInline) inlineText.end(name)
            when (name) {
                "v" -> inValue = false
                "is" -> inInline = false
                "c" -> {
                    inCell = false
                    finishCell()
                }
            }
        }

        private fun finishCell() {
            val text = if (type == "inlineStr") inlineText.text() else cellValue(raw.toString())
            if (text.isBlank()) return
            if (rowIndex >= ImportLimits.MAX_ROWS) {
                rowsCut = true
                stopped = true
                throw SAXException("Se corta la lectura: el resto de filas no se lee.")
            }
            if (col >= ImportLimits.MAX_COLUMNS) {
                columnsCut = true
                return
            }
            values.getOrPut(rowIndex) { HashMap() }[col] = text
            maxRow = maxOf(maxRow, rowIndex)
            maxCol = maxOf(maxCol, col)
        }

        /** Texto de una celda según su tipo: cadena compartida, texto, booleano, error o número con su formato. */
        private fun cellValue(text: String): String = when (type) {
            "s" -> text.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "str" -> text
            "b" -> if (text.trim() == "1") "TRUE" else "FALSE"
            "e" -> ""
            else -> numberText(text, formats.getOrElse(style ?: 0) { NumberKind.PLAIN })
        }

        /** Filas leídas, con las celdas en blanco donde no hay nada. */
        fun rows(): List<List<String>> {
            if (maxRow < 0) return emptyList()
            val width = maxCol + 1
            return (0..maxRow).map { row ->
                val cells = values[row]
                List(width) { c -> cells?.get(c).orEmpty() }
            }
        }
    }
}
