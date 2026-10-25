package com.example.kpkn.domain.nutrition.checkin

import com.example.kpkn.debug.nutritionHistorySeed
import com.example.kpkn.domain.nutrition.expenditure.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NutritionHistorySeedTest {
    private val today = LocalDate.of(2026, 10, 21)
    @Test fun `el historial limpio tiene 84 dias reproducibles sin IDs duplicados`() {
        val first = nutritionHistorySeed(today, 1)
        assertEquals(first, nutritionHistorySeed(today, 1))
        assertEquals(84, first.logs.size)
        assertEquals(84, first.weights.size)
        assertEquals(84, first.logs.map { it.id }.toSet().size)
        assertEquals(84, first.weights.map { it.id }.toSet().size)
        assertEquals(first.plan.calorieTarget.toDouble(), first.plan.calculationSnapshot!!.result.getValue("calorieTargetKcal").toDouble(), 0.0)
        val days = buildDayObservations(first.logs, emptyMap(), today)
        val estimates = ExpenditureEstimator().estimateSeries(ExpenditureInput(
            first.plan.calculationSnapshot!!.result.getValue("eerKcal").toDouble(), 1400.0, days,
            first.weights.map { WeightObservation(java.time.Instant.ofEpochMilli(it.timestampEpochMs).atZone(java.time.ZoneId.of(it.zoneId)).toLocalDate(), it.valueSi) }, today))
        assertTrue(kotlin.math.abs(estimates.last().kcal - 2400.0) < 150.0)
    }
    @Test fun `el segundo escenario incluye ayuno parciales ocho dias vacios y pesaje irregular`() {
        val seed = nutritionHistorySeed(today, 2)
        assertEquals(seed, nutritionHistorySeed(today, 2))
        assertEquals(75, seed.logs.size)
        assertEquals(4, seed.logs.count { it.foods.sumOf { food -> food.calories } == 300.0 })
        assertNotNull(seed.fastingDate)
        val days = buildDayObservations(seed.logs, emptyMap(), today)
        assertEquals(9, days.count { it.intakeKcal == null })
        val longest = days.fold(0 to 0) { (run, max), day ->
            val next = if (day.intakeKcal == null) run + 1 else 0
            next to maxOf(next, max)
        }.second
        assertEquals(8, longest)
        assertEquals(2, seed.weights.count {
            java.time.Instant.ofEpochMilli(it.timestampEpochMs).atZone(java.time.ZoneId.of(it.zoneId)).toLocalDate() >= today.minusDays(6)
        })
    }
}
