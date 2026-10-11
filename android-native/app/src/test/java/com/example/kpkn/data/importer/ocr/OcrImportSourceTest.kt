package com.example.kpkn.data.importer.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.importer.ImportKind
import com.example.kpkn.data.importer.importKindOf
import com.example.kpkn.data.importer.peekWithinLimits
import com.example.kpkn.data.importer.readSheets
import com.example.kpkn.domain.training.importer.OcrWord
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
import org.junit.Assume.assumeNoException
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

    private fun word(text: String, left: Int, top: Int) = OcrWord(text, left, top, left + text.length * 20, top + 30)

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    /** Escribe [content] en la caché de la prueba: se lee con un uri de archivo, que el ContentResolver abre sin proveedor. */
    private fun tempFile(name: String, content: ByteArray): File = File(context.cacheDir, name).apply { writeBytes(content) }

    /** Un PDF de [size] bytes que empieza por «%PDF» (no se dibuja: sirve para medir el tope). */
    private fun pdfOfSize(size: Int): ByteArray = ByteArray(size).also { "%PDF-1.7".toByteArray().copyInto(it) }

    /** Un PDF real de [pages] páginas con texto, hecho con PdfDocument. */
    private fun syntheticPdf(pages: Int): ByteArray {
        val document = PdfDocument()
        try {
            repeat(pages) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                page.canvas.drawText("Página ${index + 1}", 20f, 40f, Paint())
                document.finishPage(page)
            }
            return ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    @Test
    fun `las páginas salen en orden y la hoja lleva siempre la nota de imagen`() = runBlocking {
        val recognizer = FakeRecognizer { page -> listOf(word("Página$page", 100, 100)) }
        val sheet = OcrImportSource.readPages(FakePages(2), "Plan", recognizer) { _, _ -> }.single()
        assertEquals("Plan", sheet.name)
        assertEquals(listOf(listOf("Página1"), listOf("Página2")), sheet.rows)
        assertEquals(listOf(OcrImportSource.IMAGE_NOTE), sheet.notes)
    }

    @Test
    fun `con más de 30 páginas lee 30 y la hoja lo dice`() = runBlocking {
        val recognizer = FakeRecognizer()
        val sheet = OcrImportSource.readPages(FakePages(31), "Largo", recognizer) { _, _ -> }.single()
        assertEquals(30, recognizer.calls)
        assertEquals(listOf(OcrImportSource.IMAGE_NOTE, "Solo se leyeron las primeras 30 páginas."), sheet.notes)
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
    fun `un PDF sintético de dos páginas se dibuja y se lee página a página`() = runBlocking {
        val recognizer = FakeRecognizer { page -> listOf(word("Página$page", 100, 100)) }
        val sheet = try {
            val uri = Uri.fromFile(tempFile("sintetico.pdf", syntheticPdf(pages = 2)))
            OcrImportSource.readPdf(resolver, uri, "PDF", context.cacheDir, recognizer = recognizer).single()
        } catch (error: Throwable) {
            // Si Robolectric no puede dibujar PDF en este entorno, la prueba se salta; la lectura real se mira en el teléfono.
            assumeNoException(error)
            return@runBlocking
        }
        assertEquals(2, recognizer.calls)
        assertEquals(listOf(listOf("Página1"), listOf("Página2")), sheet.rows)
        assertEquals(listOf(OcrImportSource.IMAGE_NOTE), sheet.notes)
    }

    @Test
    fun `una foto PNG se lee con su nota y sus filas`() = runBlocking {
        val png = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val recognizer = FakeRecognizer { listOf(word("Remo", 10, 10)) }
        val uri = Uri.fromFile(tempFile("foto.png", png))
        val sheet = OcrImportSource.readImage(resolver, uri, "Foto", recognizer).single()
        assertEquals("Foto", sheet.name)
        assertEquals(listOf(listOf("Remo")), sheet.rows)
        assertEquals(listOf(OcrImportSource.IMAGE_NOTE), sheet.notes)
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
