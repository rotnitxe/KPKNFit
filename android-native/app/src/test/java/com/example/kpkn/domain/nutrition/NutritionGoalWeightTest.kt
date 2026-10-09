package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Peso objetivo del alta de nutrición: límites de la regla, ritmo y estimación, fecha y lectura del objetivo guardado. */
class NutritionGoalWeightTest {

    @Test
    fun `definir de 80 a 70 kg a 0,4 kg por semana tarda entre 22 y 29 semanas`() {
        assertEquals(22..29, goalWeeksRange(80.0, 70.0, 0.4))
    }

    @Test
    fun `sin cambio de peso o sin ritmo no hay estimacion`() {
        assertNull(goalWeeksRange(80.0, 80.0, 0.4))
        assertNull(goalWeeksRange(80.0, 70.0, 0.0))
        assertNull(goalWeeksRange(80.0, 70.0, Double.NaN))
    }

    @Test
    fun `8,5 kg a ritmo medio dan 18 a 24 semanas, la estimacion del paso`() {
        val weekly = goalWeeklyChangeKg(PlanDirection.DEFICIT, WizardPacePreset.MEDIUM, 80.0)
        assertEquals(0.4, weekly!!, 1e-9)
        assertEquals(18..24, goalWeeksRange(80.0, 71.5, weekly))
    }

    @Test
    fun `una diferencia minima cuenta como una semana`() {
        assertEquals(1..1, goalWeeksRange(80.0, 79.5, 0.5))
    }

    @Test
    fun `definir solo por debajo del peso actual y hasta menos 40 por ciento`() {
        val window = goalWeightWindow(PlanDirection.DEFICIT, 80.0)!!
        assertEquals(48.0, window.start, 1e-9)
        assertEquals(79.5, window.endInclusive, 1e-9)
    }

    @Test
    fun `volumen solo por encima del peso actual y hasta mas 25 por ciento`() {
        val window = goalWeightWindow(PlanDirection.SURPLUS, 70.0)!!
        assertEquals(70.5, window.start, 1e-9)
        assertEquals(87.5, window.endInclusive, 1e-9)
    }

    @Test
    fun `mantener y sin direccion admiten 5 kg a cada lado`() {
        val maintain = goalWeightWindow(PlanDirection.MAINTENANCE, 80.0)!!
        assertEquals(75.0, maintain.start, 1e-9)
        assertEquals(85.0, maintain.endInclusive, 1e-9)
        assertEquals(maintain, goalWeightWindow(null, 80.0))
    }

    @Test
    fun `sin peso valido no hay ventana`() {
        assertNull(goalWeightWindow(PlanDirection.DEFICIT, 0.0))
        assertNull(goalWeightWindow(PlanDirection.DEFICIT, Double.NaN))
    }

    @Test
    fun `el ritmo semanal sale del motor y mantener no tiene ritmo`() {
        assertEquals(0.2, goalWeeklyChangeKg(PlanDirection.SURPLUS, WizardPacePreset.MEDIUM, 80.0)!!, 1e-9)
        assertEquals(0.64, goalWeeklyChangeKg(PlanDirection.DEFICIT, WizardPacePreset.FAST, 80.0)!!, 1e-9)
        assertNull(goalWeeklyChangeKg(PlanDirection.MAINTENANCE, WizardPacePreset.MEDIUM, 80.0))
        assertNull(goalWeeklyChangeKg(null, WizardPacePreset.MEDIUM, 80.0))
    }

    @Test
    fun `la fecha estimada se escribe en meses y en español`() {
        assertEquals(
            "entre marzo y abril de 2027",
            formatGoalMonthSpan(LocalDate.of(2027, 3, 2), LocalDate.of(2027, 4, 28)),
        )
        assertEquals("en marzo de 2027", formatGoalMonthSpan(LocalDate.of(2027, 3, 28), LocalDate.of(2027, 3, 2)))
        assertEquals(
            "entre diciembre de 2026 y enero de 2027",
            formatGoalMonthSpan(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 10)),
        )
    }

    @Test
    fun `sin ritmo que lleve hacia la meta no hay fecha`() {
        assertNull(goalDateSpanText(80.0, 70.0, 0.4))
        assertNull(goalDateSpanText(80.0, 70.0, 0.0))
        assertNotNull(goalDateSpanText(80.0, 70.0, -0.4))
        assertNotNull(goalDateSpanText(70.0, 80.0, 0.4))
    }

    @Test
    fun `el objetivo guardado se lee en la unidad con la que se escribio`() {
        val pounds = NutritionWizardDraft(targetWeightText = "154,3", weightUnit = "lb")
        assertEquals(70.0, nutritionTargetKgOf(pounds)!!, 0.05)
        assertEquals(70.0, nutritionTargetKgOf(NutritionWizardDraft(targetWeightText = "70", weightUnit = "kg"))!!, 1e-9)
        assertNull(nutritionTargetKgOf(NutritionWizardDraft(targetWeightText = "")))
        assertNull(nutritionTargetKgOf(NutritionWizardDraft(targetWeightText = "5", weightUnit = "kg")))
        assertNull(nutritionTargetKgOf(null))
    }

    @Test
    fun `solo definir o volumen con meta activan la celebracion de la meta`() {
        assertTrue(nutritionGoalActive(PlanDirection.DEFICIT, 70.0))
        assertTrue(nutritionGoalActive(PlanDirection.SURPLUS, 90.0))
        assertFalse(nutritionGoalActive(PlanDirection.MAINTENANCE, 80.0))
        assertFalse(nutritionGoalActive(null, 80.0))
        assertFalse(nutritionGoalActive(PlanDirection.DEFICIT, null))
    }

    @Test
    fun `sin direccion la preparacion usa mantener y no da error`() {
        val result = NutritionPlanPreparation.prepare(
            NutritionPlanPreparationInput(
                planId = "plan",
                ageYears = 30,
                heightCm = 175.0,
                weightKg = 80.0,
                equationSex = EerSex.MALE,
                activity = EerActivity.ACTIVE,
                direction = null,
                goalMetric = GoalMetric.WEIGHT,
            ),
        )
        assertFalse(result.errors.containsKey("direction"))
        assertNotNull(result.plan)
        assertEquals(PlanDirection.MAINTENANCE, result.plan?.direction)
    }
}
