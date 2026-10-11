package com.example.kpkn.data.importer.ocr

import android.graphics.Bitmap
import com.example.kpkn.domain.training.importer.OcrWord
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * OCR de ML Kit con el modelo latino empaquetado en la APK: sin red ni descargas. Abre un cliente por página y lo cierra
 * al terminar; las palabras salen de sus «elements», con su caja.
 */
class MlKitTextRecognizer : TextRecognizerPort {
    override suspend fun recognize(bitmap: Bitmap): List<OcrWord> {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = client.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
            return text.textBlocks
                .flatMap { block -> block.lines }
                .flatMap { line -> line.elements }
                .mapNotNull { element ->
                    val box = element.boundingBox ?: return@mapNotNull null
                    val word = element.text.trim()
                    if (word.isEmpty()) null else OcrWord(word, box.left, box.top, box.right, box.bottom)
                }
        } finally {
            client.close()
        }
    }
}

/** Espera el resultado de una [Task] de Play Services sin la librería de coroutines de Play Services. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
