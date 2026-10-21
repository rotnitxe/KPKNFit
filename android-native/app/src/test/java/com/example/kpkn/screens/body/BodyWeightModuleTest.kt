package com.example.kpkn.screens.body

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.screens.body.design.BodyWeightModule
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class BodyWeightModuleTest {

    @get:Rule
    val rule = createComposeRule()

    private fun montar(
        summary: BodyWeightSummary = bodySampleWeightSummary(),
        goalKg: Double? = 60.0,
        onRango: (BodyChartRange) -> Unit = {},
        onAdd: () -> Unit = {},
        weightUnit: WizardMassUnit = WizardMassUnit.KG,
    ) {
        rule.setContent {
            BodyWeightModule(
                summary = summary,
                goalKg = goalKg,
                points = bodySampleWeightPoints(),
                range = BodyChartRange.THREE_MONTHS,
                today = BODY_SAMPLE_TODAY,
                weightUnit = weightUnit,
                onRangeChange = onRango,
                onAdd = onAdd,
            )
        }
    }

    @Test
    fun elRangoElegidoLlegaAlCallback() {
        var elegido: BodyChartRange? = null
        montar(onRango = { elegido = it })
        rule.onNodeWithText("6M").performClick()
        assertEquals(BodyChartRange.SIX_MONTHS, elegido)
    }

    @Test
    fun sinPesajesSeVeElVacioConSuAccion() {
        var anadido = false
        montar(summary = BodyWeightSummary(), goalKg = null, onAdd = { anadido = true })
        rule.onNodeWithText("Registra tu primer pesaje").assertIsDisplayed()
        rule.onNodeWithText("Registrar pesaje").performClick()
        assertTrue(anadido)
    }

    @Test
    fun conObjetivoSeVeCuantoFalta() {
        montar()
        rule.onNodeWithText("Te faltan 4,5 kg").assertIsDisplayed()
    }

    @Test
    fun enLibrasLaCifraLaMetaYLoQueFaltanSeEscribenEnLb() {
        // 64,5 kg son 142,2 lb; la meta de 60 kg faltan 4,5 kg, que son 9,9 lb.
        montar(weightUnit = WizardMassUnit.LB)
        rule.onNodeWithContentDescription("142,2 lb").assertIsDisplayed()
        rule.onNodeWithText("Te faltan 9,9 lb").assertIsDisplayed()
    }

    @Test
    fun sinRitmoMedidoNoHayCifraNiFecha() {
        montar(summary = bodySampleWeightSummary().copy(measuredWeeklyRate = null, eta = null))
        rule.onNodeWithText("Sin ritmo medido aún").assertIsDisplayed()
        rule.onNodeWithText("Hacen falta 2 semanas de pesajes para medir tu ritmo").assertIsDisplayed()
        rule.onNodeWithText("Sin fecha estimada aún", substring = true).assertIsDisplayed()
        rule.onNodeWithText("≈", substring = true).assertDoesNotExist()
    }
}
