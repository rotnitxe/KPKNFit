package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Bloque3ImporterHardeningTest {
    @Test
    fun `even empty ZIP entries count against the archive limit`() {
        val bytes = archive((0..ImportLimits.MAX_ZIP_ENTRIES).associate { "folder$it/" to "" })
        assertThrows(ImportFileException::class.java) { OfficeXml.archive(bytes, "too large") { false } }
    }

    @Test
    fun `XLSX normalizes relationship paths and bounds percent exponents`() {
        val bytes = archive(mapOf(
            "xl/workbook.xml" to """<workbook xmlns:r="urn:relationships"><sheets><sheet name="Plan" r:id="r1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="r1" Target="../xl/worksheets/sheet1.xml"/></Relationships>""",
            "xl/styles.xml" to """<styleSheet><cellXfs><xf numFmtId="0"/><xf numFmtId="9"/></cellXfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to """<worksheet><sheetData><row r="1">
                <c r="Ñ1" t="inlineStr"><is><t>Press</t></is></c>
                <c r="B1" s="1"><v>0.725</v></c>
                <c r="C1" s="1"><v>1e10000000</v></c>
                </row></sheetData></worksheet>""",
        ))
        val row = XlsxReader.read(bytes).single().rows.single()
        assertEquals(listOf("Press", "72.5%"), row.take(2))
        assertTrue(row[2].length < 32)
    }

    @Test
    fun `ODS bounds percentage and duration expansions`() {
        val bytes = archive(mapOf(
            "content.xml" to """<document xmlns:t="urn:table" xmlns:o="urn:office" xmlns:x="urn:text">
                <spreadsheet><t:table t:name="Plan"><t:table-row>
                <t:table-cell o:value-type="percentage" o:value="1e10000000"><x:p>Porcentaje</x:p></t:table-cell>
                <t:table-cell o:value-type="time" o:time-value="P999999999999999D"><x:p>Duración</x:p></t:table-cell>
                </t:table-row></t:table></spreadsheet></document>""",
        ))
        assertEquals(listOf("Porcentaje", "Duración"), OdsReader.read(bytes).single().rows.single())
    }

    @Test
    fun `CSV and pasted text stop after a later nonempty row`() {
        val text = "Press 3x8\n".repeat(ImportLimits.MAX_ROWS) + "\n\nSentadilla 4x6\n"
        val sheets = listOf(CsvReader.parse(text, "Plan"), TextProgramReader.read(text, "Plan"))
        for (sheet in sheets) {
            assertEquals(ImportLimits.MAX_ROWS, sheet.rows.size)
            assertTrue(TrimNotes.ROWS in sheet.notes)
        }
        assertTrue(ProgramImportParser.parseText(text, "Plan").warnings.any { it.kind == ImportWarningKind.SHEET_NOTE })
    }

    @Test
    fun `deep text containers leave readable sibling content`() {
        val document = "<document><body>" + "<sdt>".repeat(1_500) + "<p><t>Skip</t></p>" +
            "</sdt>".repeat(1_500) + "<p><t>Press 3x8</t></p></body></document>"
        val sheet = DocxReader.read(archive(mapOf("word/document.xml" to document)), "Plan")
        assertEquals(listOf(listOf("Press 3x8")), sheet.rows)
        val ods = "<document><spreadsheet><table>" + "<table-row-group>".repeat(1_500) +
            "<table-row><table-cell><p>Skip</p></table-cell></table-row>" +
            "</table-row-group>".repeat(1_500) +
            "<table-row><table-cell><p>Press 3x8</p></table-cell></table-row></table></spreadsheet></document>"
        assertEquals(listOf(listOf("Press 3x8")), OdsReader.read(archive(mapOf("content.xml" to ods))).single().rows)
        val html = "<html><body>" + "<div>".repeat(1_500) + "Skip" + "</div>".repeat(1_500) + "<p>Press 3x8</p></body></html>"
        val web = ProgramUrlReader.parsePayload(html.toByteArray(), "text/html", "plan.html", URI("https://training.example"))
        assertEquals(listOf(listOf("Press 3x8")), web.sheets.single().rows)
    }

    @Test
    fun `web imports reject local and transitional IPv6 literals`() {
        for (host in listOf("::ffff:127.0.0.1", "64:ff9b::10.0.0.1", "2002:7f00:1::1", "2001:db8::1", "fec0::1")) {
            assertThrows(ImportFileException::class.java) { ProgramUrlReader.validatePublicHttpsUrl("https://[$host]/plan") }
        }
    }

    private fun archive(entries: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}