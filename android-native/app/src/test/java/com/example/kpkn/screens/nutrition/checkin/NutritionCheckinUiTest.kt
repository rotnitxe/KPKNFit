package com.example.kpkn.screens.nutrition.checkin

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.checkin.CheckinModule
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h2400dp-mdpi")
class NutritionCheckinUiTest {
    @get:Rule val rule = createComposeRule()
    private fun content(name: String, accept: () -> Unit = {}, partial: () -> Unit = {}, complete: () -> Unit = {}, scale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides true) {
                NutritionCheckinContent(NutritionCheckinSamples.state(name), {}, {}, accept, {}, {}, {}, { partial() }, { complete() }, {}, {}, {}, Modifier.width(328.dp))
            }
        }
    }
    @Test fun `la tarjeta hace alcanzable el flujo con Empezar accesible`() {
        var opened = false
        rule.setContent { NutritionCheckinCard({ opened = true }, Modifier.width(328.dp)) }
        rule.onNodeWithText("Tu check-in semanal está listo").assertExists()
        rule.onNodeWithTag("nutrition-checkin-start").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(opened)
    }
    @Test fun `la propuesta permite aceptar desde la interfaz y enumera la calidad`() {
        var accepted = false
        content("proposal_change", accept = { accepted = true })
        rule.onNodeWithTag("checkin-quality").assertExists()
        rule.onNodeWithText("Banda de incertidumbre:", substring = true).assertExists()
        rule.onNodeWithTag("checkin-accept").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(accepted)
        rule.onNodeWithTag("checkin-decline").assertExists()
        rule.onNodeWithTag("checkin-postpone").assertExists()
    }
    @Test fun `pausado conserva el objetivo y no permite aceptar una propuesta inexistente`() {
        val state = NutritionCheckinSamples.state("paused")
        rule.setContent { NutritionCheckinContent(state.copy(stepIndex = state.steps.lastIndex), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        rule.onNodeWithText("Hoy conservamos tu objetivo").assertExists()
        rule.onNodeWithTag("checkin-accept").assertIsNotEnabled()
    }
    @Test fun `cada dia parcial ofrece dos decisiones y conserva los alimentos`() {
        var partial = false
        var complete = false
        content("partial_days", partial = { partial = true }, complete = { complete = true })
        val date = NutritionCheckinSamples.today.minusDays(2)
        rule.onNodeWithTag("checkin-partial-$date").performClick()
        rule.onNodeWithTag("checkin-complete-$date").performClick()
        assertTrue(partial && complete)
    }
    @Test fun `con pocos pesajes invita a registrar uno y conserva la tendencia visible`() {
        var opened = false
        rule.setContent {
            NutritionCheckinContent(NutritionCheckinSamples.state("weigh_in"), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { opened = true })
        }
        rule.onNodeWithText("Un pesaje reciente", substring = true).assertExists()
        rule.onNodeWithText("Tendencia:", substring = true).assertExists()
        rule.onNodeWithText("Registrar pesaje").performClick()
        assertTrue(opened)
    }
    @Test fun `el error es visible y aceptar sigue disponible con letra al 130 por ciento`() {
        content("apply_error", scale = 1.3f)
        rule.onNodeWithTag("checkin-error").assertExists()
        rule.onNodeWithTag("checkin-accept").assertIsDisplayed().assertIsEnabled()
        val button = rule.onNodeWithTag("checkin-accept").fetchSemanticsNode().boundsInRoot
        assertTrue(button.width <= 328f)
        rule.onNodeWithText("No se pudo confirmar la decisión.", substring = true).assertExists()
    }
    @Test fun `mientras actualiza mantiene las cifras y deshabilita las decisiones`() {
        rule.setContent { NutritionCheckinContent(NutritionCheckinSamples.state("proposal_change").copy(loading = true), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        rule.onNodeWithText("Actualizando la estimación…").assertExists()
        rule.onNodeWithText("Propuesto").assertExists()
        rule.onNodeWithTag("checkin-accept").assertIsNotEnabled()
        rule.onNodeWithTag("checkin-decline").assertIsNotEnabled()
        rule.onNodeWithTag("checkin-postpone").assertIsNotEnabled()
    }
    @Test fun `al fallar aceptar el aviso queda visible en una ventana de 600 dp`() {
        var error by mutableStateOf<String?>(null)
        rule.setContent {
            Column(Modifier.width(360.dp).height(600.dp).padding(16.dp).verticalScroll(rememberScrollState())) {
                NutritionCheckinContent(NutritionCheckinSamples.state("proposal_change").copy(error = error), {}, {},
                    { error = "No se pudo confirmar la decisión. Vuelve a intentarlo." }, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithTag("checkin-accept").performScrollTo().performClick()
        rule.onNodeWithTag("checkin-error").assertIsDisplayed()
        rule.onNodeWithTag("checkin-error").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
}
