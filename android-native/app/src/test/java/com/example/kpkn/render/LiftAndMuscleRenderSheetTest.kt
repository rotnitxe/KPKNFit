package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.screens.onboarding.design.entreno.FigPen
import com.example.kpkn.screens.onboarding.design.entreno.FigPose
import com.example.kpkn.screens.onboarding.design.entreno.FigStyle
import com.example.kpkn.screens.onboarding.design.entreno.LiftPoses
import com.example.kpkn.screens.onboarding.design.entreno.MuscleSymbolGrid
import com.example.kpkn.screens.onboarding.design.entreno.drawLiftStage
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO del press de banca (tres fases) y de la cuadrícula de músculos, para revisarlas sin teléfono (no comprueba
 * nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin esa variable se salta. Mismo patrón que `WizardRenderSheetTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiftAndMuscleRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** La vista de la actividad de prueba, para volcarla a PNG (ver `SymbolRenderSheet.savePng`). */
    private var hostView: View? = null

    /** Escala de letra de la cuadrícula (1 = 100 %, 1,3 = 130 %). */
    private var gridScale by mutableStateOf(1f)

    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, name) }
    }

    /** Una fase del press de banca, con el mismo encuadre y la misma transformación que el símbolo de la pantalla. */
    @Composable
    private fun BenchFrame(u: Float) {
        val pose = remember { FigPose() }
        val pen = remember { FigPen() }
        Canvas(Modifier.width(112.dp).height(140.dp)) {
            LiftPoses.solve(LiftMark.BENCH, u, pose)
            val s = size.height / LiftPoses.VIEW_H
            val ink = FigStyle.ink
            withTransform({
                translate(size.width / 2f - LiftPoses.AXIS_X * s, size.height - LiftPoses.GROUND_LINE * s)
                scale(s, s, Offset.Zero)
            }) {
                pen.begin(this)
                drawLiftStage(pen, pose, ink, ground = true)
                if (pose.bar.isSpecified) pen.plate(pose.bar, LiftPoses.PLATE_R, ink, FigStyle.PLATE)
                pen.figure(pose, ink)
            }
        }
    }

    @Test
    fun renderTheBenchPhases() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Row(
                Modifier.testTag("bench").width(360.dp).background(Color.Black).padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                for (u in listOf(0f, 0.4f, 1f)) BenchFrame(u)
            }
        }
        rule.waitForIdle()
        save("bench", "bench_phases_u0_u04_u1")
    }

    @Test
    fun renderTheMuscleGridSheets() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = gridScale)) {
                Column(
                    Modifier.testTag("grid").width(360.dp).background(Color.Black).padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    MuscleSymbolGrid(
                        selected = setOf(MuscleSymbol.BACK, MuscleSymbol.QUADS),
                        suggested = setOf(MuscleSymbol.CHEST),
                        maxSelected = MuscleSymbols.MAX_SELECTION,
                        onToggle = {},
                    )
                }
            }
        }
        for ((name, scale) in listOf("muscle_grid_s100" to 1f, "muscle_grid_s130" to 1.3f)) {
            rule.runOnUiThread { gridScale = scale }
            rule.waitForIdle()
            save("grid", name)
        }
    }
}
