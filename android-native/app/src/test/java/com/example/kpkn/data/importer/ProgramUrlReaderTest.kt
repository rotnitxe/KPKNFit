package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ProgramImportParser
import com.example.kpkn.domain.training.importer.ImportSheet
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProgramUrlReaderTest {
    @Test
    fun `configured live URL uses the production HTTPS transport`() = runTest {
        val url = System.getenv("KPKN_IMPORT_LIVE_URL")
        assumeTrue("set KPKN_IMPORT_LIVE_URL to run the public HTTPS acceptance check", !url.isNullOrBlank())

        val document = ProgramUrlReader.read(requireNotNull(url))
        val draft = ProgramImportParser.parseSheets(document.sheets, document.title)
        val exercises = draft.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }

        assertTrue(document.sourceUrl.startsWith("https://"))
        assertTrue(document.sheets.isNotEmpty())
        assertTrue(document.sheets.any { sheet -> sheet.rows.any { row -> row.any(String::isNotBlank) } })
        assertTrue("live routine should yield at least six exercises; got ${exercises.map { it.rawName to it.sets to it.repsMin }}", exercises.size >= 6)
    }

    @Test
    fun `public html tables are converted into the guided import structure`() {
        val html = """
            <!doctype html>
            <html><head><title>Rutina de fuerza</title><script>Malicioso 99x99</script></head>
            <body><header>Introducción del sitio</header><nav>Ir a inicio</nav>
              <h1>Semana 1</h1>
              <p>Día 1 — Pierna</p>
              <div class="ad-banner">Suscríbete al boletín</div>
              <table>
                <tr><th>Ejercicio</th><th>Series</th><th>Reps</th></tr>
                <tr><td>Sentadilla</td><td>4</td><td>6</td></tr>
              </table>
              <footer>Enlaces legales del sitio</footer>
            </body></html>
        """.trimIndent()

        val document = ProgramUrlReader.parsePayload(
            bytes = html.toByteArray(Charsets.UTF_8),
            contentType = "text/html; charset=utf-8",
            fileName = "programa.html",
            source = URI("https://training.example:8443/programa/signed?access_token=private#fragment"),
        )
        val draft = ProgramImportParser.parseSheets(document.sheets, document.title)

        assertEquals("Rutina de fuerza", document.title)
        assertEquals("Sentadilla", draft.weeks.single().days.single().exercises.single().rawName)
        assertEquals(4, draft.weeks.single().days.single().exercises.single().sets)
        assertEquals(6, draft.weeks.single().days.single().exercises.single().repsMin)
        assertEquals("https://training.example:8443/programa/signed", document.sourceUrl)
        assertFalse(document.sourceUrl.contains("private"))
        assertFalse(document.sourceUrl.contains("fragment"))
        assertEquals("Semana 1 Día 1 — Pierna Ejercicio Series Reps Sentadilla 4 6", document.extractedText?.replace("\n", " ")?.replace("\t", " ")?.replace(Regex(" +"), " "))
        assertTrue(document.discardedText.orEmpty().contains("Ir a inicio"))
        assertTrue(document.discardedText.orEmpty().contains("Suscríbete al boletín"))
        assertTrue(document.discardedText.orEmpty().contains("Enlaces legales del sitio"))
        assertTrue(document.sheets.single().notes.any { it == "Origen web: training.example" })
        assertFalse(document.sheets.single().rows.flatten().any { "Malicioso" in it })
    }

    @Test
    fun `HTML article is preferred over related routine cards`() {
        val html = """
            <!doctype html>
            <html><head><title>Plan principal</title></head><body>
              <nav><a href="/routines">Read more</a></nav>
              <main>
                <article>
                  <h1>Plan principal</h1>
                  <table>
                    <tr><th>Exercise</th><th>Sets</th><th>Reps</th></tr>
                    <tr><td>Press banca</td><td>3</td><td>8</td></tr>
                    <tr><td>Sentadilla</td><td>4</td><td>6</td></tr>
                  </table>
                </article>
                <article>
                  <h2>Read more routines</h2>
                  <table>
                    <tr><th>Exercise</th><th>Sets</th><th>Reps</th></tr>
                    <tr><td>Read</td><td>9</td><td>99</td></tr>
                  </table>
                </article>
              </main>
            </body></html>
        """.trimIndent()

        val document = ProgramUrlReader.parsePayload(
            bytes = html.toByteArray(Charsets.UTF_8),
            contentType = "text/html; charset=utf-8",
            fileName = "plan.html",
            source = URI("https://training.example/plan"),
        )
        val exercises = ProgramImportParser.parseSheets(document.sheets, document.title)
            .weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }

        assertEquals(listOf("Press banca", "Sentadilla"), exercises.map { it.rawName })
        assertEquals(listOf(3, 4), exercises.map { it.sets })
        assertEquals(listOf(8, 6), exercises.map { it.repsMin })
    }

    @Test
    fun `HTML day rows preserve exercise names and never invent missing prescriptions`() {
        val html = """
            <!doctype html>
            <html><head><title>Plan semanal</title></head><body><main>
              <article>
                <h1>Plan semanal</h1>
                <table>
                  <tr><th>Column</th><th>What it holds</th><th>Worth knowing</th></tr>
                  <tr><td>Workout</td><td>A session name</td><td>You will read it in the gym</td></tr>
                </table>
                <table>
                  <tr><th>Day</th><th>Workout</th><th>Exercises</th></tr>
                  <tr><td>Monday</td><td>Upper A</td><td>Bench press, Lat pulldown, Seated shoulder press</td></tr>
                  <tr><td>Tuesday</td><td>Rest</td><td></td></tr>
                  <tr><td>Thursday</td><td>Lower</td><td>Squat, Romanian deadlift</td></tr>
                </table>
              </article>
              <article>
                <h2>Read more routines</h2>
                <table>
                  <tr><th>Exercise</th><th>Sets</th><th>Reps</th></tr>
                  <tr><td>Read</td><td>9</td><td>99</td></tr>
                </table>
              </article>
            </main></body></html>
        """.trimIndent()

        val document = ProgramUrlReader.parsePayload(
            bytes = html.toByteArray(Charsets.UTF_8),
            contentType = "text/html; charset=utf-8",
            fileName = "plan-semanal.html",
            source = URI("https://training.example/plan"),
        )
        val draft = ProgramImportParser.parseSheets(document.sheets, document.title)
        val rows = document.sheets.single().rows
        val header = listOf("Exercise", "Reps")
        val headerIndex = rows.indexOfFirst { it.take(header.size) == header }

        assertTrue("Expanded rows did not contain $header: $rows", headerIndex >= 0)
        assertEquals(listOf("Bench press", "Lat pulldown", "Seated shoulder press", "Squat", "Romanian deadlift"),
            rows.drop(headerIndex + 1).mapNotNull { row ->
                row.firstOrNull()?.takeIf { it in setOf("Bench press", "Lat pulldown", "Seated shoulder press", "Squat", "Romanian deadlift") }
            })
        assertTrue(rows.any { it.firstOrNull() == "Monday — Upper A" })
        assertTrue(rows.any { it.firstOrNull() == "Tuesday — Rest" })
        assertTrue(rows.none { row -> row.any { it.equals("Read", ignoreCase = true) } })
        assertTrue(draft.weeks.single().days.any { it.name == "Tuesday — Rest" && it.isRestDay })
        assertTrue(draft.weeks.flatMap { it.days }.flatMap { it.exercises }.isEmpty())
    }

    @Test
    fun `URL input defaults to HTTPS and rejects non-public destinations`() {
        assertEquals("https", ProgramUrlReader.parseHttpsUrl("training.example/programa").scheme)
        assertEquals("training.example", ProgramUrlReader.parseHttpsUrl("training.example:8443/programa").host)
        assertThrows(ImportFileException::class.java) { ProgramUrlReader.parseHttpsUrl("http://training.example/programa") }
        assertThrows(ImportFileException::class.java) { ProgramUrlReader.parseHttpsUrl("https://user@training.example/programa") }
        assertThrows(ImportFileException::class.java) { ProgramUrlReader.validatePublicHttpsUrl("https://localhost/programa") }
    }

    @Test
    fun `read follows a redirect and parses the fetched page`() = runTest {
        val validated = mutableListOf<URI>()
        val opened = mutableListOf<URI>()
        val html = """
            <html><head><title>Plan de fuerza</title></head>
            <body><table><tr><th>Ejercicio</th><th>Series</th><th>Reps</th></tr>
            <tr><td>Press banca</td><td>3</td><td>8</td></tr></table></body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val responses = mutableListOf(
            FakeHttpConnection(
                url = URL("https://training.example/inicio"),
                status = 302,
                headers = mapOf("Location" to "/programa"),
            ),
            FakeHttpConnection(
                url = URL("https://training.example/programa"),
                status = 200,
                body = html,
                contentType = "text/html; charset=utf-8",
            ),
        )

        val document = ProgramUrlReader.readWithTransport(
            rawUrl = "training.example/inicio",
            validateHost = { uri -> validated.add(uri); uri },
            openConnection = { uri -> opened.add(uri); responses.removeAt(0) },
        )
        val draft = ProgramImportParser.parseSheets(document.sheets, document.title)

        assertEquals(listOf("/inicio", "/programa"), validated.map { it.path })
        assertEquals(listOf("/inicio", "/programa"), opened.map { it.path })
        assertEquals("Plan de fuerza", document.title)
        assertEquals("https://training.example/programa", document.sourceUrl)
        assertEquals("Press banca", draft.weeks.single().days.single().exercises.single().rawName)
        assertEquals(3, draft.weeks.single().days.single().exercises.single().sets)
    }

    @Test
    fun `read downloads xlsx and preserves parsed exercises and web origin`() = runTest {
        val workbook = minimalXlsx()
        val response = FakeHttpConnection(
            url = URL("https://training.example/rutina.xlsx"),
            status = 200,
            body = workbook,
            headers = mapOf("Content-Disposition" to "attachment; filename=\"rutina.xlsx\""),
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )

        val document = ProgramUrlReader.readWithTransport(
            rawUrl = "https://training.example/rutina.xlsx",
            validateHost = { it },
            openConnection = { response },
        )
        val draft = ProgramImportParser.parseSheets(document.sheets, document.title)
        val exercise = draft.weeks.single().days.single().exercises.single()

        assertEquals("rutina", document.title)
        assertEquals("https://training.example/rutina.xlsx", document.sourceUrl)
        assertEquals(listOf("Ejercicio", "Series", "Reps"), document.sheets.single().rows.first())
        assertTrue(document.sheets.single().notes.contains("Origen web: training.example"))
        assertEquals("Press banca", exercise.rawName)
        assertEquals(3, exercise.sets)
        assertEquals(8, exercise.repsMin)
        assertEquals(8, exercise.repsMax)
    }

    @Test
    fun `read downloads a macro-enabled xlsm workbook without executing macros`() = runTest {
        val workbook = minimalXlsx(includeMacroProject = true)
        val response = FakeHttpConnection(
            url = URL("https://training.example/rutina.xlsm"),
            status = 200,
            body = workbook,
            headers = mapOf("Content-Disposition" to "attachment; filename=\"rutina.xlsm\""),
            contentType = "application/vnd.ms-excel.sheet.macroEnabled.12",
        )

        val document = ProgramUrlReader.readWithTransport(
            rawUrl = "https://training.example/rutina.xlsm",
            validateHost = { it },
            openConnection = { response },
        )
        val exercise = ProgramImportParser.parseSheets(document.sheets, document.title)
            .weeks.single().days.single().exercises.single()

        assertEquals("rutina", document.title)
        assertEquals("Press banca", exercise.rawName)
        assertEquals(3, exercise.sets)
        assertEquals(8, exercise.repsMax)
    }

    @Test
    fun `PDF URL uses local OCR, reports page progress, and keeps its public source route`() = runTest {
        val pdf = "%PDF-1.7\nlocal fixture".toByteArray(Charsets.US_ASCII)
        val response = FakeHttpConnection(
            url = URL("https://training.example/share?token=private"),
            status = 200,
            body = pdf,
            headers = mapOf("Content-Disposition" to "attachment; filename=\"rutina.pdf\""),
            contentType = "application/pdf",
        )
        val ocr = FakeProgramUrlOcr()
        val progress = mutableListOf<Pair<Int, Int>>()

        val document = ProgramUrlReader.readWithTransport(
            rawUrl = "https://training.example/share?token=private",
            validateHost = { it },
            openConnection = { response },
            onProgress = { page, pages -> progress += page to pages },
            ocr = ocr,
        )

        assertArrayEquals(pdf, ocr.pdfBytes)
        assertEquals("rutina", document.title)
        assertEquals("https://training.example/share", document.sourceUrl)
        assertEquals(listOf(1 to 3), progress)
        assertTrue(document.sheets.single().notes.contains("Origen web: training.example"))
    }

    @Test
    fun `image URL uses the same on-device OCR route as a local photo`() = runTest {
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01)
        val response = FakeHttpConnection(
            url = URL("https://training.example/rutina.jpg"),
            status = 200,
            body = image,
            contentType = "image/jpeg",
        )
        val ocr = FakeProgramUrlOcr()

        val document = ProgramUrlReader.readWithTransport(
            rawUrl = "https://training.example/rutina.jpg",
            validateHost = { it },
            openConnection = { response },
            ocr = ocr,
        )

        assertArrayEquals(image, ocr.imageBytes)
        assertEquals("rutina", document.title)
        assertEquals("https://training.example/rutina.jpg", document.sourceUrl)
        assertTrue(document.sheets.single().notes.contains("Origen web: training.example"))
    }

    @Test
    fun `read consumes a real HttpURLConnection response and parses a routine`() = runTest {
        val body = """
            <html><head><title>Rutina local</title></head><body>
            <table><tr><th>Ejercicio</th><th>Series</th><th>Reps</th></tr>
            <tr><td>Press banca</td><td>3</td><td>8</td></tr></table>
            </body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val requestLines = AtomicReference<List<String>>(emptyList())

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val responder = thread(isDaemon = true, name = "program-url-test-server") {
                server.accept().use { client ->
                    client.soTimeout = 5_000
                    val request = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val lines = buildList {
                        while (true) {
                            val line = request.readLine() ?: break
                            add(line)
                            if (line.isEmpty()) break
                        }
                    }
                    requestLines.set(lines)
                    val responseHead = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    client.getOutputStream().apply {
                        write(responseHead.toByteArray(Charsets.ISO_8859_1))
                        write(body)
                        flush()
                    }
                }
            }

            try {
                val document = ProgramUrlReader.readWithTransport(
                    rawUrl = "https://fixture.example/rutina",
                    validateHost = { it },
                    openConnection = { uri ->
                        URL("http://127.0.0.1:${server.localPort}${uri.rawPath}")
                            .openConnection() as HttpURLConnection
                    },
                )
                val exercise = ProgramImportParser.parseSheets(document.sheets, document.title)
                    .weeks.single().days.single().exercises.single()

                responder.join(5_000)
                assertFalse("local server should finish after the response", responder.isAlive)
                assertEquals("Rutina local", document.title)
                assertEquals("fixture.example", URI(document.sourceUrl).host)
                assertEquals("Press banca", exercise.rawName)
                assertEquals(3, exercise.sets)
                assertEquals(8, exercise.repsMin)
                assertTrue(requestLines.get().first().startsWith("GET /rutina HTTP/1.1"))
                assertTrue(requestLines.get().any { it.equals("User-Agent: KPKNFit Program Import", ignoreCase = true) })
            } finally {
                server.close()
                responder.join(5_000)
            }
        }
    }

    @Test
    fun `read decodes an RFC 5987 filename for a shared workbook`() = runTest {
        val response = FakeHttpConnection(
            url = URL("https://training.example/share?token=private"),
            status = 200,
            body = minimalXlsx(),
            headers = mapOf("Content-Disposition" to "attachment; filename*=UTF-8''plan%20de%20fuerza.xlsx"),
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )

        val document = ProgramUrlReader.readWithTransport(
            rawUrl = "https://training.example/share?token=private",
            validateHost = { it },
            openConnection = { response },
        )

        assertEquals("plan de fuerza", document.title)
        assertEquals("https://training.example/share", document.sourceUrl)
        assertEquals("Press banca", ProgramImportParser.parseSheets(document.sheets, document.title)
            .weeks.single().days.single().exercises.single().rawName)
    }

    @Test
    fun `read enforces the byte cap when content length is unknown`() = runTest {
        val response = FakeHttpConnection(
            url = URL("https://training.example/programa.txt"),
            status = 200,
            body = ByteArray(ImportLimits.MAX_FILE_BYTES.toInt() + 1),
            contentType = "text/plain; charset=utf-8",
        )

        val failure = runCatching {
            ProgramUrlReader.readWithTransport(
                rawUrl = "https://training.example/programa.txt",
                validateHost = { it },
                openConnection = { response },
            )
        }.exceptionOrNull()

        assertTrue(failure is ImportFileException)
        assertEquals("La página o el archivo supera el límite de 5 MB.", failure?.message)
    }

    @Test
    fun `read stops after five redirects`() = runTest {
        val opened = mutableListOf<URI>()

        val failure = runCatching {
            ProgramUrlReader.readWithTransport(
                rawUrl = "https://training.example/inicio",
                validateHost = { it },
                openConnection = { uri ->
                    opened += uri
                    FakeHttpConnection(
                        url = uri.toURL(),
                        status = 302,
                        headers = mapOf("Location" to "/siguiente-${opened.size}"),
                    )
                },
            )
        }.exceptionOrNull()

        assertTrue(failure is ImportFileException)
        assertEquals(6, opened.size)
        assertEquals("/siguiente-5", opened.last().path)
    }

    @Test
    fun `read revalidates a redirect destination before opening it`() = runTest {
        val validated = mutableListOf<URI>()
        var opened = 0
        val first = FakeHttpConnection(
            url = URL("https://training.example/inicio"),
            status = 302,
            headers = mapOf("Location" to "https://localhost/privado"),
        )

        val failure = runCatching {
            ProgramUrlReader.readWithTransport(
                rawUrl = "https://training.example/inicio",
                validateHost = { uri ->
                    validated.add(uri)
                    if (uri.host == "localhost") ProgramUrlReader.validatePublicHttpsUrl(uri.toString()) else uri
                },
                openConnection = { opened++; first },
            )
        }.exceptionOrNull()

        assertTrue(failure is ImportFileException)
        assertEquals(listOf("training.example", "localhost"), validated.map { it.host })
        assertEquals(1, opened)
    }

    private class FakeHttpConnection(
        url: URL,
        private val status: Int,
        private val body: ByteArray = byteArrayOf(),
        private val headers: Map<String, String> = emptyMap(),
        private val contentType: String? = null,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getHeaderField(name: String?): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        override fun getContentType(): String? = contentType
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    }

    private class FakeProgramUrlOcr : ProgramUrlOcr {
        lateinit var pdfBytes: ByteArray
        lateinit var imageBytes: ByteArray

        override suspend fun readPdf(
            bytes: ByteArray,
            title: String,
            cacheDir: File?,
            onProgress: (Int, Int) -> Unit,
        ): List<ImportSheet> {
            pdfBytes = bytes
            onProgress(1, 3)
            return listOf(ImportSheet(name = title, rows = listOf(listOf("Sentadilla", "4", "6"))))
        }

        override suspend fun readImage(bytes: ByteArray, title: String): List<ImportSheet> {
            imageBytes = bytes
            return listOf(ImportSheet(name = title, rows = listOf(listOf("Press banca", "3", "8"))))
        }
    }

    private fun minimalXlsx(includeMacroProject: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entryText(
                "xl/workbook.xml",
                """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Día 1" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            zip.entryText(
                "xl/_rels/workbook.xml.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            )
            zip.entryText(
                "xl/worksheets/sheet1.xml",
                """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Ejercicio</t></is></c><c r="B1" t="inlineStr"><is><t>Series</t></is></c><c r="C1" t="inlineStr"><is><t>Reps</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>Press banca</t></is></c><c r="B2"><v>3</v></c><c r="C2"><v>8</v></c></row></sheetData></worksheet>""",
            )
            if (includeMacroProject) {
                zip.putNextEntry(ZipEntry("xl/vbaProject.bin"))
                zip.write(byteArrayOf(0x4D, 0x5A, 0x00, 0x01))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entryText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
