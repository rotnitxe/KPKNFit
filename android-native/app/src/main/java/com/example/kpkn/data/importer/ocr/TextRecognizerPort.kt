package com.example.kpkn.data.importer.ocr

import android.graphics.Bitmap
import com.example.kpkn.domain.training.importer.OcrWord

/**
 * Reconocedor de texto de una página. La implementación del teléfono es [MlKitTextRecognizer] (ML Kit, sin conexión);
 * las pruebas inyectan una falsa.
 */
interface TextRecognizerPort {
    /** Palabras de [bitmap], con su caja en píxeles de ese mismo bitmap. */
    suspend fun recognize(bitmap: Bitmap): List<OcrWord>
}
