package com.example.kpkn.data.importer.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.media.ExifInterface
import com.example.kpkn.data.importer.ImportFileException
import com.example.kpkn.data.importer.ImportLimits
import com.example.kpkn.data.importer.ImportSourceMediaStore
import com.example.kpkn.domain.training.importer.ImportDocumentType
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.robolectric.Shadows
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.importer.ImportKind
import com.example.kpkn.data.importer.importKindOf
import com.example.kpkn.data.importer.peekWithinLimits
import com.example.kpkn.data.importer.readSheets
import com.example.kpkn.domain.training.importer.OcrWord
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ProgramImportParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reconocedor falso: devuelve [words] para cada página, cuenta las llamadas y cancela la lectura en la llamada [cancelAt]. */
private class FakeRecognizer(
    private val cancelAt: Int = -1,
    private val words: (page: Int) -> List<OcrWord> = { emptyList() },
) : TextRecognizerPort {
    var calls = 0
        private set

    override suspend fun recognize(bitmap: Bitmap): List<OcrWord> {
        calls++
        if (calls == cancelAt) currentCoroutineContext()[Job]?.cancel()
        return words(calls)
    }
}

/** Páginas falsas de 16 × 16 píxeles: el reconocedor falso no mira su contenido. */
private class FakePages(override val pageCount: Int) : PdfPageSource {
    override fun render(index: Int): Bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

    override fun close() {}
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcrImportSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver get() = context.contentResolver

