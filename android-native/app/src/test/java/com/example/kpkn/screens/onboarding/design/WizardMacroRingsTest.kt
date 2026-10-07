package com.example.kpkn.screens.onboarding.design

import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.MacroRange
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanTuningContext
import com.example.kpkn.domain.nutrition.PlanValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometría de los anillos y marcas de los deslizadores: pura, sin Compose. */
class WizardMacroRingsTest {

    private fun tuning(
        eer: Double? = 2650.0,
        values: PlanValues = PlanValues(2210, 160, 254, 61),
        direction: PlanDirection = PlanDirection.DEFICIT,
    ) = PlanTuning.of(PlanTuningContext(80.0, eer, direction, EerSex.MALE), values)

    @Test
    fun `el anillo exterior son las kcal frente al mantenimiento con la marca en el 100 por ciento`() {
        val fractions = macroRingFractions(tuning())
        assertEquals(2210f / 2650f / WIZARD_RING_CALORIE_SCALE, fractions.calories, 1e-4f)
        assertEquals(1f / WIZARD_RING_CALORIE_SCALE, fractions.referenceMark!!, 1e-6f)
        // En déficit el arco no llega a la marca; en superávit la pasa.
        assertTrue(fractions.calories < fractions.referenceMark!!)
        val surplus = macroRingFractions(tuning(values = PlanValues(2900, 160, 330, 80), direction = PlanDirection.SURPLUS))
        assertTrue(surplus.calories > surplus.referenceMark!!)
        // En mantenimiento coincide con la marca.
        val maintenance = macroRingFractions(
            tuning(values = PlanValues(2650, 160, 330, 80), direction = PlanDirection.MAINTENANCE),
        )
        assertEquals(maintenance.referenceMark!!, maintenance.calories, 1e-4f)
    }

    @Test
    fun `los anillos interiores son gramos frente al maximo de su deslizador`() {
        val plan = tuning()
        val limits = plan.limits
        val fractions = macroRingFractions(plan)
        assertEquals(160f / limits.protein.maxG, fractions.protein, 1e-6f)
        assertEquals(254f / limits.carbs.maxG, fractions.carbs, 1e-6f)
        assertEquals(61f / limits.fat.maxG, fractions.fat, 1e-6f)
    }

    @Test
    fun `una fraccion nunca sale de 0 a 1`() {
        val huge = macroRingFractions(tuning(values = PlanValues(9000, 900, 1500, 600)))
        listOf(huge.calories, huge.protein, huge.carbs, huge.fat).forEach { assertTrue(it in 0f..1f) }
        val empty = macroRingFractions(tuning(values = PlanValues(0, 0, 0, 0)))
        listOf(empty.calories, empty.protein, empty.carbs, empty.fat).forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `sin EER el anillo exterior queda lleno y sin marca`() {
        val fractions = macroRingFractions(tuning(eer = null))
        assertEquals(1f, fractions.calories, 0f)
        assertNull(fractions.referenceMark)
    }

    @Test
    fun `un dia elegido dibuja los anillos con los objetivos de ese dia`() {
        val plan = tuning()
        val day = PlanValues(2450, 160, 290, 70)
        val shown = macroRingFractions(plan, day)
        assertEquals(2450f / 2650f / WIZARD_RING_CALORIE_SCALE, shown.calories, 1e-4f)
        assertEquals(290f / plan.limits.carbs.maxG, shown.carbs, 1e-6f)
    }

    @Test
    fun `el hueco de los anillos descuenta margen trazos y separaciones`() {
        // 260 dp: margen de 6 + cuatro trazos de 10 + tres huecos de 3 = 55 dp por lado.
        assertEquals(150f, macroRingsFreeDiameterDp(260f), 1e-4f)
        assertEquals(170f, macroRingsFreeDiameterDp(280f), 1e-4f)
        assertEquals(0f, macroRingsFreeDiameterDp(100f), 0f)
    }

    @Test
    fun `el numero central se achica lo justo para caber y nunca baja de 45 por ciento`() {
        val free = macroRingsFreeDiameterDp(260f)
        // Cabe con holgura (menos del 90 % del hueco): tamaño completo.
        assertEquals(1f, macroRingsHeroScale(100f, free), 0f)
        assertEquals(1f, macroRingsHeroScale(135f, free), 0f)
        // Más ancho que eso: se achica hasta ocupar el 90 % del hueco.
        assertEquals(135f / 206f, macroRingsHeroScale(206f, free), 1e-4f)
        // Un hueco minúsculo no lo vuelve ilegible, y sin medida no se toca.
        assertEquals(0.45f, macroRingsHeroScale(900f, free), 0f)
        assertEquals(1f, macroRingsHeroScale(0f, free), 0f)
        // Con más hueco, más grande: el mismo número a 344 dp cabe entero.
        assertEquals(1f, macroRingsHeroScale(206f, macroRingsFreeDiameterDp(344f)), 0f)
    }

    @Test
    fun `las marcas de graduacion son entre cuatro y nueve y nunca los extremos`() {
        listOf(
            MacroRange(64, 240),
            MacroRange(40, 110),
            MacroRange(0, 508),
            MacroRange(56, 210),
            MacroRange(0, 90),
            MacroRange(30, 62),
        ).forEach { range ->
            val ticks = macroTickValues(range)
            assertTrue("$range → $ticks", ticks.size in 3..9)
            assertTrue(ticks.all { it > range.minG && it < range.maxG })
            assertEquals(ticks.sorted(), ticks)
        }
        assertEquals(listOf(80, 100, 120, 140, 160, 180, 200, 220), macroTickValues(MacroRange(64, 240)))
        assertEquals(emptyList<Int>(), macroTickValues(MacroRange(50, 50)))
        assertNotNull(macroTickValues(MacroRange(0, 1)))
    }

    @Test
    fun `el resumen de los anillos lee kcal y gramos y nombra el dia elegido`() {
        val values = PlanValues(2300, 160, 250, 70)
        assertEquals(
            "plan de 2.300 kilocalorías al día: 160 gramos de proteína, 250 de hidratos y 70 de grasas",
            wizardNutritionRingsDescription(values, null),
        )
        val day = WizardNutritionDay(java.time.LocalDate.of(2026, 9, 21), 2450, 160, 290, 70)
        assertTrue(wizardNutritionRingsDescription(day.values, day).startsWith("Lunes: plan de 2.450"))
    }
}
