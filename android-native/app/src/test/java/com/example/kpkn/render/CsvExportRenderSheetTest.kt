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
import com.example.kpkn.screens.settings.export.*
import com.example.kpkn.data.export.CsvExportDomain
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchRow
import com.example.kpkn.screens.nutrition.design.logger.loggerSearchBasisLabel
import com.example.kpkn.screens.nutrition.design.NutritionFoodRow
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.RecipeCalculator
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
class CsvExportRenderSheetTest {
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
                    Box(Modifier.width(360.dp).height(1000.dp).background(KpknSurface.canvas).testTag("csv-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("csv-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "csv_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }
    @Composable private fun sheet(name: String) {
        val selected = if (name == "multiple") CsvExportDomain.entries.toSet() else if (name == "empty") emptySet() else setOf(CsvExportDomain.MEALS)
        CsvExportContent(selected, {}, name == "busy", if (name == "error") "No se pudo preparar la exportación. Tus datos se conservan." else null,
            name != "empty", {}, {}, Modifier.fillMaxSize())
    }
    @Test fun emptyAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("empty", 1f) { sheet("empty") }
    }
    @Test fun emptyAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("empty", 1.3f) { sheet("empty") }
    }
    @Test fun dataAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("data", 1f) { sheet("data") }
    }
    @Test fun dataAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("data", 1.3f) { sheet("data") }
    }
    @Test fun multipleAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("multiple", 1f) { sheet("multiple") }
    }
    @Test fun multipleAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("multiple", 1.3f) { sheet("multiple") }
    }
    @Test fun errorAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("error", 1f) { sheet("error") }
    }
    @Test fun errorAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("error", 1.3f) { sheet("error") }
    }
    @Test fun busyAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("busy", 1f) { sheet("busy") }
    }
    @Test fun busyAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("busy", 1.3f) { sheet("busy") }
    }
}
