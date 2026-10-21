package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupWizardDraft
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
 * HOJAS DE DIBUJO del mosaico de variantes (K2): Powerbuilding con todo cerrado y con Sentadilla abierta, y Powerlifting cerrado,
 * a 360 dp y 130 % de letra. Escribe PNG (ver `SymbolRenderSheet.savePng`) y no comprueba nada; sin `KPKN_RENDER_DIR` se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class VariantsRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de la actividad de prueba, para volcarla a PNG. */
    private var hostView: View? = null

    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, name) }
    }

    /** El mosaico de [goal] a 360 dp y 130 % de letra, todo cerrado; cada prueba decide luego si abre alguna celda. */
    private fun setSheet(goal: TrainingGoalProfile, tag: String) {
        val availability = SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM))
            .withGoalProfile(goal).trainingOptions.availability
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1.3f)) {
                Column(
                    Modifier.testTag(tag).width(360.dp).background(Color.Black).padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    LiftVariantRows(goal = goal, availability = availability, stored = emptyMap()) { _, _ -> }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun renderPowerbuildingAllClosed() {
        onlyWhenAsked()
        setSheet(TrainingGoalProfile.POWERBUILDING, "pb-closed")
        save("pb-closed", "k2_pb_closed")
    }

    @Test
    fun renderPowerbuildingSquatOpen() {
        onlyWhenAsked()
        setSheet(TrainingGoalProfile.POWERBUILDING, "pb-open")
        rule.onNodeWithTag("setup-variant-SQUAT").performClick()
        rule.waitForIdle()
        save("pb-open", "k2_pb_squat_open")
    }

    @Test
    fun renderPowerliftingClosed() {
        onlyWhenAsked()
        setSheet(TrainingGoalProfile.POWERLIFTING, "pl-closed")
        save("pl-closed", "k2_pl_closed")
    }

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
}
