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
import com.example.kpkn.screens.progress.MuscleVolumePanelActions
import com.example.kpkn.screens.progress.MuscleVolumePanelContent
import com.example.kpkn.screens.progress.MuscleVolumePanelSamples
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

/**
 * Hoja de render del panel de volumen por músculo (T5a), a 360 dp y al 100 % y 130 %: lista con datos, cuerpo con datos (frente y
 * espalda), sin datos, con registros sin asignar, regiones a cero frente a sin datos y la recuperación de AUGE. Pinta el mismo
 * contenido que la pantalla, sin recortar la zona desplazable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h8000dp-mdpi", application = android.app.Application::class)
class MuscleVolumePanelRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() {
            assumeTrue("Se necesita KPKN_RENDER_DIR para guardar los PNG.", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule val rule = createComposeRule()
    private var view: View? = null

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private fun render(name: String, scale: Float) {
        onlyWhenAsked()
        val state = MuscleVolumePanelSamples.byName(name)
        rule.setContent {
            KPKNTheme {
                view = LocalView.current
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides true) {
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
                        Column(Modifier.testTag("muscle-volume-sheet").width(360.dp)) {
                            KpknTopRow(onBack = {})
                            MuscleVolumePanelContent(state, MuscleVolumePanelActions(), Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("muscle-volume-sheet").fetchSemanticsNode().boundsInRoot
        val host = view!!
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(Canvas(full)) }
        val cropped = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "volumen_${name}_${(scale * 100).toInt()}.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cropped.recycle()
        full.recycle()
    }

    @Test fun data100() = render("data", 1f)
    @Test fun data130() = render("data", 1.3f)
    @Test fun total100() = render("total", 1f)
    @Test fun total130() = render("total", 1.3f)
    @Test fun empty100() = render("empty", 1f)
    @Test fun empty130() = render("empty", 1.3f)
    @Test fun sparse100() = render("sparse", 1f)
    @Test fun sparse130() = render("sparse", 1.3f)
    @Test fun unassigned100() = render("unassigned", 1f)
    @Test fun unassigned130() = render("unassigned", 1.3f)
    @Test fun body100() = render("body", 1f)
    @Test fun body130() = render("body", 1.3f)
    @Test fun bodyBack100() = render("body_back", 1f)
    @Test fun bodyBack130() = render("body_back", 1.3f)
    @Test fun bodySparse100() = render("body_sparse", 1f)
    @Test fun bodySparse130() = render("body_sparse", 1.3f)
    @Test fun bodyEmpty100() = render("body_empty", 1f)
    @Test fun bodyEmpty130() = render("body_empty", 1.3f)
}
