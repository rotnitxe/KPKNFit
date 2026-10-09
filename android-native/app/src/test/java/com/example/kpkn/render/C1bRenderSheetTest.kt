package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.design.entreno.LiftMarksPicker
import com.example.kpkn.screens.onboarding.entreno.EntrenoStepNote
import com.example.kpkn.screens.onboarding.entreno.LiftVariantRows
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withPlaces
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO del paso de variantes de Powerbuilding y de una marca desplegada con «Mi mejor serie», para revisarlas sin
 * teléfono (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin esa variable se salta. Mismo patrón que
 * `LiftAndMuscleRenderSheetTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class C1bRenderSheetTest {

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

    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, name) }
    }

    @Test
    fun renderThePowerbuildingVariantsStep() {
        onlyWhenAsked()
        // Material de gimnasio: aparecen las variantes con barra. La sentadilla ya tiene la barra baja elegida.
        val availability = SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM))
            .withGoalProfile(TrainingGoalProfile.POWERBUILDING).trainingOptions.availability
        rule.setContent {
            hostView = LocalView.current
            Column(
                Modifier.testTag("variants").width(360.dp).background(Color.Black).padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                LiftVariantRows(
                    goal = TrainingGoalProfile.POWERBUILDING,
                    availability = availability,
                    stored = mapOf(LiftMark.SQUAT to "low_bar_back_squat__barbell"),
                    onChoose = { _, _ -> },
                )
                EntrenoStepNote(text = "Las usamos como ejercicios principales de tus sesiones.")
            }
        }
        rule.waitForIdle()
        save("variants", "pb_variants_step")
    }

    @Test
    fun renderAnExpandedMarkInMiMejorSerie() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Column(
                Modifier.testTag("marks").width(360.dp).background(Color.Black).padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                LiftMarksPicker(
                    lifts = listOf(LiftMark.SQUAT, LiftMark.BENCH),
                    valuesKg = mapOf(LiftMark.SQUAT to 125.0),
                    unit = "kg",
                    onValueKg = { _, _ -> },
                    onUnit = {},
                    repsByLift = mapOf(LiftMark.SQUAT to 5),
                    variantLabels = mapOf(LiftMark.SQUAT to "Frontal"),
                )
            }
        }
        rule.waitForIdle()
        save("marks", "mark_mi_mejor_serie")
    }
}
