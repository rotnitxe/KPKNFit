package com.example.kpkn.screens.onboarding.design

import com.example.kpkn.domain.nutrition.PaceZone
import com.example.kpkn.domain.nutrition.PlanWarning
import com.example.kpkn.domain.nutrition.RiskSeverity
import com.example.kpkn.domain.nutrition.WizardPacePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/** Cifras y textos cortos del panel del plan: puros, sin Compose. */
class WizardNutritionFormatTest {

    @Test
    fun `las kcal llevan punto de millar desde las cuatro cifras`() {
        assertEquals("0", formatKcalEs(0))
        assertEquals("999", formatKcalEs(999))
        assertEquals("1.000", formatKcalEs(1000))
        assertEquals("2.300", formatKcalEs(2300))
        assertEquals("12.345", formatKcalEs(12345))
        assertEquals("1.234.567", formatKcalEs(1234567))
        assertEquals("−1.500", formatKcalEs(-1500))
    }

    @Test
    fun `los decimales llevan coma y los negativos el menos tipografico`() {
        assertEquals("1,5", formatDecimalEs(1.5, 1))
        assertEquals("2,0", formatDecimalEs(2.04, 1))
        assertEquals("0,00", formatDecimalEs(0.0, 2))
        assertEquals("−0,45", formatDecimalEs(-0.45, 2))
        assertEquals("0,0", formatDecimalEs(Double.NaN, 1))
    }

    @Test
    fun `el cambio de peso lleva signo explicito y un cero redondeado no lo lleva`() {
        assertEquals("−0,45", formatSignedDecimalEs(-0.45, 2))
        assertEquals("+0,25", formatSignedDecimalEs(0.25, 2))
        assertEquals("−3,6", formatSignedDecimalEs(-3.6, 1))
        assertEquals("+2,0", formatSignedDecimalEs(1.96, 1))
        assertEquals("0,00", formatSignedDecimalEs(0.001, 2))
        assertEquals("0,00", formatSignedDecimalEs(-0.004, 2))
        assertEquals("0,0", formatSignedDecimalEs(Double.POSITIVE_INFINITY, 1))
    }

    @Test
    fun `cada zona de ritmo tiene una etiqueta de una palabra y un color distinto`() {
        PaceZone.entries.forEach { zone ->
            assertTrue("$zone", paceZoneLabel(zone).isNotBlank() && !paceZoneLabel(zone).contains(' '))
        }
        assertEquals("Sostenible", paceZoneLabel(PaceZone.SUSTAINABLE))
        assertEquals("Extremo", paceZoneLabel(PaceZone.EXTREME))
        val colors = PaceZone.entries.map { WizardNutritionPalette.zone(it) }
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun `las muescas se llaman Lento Medio y Rapido`() {
        assertEquals("Lento", paceNotchLabel(WizardPacePreset.SLOW))
        assertEquals("Medio", paceNotchLabel(WizardPacePreset.MEDIUM))
        assertEquals("Rápido", paceNotchLabel(WizardPacePreset.FAST))
    }

    @Test
    fun `los textos de los avisos tienen como mucho seis palabras`() {
        PlanWarning.entries.forEach { warning ->
            listOf(true, false).forEach { loss ->
                val words = planWarningText(warning, loss).trim().split(Regex("\\s+"))
                assertTrue("$warning loss=$loss: ${words.size} palabras", words.size in 1..6)
            }
        }
    }

    @Test
    fun `el texto del ritmo distingue perder de ganar y los demas no`() {
        assertNotEquals(
            planWarningText(PlanWarning.PACE_AGGRESSIVE, true),
            planWarningText(PlanWarning.PACE_AGGRESSIVE, false),
        )
        assertNotEquals(
            planWarningText(PlanWarning.PACE_EXTREME, true),
            planWarningText(PlanWarning.PACE_EXTREME, false),
        )
        assertEquals(
            planWarningText(PlanWarning.LOW_FAT, true),
            planWarningText(PlanWarning.LOW_FAT, false),
        )
    }

    @Test
    fun `los avisos se reservan de mas a menos graves y cada uno una vez`() {
        assertEquals(PlanWarning.entries.toSet(), WizardPlanWarningOrder.toSet())
        assertEquals(PlanWarning.entries.size, WizardPlanWarningOrder.size)
        assertTrue(
            WizardPlanWarningOrder.zipWithNext().all { (a, b) -> a.severity.ordinal >= b.severity.ordinal },
        )
    }

    @Test
    fun `los colores de gravedad distinguen avisar de peligro`() {
        assertNotEquals(
            WizardNutritionPalette.severity(RiskSeverity.WARNING),
            WizardNutritionPalette.severity(RiskSeverity.DANGER),
        )
    }

    @Test
    fun `los dias son L M X J V S D`() {
        val initials = DayOfWeek.entries.joinToString("") { weekdayInitialEs(it) }
        assertEquals("LMXJVSD", initials)
        assertEquals("miércoles", weekdayNameEs(DayOfWeek.WEDNESDAY))
        assertEquals("domingo", weekdayNameEs(DayOfWeek.SUNDAY))
    }
}
