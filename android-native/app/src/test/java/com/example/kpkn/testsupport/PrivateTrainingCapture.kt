package com.example.kpkn.testsupport

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import java.io.File

/** Optional native Robolectric rendering evidence; the behavioral assertions always execute. */
fun SemanticsNodeInteraction.capturePrivateTrainingEvidence(name: String) {
    val directory = System.getenv("KPKN_RENDER_DIR") ?: return
    val bitmap = captureToImage().asAndroidBitmap()
    val file = File(directory, "$name.png")
    file.parentFile?.mkdirs()
    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
}
