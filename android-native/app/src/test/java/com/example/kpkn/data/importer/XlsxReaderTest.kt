package com.example.kpkn.data.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun `un libro con mas de 40 columnas con datos se rechaza con un mensaje claro`() {
        val wide = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
            """<row r="1"><c r="AO1"><v>1</v></c></row></sheetData></worksheet>"""

        assertThrows(ImportFileException::class.java) {
            XlsxReader.read(ByteArrayInputStream(workbook(wide, SHEET_TWO, names = listOf("Ancha", "B"))))
        }
    }

    @Test
    fun `un archivo que no es un libro de Excel se rechaza`() {
        assertThrows(ImportFileException::class.java) {
            XlsxReader.read(ByteArrayInputStream("esto no es un libro".toByteArray(Charsets.UTF_8)))
        }
    }

    private fun workbook(sheetOne: String, sheetTwo: String, names: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entry("xl/workbook.xml", workbookXml(names))
            zip.entry("xl/_rels/workbook.xml.rels", relationshipsXml())
            zip.entry("xl/sharedStrings.xml", SHARED_STRINGS)
            zip.entry("xl/worksheets/sheet1.xml", sheetOne)
            zip.entry("xl/worksheets/sheet2.xml", sheetTwo)
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun workbookXml(names: List<String>): String {
        val sheets = names.mapIndexed { index, name ->
            """<sheet name="$name" sheetId="${index + 1}" r:id="rId${index + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$sheets</sheets></workbook>"""
    }

    private fun relationshipsXml(): String = """<?xml version="1.0" encoding="UTF-8"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
        """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>""" +
        """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""" +
        "</Relationships>"

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
    }
}
