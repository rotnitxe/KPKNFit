package com.example.kpkn.data.importer.ocr

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.domain.training.importer.OcrWord
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfRendererImportInstrumentedTest {
    @Test
    fun realPdfRendererReadsEveryPageAndReturnsItsOcrRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "ocr-render-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                page.canvas.drawText("Page ${index + 1}", 20f, 40f, Paint())
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }

        var calls = 0
        val recognizer = object : TextRecognizerPort {
            override suspend fun recognize(bitmap: android.graphics.Bitmap): List<OcrWord> {
                calls++
                return listOf(OcrWord("Page$calls", 10, 10, 80, 40))
            }
        }
        try {
            val sheet = OcrImportSource.readPdf(
                contentResolver = context.contentResolver,
                uri = Uri.fromFile(file),
                title = "PDF real",
                cacheDir = context.cacheDir,
                recognizer = recognizer,
            ).single()

            assertEquals(2, calls)
            assertEquals(listOf(listOf("Page1"), listOf("Page2")), sheet.rows)
            assertEquals(listOf(OcrImportSource.PDF_OCR_NOTE), sheet.notes)
        } finally {
            file.delete()
        }
    }
}
