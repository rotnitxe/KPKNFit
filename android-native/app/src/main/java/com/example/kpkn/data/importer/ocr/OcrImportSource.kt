package com.example.kpkn.data.importer.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.kpkn.data.importer.ImportFileException
import com.example.kpkn.data.importer.ImportLimits
import com.example.kpkn.data.importer.readBoundedBytes
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.OcrLayout
import com.example.kpkn.domain.training.importer.OcrPage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Lee un PDF o una foto con OCR en el teléfono: ML Kit sin conexión y las filas reconstruidas por la posición del texto
 * ([OcrLayout]). Cada resultado es una sola hoja con el título y la nota de que el texto sale de una imagen. Corre en IO y
 * en Default, y se puede cancelar entre páginas.
 */
object OcrImportSource {
    /** Aviso que lleva siempre la hoja leída por OCR. */
    const val IMAGE_NOTE = "Lo leímos de una imagen: revisa los números antes de guardar."

    internal const val PDF_UNREADABLE_MESSAGE = "No pudimos abrir este PDF."
    internal const val PDF_TOO_LARGE_MESSAGE = "El PDF es demasiado grande (máx. 20 MB)."
    internal const val IMAGE_UNREADABLE_MESSAGE = "No pudimos abrir esta imagen."
    internal const val IMAGE_TOO_LARGE_MESSAGE = "La imagen es demasiado grande (máx. 15 MB)."

    /**
     * Lee las páginas de un PDF (hasta [ImportLimits.MAX_OCR_PAGES]). [onProgress] recibe cada página al empezarla. [cacheDir]
     * es donde se copia el PDF si el proveedor no lo deja abrir directamente.
     */
    suspend fun readPdf(
        contentResolver: ContentResolver,
        uri: Uri,
        title: String,
        cacheDir: File?,
        onProgress: (page: Int, pages: Int) -> Unit = { _, _ -> },
        recognizer: TextRecognizerPort = MlKitTextRecognizer(),
    ): List<ImportSheet> {
        val pages = withContext(Dispatchers.IO) { PdfRendererPages.open(contentResolver, uri, cacheDir) }
        return pages.use { readPages(it, title, recognizer, onProgress) }
    }

    /** Lee una foto (JPEG, PNG, WebP o HEIC) con OCR, orientada según su EXIF y con el lado largo acotado. */
    suspend fun readImage(
        contentResolver: ContentResolver,
        uri: Uri,
        title: String,
        recognizer: TextRecognizerPort = MlKitTextRecognizer(),
    ): List<ImportSheet> {
        val bitmap = withContext(Dispatchers.IO) { decodeUpright(contentResolver, uri) }
        val page = try {
            OcrPage(bitmap.width, bitmap.height, recognizer.recognize(bitmap))
        } finally {
            bitmap.recycle()
        }
        val rows = withContext(Dispatchers.Default) { OcrLayout.reconstruct(listOf(page)) }
        return listOf(ImportSheet(name = title, rows = rows, notes = listOf(IMAGE_NOTE), pageStarts = listOf(0)))
    }

    /**
     * Reconoce cada página de [source] (como mucho [ImportLimits.MAX_OCR_PAGES]), la reconstruye y arma la hoja. Comprueba
     * la cancelación antes de cada página y recicla cada bitmap después de reconocerlo.
     */
    internal suspend fun readPages(
        source: PdfPageSource,
        title: String,
        recognizer: TextRecognizerPort,
        onProgress: (page: Int, pages: Int) -> Unit,
    ): List<ImportSheet> {
        val total = minOf(source.pageCount, ImportLimits.MAX_OCR_PAGES)
        val pages = ArrayList<OcrPage>(total)
        for (index in 0 until total) {
            currentCoroutineContext().ensureActive()
            onProgress(index + 1, total)
            val bitmap = withContext(Dispatchers.Default) { source.render(index) }
            val page = try {
                OcrPage(bitmap.width, bitmap.height, recognizer.recognize(bitmap))
            } finally {
                bitmap.recycle()
            }
            pages.add(page)
        }
        // Las filas de cada página van aparte: al unirlas se quitan los muebles de página (OcrLayout.joinPages).
        val pageRows = withContext(Dispatchers.Default) { pages.map { OcrLayout.reconstruct(listOf(it)) } }
        val joined = withContext(Dispatchers.Default) { OcrLayout.joinPages(pageRows) }
        val notes = buildList {
            add(IMAGE_NOTE)
            if (source.pageCount > ImportLimits.MAX_OCR_PAGES) {
                add("Solo se leyeron las primeras ${ImportLimits.MAX_OCR_PAGES} páginas.")
            }
        }
        return listOf(ImportSheet(name = title, rows = joined.rows, notes = notes, ocrPages = pageRows, pageStarts = joined.pageStarts))
    }

    /** La foto como bitmap, con el lado largo de como mucho [ImportLimits.MAX_OCR_IMAGE_SIDE] px y girada según su EXIF. */
    private fun decodeUpright(contentResolver: ContentResolver, uri: Uri): Bitmap {
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            readBoundedBytes(input, ImportLimits.MAX_IMAGE_BYTES, IMAGE_TOO_LARGE_MESSAGE)
        } ?: throw ImportFileException(IMAGE_UNREADABLE_MESSAGE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val side = max(bounds.outWidth, bounds.outHeight)
        if (side <= 0) throw ImportFileException(IMAGE_UNREADABLE_MESSAGE)
        // Submuestreo de potencias de dos: el lado largo queda entre el tope y el doble, sin cargar la foto completa.
        var sample = 1
        while (side / (sample * 2) >= ImportLimits.MAX_OCR_IMAGE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw ImportFileException(IMAGE_UNREADABLE_MESSAGE)
        return scaleToSide(rotateByExif(decoded, bytes), ImportLimits.MAX_OCR_IMAGE_SIDE)
    }

    /** Gira o refleja la foto según su orientación EXIF; sin EXIF legible, la deja como está. */
    private fun rotateByExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (error: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = orientationMatrix(orientation) ?: return bitmap
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return turned
    }

    /** La transformación de cada orientación EXIF; null si la foto ya está derecha. */
    private fun orientationMatrix(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return null
        }
        return matrix
    }

    /** Reduce la foto para que su lado largo no pase de [maxSide] píxeles. */
    private fun scaleToSide(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxSide) return bitmap
        val factor = maxSide.toDouble() / longSide
        val width = max(1, (bitmap.width * factor).roundToInt())
        val height = max(1, (bitmap.height * factor).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        bitmap.recycle()
        return scaled
    }
}
