package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class MacroCalculatorTest {

    // ─── Scale Food By Portion ─────────────────────────────────────────────

    @Test
    fun `scale food medium portion`() {
        val food = FoodItem(
            id = "test1", name = "Arroz", servingSize = 100.0, unit = "g",
            calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3,
        )
        val logged = scaleFoodByPortion(food, quantity = 1, portion = PortionPreset.MEDIUM)
        assertEquals(130.0, logged.calories, 0.01)
        assertEquals(2.7, logged.protein, 0.01)
    }

    @Test
    fun `scale food large portion`() {
        val food = FoodItem(
            id = "test2", name = "Arroz", servingSize = 100.0, unit = "g",
            calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3,
        )
        val logged = scaleFoodByPortion(food, quantity = 1, portion = PortionPreset.LARGE)
        // large = 1.5x
        assertEquals(195.0, logged.calories, 0.5)
        assertEquals(4.1, logged.protein, 0.1)
    }

    @Test
    fun `scale food with explicit grams`() {
        val food = FoodItem(
            id = "test3", name = "Arroz", servingSize = 100.0, unit = "g",
            calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 250.0)
        assertEquals(325.0, logged.calories, 0.5) // 250/100 * 130 = 325
        assertEquals(6.8, logged.protein, 0.1)
    }

    // ─── Daily Totals ──────────────────────────────────────────────────────

    @Test
    fun `compute daily totals empty`() {
        val totals = computeDailyTotals(emptyList())
        assertEquals(0.0, totals.calories, 0.01)
    }

    @Test
    fun `compute daily totals single log`() {
        val logs = listOf(
            NutritionLog(
                id = "1", date = "2025-01-15T12:00:00", mealType = MealType.LUNCH,
                foods = listOf(
                    LoggedFood(id = "f1", foodName = "Arroz", calories = 200.0, protein = 5.0, carbs = 40.0, fats = 1.0),
                    LoggedFood(id = "f2", foodName = "Pollo", calories = 300.0, protein = 40.0, carbs = 0.0, fats = 10.0),
                ),
            )
        )
        val totals = computeDailyTotals(logs)
        assertEquals(500.0, totals.calories, 0.01)
        assertEquals(45.0, totals.protein, 0.01)
        assertEquals(40.0, totals.carbs, 0.01)
        assertEquals(11.0, totals.fats, 0.01)
    }

    @Test
    fun `compute daily totals skips planned`() {
        val logs = listOf(
            NutritionLog(
                id = "1", date = "2025-01-15T12:00:00", mealType = MealType.LUNCH,
                foods = listOf(LoggedFood(id = "f1", foodName = "Arroz", calories = 200.0, protein = 5.0, carbs = 40.0, fats = 1.0)),
                status = NutritionStatus.PLANNED,
            ),
            NutritionLog(
                id = "2", date = "2025-01-15T18:00:00", mealType = MealType.DINNER,
                foods = listOf(LoggedFood(id = "f2", foodName = "Pollo", calories = 300.0, protein = 40.0, carbs = 0.0, fats = 10.0)),
                status = NutritionStatus.CONSUMED,
            ),
        )
        val totals = computeDailyTotals(logs)
        assertEquals(300.0, totals.calories, 0.01) // only consumed
    }

    // ─── Meal Groups ───────────────────────────────────────────────────────

    @Test
    fun `compute meal groups`() {
        val logs = listOf(
            NutritionLog(id = "1", date = "2025-01-15T08:00:00", mealType = MealType.BREAKFAST,
                foods = listOf(LoggedFood(id = "f1", foodName = "Avena", calories = 300.0, protein = 10.0, carbs = 50.0, fats = 5.0))),
            NutritionLog(id = "2", date = "2025-01-15T12:00:00", mealType = MealType.LUNCH,
                foods = listOf(LoggedFood(id = "f2", foodName = "Pollo", calories = 400.0, protein = 40.0, carbs = 0.0, fats = 10.0))),
        )
        val groups = computeMealGroups(logs)
        assertEquals(4, groups.size) // BREAKFAST, LUNCH, DINNER, SNACK
        val breakfast = groups.find { it.mealType == MealType.BREAKFAST }
        assertEquals(1, breakfast?.logs?.size)
        assertEquals(300.0, breakfast?.totals?.calories, 0.01)
    }

    // ─── Macro Ring Pct ────────────────────────────────────────────────────

    @Test
    fun `macro ring pct`() {
        val totals = DailyMacroTotals(calories = 2000.0, protein = 100.0, carbs = 200.0, fats = 60.0)
        val goals = MacroGoals(calorieGoal = 2500, proteinGoal = 150, carbGoal = 250, fatGoal = 70)
        val pct = computeMacroRingPct(totals, goals.calorieGoal, goals.proteinGoal, goals.carbGoal, goals.fatGoal)
        assertEquals(0.8, pct.calories, 0.01) // 2000/2500
        assertEquals(0.667, pct.protein, 0.01) // 100/150
        assertEquals(0.8, pct.carbs, 0.01)
        assertEquals(0.857, pct.fats, 0.01)
    }

    @Test
    fun `macro ring pct zero goals`() {
        val totals = DailyMacroTotals(calories = 2000.0, protein = 100.0, carbs = 200.0, fats = 60.0)
        val pct = computeMacroRingPct(totals, 0, 0, 0, 0)
        assertEquals(0.0, pct.calories, 0.01)
    }

    // ─── Duplicate Log ─────────────────────────────────────────────────────

    @Test
    fun `duplicate log changes id and date`() {
        val original = NutritionLog(
            id = "original",
            date = "2025-01-10T12:00:00",
            mealType = MealType.LUNCH,
            foods = listOf(LoggedFood(id = "f1", foodName = "Arroz", calories = 200.0)),
            notes = "Nota original",
        )
        val dup = duplicateLog(original, "2025-01-15")
        assertNotEquals("original", dup.id)
        assertEquals("2025-01-15", dup.date.take(10))
        assertEquals("Nota original (duplicado)", dup.notes)
        assertEquals(NutritionStatus.CONSUMED, dup.status)
    }

    // ─── Create Logged Food ────────────────────────────────────────────────

    @Test
    fun `create logged food`() {
        val food = createLoggedFood(
            foodName = "Test",
            amount = 100.0,
            calories = 250.0,
            protein = 20.0,
        )
        assertEquals("Test", food.foodName)
        assertEquals(100.0, food.amount, 0.01)
        assertEquals(250.0, food.calories, 0.01)
        assertEquals(20.0, food.protein, 0.01)
    }
}
