package com.example.kpkn.data.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.training.importer.ExerciseNameMatcher
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Pruebas del informe sobre un corpus sintético que se crea aquí, en una carpeta temporal (nada del corpus real). */
class ImportCorpusReporterTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val matcher by lazy {
        ExerciseNameMatcher(ExerciseCatalogV2Loader.decodeApproved(File("src/main/assets/exercise_catalog_v2.json").readText()))
    }

    @Test
    fun `escribe report json y report md, una hoja por archivo, y el json se vuelve a leer igual`() {
        val out = temporaryFolder.newFolder("informe")

        val report = ImportCorpusReporter(matcher).run(syntheticCorpus(), out)

        assertTrue(File(out, "report.md").isFile)
        assertTrue(File(out, "report.md").readText().contains("## Reconocidos con confianza (muestra)"))
        assertEquals(report.files.size, File(out, "sheets").listFiles()!!.count { it.extension == "md" })
        val decoded = Json.decodeFromString(CorpusReport.serializer(), File(out, "report.json").readText())
        assertEquals(report, decoded)
    }

    @Test
    fun `cada archivo sale con su resultado en orden de gravedad y lo que no es hoja se ignora`() {
        val report = ImportCorpusReporter(matcher).run(syntheticCorpus(), temporaryFolder.newFolder("informe"))

        assertEquals(
            listOf(
                "003-no-xlsx/003-no-xlsx.xlsx" to CorpusOutcome.RECHAZO,
                "004-veintiuna/004-veintiuna.xlsx" to CorpusOutcome.NADA,
                "006-vacio/006-vacio.csv" to CorpusOutcome.NADA,
                "005-pobre/005-pobre.csv" to CorpusOutcome.POBRE,
                "001-valido/001-valido.xlsx" to CorpusOutcome.BIEN,
                "002-csv/002-csv.csv" to CorpusOutcome.BIEN,
            ),
            report.files.map { it.path to it.outcome },
        )
        assertEquals("No es un libro de Excel (.xlsx) válido", report.files[0].note)
        // Veintiuna hojas no es un rechazo: se leen 20 y el aviso de hoja lo dice.
        val veintiuna = report.files.single { it.path == "004-veintiuna/004-veintiuna.xlsx" }
        assertEquals(20, veintiuna.sheets.size)
        assertEquals(1, veintiuna.warnings["SHEET_NOTE"])
        assertEquals(
            "Hoja «Hoja 1»: El libro tiene 21 hojas; se leyeron las 20 primeras.",
            veintiuna.warningSamples.first().message,
        )
        assertEquals(1, report.summary.ignored)
    }

    @Test
    fun `recorta las celdas a 24 caracteres y escapa barras verticales y saltos de linea`() {
        val out = temporaryFolder.newFolder("informe")
        val report = ImportCorpusReporter(matcher).run(syntheticCorpus(), out)

        assertEquals("corta", ImportCorpusReporter.cutCell("corta"))
        assertEquals("a".repeat(24), ImportCorpusReporter.cutCell("a".repeat(24)))
        val cut = ImportCorpusReporter.cutCell("b".repeat(30))
        assertEquals(24, cut.length)
        assertTrue(cut.endsWith("…"))
        assertEquals("uno\\|dos\\ntres", ImportCorpusReporter.mdCell("uno|dos\r\ntres"))

        val sheetFile = report.files.single { it.path == "001-valido/001-valido.xlsx" }.sheetFile
        val sheet = File(out, "sheets/$sheetFile").readText()
        assertTrue(sheet.contains("| 2 | Press banca | 4 | 8 | 80 |"))
        assertTrue(sheet.contains("Notas \\| variante con un… |"))
    }

    @Test
    fun `lee ods, docx, md y txt, y rechaza pdf y doc con su mensaje`() {
        val root = temporaryFolder.newFolder("corpus-formatos")
        fun write(path: String, bytes: ByteArray) {
            File(root, path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        }
        val odsRows = odsRow(odsCell(text = "Ejercicio"), odsCell(text = "Series"), odsCell(text = "Reps")) +
            odsRow(
                odsCell(text = "Press banca"),
                odsCell("office:value-type='float' office:value='4'", "4"),
                odsCell("office:value-type='float' office:value='8'", "8"),
            )
        write("010-ods/010-ods.ods", odsFile("Día 1" to odsRows))
        write(
            "011-docx/011-docx.docx",
            docxFile(
                docxTable(
                    docxRow(docxCell("Ejercicio"), docxCell("Series"), docxCell("Reps")),
                    docxRow(docxCell("Press banca"), docxCell("4"), docxCell("8")),
                ),
            ),
        )
        write("012-md/012-md.md", "```liftoscript\nPress banca / 4x8 / 80kg\n```\n".toByteArray(Charsets.UTF_8))
        write("013-txt/013-txt.txt", "Press banca 4x8 80kg\n".toByteArray(Charsets.UTF_8))
        write("014-pdf/014-pdf.pdf", "%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
        write("015-doc/015-doc.doc", byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))

        val report = ImportCorpusReporter(matcher).run(root, temporaryFolder.newFolder("informe-formatos"))

        assertEquals(
            listOf(
                "014-pdf/014-pdf.pdf" to CorpusOutcome.RECHAZO,
                "015-doc/015-doc.doc" to CorpusOutcome.RECHAZO,
                "010-ods/010-ods.ods" to CorpusOutcome.BIEN,
                "011-docx/011-docx.docx" to CorpusOutcome.BIEN,
                "012-md/012-md.md" to CorpusOutcome.BIEN,
                "013-txt/013-txt.txt" to CorpusOutcome.BIEN,
            ),
            report.files.map { it.path to it.outcome },
        )
        assertEquals("Para importar un PDF, copia su texto y usa «Pegar texto».", report.files[0].note)
        assertEquals("Es un formato antiguo de Office. Guárdalo como .xlsx o .docx y vuelve a intentarlo.", report.files[1].note)
    }

    @Test
    fun `un fallo inesperado queda como FALLO y el informe se escribe igual`() {
        val out = temporaryFolder.newFolder("informe")
        val reporter = ImportCorpusReporter(matcher, parse = { sheets, title ->
            if (title == "002-csv") throw IllegalStateException("prueba") else ProgramImportParser.parseSheets(sheets, title)
        })

        val report = reporter.run(syntheticCorpus(), out)

        assertEquals(CorpusOutcome.FALLO, report.files.single { it.path == "002-csv/002-csv.csv" }.outcome)
        assertEquals(listOf("002-csv/002-csv.csv: análisis: java.lang.IllegalStateException: prueba"), report.summary.failures)
        assertTrue(File(out, "report.md").isFile)
    }

    @Test
    fun `un rechazo en ZIP lista sus entradas más grandes con su tamaño descomprimido`() {
        val root = temporaryFolder.newFolder("corpus-zip")
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            out.putNextEntry(ZipEntry("notas.txt"))
            out.write("hola".toByteArray(Charsets.UTF_8))
            out.closeEntry()
            out.putNextEntry(ZipEntry("xl/media/grande.bin"))
            out.write(ByteArray(5_000))
            out.closeEntry()
            out.putNextEntry(ZipEntry("xl/media/mediano.bin"))
            out.write(ByteArray(300))
            out.closeEntry()
        }
        File(root, "020-zip/020-zip.xlsx").apply { parentFile?.mkdirs() }.writeBytes(zip.toByteArray())

        val report = ImportCorpusReporter(matcher).run(root, temporaryFolder.newFolder("informe-zip"))

        val file = report.files.single()
        assertEquals(CorpusOutcome.RECHAZO, file.outcome)
        val sizes = requireNotNull(file.zipSizes)
        assertEquals(3, sizes.entries)
        assertEquals(5_304L, sizes.totalBytes)
        assertEquals(
            listOf("xl/media/grande.bin" to 5_000L, "xl/media/mediano.bin" to 300L, "notas.txt" to 4L),
            sizes.largest.map { it.name to it.bytes },
        )
    }

    @Test
    fun `un archivo que es un programa de KPKN se cuenta por plan en el resumen y sale en la tabla`() {
        val root = temporaryFolder.newFolder("corpus-plantilla")
        File(root, "GZCLP.csv").writeText("Ejercicio;Series;Reps\nSentadilla;4;6\n")
        val out = temporaryFolder.newFolder("informe-plantilla")

        val report = ImportCorpusReporter(matcher).run(root, out)

        assertEquals("protocol:gzclp", report.files.single().template)
        assertEquals(listOf(CorpusTemplateGroup("protocol:gzclp", listOf("GZCLP.csv"))), report.summary.templates)
        val markdown = File(out, "report.md").readText()
        assertTrue(markdown.contains("Archivos reconocidos como plantilla de KPKN: 1."))
        assertTrue(markdown.contains("| GZCLP |"))
    }

    /** Corpus mínimo: un xlsx de dos hojas, un csv bueno, un no-xlsx, uno con 21 hojas, un csv pobre y uno vacío. */
    private fun syntheticCorpus(): File {
        val root = temporaryFolder.newFolder("corpus")
        fun write(path: String, bytes: ByteArray) {
            File(root, path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        }
        write("001-valido/001-valido.xlsx", workbook(listOf("Día 1" to sheetXml(DAY_ONE), "Día 2" to sheetXml(DAY_TWO))))
        write("002-csv/002-csv.csv", CSV_OK.toByteArray(Charsets.UTF_8))
        write("003-no-xlsx/003-no-xlsx.xlsx", "esto no es un libro".toByteArray(Charsets.UTF_8))
        write("004-veintiuna/004-veintiuna.xlsx", workbook((1..21).map { "Hoja $it" to sheetXml(emptyList()) }))
        write("005-pobre/005-pobre.csv", CSV_POBRE.toByteArray(Charsets.UTF_8))
        write("006-vacio/006-vacio.csv", ByteArray(0))
        write("007-notas/portada.jpg", "no se lee".toByteArray(Charsets.UTF_8))
        return root
    }

    private fun workbook(sheets: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val entries = sheets.mapIndexed { index, (name, _) ->
                """<sheet name="$name" sheetId="${index + 1}" r:id="rId${index + 1}"/>"""
            }.joinToString("")
            zip.entry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>""" +
                    """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
                    """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$entries</sheets></workbook>""",
            )
            zip.entry("xl/_rels/workbook.xml.rels", relationships(sheets.size))
            sheets.forEachIndexed { index, (_, xml) -> zip.entry("xl/worksheets/sheet${index + 1}.xml", xml) }
        }
        return out.toByteArray()
    }

    private fun relationships(count: Int): String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            (1..count).joinToString("") {
                """<Relationship Id="rId$it" """ +
                    """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """ +
                    """Target="worksheets/sheet$it.xml"/>"""
            } +
            "</Relationships>"

    /** Hoja con celdas de texto en línea y números; las filas se numeran desde la 1. */
    private fun sheetXml(rows: List<List<String>>): String {
        val body = rows.mapIndexed { rowIndex, cells ->
            val cellsXml = cells.mapIndexed { column, text ->
                val ref = "${'A' + column}${rowIndex + 1}"
                if (text.toDoubleOrNull() != null) {
                    """<c r="$ref"><v>$text</v></c>"""
                } else {
                    """<c r="$ref" t="inlineStr"><is><t>$text</t></is></c>"""
                }
            }.joinToString("")
            """<row r="${rowIndex + 1}">$cellsXml</row>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>"""
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private companion object {
        val DAY_ONE = listOf(
            listOf("Ejercicio", "Series", "Reps", "Peso"),
            listOf("Press banca", "4", "8", "80"),
        )
        val DAY_TWO = listOf(
            listOf("Ejercicio", "Series", "Reps", "Peso", "Notas | variante con una descripción larga"),
            listOf("Press banca", "3", "10", "75"),
        )
        const val CSV_OK = "Ejercicio;Series;Reps;Peso\nPress banca;4;8-10;80\n"
        const val CSV_POBRE = "Ejercicio;Series;Reps\nejercicio inventado xyz;3;10\nejercicio inventado xyz;3;10\n"
    }
}
