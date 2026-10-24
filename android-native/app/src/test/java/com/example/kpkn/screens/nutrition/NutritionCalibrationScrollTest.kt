package com.example.kpkn.screens.nutrition

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.example.kpkn.screens.nutrition.checkin.NutritionCheckinSamples
import com.example.kpkn.screens.nutrition.checkin.NutritionCheckinUiState
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La página de calibración lleva el módulo del check-in a la parte alta de la ventana al abrirse y al cambiar de paso. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class NutritionCalibrationScrollTest {
    @get:Rule val rule = createComposeRule()

    // ── La decisión pura ─────────────────────────────────────────────────────

    @Test fun `no salta si el modulo ya esta arriba y si salta cuando esta lejos o fuera`() {
        assertTrue("sin dibujar todavía", shouldScrollToCheckin(null, 2000))
        assertFalse("pegado al borde alto", shouldScrollToCheckin(0, 2000))
        assertFalse("unos píxeles por encima", shouldScrollToCheckin(-3, 2000))
        assertFalse("en la cuarta parte alta", shouldScrollToCheckin(400, 2000))
        assertTrue("a media pantalla", shouldScrollToCheckin(900, 2000))
        assertTrue("ya salió por arriba", shouldScrollToCheckin(-300, 2000))
    }

    // ── La lista real ────────────────────────────────────────────────────────

    private var checkin by mutableStateOf(NutritionCheckinUiState(loading = false))

    private fun montar() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                NutritionCalibrationContent(
                    state = NutritionCalibrationUiState(),
                    onBack = {}, onEvaluate = {}, onReset = {}, onAnswerWizard = {}, onSkipWizard = {}, onResumeWizard = {},
                    checkinState = checkin,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun arribaDelModulo(): Float =
        rule.onNodeWithTag("nutrition-checkin-flow").fetchSemanticsNode().boundsInRoot.top

    private fun irAlInicioDeLaPagina() {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(0)
        rule.waitForIdle()
    }

    @Test fun `al abrir con el check-in disponible la lista lleva el modulo arriba`() {
        checkin = NutritionCheckinSamples.state("ready")
        montar()
        assertTrue("el módulo quedó en ${arribaDelModulo()} px", arribaDelModulo() <= 160f)
    }

    @Test fun `al cambiar de paso desde otro punto de la lista vuelve al inicio del modulo`() {
        checkin = NutritionCheckinSamples.state("ready")
        montar()
        irAlInicioDeLaPagina()
        assertTrue("precondición: el módulo está lejos del borde alto", arribaDelModulo() > 160f)
        checkin = checkin.copy(stepIndex = 1)
        rule.waitForIdle()
        assertTrue("el módulo quedó en ${arribaDelModulo()} px", arribaDelModulo() <= 160f)
    }

    @Test fun `si la persona ya ve el modulo arriba el cambio de paso no mueve la lista`() {
        checkin = NutritionCheckinSamples.state("ready")
        montar()
        val antes = arribaDelModulo()
        checkin = checkin.copy(stepIndex = 1)
        rule.waitForIdle()
        assertEquals(antes, arribaDelModulo(), 4f)
    }

    @Test fun `sin check-in disponible la lista no se desplaza`() {
        checkin = NutritionCheckinUiState(loading = false)
        montar()
        assertTrue("el flujo debe seguir debajo de la cabecera y el estado", arribaDelModulo() > 160f)
    }
}
