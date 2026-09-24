package com.example.kpkn.screens.onboarding.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Contratos puros de las escalas de medida del wizard.
 *
 * Cubren los criterios de "Medidas y personajes": conversión kg/lb reversible,
 * conversión cm/ft-in sin deriva, decimales con coma y valores extremos o no
 * finitos acotados al rango canónico.
 */
class WizardMeasuresTest {

    // ─── Peso ────────────────────────────────────────────────────────────────

    @Test
    fun `weight conversion round trips without drift`() {
        var kg = WizardWeightScale.MIN_KG
        while (kg <= WizardWeightScale.MAX_KG) {
            val lb = WizardWeightScale.toDisplay(kg, WizardMassUnit.LB)
            val back = WizardWeightScale.toKg(lb, WizardMassUnit.LB)
            assertTrue("deriva en $kg kg -> $back", abs(back - kg) < 1e-6)
            kg += 7.3
        }
    }

    @Test
    fun `weight conversion is stable when repeated`() {
        // Alternar unidad no debe acumular error: el valor se reexpresa siempre
        // desde el canónico, nunca desde la conversión anterior.
        val original = 82.4
        var current = original
        repeat(50) {
            val display = WizardWeightScale.toDisplay(current, WizardMassUnit.LB)
            current = WizardWeightScale.toKg(display, WizardMassUnit.LB)
        }
        assertTrue("deriva acumulada: $current", abs(current - original) < 1e-6)
    }

    @Test
    fun `weight keeps one decimal and Spanish decimal comma`() {
        assertEquals("70", WizardWeightScale.format(70.0))
        assertEquals("70,5", WizardWeightScale.format(70.5))
        assertEquals("104,3", WizardWeightScale.format(104.3))
    }

    @Test
    fun `weight snapping never leaves one decimal`() {
        listOf(70.04, 70.05, 70.06, 104.29, 104.31).forEach { raw ->
            val snapped = WizardWeightScale.snap(raw)
            val scaled = snapped * 10.0
            assertEquals("$raw no cuadra a una décima", scaled, kotlin.math.round(scaled), 1e-9)
        }
    }

    @Test
    fun `weight clamps to the canonical 20 to 500 kg range`() {
        listOf(0.0, 19.9, 500.1, 5000.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
            .forEach { raw ->
                val clamped = WizardWeightScale.clampDisplay(raw, WizardMassUnit.KG)
                assertTrue("$raw -> $clamped fuera de rango", clamped in 20.0..500.0)
            }
    }

    @Test
    fun `pound display range maps back inside the canonical kilogram range`() {
        val range = WizardWeightScale.displayRange(WizardMassUnit.LB)
        assertTrue(WizardWeightScale.toKg(range.start, WizardMassUnit.LB) >= WizardWeightScale.MIN_KG - 0.1)
        assertTrue(WizardWeightScale.toKg(range.endInclusive, WizardMassUnit.LB) <= WizardWeightScale.MAX_KG + 0.1)
    }

    @Test
    fun `formatted weight keeps its unit`() {
        assertEquals("70 kg", WizardWeightScale.formatWithUnit(70.0, WizardMassUnit.KG))
        assertEquals("154,3 lb", WizardWeightScale.formatWithUnit(70.0, WizardMassUnit.LB))
    }

    // ─── Altura ──────────────────────────────────────────────────────────────

    @Test
    fun `height feet inches conversion never accumulates drift`() {
        for (cm in WizardHeightScale.MIN_CM..WizardHeightScale.MAX_CM) {
            val roundTrip = WizardHeightScale.cmFromFeetInches(
                WizardHeightScale.feet(cm),
                WizardHeightScale.inchesRemainder(cm),
            )
            // Pies/pulgadas es más grueso que cm: admite el redondeo de media
            // pulgada, pero repetir la conversión no puede seguir moviendo el valor.
            assertTrue("deriva en $cm -> $roundTrip", abs(roundTrip - cm) <= 2)
            val fixed = WizardHeightScale.cmFromFeetInches(
                WizardHeightScale.feet(roundTrip),
                WizardHeightScale.inchesRemainder(roundTrip),
            )
            assertEquals("punto fijo roto en $cm", roundTrip, fixed)
        }
    }

    @Test
    fun `height clamps to the canonical 100 to 250 cm range`() {
        assertEquals(100, WizardHeightScale.clampCm(0))
        assertEquals(100, WizardHeightScale.clampCm(99))
        assertEquals(250, WizardHeightScale.clampCm(251))
        assertEquals(250, WizardHeightScale.clampCm(5000))
        assertEquals(175, WizardHeightScale.clampCm(175))
    }

    @Test
    fun `height formatting matches the reference notation`() {
        assertEquals("175", WizardHeightScale.formatCm(175))
        assertEquals("5′ 9″", WizardHeightScale.formatFtIn(175))
        assertEquals("175", WizardHeightScale.format(175, WizardHeightUnit.CM))
        assertEquals("5′ 9″", WizardHeightScale.format(175, WizardHeightUnit.FT_IN))
    }

    @Test
    fun `height wheel covers the canonical range in both units`() {
        WizardHeightUnit.entries.forEach { unit ->
            val labels = WizardHeightScale.labels(unit)
            assertEquals(WizardHeightScale.stepCount(unit), labels.size)
            labels.indices.forEach { index ->
                val cm = WizardHeightScale.cmForStepIndex(index, unit)
                assertTrue("$unit paso $index -> $cm fuera de rango", cm in WizardHeightScale.MIN_CM..WizardHeightScale.MAX_CM)
            }
        }
    }

    @Test
    fun `height wheel step index round trips to the same step`() {
        WizardHeightUnit.entries.forEach { unit ->
            (WizardHeightScale.MIN_CM..WizardHeightScale.MAX_CM).forEach { cm ->
                val index = WizardHeightScale.nearestStepIndex(cm, unit)
                assertTrue("$unit indice $index fuera de rango", index in 0 until WizardHeightScale.stepCount(unit))
                val settled = WizardHeightScale.cmForStepIndex(index, unit)
                val again = WizardHeightScale.nearestStepIndex(settled, unit)
                assertEquals("$unit punto fijo roto en $cm", index, again)
            }
        }
    }

    @Test
    fun `centimetre and feet inch notations describe the same canonical value`() {
        (WizardHeightScale.MIN_CM..WizardHeightScale.MAX_CM step 5).forEach { cm ->
            val viaFeetInches = WizardHeightScale.cmFromFeetInches(
                WizardHeightScale.feet(cm),
                WizardHeightScale.inchesRemainder(cm),
            )
            assertTrue("desviación incoherente en $cm", abs(viaFeetInches - cm) <= 2)
        }
    }
}
