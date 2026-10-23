package com.example.kpkn.screens.workout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.io.File

/** Optional evidence from the production layout, using the existing render directory contract. */
internal fun saveLiveQaRender(rule: ComposeTestRule, view: View, name: String) {
    val directory = System.getenv("KPKN_RENDER_DIR")?.takeIf { it.isNotBlank() } ?: return
    rule.waitForIdle()
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val folder = File(directory).apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally { bitmap.recycle() }
}
