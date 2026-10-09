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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.entreno.plan.goalArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.goalCoverArt
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO de los tres objetivos generales (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin
 * ella se salta. Escribe `goal_generales_lista.png` (ocho cuadros del período de cada símbolo a 56 dp, seleccionados) y
 * `goal_generales_portada.png` (los mismos a 200 dp, con la línea fina de portada).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w2100dp-h2100dp-mdpi", application = android.app.Application::class)
class GoalArtRenderSheetTest {

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

    /** Los tres generales, en el orden de la lista. */
    private val generals = listOf(
        TrainingGoalProfile.STRENGTH_MUSCLE,
        TrainingGoalProfile.STRENGTH_CARDIO,
        TrainingGoalProfile.FUNCTIONAL_HEALTH,
    )

    private var hostView: View? = null

    /** Una fila de ocho cuadros por símbolo: a tamaño de lista (56 dp) si [list], o de portada (200 dp) si no. */
    @Composable
    private fun Sheet(list: Boolean) {
        Column {
            for (profile in generals) {
                val art = if (list) goalArt(profile) else goalCoverArt(profile)
                val px = if (list) 56f else 200f
                Row {
                    for (i in 0 until 8) SymbolRenderSheet.Frame(art, art.period * i / 8f, selected = true, px = px)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    @Test
    fun renderTheGeneralSheets() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column(Modifier.background(Color.Black).padding(16.dp)) {
                    Box(Modifier.testTag("lista")) { Sheet(list = true) }
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.testTag("portada")) { Sheet(list = false) }
                }
            }
        }
        rule.waitForIdle()
        val lista = rule.onNodeWithTag("lista").fetchSemanticsNode().boundsInRoot
        val portada = rule.onNodeWithTag("portada").fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread {
            SymbolRenderSheet.savePng(hostView!!, lista, "goal_generales_lista")
            SymbolRenderSheet.savePng(hostView!!, portada, "goal_generales_portada")
        }
    }
}
