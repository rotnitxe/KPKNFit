package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class MacroCalculatorTest {

    // ─── Scale Food By Portion ─────────────────────────────────────────────

    @Test
    fun `scale food medium portion`() {
        val food = FoodItem(
            id = "test1", name = "Alimento de prueba", servingSize = 100.0, unit = "g",
            calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3,
        )
        val logged = scaleFoodByPortion(food, quantity = 1.0, portion = PortionPreset.MEDIUM)
        assertEquals(130.0, logged.calories, 0.01)
        assertEquals(2.7, logged.protein, 0.01)
    }

    @Test
    fun `scale food large portion`() {
        val food = FoodItem(
            id = "test2", name = "Alimento de prueba", servingSize = 100.0, unit = "g",
            calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3,
        )
        val logged = scaleFoodByPortion(food, quantity = 1.0, portion = PortionPreset.LARGE)
        // large = 1.5x
        assertEquals(195.0, logged.calories, 0.5)
        assertEquals(4.1, logged.protein, 0.1)
    }

    @Test
    fun `scale food with explicit grams`() {
        val food = FoodItem(
            id = "test3", name = "Alimento de prueba", servingSize = 100.0, unit = "g",
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
        assertEquals(300.0, breakfast?.totals?.calories ?: 0.0, 0.01)
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
    fun `scale food copies caffeine and creatine`() {
        val food = FoodItem(
            id = "enr_test",
            name = "Test Energy",
            servingSize = 250.0,
            unit = "ml",
            calories = 110.0,
            caffeineMg = 80.0,
            creatineG = 0.0,
        )
        val logged = scaleFoodByPortion(food, quantity = 1.0)
        assertEquals(80.0, logged.caffeineMg, 0.01)

        val creatine = FoodItem(
            id = "gen106",
            name = "Creatina",
            servingSize = 5.0,
            unit = "g",
            creatineG = 5.0,
        )
        val creatineLog = scaleFoodByPortion(creatine, quantity = 1.0)
        assertEquals(5.0, creatineLog.creatineG, 0.01)
    }

    @Test
    fun `daily totals sum caffeine and creatine`() {
        val logs = listOf(
            NutritionLog(
                foods = listOf(
                    LoggedFood(foodName = "Energy", calories = 100.0, caffeineMg = 80.0),
                    LoggedFood(foodName = "Creatina", creatineG = 5.0),
                ),
            ),
        )
        val totals = computeDailyTotals(logs)
        assertEquals(80.0, totals.caffeineMg, 0.01)
        assertEquals(5.0, totals.creatineG, 0.01)
    }

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

    // ─── Cooking Method Application ─────────────────────────────────────────

    @Test
    fun `scale food with frito concentrates macros for meat`() {
        val food = FoodItem(
            id = "pollo", name = "Pollo", servingSize = 100.0, unit = "g",
            calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0, cookingMethod = CookingMethod.FRITO)
        assertEquals(200.0, logged.amount, 0.01)
        assertEquals(363.0, logged.calories, 1.0) // 165 * 1.10 * 2 = 363
        assertEquals(68.2, logged.protein, 0.5)    // 31 * 1.10 * 2 = 68.2
        assertEquals(0.0, logged.carbs, 0.5)
        assertEquals(7.2, logged.fats, 0.5)       // 3.6 * 1.00 * 2 = 7.2
        assertEquals(CookingMethod.FRITO, logged.cookingMethod)
    }

    @Test
    fun `scale food with plancha reduces fat`() {
        val food = FoodItem(
            id = "pollo", name = "Pollo", servingSize = 100.0, unit = "g",
            calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0, cookingMethod = CookingMethod.PLANCHA)
        assertEquals(200.0, logged.amount, 0.01)
        assertEquals(330.0, logged.calories, 1.0) // 165 * 1.0 * 2 = 330
        assertTrue(logged.protein > 62.0) // protein boosted slightly (1.05x)
        assertTrue(logged.fats < 8.0) // fats reduced (0.95x)
    }

    @Test
    fun `scale food with empanizado frito concentrates macros and carbs`() {
        val food = FoodItem(
            id = "merluza", name = "Merluza", servingSize = 100.0, unit = "g",
            calories = 120.0, protein = 22.0, carbs = 0.0, fats = 3.0,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 150.0, cookingMethod = CookingMethod.EMPANIZADO_FRITO)
        assertEquals(150.0, logged.amount, 0.01)
        assertEquals(216.0, logged.calories, 1.0)
        assertEquals(36.3, logged.protein, 0.5)
        assertEquals(4.5, logged.fats, 0.5)
    }

    @Test
    fun `scale food with cocido reduces kcal due to water dilution`() {
        val food = FoodItem(
            id = "pollo", name = "Pollo", servingSize = 100.0, unit = "g",
            calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0, cookingMethod = CookingMethod.COCIDO)
        // kcal: 165 * 0.90 * 2 = 297
        assertTrue(logged.calories < 310.0)
        assertTrue(logged.protein < 60.0) // 31 * 0.95 * 2 = 58.9
    }

    @Test
    fun `scale food without cooking keeps original macros`() {
        val food = FoodItem(
            id = "pollo", name = "Pollo", servingSize = 100.0, unit = "g",
            calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0)
        assertEquals(330.0, logged.calories, 1.0) // 165 * 2 = 330
        assertEquals(62.0, logged.protein, 0.5) // 31 * 2 = 62
        assertEquals(7.2, logged.fats, 0.5) // 3.6 * 2 = 7.2
    }

    @Test
    fun `scale food with frito concentrates macros`() {
        val food = FoodItem(
            id = "verduras", name = "Verduras", servingSize = 100.0, unit = "g",
            calories = 28.0, protein = 2.0, carbs = 5.0, fats = 0.3,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 150.0, cookingMethod = CookingMethod.FRITO)
        // kcal: 28 * 1.10 * 1.5 = 46.2, which rounds to 46.0
        // fats: 0.3 * 1.00 * 1.5 = 0.45, which rounds to 0.5
        assertEquals(46.0, logged.calories, 0.1)
        assertEquals(0.5, logged.fats, 0.1)
    }

    @Test
    fun `liquid food uses ml unit`() {
        val food = FoodItem(
            id = "leche", name = "Leche entera", servingSize = 200.0, unit = "g",
            calories = 62.0, protein = 3.2, carbs = 4.8, fats = 3.4,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0)
        assertEquals("ml", logged.unit)
    }

    @Test
    fun `liquid food bebida uses ml unit`() {
        val food = FoodItem(
            id = "bebida", name = "Bebida energética", servingSize = 250.0, unit = "g",
            calories = 45.0, protein = 0.0, carbs = 11.0, fats = 0.0,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 250.0)
        assertEquals("ml", logged.unit)
    }

    @Test
    fun `non-liquid food keeps g unit`() {
        val food = FoodItem(
            id = "pollo", name = "Pollo", servingSize = 100.0, unit = "g",
            calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0)
        assertEquals("g", logged.unit)
    }

    @Test
    fun `getContextualDefaultServingSize handles categories logically`() {
        val oil = FoodItem(id = "oil", name = "Aceite de Oliva", servingSize = 100.0)
        assertEquals(10.0, getContextualDefaultServingSize(oil), 0.01)

        val butter = FoodItem(id = "butter", name = "Mantequilla", servingSize = 100.0)
        assertEquals(15.0, getContextualDefaultServingSize(butter), 0.01)

        val rawChicken = FoodItem(id = "chicken_raw", name = "Pechuga de Pollo (cruda)", servingSize = 100.0)
        assertEquals(150.0, getContextualDefaultServingSize(rawChicken), 0.01)

        val cookedChicken = FoodItem(id = "chicken_cooked", name = "Pechuga de Pollo (plancha)", servingSize = 100.0)
        assertEquals(120.0, getContextualDefaultServingSize(cookedChicken), 0.01)

        val dryOats = FoodItem(id = "oats", name = "Avena en Hojuelas", servingSize = 100.0)
        assertEquals(40.0, getContextualDefaultServingSize(dryOats), 0.01)

        val rawPasta = FoodItem(id = "pasta_raw", name = "Pasta (cruda)", servingSize = 100.0)
        assertEquals(45.0, getContextualDefaultServingSize(rawPasta), 0.01)

        val cookedRice = FoodItem(id = "rice_cooked", name = "Arroz Blanco (cocido)", servingSize = 100.0)
        assertEquals(120.0, getContextualDefaultServingSize(cookedRice), 0.01)
    }

    @Test
    fun `scaleFoodByPortion applies portion adjustment without touching density`() {
        val food = FoodItem(
            id = "chicken", name = "Pechuga de Pollo (cocida)", servingSize = 100.0,
            calories = 195.0, protein = 30.0, carbs = 0.0, fats = 7.8
        )
        val logged = scaleFoodByPortion(
            food = food,
            quantity = 1.0,
            portion = PortionPreset.MEDIUM,
            amountGrams = null,
            cookingMethod = null,
            portionAdjustment = 1.1
        )
        // Porción subjetiva (cocinado ~120 g) x 1.1 contexto = 132 g;
        // la densidad por 100 g de la ficha no cambia por contexto.
        assertEquals(132.0, logged.amount, 0.01)
        assertEquals(257.0, logged.calories, 1.0)
        assertEquals(39.6, logged.protein, 0.1)
    }

    @Test
    fun `deriveMacroGoals prefers active plan targets over settings`() {
        val settings = Settings(dailyCalorieGoal = 2200, dailyProteinGoal = 140)
        val plan = NutritionPlan(
            id = "plan1",
            name = "Cut",
            calorieTarget = 1800,
            proteinGoal = 160,
            carbGoal = 120,
            fatGoal = 50,
            isActive = true,
        )
        val goals = deriveMacroGoals(settings, plan)
        assertEquals(1800, goals.calorieGoal)
        assertEquals(160, goals.proteinGoal)
        assertEquals(120, goals.carbGoal)
        assertEquals(50, goals.fatGoal)
    }

    @Test
    fun `deriveMacroGoals falls back to settings when plan is null`() {
        val settings = Settings(
            dailyCalorieGoal = 2500,
            dailyProteinGoal = 180,
            dailyCarbGoal = 280,
            dailyFatGoal = 75,
        )
        val goals = deriveMacroGoals(settings, null)
        assertEquals(2500, goals.calorieGoal)
        assertEquals(180, goals.proteinGoal)
        assertEquals(280, goals.carbGoal)
        assertEquals(75, goals.fatGoal)
    }
}
