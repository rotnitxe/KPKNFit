package com.example.kpkn.screens.nutrition.design

import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.data.models.TrendPoint
import com.example.kpkn.domain.nutrition.NutritionHistoryCoverage
import com.example.kpkn.domain.nutrition.NutritionHistoryPoint
import com.example.kpkn.domain.nutrition.NutritionHistorySeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Lógica pura de las filas de comida y de «Últimos 7 días»: reparto de energía, rangos, porciones, medias y bigotes. */
class NutritionMealMathTest {

    @Test
    fun `la energia de P, H y G se reparte con 4, 4 y 9 kcal por gramo`() {
        val shares = macroEnergyShares(protein = 10.0, carbs = 20.0, fat = 5.0)!!
        // 40, 80 y 45 kcal: 165 en total.
        assertEquals(40.0 / 165.0, shares.protein.toDouble(), 0.0001)
        assertEquals(80.0 / 165.0, shares.carbs.toDouble(), 0.0001)
        assertEquals(45.0 / 165.0, shares.fat.toDouble(), 0.0001)
    }

    @Test
    fun `sin macros no hay barra que repartir`() {
        assertNull(macroEnergyShares(protein = 0.0, carbs = 0.0, fat = 0.0))
    }

    @Test
    fun `el rango de kcal solo existe si la banda tiene ancho`() {
        val banded = DailyMacroTotals(calories = 450.0, caloriesMin = 420.0, caloriesMax = 510.0, isEstimate = true)
        assertEquals("420–510 kcal", kcalRangeText(banded))
        assertNull(kcalRangeText(banded.copy(caloriesMin = 450.0, caloriesMax = 450.0)))
        assertNull(kcalRangeText(DailyMacroTotals(calories = 450.0)))
    }

    @Test
    fun `la porcion dice lo que el registro ya tiene`() {
        val grams = NutritionLog(id = "a", foods = listOf(LoggedFood(foodName = "Avena", amount = 150.0, unit = "g")))
        assertEquals("150 g", logPortionText(grams))
        val preset = NutritionLog(id = "b", foods = listOf(LoggedFood(foodName = "Pan", portionPreset = PortionPreset.MEDIUM)))
        assertEquals("Mediano", logPortionText(preset))
        val several = NutritionLog(id = "c", foods = listOf(LoggedFood(foodName = "Pan"), LoggedFood(foodName = "Queso")))
        assertEquals("2 alimentos", logPortionText(several))
        assertNull(logPortionText(NutritionLog(id = "d")))
    }

    private fun trend(date: String, calories: Double, hasData: Boolean = true) =
        TrendPoint(date = date, calories = calories, goal = 2300.0, hasData = hasData)

    @Test
    fun `un dia sin registros no tiene kcal y un dia con banda lleva su rango`() {
        val trend = listOf(
            trend("2026-10-12", 1240.0),
            trend("2026-10-13", 0.0, hasData = false),
        )
        val series = NutritionHistorySeries(
            points = listOf(
                NutritionHistoryPoint(
                    date = LocalDate.of(2026, 10, 12),
                    intakeCalories = 1240.0,
                    intakeCaloriesMin = 1180.0,
                    intakeCaloriesMax = 1300.0,
                ),
                NutritionHistoryPoint(date = LocalDate.of(2026, 10, 13), intakeCalories = null),
            ),
            coverage = NutritionHistoryCoverage(registeredDays = 1, totalDays = 2),
            averageCaloriesOnRegisteredDays = 1240.0,
        )
        val bars = buildNutritionWeekBars(trend, series, today = "2026-10-13")
        assertEquals(1240, bars[0].kcal)
        assertEquals(1180, bars[0].kcalMin)
        assertEquals(1300, bars[0].kcalMax)
        assertTrue(bars[0].hasRange)
        assertEquals(2300, bars[0].goal)
        assertFalse(bars[0].isToday)
        assertNull(bars[1].kcal)
        assertFalse(bars[1].hasRange)
        assertTrue(bars[1].isToday)
    }

    @Test
    fun `la media solo cuenta los dias registrados y dice cuantos son`() {
        val bars = listOf(
            NutritionWeekBar("2026-10-07", 2000),
            NutritionWeekBar("2026-10-08", 2100),
            NutritionWeekBar("2026-10-09", null),
            NutritionWeekBar("2026-10-10", 1900),
            NutritionWeekBar("2026-10-11", null),
            NutritionWeekBar("2026-10-12", 2050),
            NutritionWeekBar("2026-10-13", 2000, isToday = true),
        )
        assertEquals("Media de los días registrados: 2.010 kcal · 5 de 7 días", weekAverageLine(bars))
        assertEquals("Últimos 7 días: media 2.010 kcal, 5 días registrados", weekSummaryDescription(bars))
        val empty = bars.map { it.copy(kcal = null) }
        assertEquals("Aún no hay días registrados", weekAverageLine(empty))
        assertEquals("Últimos 7 días: sin días registrados", weekSummaryDescription(empty))
        val one = empty.mapIndexed { index, bar -> if (index == 6) bar.copy(kcal = 1800) else bar }
        assertEquals("Últimos 7 días: media 1.800 kcal, 1 día registrado", weekSummaryDescription(one))
    }
}
