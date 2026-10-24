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
import com.example.kpkn.screens.nutrition.customfood.*
import com.example.kpkn.domain.nutrition.label.*
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchRow
import com.example.kpkn.screens.nutrition.design.logger.loggerSearchBasisLabel
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
class LabelReadRenderSheetTest {
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
                    Box(Modifier.width(360.dp).height(2300.dp).background(KpknSurface.canvas).testTag("label-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("label-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "label_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }
    @Composable private fun sheet(name: String) {
        val label = if (name == "before") null else LabelParse(LabelBasis.PER_100G, 30.0, "g", mapOf(
            LabelField.ENERGY_KCAL to LabelValue(200.0, LabelConfidence.READ, "200 kcal"),
            LabelField.PROTEIN_G to LabelValue(10.0, if (name == "warning") LabelConfidence.UNSURE else LabelConfidence.READ, "10 g")),
            warnings = if (name == "warning") listOf("Las kcal difieren de los macronutrientes. Revisa la foto.") else emptyList())
        val initial = CustomFoodForm(name = "Yogur natural", brand = "Mi despensa")
        val parsed = if (name == "partial" || label == null) label else label.copy(fields = label.fields + mapOf(
            LabelField.CARBS_G to LabelValue(20.0, LabelConfidence.READ, "20 g"),
            LabelField.FAT_G to LabelValue(8.0, LabelConfidence.READ, "8 g")))
        val form = if (parsed == null) initial else LabelFormAdapter.apply(initial, parsed)
        val state = LabelFormState(form, label = parsed, markers = parsed?.fields?.mapKeys { LabelFormAdapter.fields.getValue(it.key) }?.mapValues { it.value.confidence } ?: emptyMap(), canUndo = parsed != null)
        CustomFoodFormContent(form, CustomFoodValidator.validate(form, "render"), false, false, false, null, false,
            {}, {}, {}, {}, {}, {}, Modifier.fillMaxSize(), state)
    }
    @Test fun beforeAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("before", 1f) { sheet("before") }
    }
    @Test fun beforeAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("before", 1.3f) { sheet("before") }
    }
    @Test fun afterAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("after", 1f) { sheet("after") }
    }
    @Test fun afterAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("after", 1.3f) { sheet("after") }
    }
    @Test fun warningAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("warning", 1f) { sheet("warning") }
    }
    @Test fun warningAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("warning", 1.3f) { sheet("warning") }
    }
    @Test fun partialAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("partial", 1f) { sheet("partial") }
    }
    @Test fun partialAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("partial", 1.3f) { sheet("partial") }
    }
}
