package com.example.kpkn.screens.nutrition.scan

import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.kpkn.domain.nutrition.Gtin
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * El lector de códigos: de una imagen salen los textos de los códigos que ve, sin validarlos (eso lo hace `Gtin`). Va detrás de una
 * interfaz para que la cámara real, la autocomprobación con mapas de bits y las pruebas usen el mismo contrato.
 */
interface BarcodeDecoder {
    /** Textos de los códigos EAN-13, EAN-8, UPC-A y UPC-E que se ven en [bitmap]; vacío si no hay ninguno. */
    suspend fun decode(bitmap: Bitmap): List<String>

    /** Lo mismo para un fotograma de CameraX. Quien llama cierra el fotograma al terminar. */
    suspend fun decode(frame: ImageProxy): List<String>

    /** Libera el modelo. Después no se usa más. */
    fun close()
}

/**
 * ML Kit `barcode-scanning` en su variante EMPAQUETADA: el modelo va dentro de la APK, así que no hay Play Services, descargas ni red.
 * Solo se piden los cuatro formatos de producto de tienda; el cliente se crea al primer uso.
 */
class MlKitBarcodeDecoder : BarcodeDecoder {
    private val scanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }

    override suspend fun decode(bitmap: Bitmap): List<String> = process(InputImage.fromBitmap(bitmap, 0))

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override suspend fun decode(frame: ImageProxy): List<String> {
        val media = frame.image ?: return emptyList()
        return process(InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees))
    }

    override fun close() {
        scanner.close()
    }

    /**
     * Los textos de los códigos vistos. Un UPC-E se entrega ya expandido a UPC-A: ML Kit sabe que es UPC-E y, con solo sus 8 dígitos,
     * `Gtin` no podría distinguirlo de un EAN-8 (con compresión de 5 a 9 todos los UPC-E cuadran también como EAN-8).
     */
    private suspend fun process(image: InputImage): List<String> =
        scanner.process(image).awaitResult().mapNotNull { barcode ->
            val raw = barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (barcode.format == Barcode.FORMAT_UPC_E) Gtin.upcEToUpcA(raw) ?: raw else raw
        }
}

/** Espera el resultado de una [Task] de Play Services sin la librería de coroutines de Play Services. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
