package com.example.kpkn.data.nutrition.label

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.example.kpkn.data.importer.ImportFileException
import com.example.kpkn.data.importer.ocr.*
import com.example.kpkn.domain.nutrition.label.*
import com.example.kpkn.domain.training.importer.OcrLayout
import com.example.kpkn.domain.training.importer.OcrPage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface LabelReadResult {
    data class Ready(val label: LabelParse) : LabelReadResult
    data class NoTable(val message: String = "No he encontrado una tabla nutricional en esta foto.") : LabelReadResult
    data class Failed(val message: String, val imageError: Boolean) : LabelReadResult
}
interface LabelReaderPort { suspend fun read(uri: Uri): LabelReadResult }

class ReadNutritionLabel(private val resolver: ContentResolver, private val recognizer: TextRecognizerPort = MlKitTextRecognizer()) : LabelReaderPort {
    private val gate = Mutex()
    override suspend fun read(uri: Uri): LabelReadResult = process {
        OcrImportSource.readImage(resolver, uri, "Etiqueta nutricional", recognizer).flatMap { it.rows }
    }
    /** El harness transfiere la propiedad del bitmap; se recicla al terminar, igual que en la ruta de galería. */
    internal suspend fun readBitmapForDebug(bitmap: Bitmap): LabelReadResult = process {
        val page = try { OcrPage(bitmap.width, bitmap.height, recognizer.recognize(bitmap)) } finally { bitmap.recycle() }
        OcrLayout.reconstruct(listOf(page))
    }
    private suspend fun process(rows: suspend () -> List<List<String>>): LabelReadResult = withContext(Dispatchers.IO) {
        gate.withLock {
            try {
                // ML Kit no cancela su tarea nativa: esperar protege el bitmap mientras la usa.
                val reconstructed = withContext(NonCancellable) { rows() }
                currentCoroutineContext().ensureActive()
                val parsed = NutritionLabelParser.parse(reconstructed)
                if (parsed.fields.isEmpty()) LabelReadResult.NoTable(parsed.reason ?: "No he encontrado una tabla nutricional en esta foto.") else LabelReadResult.Ready(parsed)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ImportFileException) { LabelReadResult.Failed("No se pudo abrir la imagen. Elige otra foto.", true) }
            catch (_: IOException) { LabelReadResult.Failed("No se pudo leer la imagen. Elige otra foto.", true) }
            catch (_: SecurityException) { LabelReadResult.Failed("No hay acceso a esa imagen. Vuelve a elegirla.", true) }
            catch (_: Exception) { LabelReadResult.Failed("No se pudo leer la etiqueta. Inténtalo con otra imagen.", false) }
        }
    }
}
