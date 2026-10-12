package com.example.kpkn.data.importer.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.kpkn.data.importer.ImportFileException
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Ancho al que se dibuja cada página del PDF antes del OCR. */
internal const val OCR_PAGE_WIDTH_PX = 1600

/** Alto máximo de una página dibujada: una página más alta se estrecha para no pedir un bitmap enorme. */
private const val MAX_PAGE_HEIGHT_PX = 4_000

/** Páginas de un PDF dibujadas a bitmap: [PdfRendererPages] en el teléfono y un falso en las pruebas. */
internal interface PdfPageSource : Closeable {
    /** Páginas que tiene el documento (pueden ser más que las que se leen). */
    val pageCount: Int

    /** Dibuja la página [index] (desde 0) sobre blanco, en ARGB_8888. Quien la pide la recicla. */
    fun render(index: Int): Bitmap
}

/**
 * Un PDF abierto con [PdfRenderer]. Si el proveedor no da un descriptor que el renderer acepte, el archivo se copia a la
 * caché y se abre la copia; al cerrar, se borra.
 */
internal class PdfRendererPages private constructor(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    private val copy: File?,
) : PdfPageSource {

    override val pageCount: Int
        get() = renderer.pageCount

    override fun render(index: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val (width, height) = targetSize(page.width, page.height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        copy?.delete()
    }

    companion object {
        /** Abre el PDF de [uri]. Lanza [ImportFileException] si no se puede abrir (protegido, dañado o sin acceso). */
        fun open(contentResolver: ContentResolver, uri: Uri, cacheDir: File?): PdfRendererPages {
            val direct = runCatching { contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (direct != null) {
                try {
                    return PdfRendererPages(PdfRenderer(direct), direct, copy = null)
                } catch (error: Exception) {
                    runCatching { direct.close() }
                }
            }
            // Sin un descriptor que PdfRenderer acepte: se copia el archivo a la caché y se abre la copia.
            val target = cacheDir?.let { runCatching { File.createTempFile("ocr-", ".pdf", it) }.getOrNull() }
                ?: throw ImportFileException(OcrImportSource.PDF_UNREADABLE_MESSAGE)
            try {
                val copied = contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (copied == null) throw ImportFileException(OcrImportSource.PDF_UNREADABLE_MESSAGE)
                val descriptor = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    return PdfRendererPages(PdfRenderer(descriptor), descriptor, copy = target)
                } catch (error: Exception) {
                    runCatching { descriptor.close() }
                    throw error
                }
            } catch (error: Exception) {
                target.delete()
                throw ImportFileException(OcrImportSource.PDF_UNREADABLE_MESSAGE)
            }
        }

        /** Ancho fijo de [OCR_PAGE_WIDTH_PX] con el alto proporcional; si la página es muy alta, se estrecha. */
        private fun targetSize(pageWidth: Int, pageHeight: Int): Pair<Int, Int> {
            val widthPoints = max(1, pageWidth).toDouble()
            val heightPoints = max(1, pageHeight).toDouble()
            var width = OCR_PAGE_WIDTH_PX
            var height = (heightPoints * width / widthPoints).roundToInt()
            if (height > MAX_PAGE_HEIGHT_PX) {
                height = MAX_PAGE_HEIGHT_PX
                width = (widthPoints * height / heightPoints).roundToInt()
            }
            return max(1, width) to max(1, height)
        }
    }
}
