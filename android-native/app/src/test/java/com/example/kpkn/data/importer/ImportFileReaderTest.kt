package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportFileReaderTest {

    private val oldOffice = "Es un formato antiguo de Office. Guárdalo como .xlsx o .docx y vuelve a intentarlo."

    @Test
    fun `un OLE, un PDF y un ZIP que no es libro, hoja ni documento dan su mensaje`() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) +
            ByteArray(16)

        assertEquals(oldOffice, rejectionOf { ImportFileReader.read(ole, "viejo.doc", null) })
        assertEquals(oldOffice, rejectionOf { ImportFileReader.read(ole, "viejo.xls", null) })
        assertEquals(
            "Para importar un PDF, copia su texto y usa «Pegar texto».",
            rejectionOf { ImportFileReader.read("%PDF-1.7\n".toByteArray(Charsets.US_ASCII), "guia.pdf", null) },
        )
        assertEquals(
            "No es un archivo que sepamos leer. Usa .xlsx, .ods, .csv, .docx o texto.",
            rejectionOf { ImportFileReader.read(zipFile("notas.txt" to "hola"), "notas.zip", null) },
        )
        assertEquals(
            "No es un archivo que sepamos leer. Usa .xlsx, .ods, .csv, .docx o texto.",
            rejectionOf {
                ImportFileReader.read(
                    zipFile("mimetype" to "application/vnd.oasis.opendocument.text", "content.xml" to "<x/>"),
                    "carta.odt",
                    null,
                )
            },
        )
    }

    @Test
    fun `el texto se lee según su extensión, el tipo MIME o, sin ninguno, como csv`() {
        val markdown = "Programa de ejemplo.\n```liftoscript\n# Week 1\n## Día 1 — Pierna\nPress banca / 4x8 / 80kg\n```\n"
        val expectedMarkdownRows = listOf(
            listOf("Week 1", "", ""),
            listOf("Día 1 — Pierna", "", ""),
            listOf("Press banca", "4x8", "80kg"),
        )

        val md = ImportFileReader.read(markdown.toByteArray(Charsets.UTF_8), "programa.md", null).single()
        assertEquals("programa", md.name)
        assertEquals(expectedMarkdownRows, md.rows)
        assertEquals(
            expectedMarkdownRows,
            ImportFileReader.read(markdown.toByteArray(Charsets.UTF_8), "programa", "text/markdown").single().rows,
        )

        val csv = ImportFileReader.read("Ejercicio;Series;Reps\nPress banca;4;8\n".toByteArray(Charsets.UTF_8), "datos.csv", null).single()
        assertEquals("datos", csv.name)
        assertEquals(listOf(listOf("Ejercicio", "Series", "Reps"), listOf("Press banca", "4", "8")), csv.rows)
        val unknown = ImportFileReader.read("Press banca;4;8\n".toByteArray(Charsets.UTF_8), "programa", null).single()
        assertEquals(listOf(listOf("Press banca", "4", "8")), unknown.rows)

        assertEquals(
            "No es un libro de Excel (.xlsx) válido",
            rejectionOf { ImportFileReader.read("esto no es un libro".toByteArray(Charsets.UTF_8), "x.xlsx", null) },
        )
    }

    @Test
    fun `un ods se reconoce por su mimetype y sale como hoja de cálculo`() {
        val ods = odsFile("Día 1" to odsRow(odsCell(text = "Ejercicio"), odsCell(text = "Series")))

        assertEquals(listOf("Día 1"), ImportFileReader.read(ods, "programa.ods", null).map { it.name })
    }

    @Test
    fun `un docx y un md dan los ejercicios esperados con el analizador`() {
        val word = docxFile(
            docxParagraph("Día 1 — Pierna") +
                docxTable(
                    docxRow(docxCell("Ejercicio"), docxCell("Series"), docxCell("Reps")),
                    docxRow(docxCell("Sentadilla"), docxCell("4"), docxCell("8")),
                    docxRow(docxCell("Prensa"), docxCell("3"), docxCell("10")),
                ),
        )
        val fromWord = ProgramImportParser.parseSheets(ImportFileReader.read(word, "programa.docx", null), "programa")
        val wordDay = fromWord.weeks.single().days.single()
        assertEquals("Día 1 — Pierna", wordDay.name)
        assertEquals(
            listOf(Triple("Sentadilla", 4, 8), Triple("Prensa", 3, 10)),
            wordDay.exercises.map { Triple(it.rawName, it.sets, it.repsMax) },
        )

        val markdown = "Programa de ejemplo.\n```liftoscript\n# Week 1\n## Día 1 — Pierna\n" +
            "Sentadilla / 4x6 / 120kg / 120s\nPrensa / 3x10 / 90s\n```\n"
        val fromMarkdown = ProgramImportParser.parseSheets(
            ImportFileReader.read(markdown.toByteArray(Charsets.UTF_8), "programa.md", null),
            "programa",
        )
        assertEquals(1, fromMarkdown.weeks.single().index)
        val exercises = fromMarkdown.weeks.single().days.single().exercises
        assertEquals(listOf(Triple("Sentadilla", 4, 6), Triple("Prensa", 3, 10)), exercises.map { Triple(it.rawName, it.sets, it.repsMax) })
        assertEquals(listOf(120.0, null), exercises.map { it.loadKg })
        assertEquals(listOf(120, 90), exercises.map { it.restSeconds })
        assertEquals(emptyList<String>(), fromMarkdown.warningMessages)
    }

    @Test
    fun `un xlsx con un medio de 40 MB descomprimidos se lee sin rechazarlo al detectar el tipo`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entryText("xl/workbook.xml", WORKBOOK_XML)
            zip.entryText("xl/_rels/workbook.xml.rels", RELS_XML)
            zip.entryText("xl/worksheets/sheet1.xml", SHEET_XML)
            zip.putNextEntry(ZipEntry("xl/media/image1.png"))
            zip.write(ByteArray(40 * 1024 * 1024))
            zip.closeEntry()
        }

        val sheets = ImportFileReader.read(out.toByteArray(), "programa.xlsx", null)

        assertEquals(listOf(listOf("Press banca", "4")), sheets.single().rows)
    }

    @Test
    fun `una bomba ZIP de más de 128 MB descomprimidos se rechaza al detectar el tipo`() {
        val chunk = ByteArray(1024 * 1024)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/media/bomba.bin"))
            repeat(130) { zip.write(chunk) }
            zip.closeEntry()
        }

        assertEquals(
            "El archivo descomprimido es demasiado grande",
            rejectionOf { ImportFileReader.read(out.toByteArray(), "bomba.xlsx", null) },
        )
    }

    @Test
    fun `un archivo de más de 5 MB se rechaza antes de leerlo`() {
        assertEquals(
            "El archivo es demasiado grande (máx. 5 MB).",
            rejectionOf { ImportFileReader.read(ByteArray(5 * 1024 * 1024 + 1), "grande.csv", null) },
        )
    }

    private fun ZipOutputStream.entryText(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private val WORKBOOK_XML = """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
        """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
        """<sheet name="Día 1" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private val RELS_XML = """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """ +
        """Target="worksheets/sheet1.xml"/></Relationships>"""

    private val SHEET_XML = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
        """<row r="1"><c r="A1" t="inlineStr"><is><t>Press banca</t></is></c><c r="B1"><v>4</v></c></row>""" +
        "</sheetData></worksheet>"
}
