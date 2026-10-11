package com.example.kpkn.data.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxReaderTest {

    @Test
    fun `lee cadenas compartidas con texto enriquecido, numeros y huecos vacios`() {
        val sheets = XlsxReader.read(ByteArrayInputStream(workbook(SHEET_ONE, SHEET_TWO, names = listOf("Día 1", "Día 2"))))

        assertEquals(listOf("Día 1", "Día 2"), sheets.map { it.name })
        assertEquals(
            listOf(
                listOf("Ejercicio", "Series", "", ""),
                listOf("Press banca", "4", "", "80.5"),
                listOf("Sentadilla", "", "", ""),
            ),
            sheets[0].rows,
        )
    }

    @Test
    fun `lee booleanos, textos de formula y filas con huecos`() {
        val sheets = XlsxReader.read(ByteArrayInputStream(workbook(SHEET_ONE, SHEET_TWO, names = listOf("A", "B"))))

        assertEquals(
            listOf(
                listOf("TRUE", "", "Lunes"),
                listOf("", "3", ""),
            ),
            sheets[1].rows,
        )
    }

    @Test
    fun `un libro con mas de 40 columnas recorta a 40 y avisa con una nota`() {
        val wide = numbers(listOf(List(45) { "${it + 1}" }))

        val sheets = XlsxReader.read(ByteArrayInputStream(xlsx(listOf(Sheet("Ancha", wide), Sheet("B", SHEET_TWO)))))

        assertEquals(listOf((1..40).map { "$it" }), sheets[0].rows)
        assertEquals(listOf(TrimNotes.COLUMNS), sheets[0].notes)
        assertEquals(emptyList<String>(), sheets[1].notes)
    }

    @Test
    fun `mas de 2000 filas recortan a 2000 y avisan con una nota`() {
        val tall = numbers(List(2_100) { listOf("${it + 1}") })

        val sheet = XlsxReader.read(ByteArrayInputStream(xlsx(listOf(Sheet("Larga", tall))))).single()

        assertEquals(2_000, sheet.rows.size)
        assertEquals(listOf("2000"), sheet.rows.last())
        assertEquals(listOf(TrimNotes.ROWS), sheet.notes)
    }

    @Test
    fun `las hojas ocultas no se devuelven ni cuentan para el tope, y las visibles de mas se recortan con nota`() {
        val visible = (1..25).map { Sheet("Hoja $it", numbers(listOf(listOf("${it}")))) }
        val hidden = listOf(
            Sheet("Oculta A", numbers(listOf(listOf("901"))), state = "hidden"),
            Sheet("Oculta B", numbers(listOf(listOf("902"))), state = "veryHidden"),
            Sheet("Oculta C", numbers(listOf(listOf("903"))), state = "hidden"),
        )

        val sheets = XlsxReader.read(ByteArrayInputStream(xlsx(visible.take(10) + hidden + visible.drop(10))))

        assertEquals((1..20).map { "Hoja $it" }, sheets.map { it.name })
        assertEquals(listOf("El libro tiene 25 hojas; se leyeron las 20 primeras."), sheets.first().notes)
        assertEquals(emptyList<String>(), sheets.drop(1).flatMap { it.notes })
        assertTrue(sheets.none { sheet -> sheet.rows.any { row -> row.any { it in setOf("901", "902", "903") } } })
    }

    @Test
    fun `el formato de la celda da porcentaje y hora, y lo demas se lee como antes`() {
        val row = """<row r="1">""" +
            """<c r="A1" s="1"><v>0.65</v></c>""" +
            """<c r="B1" s="2"><v>0.725</v></c>""" +
            """<c r="C1" s="3"><v>0.00138889</v></c>""" +
            """<c r="D1" s="4"><v>0.00138889</v></c>""" +
            """<c r="E1" s="0"><v>0.65</v></c>""" +
            """<c r="F1" s="5"><v>45000</v></c>""" +
            """<c r="G1" s="6"><v>1.5</v></c>""" +
            """<c r="H1" s="7"><v>0.65</v></c>""" +
            """<c r="I1" s="1" t="str"><f>A1</f><v>0.65</v></c>""" +
            "</row>"

        val rows = XlsxReader.read(ByteArrayInputStream(xlsx(listOf(Sheet("Formatos", sheetXml(row))), styles = STYLES)))
            .single().rows

        assertEquals(listOf(listOf("65%", "72.5%", "2:00", "2:00", "0.65", "45000", "36:00:00", "0.65", "0.65")), rows)
    }

    @Test
    fun `una entrada de 40 MB que no es hoja ni estilo no impide leer el libro`() {
        val media = ByteArray(40 * 1024 * 1024)

        val sheet = XlsxReader.read(
            ByteArrayInputStream(
                xlsx(listOf(Sheet("Día 1", numbers(listOf(listOf("Press banca", "4"))))), extra = { zip ->
                    zip.putNextEntry(ZipEntry("xl/media/image1.png"))
                    zip.write(media)
                    zip.closeEntry()
                }),
            ),
        ).single()

        assertEquals(listOf(listOf("Press banca", "4")), sheet.rows)
    }

    @Test
    fun `una bomba ZIP de mas de 128 MB descomprimidos se rechaza sin agotar la memoria`() {
        val chunk = ByteArray(1024 * 1024)
        val bomb = xlsx(listOf(Sheet("Día 1", numbers(listOf(listOf("1"))))), extra = { zip ->
            zip.putNextEntry(ZipEntry("xl/media/bomba.bin"))
            repeat(130) { zip.write(chunk) }
            zip.closeEntry()
        })

        assertEquals("El libro descomprimido es demasiado grande", rejectionOf { XlsxReader.read(ByteArrayInputStream(bomb)) })
    }

    @Test
    fun `lo que se guarda de un libro pasa de 32 MB y se rechaza`() {
        val sharedStrings = """<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            " ".repeat(33 * 1024 * 1024) + "</sst>"

        val message = rejectionOf {
            XlsxReader.read(ByteArrayInputStream(xlsx(listOf(Sheet("Día 1", numbers(listOf(listOf("1"))))), sharedStrings = sharedStrings)))
        }

        assertEquals("El libro descomprimido es demasiado grande", message)
    }

    @Test
    fun `una hoja de 60 000 celdas se lee en menos de 3 segundos`() {
        val rows = List(2_000) { r -> List(30) { c -> "${r * 30 + c}" } }
        val bytes = xlsx(listOf(Sheet("Grande", numbers(rows))))

        val start = System.nanoTime()
        val sheet = XlsxReader.read(ByteArrayInputStream(bytes)).single()
        val millis = (System.nanoTime() - start) / 1_000_000

        assertEquals(2_000, sheet.rows.size)
        assertEquals("59999", sheet.rows.last().last())
        assertEquals(emptyList<String>(), sheet.notes)
        assertTrue("La lectura tardó $millis ms", millis < 3_000)
    }

    @Test
    fun `un archivo que no es un libro de Excel se rechaza`() {
        assertThrows(ImportFileException::class.java) {
            XlsxReader.read(ByteArrayInputStream("esto no es un libro".toByteArray(Charsets.UTF_8)))
        }
    }

    /** Una hoja del libro: su nombre, su XML y su estado («hidden» o «veryHidden») si lo tiene. */
    private class Sheet(val name: String, val xml: String, val state: String? = null)

    private fun workbook(sheetOne: String, sheetTwo: String, names: List<String>): ByteArray =
        xlsx(listOf(Sheet(names[0], sheetOne), Sheet(names[1], sheetTwo)))

    /** Un .xlsx con sus hojas; [styles] es `xl/styles.xml` si se pasa, y [extra] escribe entradas adicionales. */
    private fun xlsx(
        sheets: List<Sheet>,
        styles: String? = null,
        sharedStrings: String = SHARED_STRINGS,
        extra: ((ZipOutputStream) -> Unit)? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entry("xl/workbook.xml", workbookXml(sheets))
            zip.entry("xl/_rels/workbook.xml.rels", relationshipsXml(sheets.size))
            zip.entry("xl/sharedStrings.xml", sharedStrings)
            sheets.forEachIndexed { index, sheet -> zip.entry("xl/worksheets/sheet${index + 1}.xml", sheet.xml) }
            styles?.let { zip.entry("xl/styles.xml", it) }
            extra?.invoke(zip)
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun workbookXml(sheets: List<Sheet>): String {
        val entries = sheets.mapIndexed { index, sheet ->
            val state = sheet.state?.let { """ state="$it"""" }.orEmpty()
            """<sheet name="${sheet.name}" sheetId="${index + 1}"$state r:id="rId${index + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$entries</sheets></workbook>"""
    }

    private fun relationshipsXml(sheetCount: Int): String = """<?xml version="1.0" encoding="UTF-8"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        (1..sheetCount).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """ +
                """Target="worksheets/sheet$it.xml"/>"""
        } +
        """<Relationship Id="rId${sheetCount + 1}" """ +
        """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""" +
        "</Relationships>"

    /** Una hoja con números o textos en línea; cada lista es una fila y los textos vacíos no se escriben. */
    private fun numbers(rows: List<List<String>>): String = sheetXml(
        rows.mapIndexed { r, cells ->
            """<row r="${r + 1}">""" + cells.mapIndexed { c, value -> cellXml("${column(c)}${r + 1}", value) }.joinToString("") + "</row>"
        }.joinToString(""),
    )

    private fun cellXml(reference: String, value: String): String = when {
        value.isEmpty() -> ""
        value.toDoubleOrNull() != null -> """<c r="$reference"><v>$value</v></c>"""
        else -> """<c r="$reference" t="inlineStr"><is><t>$value</t></is></c>"""
    }

    private fun sheetXml(body: String): String = """<?xml version="1.0" encoding="UTF-8"?>""" +
        """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>"""

    /** Letra de columna de hoja de cálculo, desde 0 (0 → A, 26 → AA). */
    private fun column(index: Int): String =
        if (index < 26) "${'A' + index}" else "${'A' + index / 26 - 1}${'A' + index % 26}"

    private companion object {
        const val SHARED_STRINGS = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">""" +
            """<si><t>Ejercicio</t></si>""" +
            """<si><r><t xml:space="preserve">Press </t></r><r><t>banca</t></r></si>""" +
            """<si><t>Series</t></si></sst>"""

        const val SHEET_ONE = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
            """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>2</v></c></row>""" +
            """<row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2"><v>4</v></c><c r="D2"><v>80.50</v></c></row>""" +
            """<row r="3"><c r="A3" t="inlineStr"><is><t>Sentadilla</t></is></c></row>""" +
            "</sheetData></worksheet>"

        const val SHEET_TWO = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
            """<row r="1"><c r="A1" t="b"><v>1</v></c><c r="C1" t="str"><f>A1</f><v>Lunes</v></c></row>""" +
            """<row r="2"><c r="B2"><v>3</v></c></row>""" +
            "</sheetData></worksheet>"

        /**
         * Estilos de la prueba: s0 general; s1 porcentaje integrado (9); s2 «0.0%»; s3 hora integrada (21); s4 «m:ss»;
         * s5 fecha integrada (14); s6 «[h]:mm:ss»; s7 «0.0"%"» (porcentaje entre comillas, no es porcentaje).
         */
        const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="4"><numFmt numFmtId="164" formatCode="0.0%"/><numFmt numFmtId="165" formatCode="m:ss"/>""" +
            """<numFmt numFmtId="166" formatCode="[h]:mm:ss"/><numFmt numFmtId="167" formatCode="0.0&quot;%&quot;"/></numFmts>""" +
            """<cellXfs count="8"><xf numFmtId="0"/><xf numFmtId="9"/><xf numFmtId="164"/><xf numFmtId="21"/>""" +
            """<xf numFmtId="165"/><xf numFmtId="14"/><xf numFmtId="166"/><xf numFmtId="167"/></cellXfs></styleSheet>"""
    }
}
