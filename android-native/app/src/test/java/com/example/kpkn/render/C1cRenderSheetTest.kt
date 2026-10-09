package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.screens.onboarding.design.entreno.HipThrustArt
import com.example.kpkn.screens.onboarding.design.entreno.JOG_ART
import com.example.kpkn.screens.onboarding.design.entreno.JumpsArt
import com.example.kpkn.screens.onboarding.design.entreno.NoneArt
import com.example.kpkn.screens.onboarding.design.entreno.ParallelBarsArt
import com.example.kpkn.screens.onboarding.design.entreno.RUNNING_ART
import com.example.kpkn.screens.onboarding.design.entreno.RUN_ART
import com.example.kpkn.screens.onboarding.design.entreno.SPRINT_ART
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.WALK_ART
import com.example.kpkn.screens.onboarding.design.entreno.armArtOf
import com.example.kpkn.screens.onboarding.entreno.ArmStyleContent
import com.example.kpkn.screens.onboarding.entreno.AvoidGroupsContent
import com.example.kpkn.screens.onboarding.entreno.CapabilityRepsRows
import com.example.kpkn.screens.onboarding.entreno.CardioBaseContent
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO del punto de partida (C1c), para revisarlas sin teléfono (no comprueba nada: escribe PNG). Los símbolos nuevos
 * en reposo y en cuatro fotogramas, a 60 dp, y cada paso a 360 dp. Solo corre con `KPKN_RENDER_DIR`; sin esa variable se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class C1cRenderSheetTest {

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
    fun renderTheNewSymbolsAtRestAndInFourFrames() {
        onlyWhenAsked()
        val arts: List<SymbolArt> = listOf(
            ParallelBarsArt, HipThrustArt, JumpsArt, RUNNING_ART, NoneArt,
            WALK_ART, JOG_ART, RUN_ART, SPRINT_ART,
            armArtOf(ArmStyle.TOP_ROLL), armArtOf(ArmStyle.HOOK), armArtOf(ArmStyle.PRESS), armArtOf(ArmStyle.UNSURE),
        )
        rule.setContent {
            hostView = LocalView.current
            Column(Modifier.testTag("symbols").background(Color.Black).padding(12.dp)) {
                for (art in arts) {
                    Row {
                        // Primero el cuadro de reposo (sin seleccionar) y luego cuatro fotogramas del bucle seleccionado.
                        SymbolRenderSheet.Frame(art, art.restT, selected = false, px = 60f)
                        for (k in 0 until 4) SymbolRenderSheet.Frame(art, art.period * k / 4f, selected = true, px = 60f)
                    }
                }
            }
        }
        rule.waitForIdle()
        save("symbols", "c1c_symbols_rest_and_frames")
    }

    @Test
    fun renderTheAvoidStepAt360dp() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Column(Modifier.testTag("avoid").width(360.dp).background(Color.Black).padding(horizontal = 16.dp, vertical = 16.dp)) {
                AvoidGroupsContent(
                    selected = setOf(AvoidGroup.DEADLIFT, AvoidGroup.JUMPS),
                    onToggle = {},
                    onNone = {},
                )
            }
        }
        rule.waitForIdle()
        save("avoid", "c1c_avoid_step")
    }

    @Test
    fun renderTheCardioBaseStepAt360dp() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Column(Modifier.testTag("cardio").width(360.dp).background(Color.Black).padding(horizontal = 16.dp, vertical = 16.dp)) {
                CardioBaseContent(selected = CardioBase.MODERATE, onChoose = {})
            }
        }
        rule.waitForIdle()
        save("cardio", "c1c_cardio_step")
    }

    @Test
    fun renderTheArmStyleStepAt360dp() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Column(Modifier.testTag("arm").width(360.dp).background(Color.Black).padding(horizontal = 16.dp, vertical = 16.dp)) {
                ArmStyleContent(selected = ArmStyle.HOOK, onChoose = {})
            }
        }
        rule.waitForIdle()
        save("arm", "c1c_arm_style_step")
    }

    @Test
    fun renderTheBodyweightRepsRowsAt360dp() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Column(Modifier.testTag("reps").width(360.dp).background(Color.Black).padding(horizontal = 16.dp, vertical = 16.dp)) {
                CapabilityRepsRows(
                    skills = listOf(CapabilitySkill.PULL_UP, CapabilitySkill.PUSH_UP, CapabilitySkill.DIP, CapabilitySkill.PISTOL_SQUAT),
                    reps = mapOf(CapabilitySkill.PULL_UP to 8, CapabilitySkill.PUSH_UP to 20),
                    levels = mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.SOME),
                    required = setOf(CapabilitySkill.PULL_UP),
                    onReps = { _, _ -> },
                    onLevel = { _, _ -> },
                )
            }
        }
        rule.waitForIdle()
        save("reps", "c1c_bodyweight_reps_step")
    }
}
