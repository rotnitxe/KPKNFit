package com.example.kpkn.screens.body

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.screens.body.design.BodyWeightModule
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
    ) {
        rule.setContent {
            BodyWeightModule(
                summary = summary,
                goalKg = goalKg,
                points = bodySampleWeightPoints(),
                range = BodyChartRange.THREE_MONTHS,
                today = BODY_SAMPLE_TODAY,
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
    fun sinRitmoMedidoNoHayCifraNiFecha() {
        montar(summary = bodySampleWeightSummary().copy(measuredWeeklyRate = null, eta = null))
        rule.onNodeWithText("Sin ritmo medido aún").assertIsDisplayed()
        rule.onNodeWithText("Hacen falta 2 semanas de pesajes para medir tu ritmo").assertIsDisplayed()
        rule.onNodeWithText("Sin fecha estimada aún", substring = true).assertIsDisplayed()
        rule.onNodeWithText("≈", substring = true).assertDoesNotExist()
    }
}
