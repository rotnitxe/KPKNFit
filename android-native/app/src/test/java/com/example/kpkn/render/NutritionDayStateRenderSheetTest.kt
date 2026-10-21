package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.nutrition.daystate.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknShape
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

/** Línea y contenido real de su hoja, a 360 dp y con la letra al 100 % y 130 %. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-mdpi", application = android.app.Application::class)
class NutritionDayStateRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() {
            assumeTrue("Se necesita KPKN_RENDER_DIR para guardar los PNG.", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }
    @get:Rule val rule = createComposeRule()
    private var hostView: View? = null
    private fun render(name: String, scale: Float) {
        val state = NutritionDayStateSamples.state(name)
        rule.setContent { KPKNTheme {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides true) {
                Column(Modifier.testTag("day-state-sheet").width(360.dp).background(KpknSurface.canvas).padding(top = 16.dp)) {
                    NutritionDayStateLine(state, {}, {}, {}, Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.kpknGlassOrFallback(null, KpknShape.sheet, variant = KpknGlassVariant.SHEET)) {
                        NutritionDayStateOptions(state, {})
                    }
                }
            }
        } }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("day-state-sheet").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(full)) }
        val cropped = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "dia_${name}_${(scale * 100).toInt()}.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cropped.recycle()
        full.recycle()
    }
    @Test fun unmarked100() = render("unmarked", 1f)
    @Test fun unmarked130() = render("unmarked", 1.3f)
    @Test fun complete100() = render("complete", 1f)
    @Test fun complete130() = render("complete", 1.3f)
    @Test fun partial100() = render("partial", 1f)
    @Test fun partial130() = render("partial", 1.3f)
    @Test fun fasting100() = render("fasting", 1f)
    @Test fun fasting130() = render("fasting", 1.3f)
    @Test fun suggested100() = render("suggested", 1f)
    @Test fun suggested130() = render("suggested", 1.3f)
}
