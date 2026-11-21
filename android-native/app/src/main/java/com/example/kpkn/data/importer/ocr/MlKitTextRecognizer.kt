package com.example.kpkn.data.importer.ocr

import android.graphics.Bitmap
import com.example.kpkn.domain.training.importer.OcrWord
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * OCR de ML Kit con un cliente local reutilizado durante la lectura de un documento.
 */
class MlKitTextRecognizer private constructor(private val sessionClient: TextRecognizer?) : TextRecognizerPort, Closeable {
    constructor() : this(null)

    override suspend fun recognize(bitmap: Bitmap): List<OcrWord> {
        val client = sessionClient ?: newClient()
        try {
            return recognize(client, bitmap)
        } finally {
            if (sessionClient == null) client.close()
        }
    }

    private suspend fun recognize(client: TextRecognizer, bitmap: Bitmap): List<OcrWord> {
        val task = client.process(InputImage.fromBitmap(bitmap, 0))
        val completed = CompletableDeferred<Unit>()
        task.addOnCompleteListener(Executor { it.run() }) { completed.complete(Unit) }
        try {
            val text = task.awaitResult()
            return text.textBlocks
                .flatMap { block -> block.lines }
                .flatMap { line -> line.elements }
                .mapNotNull { element ->
                    val box = element.boundingBox ?: return@mapNotNull null
                    val word = element.text.trim()
                    if (word.isEmpty()) null else OcrWord(word, box.left, box.top, box.right, box.bottom, element.confidence)
                }
        } finally {
            withContext(NonCancellable) { completed.await() }
        }
    }

    override fun close() {
        sessionClient?.close()
    }

    internal companion object {
        private fun newClient(): TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        fun forSession(): MlKitTextRecognizer = MlKitTextRecognizer(newClient())
    }
}

/** Espera el resultado de una [Task] de Play Services sin la librería de coroutines de Play Services. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    val executor = Executor { it.run() }
    addOnSuccessListener(executor) { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener(executor) { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener(executor) { continuation.cancel() }
}
