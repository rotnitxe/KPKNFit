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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.nutrition.nutrients.*
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchRow
import com.example.kpkn.screens.nutrition.design.NutritionFoodRow
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.NutrientKey
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
class NutrientsRenderSheetTest {
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
                    Box(Modifier.width(360.dp).height(1500.dp).background(KpknSurface.canvas).testTag("nutrients-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("nutrients-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "nutrients_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }
    @Composable private fun sheet(name: String, details: Boolean = false) = NutrientsContent(
        NutrientsSamples.state(name), if (details) setOf(NutrientKey.FIBER) else emptySet(), {}, {}, Modifier.fillMaxSize())
    @Test fun completeAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("complete", 1f) { sheet("complete") }
    }
    @Test fun completeAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("complete", 1.3f) { sheet("complete") }
    }
    @Test fun partialAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("partial", 1f) { sheet("partial") }
    }
    @Test fun partialAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("partial", 1.3f) { sheet("partial") }
    }
    @Test fun nodataAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("no_data", 1f) { sheet("no_data") }
    }
    @Test fun nodataAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("no_data", 1.3f) { sheet("no_data") }
    }
    @Test fun withquickentryAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("with_quick_entry", 1f) { sheet("with_quick_entry") }
    }
    @Test fun withquickentryAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("with_quick_entry", 1.3f) { sheet("with_quick_entry") }
    }
    @Test fun contributorsAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("contributors", 1f) { sheet("partial", true) }
    }
    @Test fun contributorsAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("contributors", 1.3f) { sheet("partial", true) }
    }
}
