package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.MuscleArtCanvas
import com.example.kpkn.screens.onboarding.design.entreno.MuscleSymbolGrid
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO de la cuadrícula de músculos por familias y de su contracción: no comprueban nada, escriben PNG. Solo
 * corren con `KPKN_RENDER_DIR`; sin esa variable se saltan. Mismo patrón que `LiftAndMuscleRenderSheetTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h1800dp-xxhdpi", application = android.app.Application::class)
class MuscleFamilyRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de la actividad de prueba, para volcarla a PNG (ver `SymbolRenderSheet.savePng`). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, name) }
    }

    /** Una fila de músculos de [cell] dp de lado, sin etiquetas: así se ve si cada uno se reconoce sin leer el nombre. */
    @Composable
    private fun MuscleRow(muscles: List<MuscleSymbol>, selected: Boolean, cell: Int) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (m in muscles) MuscleArtCanvas(m, selected, contraction = 0f, modifier = Modifier.size(cell.dp))
        }
    }

    @Test
    fun gridWithoutSelection() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Column(
                Modifier.testTag("sheet").background(Color.Black).width(360.dp).padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                MuscleSymbolGrid(
                    selected = emptySet(),
                    suggested = emptySet(),
                    maxSelected = MuscleSymbols.MAX_SELECTION,
                    onToggle = {},
                )
            }
        }
        rule.waitForIdle()
        save("sheet", "muscle_grid_none")
    }

    @Test
    fun gridWithEveryMuscleChosen() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            // Con «reducir movimiento» cada elegido queda en su cuadro contraído, sin bucle: así la hoja es estable.
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                Column(
                    Modifier.testTag("sheet").background(Color.Black).width(360.dp).padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    MuscleSymbolGrid(
                        selected = MuscleSymbol.entries.toSet(),
                        suggested = emptySet(),
                        maxSelected = MuscleSymbol.entries.size,
                        onToggle = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        save("sheet", "muscle_grid_all")
    }

    @Test
    fun contractionFramesOfBicepsQuadsAndBack() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Column(
                Modifier.testTag("sheet").background(Color.Black).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (m in listOf(MuscleSymbol.BICEPS, MuscleSymbol.QUADS, MuscleSymbol.BACK)) {
                    Text(text = m.label, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Reposo, un tercio, dos tercios y contraído: los cuatro fotogramas del bucle.
                        for (k in listOf(0f, 1f / 3f, 2f / 3f, 1f)) {
                            MuscleArtCanvas(m, selected = true, contraction = k, modifier = Modifier.size(96.dp))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save("sheet", "muscle_contraction_frames")
    }

    @Test
    fun sixtyDpCellsUnselectedAndChosen() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Column(
                Modifier.testTag("sheet").background(Color.Black).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (selected in listOf(false, true)) {
                    MuscleRow(MuscleSymbol.entries.take(6), selected, cell = 60)
                    MuscleRow(MuscleSymbol.entries.drop(6), selected, cell = 60)
                }
            }
        }
        rule.waitForIdle()
        save("sheet", "muscle_sixty_dp")
    }
}
