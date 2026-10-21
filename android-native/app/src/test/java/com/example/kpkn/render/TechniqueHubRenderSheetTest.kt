package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import com.example.kpkn.screens.technique.hub.TechniqueHubSamples
import com.example.kpkn.screens.technique.hub.TechniqueHubScreen
import com.example.kpkn.screens.technique.hub.TechniqueHubUiState
import com.example.kpkn.screens.technique.hub.design.TechniqueSettingsModule
import com.example.kpkn.screens.technique.importer.VideoImportContent
import com.example.kpkn.ui.design.KpknSurface
import java.io.File
import java.io.FileOutputStream
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
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class TechniqueHubRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun onlyWhenAsked() {
            assumeTrue("La hoja requiere KPKN_RENDER_DIR", !System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        }
    }
    @get:Rule val rule = createComposeRule()
    private var host: View? = null

    private fun render(name: String, scale: Float, padding: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            host = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides true) {
                Box(Modifier.width(360.dp).background(KpknSurface.canvas).testTag("hub-render").then(if (padding) Modifier.padding(16.dp) else Modifier)) {
                    content()
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("hub-render", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val view = requireNotNull(host)
        val complete = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(complete).apply { drawColor(android.graphics.Color.BLACK) }) }
        val left = bounds.left.toInt().coerceIn(0, complete.width - 1)
        val top = bounds.top.toInt().coerceIn(0, complete.height - 1)
        val width = bounds.width.toInt().coerceIn(1, complete.width - left)
        val height = bounds.height.toInt().coerceIn(1, complete.height - top)
        val crop = Bitmap.createBitmap(complete, left, top, width, height)
        val row = IntArray(crop.width)
        var useful = crop.height
        for (y in crop.height - 1 downTo 0) {
            crop.getPixels(row, 0, crop.width, 0, y, crop.width, 1)
            if (row.any { it != android.graphics.Color.BLACK }) {
                useful = minOf(crop.height, y + 1 + (24 * view.resources.displayMetrics.density).toInt())
                break
            }
        }
        val final = Bitmap.createBitmap(crop, 0, 0, crop.width, useful)
        val directory = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { final.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Composable private fun hub(state: TechniqueHubUiState) {
        TechniqueHubScreen(state, {}, {}, {}, {}, {}, {}, {}, {})
    }
    @Composable private fun settings() {
        TechniqueSettingsModule(TechniqueHubSamples.settings, true, {}, {}, {}, {}, {})
    }
    @Composable private fun importer() {
        VideoImportContent(TechniqueHubSamples.importing, TechniqueHubSamples.exercises, {}, {}, {}, {}, {})
    }
    @Test fun hubData100() = render("hub_data_100", 1f) { hub(TechniqueHubSamples.populated) }
    @Test fun hubData130() = render("hub_data_130", 1.3f) { hub(TechniqueHubSamples.populated) }
    @Test fun hubEmpty100() = render("hub_empty_100", 1f) { hub(TechniqueHubSamples.empty) }
    @Test fun hubEmpty130() = render("hub_empty_130", 1.3f) { hub(TechniqueHubSamples.empty) }
    @Test fun settings100() = render("hub_settings_100", 1f, true) { settings() }
    @Test fun settings130() = render("hub_settings_130", 1.3f, true) { settings() }
    @Test fun import100() = render("import_sheet_100", 1f, true) { importer() }
    @Test fun import130() = render("import_sheet_130", 1.3f, true) { importer() }
}
