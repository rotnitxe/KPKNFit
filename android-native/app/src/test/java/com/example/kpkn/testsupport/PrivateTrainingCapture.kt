package com.example.kpkn.testsupport

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.robolectric.shadows.ShadowDialog
import java.io.File

/** Native Robolectric View.draw evidence; PixelCopy callbacks are unavailable on this host. */
fun ComposeContentTestRule.capturePrivateTrainingEvidence(name: String, dialog: Boolean = true, host: View? = null) {
    val directory = System.getenv("KPKN_RENDER_DIR") ?: return
    runOnIdle {
        val view = if (dialog) requireNotNull(ShadowDialog.getLatestDialog()?.window?.decorView) else requireNotNull(host)
        check(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File(directory, "$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
