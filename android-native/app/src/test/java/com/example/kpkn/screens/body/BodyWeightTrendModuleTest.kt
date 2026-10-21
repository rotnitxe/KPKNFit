package com.example.kpkn.screens.body

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.domain.body.EtaRange
import com.example.kpkn.screens.body.design.BodyWeightModule
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «Peso» de Cuerpo con la tendencia como cifra principal: el último pesaje queda debajo como dato, la meta y lo que falta se cuentan
 * desde la tendencia y la fecha es un rango cuando el margen cambia de mes. Sin tendencia, la cifra es el último pesaje.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class BodyWeightTrendModuleTest {

    @get:Rule
    val rule = createComposeRule()

    /** Último pesaje 64,5 kg y tendencia 64,7 kg: faltan 4,7 kg hasta 60 kg. */
    private fun conTendencia(etaRange: EtaRange? = null): BodyWeightSummary = bodySampleWeightSummary().copy(
        trendKg = 64.7,
        trendWeeklyRate = -0.4,
        trendRemaining = 60.0 - 64.7,
        trendProgressPercent = 30,
        etaRange = etaRange,
    )

    private fun montar(summary: BodyWeightSummary, unidad: WizardMassUnit = WizardMassUnit.KG) {
        rule.setContent {
            BodyWeightModule(
                summary = summary,
                goalKg = 60.0,
                points = bodySampleWeightPoints(),
                range = BodyChartRange.THREE_MONTHS,
                today = BODY_SAMPLE_TODAY,
                weightUnit = unidad,
                onRangeChange = {},
                onAdd = {},
            )
        }
    }

    @Test
    fun `con tendencia la cifra grande es la tendencia y el ultimo pesaje queda debajo`() {
        montar(conTendencia())

        rule.onNodeWithText("Tendencia").assertIsDisplayed()
        rule.onNodeWithContentDescription("64,7 kg").assertIsDisplayed()
        rule.onNodeWithText("Último pesaje: 64,5 kg · hoy").assertIsDisplayed()
    }

    @Test
    fun `con tendencia, lo que falta se cuenta desde la tendencia`() {
        montar(conTendencia())

        rule.onNodeWithText("Te faltan 4,7 kg").assertIsDisplayed()
    }

    @Test
    fun `con tendencia el ritmo medido se muestra`() {
        montar(conTendencia())

        rule.onNodeWithText("Ritmo:", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Sin ritmo medido aún").assertDoesNotExist()
    }

    @Test
    fun `sin ritmo de tendencia sigue diciendo que hacen falta dos semanas`() {
        montar(conTendencia().copy(trendWeeklyRate = null, measuredWeeklyRate = null))

        rule.onNodeWithText("Sin ritmo medido aún").assertIsDisplayed()
        rule.onNodeWithText("Hacen falta 2 semanas de pesajes para medir tu ritmo").assertIsDisplayed()
    }

    @Test
    fun `un solo pesaje es la cifra y se rotula como ultimo pesaje, sin tendencia`() {
        montar(bodySampleWeightSummary())

        rule.onNodeWithText("Último pesaje").assertIsDisplayed()
        rule.onNodeWithText("Tendencia").assertDoesNotExist()
        rule.onNodeWithContentDescription("64,5 kg").assertIsDisplayed()
    }

    @Test
    fun `el margen que cambia de mes se dice como rango`() {
        // Hoy es 13 de octubre de 2026: el margen cae en 2027.
        montar(conTendencia(EtaRange(central = YearMonth.of(2027, 3), earliest = YearMonth.of(2027, 2), latest = YearMonth.of(2027, 6))))

        rule.onNodeWithText("Meta: 60 kg · entre febrero y junio de 2027").assertIsDisplayed()
    }

    @Test
    fun `un margen sin cambio de mes conserva el formato de un solo mes`() {
        val mes = YearMonth.of(2027, 3)
        montar(conTendencia(EtaRange(central = mes, earliest = mes, latest = mes)))

        rule.onNodeWithText("Meta: 60 kg · ≈ marzo de 2027").assertIsDisplayed()
    }

    @Test
    fun `un margen abierto dice a partir de`() {
        montar(conTendencia(EtaRange(central = YearMonth.of(2027, 3), earliest = YearMonth.of(2026, 12), latest = null)))

        rule.onNodeWithText("Meta: 60 kg · a partir de diciembre").assertIsDisplayed()
    }

    @Test
    fun `con tendencia pero sin fecha, la fecha sigue sin inventarse`() {
        montar(conTendencia(etaRange = null).copy(eta = YearMonth.of(2027, 3)))

        // El mes heredado no se usa cuando hay tendencia: la fecha solo viene del cálculo robusto.
        rule.onNodeWithText("Meta: 60 kg · Sin fecha estimada aún").assertIsDisplayed()
    }

    @Test
    fun `en libras la tendencia y el ultimo pesaje se escriben en lb`() {
        // 64,7 kg son 142,6 lb y 64,5 kg son 142,2 lb.
        montar(conTendencia(), WizardMassUnit.LB)

        rule.onNodeWithContentDescription("142,6 lb").assertIsDisplayed()
        rule.onNodeWithText("Último pesaje: 142,2 lb · hoy").assertIsDisplayed()
    }
}
