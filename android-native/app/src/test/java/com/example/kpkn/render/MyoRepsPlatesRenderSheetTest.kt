package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.debug.MfpTrainingPreview
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-mdpi", application = Application::class)
class MyoRepsPlatesRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() { assumeTrue(System.getenv("KPKN_RENDER_DIR") != null) }
    }
    @get:Rule val rule = createComposeRule()
    private var host: View? = null
    private fun render(name: String, scale: Float, content: @Composable () -> Unit) {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            KPKNTheme {
                host = LocalView.current
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides true) {
                    Box(Modifier.width(360.dp).height(2300.dp).background(KpknSurface.canvas).testTag("exercise-progress-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("training-card").fetchSemanticsNodes().isNotEmpty() || name == "editor" }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("exercise-progress-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "myo_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }
    @Test fun myobackAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("myo_back",1f) { MfpTrainingPreview.Myo("myo_back") }
    }
    @Test fun myobackAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("myo_back",1.3f) { MfpTrainingPreview.Myo("myo_back") }
    }
    @Test fun normalbackAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("normal_back",1f) { MfpTrainingPreview.Myo("normal_back") }
    }
    @Test fun normalbackAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("normal_back",1.3f) { MfpTrainingPreview.Myo("normal_back") }
    }
    @Test fun dropbackAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("drop_back",1f) { MfpTrainingPreview.Myo("drop_back") }
    }
    @Test fun dropbackAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("drop_back",1.3f) { MfpTrainingPreview.Myo("drop_back") }
    }
    @Test fun restbackAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("rest_back",1f) { MfpTrainingPreview.Myo("rest_back") }
    }
    @Test fun restbackAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("rest_back",1.3f) { MfpTrainingPreview.Myo("rest_back") }
    }
    @Test fun platesAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("plates",1f) { MfpTrainingPreview.Myo("plates") }
    }
    @Test fun platesAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("plates",1.3f) { MfpTrainingPreview.Myo("plates") }
    }
    @Test fun platesunreachableAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("plates_unreachable",1f) { MfpTrainingPreview.Myo("plates_unreachable") }
    }
    @Test fun platesunreachableAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("plates_unreachable",1.3f) { MfpTrainingPreview.Myo("plates_unreachable") }
    }
    @Test fun noinventoryAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("no_inventory",1f) { MfpTrainingPreview.Myo("no_inventory") }
    }
    @Test fun noinventoryAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("no_inventory",1.3f) { MfpTrainingPreview.Myo("no_inventory") }
    }
    @Test fun editorAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("editor",1f) { MfpTrainingPreview.Myo("editor") }
    }
    @Test fun editorAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("editor",1.3f) { MfpTrainingPreview.Myo("editor") }
    }
}
