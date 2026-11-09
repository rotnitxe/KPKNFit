package com.example.kpkn.data.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.training.importer.ExerciseNameMatcher
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFileReaderTest {

    private val oldOffice = "Es un formato antiguo de Office. Guárdalo como .xlsx o .docx y vuelve a intentarlo."

    @Test
    fun `un OLE, un PDF y un ZIP que no es libro, hoja ni documento dan su mensaje`() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) +
            ByteArray(16)

        assertEquals(oldOffice, rejectionOf { ImportFileReader.read(ole, "viejo.doc", null) })
        assertEquals("No es un libro de Excel (.xls) válido.", rejectionOf { ImportFileReader.read(ole, "viejo.xls", null) })
        assertEquals(
            "Para importar un PDF, copia su texto y usa «Pegar texto».",
            rejectionOf { ImportFileReader.read("%PDF-1.7\n".toByteArray(Charsets.US_ASCII), "guia.pdf", null) },
        )
        assertEquals(
            "No es un archivo que sepamos leer. Usa .xls, .xlsx, .xlsm, .ods, .csv, .docx o texto.",
            rejectionOf { ImportFileReader.read(zipFile("notas.txt" to "hola"), "notas.zip", null) },
        )
        assertEquals(
            "No es un archivo que sepamos leer. Usa .xls, .xlsx, .xlsm, .ods, .csv, .docx o texto.",
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
            listOf("Programa de ejemplo.", "", ""),
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
            "No es un libro de Excel (.xlsx o .xlsm) válido",
            rejectionOf { ImportFileReader.read("esto no es un libro".toByteArray(Charsets.UTF_8), "x.xlsx", null) },
        )
    }

    @Test
    fun `el MIME de XLSM se compara sin distinguir mayúsculas ni parámetros`() {
        val message = "No es un libro de Excel (.xlsx o .xlsm) válido"
        listOf(
            "application/vnd.ms-excel.sheet.macroenabled.12",
            "Application/Vnd.Ms-Excel.Sheet.MacroEnabled.12; charset=binary",
        ).forEach { mime ->
            assertEquals(
                message,
                rejectionOf { ImportFileReader.read("no es un xlsx".toByteArray(Charsets.UTF_8), "programa", mime) },
            )
        }
    }

    @Test
    fun `al importar una tabla con slug y nombre de ejercicio usa el nombre legible`() {
        val csv = "exercise_slug,exercise_name,sets,reps\nbarbell-bench-press,Barbell Bench Press,3,8\n"
        val sheets = ImportFileReader.read(csv.toByteArray(Charsets.UTF_8), "programa.csv", null)
        val draft = ProgramImportParser.parseSheets(sheets, "Programa")
        val exercise = draft.weeks.single().days.single().exercises.single()

        assertEquals("Barbell Bench Press", exercise.rawName)
        assertEquals(3, exercise.sets)
        assertEquals(8, exercise.repsMax)
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
        assertEquals(listOf("Hoja «programa»: se omitió 1 línea de texto."), fromMarkdown.warningMessages)
    }

    @Test
    fun `un xlsx recorre lector analizador y catalogo con semana dia carga y descanso`() {
        val workbook = zipFile(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Semana 1" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="inlineStr"><is><t>Día 1 — Pierna</t></is></c></row>
                <row r="2"><c r="A2" t="inlineStr"><is><t>Ejercicio</t></is></c><c r="B2" t="inlineStr"><is><t>Sets x Reps</t></is></c><c r="C2" t="inlineStr"><is><t>Carga</t></is></c><c r="D2" t="inlineStr"><is><t>Descanso</t></is></c></row>
                <row r="3"><c r="A3" t="inlineStr"><is><t>Sentadilla Libre con Barra Olímpica</t></is></c><c r="B3" t="inlineStr"><is><t>4 x 6-8</t></is></c><c r="C3" t="inlineStr"><is><t>80 kg</t></is></c><c r="D3" t="inlineStr"><is><t>120 s</t></is></c></row>
                <row r="4"><c r="A4" t="inlineStr"><is><t>Press banca con barra</t></is></c><c r="B4" t="inlineStr"><is><t>3 x 10</t></is></c><c r="C4" t="inlineStr"><is><t>100 kg</t></is></c><c r="D4" t="inlineStr"><is><t>90 s</t></is></c></row>
            </sheetData></worksheet>""",
        )

        val draft = ProgramImportParser.parseSheets(
            ImportFileReader.read(workbook, "rutina.xlsx", null),
            "Rutina",
        )
        assertEquals(
            ImportFileReader.read(workbook, "rutina.xlsx", null).single().rows,
            ImportFileReader.read(workbook, "rutina.xlsm", null).single().rows,
        )
        val week = draft.weeks.single()
        val day = week.days.single()

        assertEquals(1, week.index)
        assertEquals("Día 1 — Pierna", day.name)
        assertEquals(listOf("Sentadilla Libre con Barra Olímpica", "Press banca con barra"), day.exercises.map { it.rawName })
        assertEquals(listOf(4, 3), day.exercises.map { it.sets })
        assertEquals(listOf(6, 10), day.exercises.map { it.repsMin })
        assertEquals(listOf(8, 10), day.exercises.map { it.repsMax })
        assertEquals(listOf(80.0, 100.0), day.exercises.map { it.loadKg })
        assertEquals(listOf(120, 90), day.exercises.map { it.restSeconds })

        val matcher = ExerciseNameMatcher(
            ExerciseCatalogV2Loader.decodeApproved(File("src/main/assets/exercise_catalog_v2.json").readText()),
        )
        assertEquals(
            listOf("high_bar_back_squat__barbell", "bench_press__barbell"),
            day.exercises.map { matcher.match(it.rawName).best?.configurationId },
        )
        assertTrue(day.exercises.all { matcher.canResolveAutomatically(matcher.match(it.rawName)) })
    }

    @Test
    fun `un libro BIFF8 XLS pasa por el lector de hojas y conserva sus celdas`() {
        val sheets = ImportFileReader.read(biff8CompoundFile(), "rutina.xls", "application/vnd.ms-excel")

        assertEquals("Semana 1", sheets.single().name)
        assertEquals(
            listOf(
                listOf("Día 1 — Pierna", "", ""),
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Sentadilla", "4", "6"),
            ),
            sheets.single().rows,
        )
        val exercise = ProgramImportParser.parseSheets(sheets, "Rutina").weeks.single().days.single().exercises.single()
        assertEquals("Sentadilla", exercise.rawName)
        assertEquals(4, exercise.sets)
        assertEquals(6, exercise.repsMax)
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

    /** Crea una muestra BIFF8 mínima dentro de CFB con mini-stream para probar el camino real de archivos .xls. */
    private fun biff8CompoundFile(): ByteArray {
        val shared = listOf("Día 1 — Pierna", "Ejercicio", "Series", "Reps", "Sentadilla")
        val bounds = ByteArray(8 + "Semana 1".length * 2)
        bounds[4] = 0 // visible
        bounds[5] = 0 // worksheet
        bounds[6] = "Semana 1".length.toByte()
        bounds[7] = 1 // UTF-16LE
        "Semana 1".toByteArray(Charsets.UTF_16LE).copyInto(bounds, 8)
        val sstPayload = ByteArrayOutputStream().apply {
            write(littleU32(shared.size.toLong()))
            write(littleU32(shared.size.toLong()))
            shared.forEach { value ->
                write(littleU16(value.length))
                write(1)
                write(value.toByteArray(Charsets.UTF_16LE))
            }
        }.toByteArray()
        val globalBof = biffRecord(0x0809, littleU16(0x0600) + littleU16(0x0005))
        val sst = biffRecord(0x00FC, sstPayload)
        val end = biffRecord(0x000A, ByteArray(0))
        val boundLength = 4 + bounds.size
        val sheetOffset = globalBof.size + boundLength + sst.size + end.size
        putU32(bounds, 0, sheetOffset.toLong())
        val boundSheet = biffRecord(0x0085, bounds)
        val labelsAndNumbers = ByteArrayOutputStream().apply {
            write(cellLabelSst(0, 0, 0))
            write(cellLabelSst(1, 0, 1))
            write(cellLabelSst(1, 1, 2))
            write(cellLabelSst(1, 2, 3))
            write(cellLabelSst(2, 0, 4))
            write(cellRk(2, 1, 4))
            write(cellNumber(2, 2, 6.0))
        }.toByteArray()
        val sheet = biffRecord(0x0809, littleU16(0x0600) + littleU16(0x0010)) +
            labelsAndNumbers + end
        val workbook = globalBof + boundSheet + sst + end + sheet
        return cfbWithMiniStream(workbook)
    }

    private fun cellLabelSst(row: Int, column: Int, sharedIndex: Int): ByteArray = biffRecord(
        0x00FD,
        littleU16(row) + littleU16(column) + littleU16(0) + littleU32(sharedIndex.toLong()),
    )

    private fun cellRk(row: Int, column: Int, value: Int): ByteArray = biffRecord(
        0x027E,
        littleU16(row) + littleU16(column) + littleU16(0) + littleU32(((value shl 2) or 2).toLong()),
    )

    private fun cellNumber(row: Int, column: Int, value: Double): ByteArray {
        val bits = java.lang.Double.doubleToRawLongBits(value)
        val raw = ByteArray(8) { index -> (bits ushr (index * 8)).toByte() }
        return biffRecord(0x0203, littleU16(row) + littleU16(column) + littleU16(0) + raw)
    }

    private fun biffRecord(id: Int, payload: ByteArray): ByteArray =
        littleU16(id) + littleU16(payload.size) + payload

    private fun cfbWithMiniStream(workbook: ByteArray): ByteArray {
        val miniSectorCount = (workbook.size + 63) / 64
        val miniStreamSize = miniSectorCount * 64
        require(miniStreamSize <= 512)
        val header = ByteArray(512)
        byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
            .copyInto(header)
        putU16(header, 24, 0x003E)
        putU16(header, 26, 3)
        putU16(header, 28, 0xFFFE)
        putU16(header, 30, 9)
        putU16(header, 32, 6)
        putU32(header, 40, 0)
        putU32(header, 44, 1)
        putU32(header, 48, 1)
        putU32(header, 56, 4096)
        putU32(header, 60, 3)
        putU32(header, 64, 1)
        putU32(header, 68, 0xFFFF_FFFEL)
        putU32(header, 72, 0)
        repeat(109) { putU32(header, 76 + it * 4, 0xFFFF_FFFFL) }
        putU32(header, 76, 0)

        val fat = ByteArray(512)
        repeat(128) { putU32(fat, it * 4, 0xFFFF_FFFFL) }
        putU32(fat, 0, 0xFFFF_FFFDL)
        putU32(fat, 4, 0xFFFF_FFFEL)
        putU32(fat, 8, 0xFFFF_FFFEL)
        putU32(fat, 12, 0xFFFF_FFFEL)

        val directory = ByteArray(512)
        cfbDirectoryEntry("Root Entry", 5, 2, miniStreamSize, child = 1).copyInto(directory, 0)
        cfbDirectoryEntry("Workbook", 2, 0, workbook.size).copyInto(directory, 128)
        val miniStream = ByteArray(512)
        workbook.copyInto(miniStream)
        val miniFat = ByteArray(512)
        repeat(128) { putU32(miniFat, it * 4, 0xFFFF_FFFFL) }
        repeat(miniSectorCount) { index ->
            putU32(miniFat, index * 4, if (index == miniSectorCount - 1) 0xFFFF_FFFEL else (index + 1).toLong())
        }
        return header + fat + directory + miniStream + miniFat
    }

    private fun cfbDirectoryEntry(name: String, type: Int, startSector: Int, size: Int, child: Int = -1): ByteArray {
        val entry = ByteArray(128)
        val nameBytes = (name + '\u0000').toByteArray(Charsets.UTF_16LE)
        nameBytes.copyInto(entry)
        putU16(entry, 64, nameBytes.size)
        entry[66] = type.toByte()
        entry[67] = 1
        putU32(entry, 68, 0xFFFF_FFFFL)
        putU32(entry, 72, 0xFFFF_FFFFL)
        putU32(entry, 76, child.toLong().and(0xFFFF_FFFFL))
        putU32(entry, 116, startSector.toLong())
        putU32(entry, 120, size.toLong())
        return entry
    }

    private fun littleU16(value: Int): ByteArray = byteArrayOf(value.toByte(), (value ushr 8).toByte())

    private fun littleU32(value: Long): ByteArray = ByteArray(4) { index -> (value ushr (index * 8)).toByte() }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 4) bytes[offset + index] = (value ushr (index * 8)).toByte()
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
