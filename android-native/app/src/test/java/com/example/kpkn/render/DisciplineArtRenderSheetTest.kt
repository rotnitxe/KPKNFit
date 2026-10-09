package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.plan.BodybuildingArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.COVER_LINE_SCALE
import com.example.kpkn.screens.onboarding.design.entreno.plan.GoalArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.PowerbuildingFlexArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.PowerliftingArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.StrongmanArt
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO de los cuatro símbolos de disciplina nuevos (no comprueba nada: escribe PNG). Solo corre con
 * `KPKN_RENDER_DIR`; sin ella se salta. Escribe `disc_lista.png` (diez cuadros del período de cada símbolo a 56 dp,
 * seleccionados) y `disc_portada.png` (los mismos a 200 dp, con la línea fina de portada).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w2100dp-h2100dp-mdpi", application = android.app.Application::class)
class DisciplineArtRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private var hostView: View? = null

    /** Una fila de diez cuadros por símbolo, a [px] píxeles, con el grosor de línea de la escala [lk]. */
    @Composable
    private fun Sheet(lk: Float, px: Float) {
        val arts = remember(lk) {
            listOf<GoalArt>(
                PowerbuildingFlexArt(lk),
                BodybuildingArt(lk),
                StrongmanArt(lk),
                PowerliftingArt(lk),
            )
        }
        Column {
            for (art in arts) {
                Row {
                    for (i in 0 until 10) SymbolRenderSheet.Frame(art, art.period * i / 10f, selected = true, px = px)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    @Test
    fun renderTheDisciplineSheets() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column(Modifier.background(Color.Black).padding(16.dp)) {
                    Box(Modifier.testTag("lista")) { Sheet(lk = 1f, px = 56f) }
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.testTag("portada")) { Sheet(lk = COVER_LINE_SCALE, px = 200f) }
                }
            }
        }
        rule.waitForIdle()
        val lista = rule.onNodeWithTag("lista").fetchSemanticsNode().boundsInRoot
        val portada = rule.onNodeWithTag("portada").fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread {
            SymbolRenderSheet.savePng(hostView!!, lista, "disc_lista")
            SymbolRenderSheet.savePng(hostView!!, portada, "disc_portada")
        }
    }
}
