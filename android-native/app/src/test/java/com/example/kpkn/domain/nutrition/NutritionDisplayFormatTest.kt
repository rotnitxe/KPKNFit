package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WP-U15 / C22: un total o una fila con alimentos inciertos no se presenta como exacto. Un valor exacto conserva el
 * formato de siempre (entero redondeado, sin separador de miles); lo estimado suma «≈» y el rango guardado.
 * Puro JVM.
 */
class NutritionDisplayFormatTest {

    private fun logOf(vararg foods: LoggedFood, status: NutritionStatus = NutritionStatus.CONSUMED) =
        NutritionLog(id = "l1", date = "2026-10-03T12:00:00", foods = foods.toList(), status = status)

    private val rice = LoggedFood(foodName = "Arroz", calories = 200.0)
    private val stew = LoggedFood(
        foodName = "Guiso casero", calories = 320.0, caloriesMin = 250.0, caloriesMax = 400.0, isUncertain = true,
    )
    private val dessert = LoggedFood(foodName = "Postre", calories = 480.0, isUncertain = true)

    // ─── kcalLabel / kcalValue ──────────────────────────────────────────────

    @Test
    fun `an exact total keeps the plain label without the approximation mark`() {
        val totals = DailyMacroTotals(calories = 1234.0, caloriesMin = 1234.0, caloriesMax = 1234.0)
        assertEquals("1234 kcal", NutritionDisplayFormat.kcalLabel(totals))
        assertEquals("1234", NutritionDisplayFormat.kcalValue(totals))
        assertEquals("", NutritionDisplayFormat.approxPrefix(false))
    }

    @Test
    fun `an estimated total gets the approximation mark`() {
        val totals = DailyMacroTotals(calories = 1250.0, caloriesMin = 1100.0, caloriesMax = 1400.0, isEstimate = true)
        assertEquals("≈ 1250 kcal", NutritionDisplayFormat.kcalLabel(totals))
        assertEquals("≈ 1250", NutritionDisplayFormat.kcalValue(totals))
        assertEquals("≈ ", NutritionDisplayFormat.approxPrefix(true))
    }

    @Test
    fun `numbers keep the existing convention of a rounded integer without thousands separator`() {
        assertEquals("0 kcal", NutritionDisplayFormat.kcalLabel(DailyMacroTotals()))
        assertEquals("2000 kcal", NutritionDisplayFormat.kcalLabel(DailyMacroTotals(calories = 2000.0)))
        assertEquals("1235 kcal", NutritionDisplayFormat.kcalLabel(DailyMacroTotals(calories = 1234.6)))
        assertEquals("1234 kcal", NutritionDisplayFormat.kcalLabel(DailyMacroTotals(calories = 1234.4)))
        assertEquals("12345 kcal", NutritionDisplayFormat.kcalLabel(DailyMacroTotals(calories = 12345.0)))
        val wide = DailyMacroTotals(calories = 11000.0, caloriesMin = 10000.0, caloriesMax = 12500.0, isEstimate = true)
        assertEquals("rango 10000–12500 kcal", NutritionDisplayFormat.kcalRangeLabel(wide))
    }

    // ─── kcalRangeLabel ─────────────────────────────────────────────────────

    @Test
    fun `range label shows the stored band only when it has width`() {
        val totals = DailyMacroTotals(calories = 1250.0, caloriesMin = 1100.0, caloriesMax = 1400.0, isEstimate = true)
        assertEquals("rango 1100–1400 kcal", NutritionDisplayFormat.kcalRangeLabel(totals))
    }

    @Test
    fun `there is no range label for a zero-width or missing band`() {
        val exact = DailyMacroTotals(calories = 500.0, caloriesMin = 500.0, caloriesMax = 500.0)
        assertNull(NutritionDisplayFormat.kcalRangeLabel(exact))
        // Sin banda: la marca puede estar (isEstimate) pero no hay números que mostrar.
        assertNull(NutritionDisplayFormat.kcalRangeLabel(DailyMacroTotals(calories = 500.0, isEstimate = true)))
        assertNull(NutritionDisplayFormat.kcalRangeLabel(DailyMacroTotals(calories = 500.0, caloriesMin = 450.0)))
        assertNull(NutritionDisplayFormat.kcalRangeLabel(DailyMacroTotals(calories = 500.0, caloriesMax = 550.0)))
    }

    @Test
    fun `a band compares and shows its bounds after rounding`() {
        val collapsed = DailyMacroTotals(calories = 100.0, caloriesMin = 99.6, caloriesMax = 100.4, isEstimate = true)
        assertNull(NutritionDisplayFormat.kcalRangeLabel(collapsed))
        val narrow = DailyMacroTotals(calories = 100.0, caloriesMin = 99.6, caloriesMax = 100.6, isEstimate = true)
        assertEquals("rango 100–101 kcal", NutritionDisplayFormat.kcalRangeLabel(narrow))
    }

    @Test
    fun `labels built from computed daily totals`() {
        val estimated = computeDailyTotals(listOf(logOf(rice, stew)))
        assertEquals("≈ 520 kcal", NutritionDisplayFormat.kcalLabel(estimated))
        assertEquals("rango 450–600 kcal", NutritionDisplayFormat.kcalRangeLabel(estimated))

        val exact = computeDailyTotals(listOf(logOf(rice)))
        assertEquals("200 kcal", NutritionDisplayFormat.kcalLabel(exact))
        assertNull(NutritionDisplayFormat.kcalRangeLabel(exact))
    }

    // ─── logKcalSummary ─────────────────────────────────────────────────────

    @Test
    fun `an exact log summary has neither mark nor range`() {
        assertEquals("200 kcal", NutritionDisplayFormat.logKcalSummary(logOf(rice)))
        // Un registro V2 exacto guarda su banda como [centro, centro].
        val stored = LoggedFood(foodName = "Pollo", calories = 300.0, caloriesMin = 300.0, caloriesMax = 300.0)
        assertEquals("300 kcal", NutritionDisplayFormat.logKcalSummary(logOf(stored)))
    }

    @Test
    fun `an uncertain log with a range shows the mark and the range`() {
        assertEquals("≈ 520 kcal · rango 450–600 kcal", NutritionDisplayFormat.logKcalSummary(logOf(rice, stew)))
    }

    @Test
    fun `an uncertain log without a range shows the mark only`() {
        assertEquals("≈ 480 kcal", NutritionDisplayFormat.logKcalSummary(logOf(dessert)))
    }

    @Test
    fun `a planned log is summarized with its own foods`() {
        val planned = logOf(stew, status = NutritionStatus.PLANNED)
        assertEquals("≈ 320 kcal · rango 250–400 kcal", NutritionDisplayFormat.logKcalSummary(planned))
    }

    @Test
    fun `an empty log summary is zero kcal`() {
        assertEquals("0 kcal", NutritionDisplayFormat.logKcalSummary(logOf()))
    }

    @Test
    fun `a single log day and its log agree on the mark and the range`() {
        val log = logOf(rice, stew)
        val totals = computeDailyTotals(listOf(log))
        val joined = listOfNotNull(NutritionDisplayFormat.kcalLabel(totals), NutritionDisplayFormat.kcalRangeLabel(totals))
            .joinToString(" · ")
        assertEquals(joined, NutritionDisplayFormat.logKcalSummary(log))
    }
}
