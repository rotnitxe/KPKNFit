package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.settings.equipment.*
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopRow
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

/** La página desplegada usa las mismas piezas que la pantalla, sin recortar la zona desplazable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h6000dp-mdpi", application = android.app.Application::class)
class EquipmentInventoryRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() {
            assumeTrue("Se necesita KPKN_RENDER_DIR para guardar los PNG.", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }
    @get:Rule val rule = createComposeRule()
    private var view: View? = null

    private fun render(name: String, scale: Float) {
        assumeTrue("Sin KPKN_RENDER_DIR no se dibuja nada.", System.getenv("KPKN_RENDER_DIR") != null)
        val state = EquipmentInventorySamples.state(name)
        val actions = EquipmentInventoryActions()
        rule.setContent { KPKNTheme {
            view = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides true) {
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
                    Column(Modifier.testTag("equipment-sheet").width(360.dp)) {
                        KpknTopRow(onBack = {})
                        EquipmentInventoryForm(state, actions, Modifier.padding(horizontal = 16.dp))
                        EquipmentInventoryFooter(state, actions, Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        } }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("equipment-sheet").fetchSemanticsNode().boundsInRoot
        val host = view!!
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(Canvas(full)) }
        val cropped = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "material_${name}_${(scale * 100).toInt()}.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cropped.recycle()
        full.recycle()
    }

    @Test fun empty100() = render("empty", 1f)
    @Test fun empty130() = render("empty", 1.3f)
    @Test fun commercial100() = render("commercial", 1f)
    @Test fun commercial130() = render("commercial", 1.3f)
    @Test fun error100() = render("error", 1f)
    @Test fun error130() = render("error", 1.3f)
}