    @Test
    fun `PDF fallback limits the copy and deletes the temporary file`() {
        val uri = Uri.parse("content://test.import/large-pdf")
        val source = ByteArrayInputStream(pdfOfSize((ImportLimits.MAX_PDF_BYTES + 1).toInt()))
        Shadows.shadowOf(resolver).registerInputStream(uri, source)
        val cache = context.cacheDir.resolve("pdf-fallback").apply { mkdirs() }
        val error = assertThrows(ImportFileException::class.java) {
            runBlocking { PdfRendererPages.open(resolver, uri, cache) }
        }
        assertEquals(OcrImportSource.PDF_TOO_LARGE_MESSAGE, error.message)
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `cancelled evidence retention propagates and cleans partial copies`() {
        val uri = Uri.parse("content://test.import/cancel-copy")
        val source = object : InputStream() {
            private var started = false
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (started) throw CancellationException("Cancelled during copy")
                started = true
                buffer[offset] = 1
                return 1
            }
        }
        Shadows.shadowOf(resolver).registerInputStream(uri, source)
        val files = context.filesDir.resolve("retention-test")
        val store = ImportSourceMediaStore(files)
        assertThrows(CancellationException::class.java) {
            runBlocking { store.retain(resolver, uri, ImportDocumentType.PDF) }
        }
        assertTrue(files.resolve("import-source-evidence").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `corrupt EXIF falls back to original orientation and releases the decoded bitmap`() {
        val decoded = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        val scaled = OcrImportSource.orientAndScale(decoded, 32) {
            ExifInterface(ByteArrayInputStream(byteArrayOf(0)))
        }
        try {
            assertEquals(32, scaled.width)
            assertEquals(16, scaled.height)
            assertTrue(decoded.isRecycled)
        } finally {
            scaled.recycle()
        }
    }

    @Test
    fun `orientAndScale preserves positive axes when scaling thin bitmaps rotated 90 degrees`() {
        val temp = File(context.cacheDir, "exif-rot90.jpg")
        val tiny = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        temp.outputStream().use { out -> tiny.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        tiny.recycle()
        ExifInterface(temp.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val decoded = Bitmap.createBitmap(4_000, 1, Bitmap.Config.ARGB_8888)
        val scaled = try {
            OcrImportSource.orientAndScale(decoded, 2_000) { ExifInterface(temp.absolutePath) }
        } finally {
            temp.delete()
        }
        try {
            assertEquals(1, scaled.width)
            assertEquals(2_000, scaled.height)
            assertTrue(decoded.isRecycled)
        } finally {
            scaled.recycle()
        }
    }

    private fun word(text: String, left: Int, top: Int, confidence: Float? = null) =
        OcrWord(text, left, top, left + text.length * 20, top + 30, confidence)

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    /** Escribe [content] en la caché de la prueba: se lee con un uri de archivo, que el ContentResolver abre sin proveedor. */
    private fun tempFile(name: String, content: ByteArray): File = File(context.cacheDir, name).apply { writeBytes(content) }

    /** Un PDF de [size] bytes que empieza por «%PDF» (no se dibuja: sirve para medir el tope). */
    private fun pdfOfSize(size: Int): ByteArray = ByteArray(size).also { "%PDF-1.7".toByteArray().copyInto(it) }

    @Test
    fun `las páginas salen en orden y la hoja lleva siempre la nota de imagen`() = runBlocking {
        val recognizer = FakeRecognizer { page -> listOf(word("Página$page", 100, 100)) }
        val captured = mutableListOf<Pair<Int, OcrWord>>()
        val sheet = OcrImportSource.readPages(
            FakePages(2), "Plan", recognizer, onProgress = { _, _ -> },
            onPageRecognized = { page, ocrPage -> captured += page to ocrPage.words.single() },
        ).single()
        assertEquals("Plan", sheet.name)
        assertEquals(listOf(listOf("Página1"), listOf("Página2")), sheet.rows)
        assertEquals(listOf(OcrImportSource.PDF_OCR_NOTE), sheet.notes)
        assertEquals(listOf(1, 2), captured.map { it.first })
        assertEquals(listOf("Página1", "Página2"), captured.map { it.second.text })
        assertEquals(0.0f, captured[0].second.confidence ?: 0.0f, 0.001f)
    }

    @Test
    fun `confianza OCR baja en foto añade un aviso de revisión que llega al borrador`() = runBlocking {
        val png = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val recognizer = FakeRecognizer {
            listOf(
                word("Sentadilla", 20, 100, confidence = 0.42f),
                word("3x8", 500, 100, confidence = 0.92f),
            )
        }

        val uri = Uri.fromFile(tempFile("baja-confianza.png", png))
        val sheet = OcrImportSource.readImage(resolver, uri, "Foto", recognizer).single()
        val draft = ProgramImportParser.parseSheets(listOf(sheet), "Plan")

        assertTrue("la palabra de confianza baja se conserva", sheet.rows.flatten().contains("Sentadilla"))
        assertTrue(sheet.notes.contains(OcrImportSource.LOW_CONFIDENCE_REVIEW_NOTE))
        assertEquals(2, sheet.ocrConfidence?.detectedWords)
        assertEquals(2, sheet.ocrConfidence?.scoredWords)
        assertEquals(1, sheet.ocrConfidence?.lowConfidenceWords)
        assertEquals(0.42f, sheet.ocrConfidence?.minimumConfidence!!, 0.001f)
        assertEquals(0.67f, sheet.ocrConfidence?.meanConfidence!!, 0.001f)
        assertTrue(
            draft.warnings.any {
                it.kind == ImportWarningKind.SHEET_NOTE && it.message ==
                    "Hoja «Foto»: ${OcrImportSource.LOW_CONFIDENCE_REVIEW_NOTE}"
            },
        )
    }

    @Test
    fun `con más de 30 páginas lee 30 y la hoja lo dice`() = runBlocking {
        val recognizer = FakeRecognizer()
        val sheet = OcrImportSource.readPages(FakePages(31), "Largo", recognizer) { _, _ -> }.single()
        assertEquals(30, recognizer.calls)
        assertEquals(listOf(OcrImportSource.PDF_OCR_NOTE, "Solo se leyeron las primeras 30 páginas."), sheet.notes)
    }

    @Test
    fun `cancelar entre páginas corta la lectura sin leer el resto`() = runBlocking {
        val recognizer = FakeRecognizer(cancelAt = 2)
        val job = launch(Dispatchers.Default, start = CoroutineStart.DEFAULT) {
            OcrImportSource.readPages(FakePages(5), "Plan", recognizer) { _, _ -> }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(2, recognizer.calls)
    }

    @Test
    fun `una foto PNG se lee con su nota y sus filas`() = runBlocking {
        val png = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val recognizer = FakeRecognizer { listOf(word("Remo", 10, 10)) }
        val uri = Uri.fromFile(tempFile("foto.png", png))
        val captured = mutableListOf<OcrWord>()
        val sheet = OcrImportSource.readImage(resolver, uri, "Foto", recognizer, onPageRecognized = { captured += it.words }).single()
        assertEquals("Foto", sheet.name)
        assertEquals(listOf(listOf("Remo")), sheet.rows)
        assertEquals(listOf(OcrImportSource.IMAGE_NOTE), sheet.notes)
        assertEquals(listOf("Remo"), captured.map { it.text })
        assertEquals(10, captured.single().left)
    }

    @Test
    fun `un PDF de 6 MB no se rechaza por tamaño y uno de 21 MB sí`() = runBlocking {
        val mb = 1024 * 1024
        // Dentro del tope de 20 MB: el tipo sale PDF y no hay error de tamaño.
        assertEquals(ImportKind.PDF, peekWithinLimits(ByteArrayInputStream(pdfOfSize(6 * mb)), "application/pdf").kind)
        // Por encima del tope se corta antes de abrir el PDF, así que no llega a dibujarse.
        val big = readSheets(resolver, Uri.fromFile(tempFile("veintiuno.pdf", pdfOfSize(21 * mb))), "application/pdf")
        assertEquals("El PDF es demasiado grande (máx. 20 MB).", big.exceptionOrNull()?.message)
        // Un texto o una hoja sigue con su tope de 5 MB.
        val csv = runCatching { peekWithinLimits(ByteArrayInputStream(ByteArray(6 * mb)), "text/csv") }.exceptionOrNull()
        assertEquals("El archivo es demasiado grande (máx. 5 MB).", csv?.message)
    }

    @Test
    fun `la firma decide el tipo y el MIME solo rescata una foto sin firma`() {
        assertEquals(ImportKind.PDF, importKindOf("%PDF-1.7".toByteArray(), null))
        assertEquals(ImportKind.IMAGE, importKindOf(bytes(0xFF, 0xD8, 0xFF, 0xE0), null))
        assertEquals(ImportKind.IMAGE, importKindOf(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), null))
        assertEquals(ImportKind.IMAGE, importKindOf("RIFF".toByteArray() + ByteArray(4) + "WEBPVP8 ".toByteArray(), null))
        assertEquals(ImportKind.IMAGE, importKindOf(ByteArray(4) + "ftyp".toByteArray() + "heic".toByteArray(), null))
        // Un ZIP es un libro o un documento aunque el proveedor diga que es una imagen.
        assertEquals(ImportKind.OTHER, importKindOf(bytes(0x50, 0x4B, 0x03, 0x04), "image/png"))
        assertEquals(ImportKind.IMAGE, importKindOf("hola".toByteArray(), "image/jpeg"))
        assertEquals(ImportKind.OTHER, importKindOf("Press banca;4;8".toByteArray(), "text/plain"))
    }
}
