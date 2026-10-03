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
        // large = 1.25x (WP-N6: one size scale; it was 1.5x)
        assertEquals(162.5, logged.calories, 0.5)
        assertEquals(3.4, logged.protein, 0.1)
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

    @Test
    fun `daily totals carry min and max and estimate flag from foods`() {
        val logs = listOf(
            NutritionLog(
                id = "1", date = "2025-01-15T12:00:00", mealType = MealType.LUNCH,
                foods = listOf(
                    LoggedFood(id = "f1", foodName = "Arroz", calories = 200.0),
                    LoggedFood(
                        id = "f2", foodName = "Guiso casero", calories = 400.0,
                        caloriesMin = 300.0, caloriesMax = 550.0, isUncertain = true,
                    ),
                ),
            )
        )
        val totals = computeDailyTotals(logs)
        assertEquals(600.0, totals.calories, 0.01)
        // El alimento exacto aporta su centro a ambos extremos; el incierto, su rango guardado.
        assertEquals(500.0, totals.caloriesMin!!, 0.01) // 200 + 300
        assertEquals(750.0, totals.caloriesMax!!, 0.01) // 200 + 550
        assertTrue(totals.isEstimate)
    }

    @Test
    fun `exact foods produce a zero-width range`() {
        val logs = listOf(
            NutritionLog(
                id = "1", date = "2025-01-15T12:00:00", mealType = MealType.LUNCH,
                foods = listOf(
                    LoggedFood(id = "f1", foodName = "Arroz", calories = 200.0),
                    // Un registro V2 exacto guarda su banda como [centro, centro]: tampoco es una estimación.
                    LoggedFood(id = "f2", foodName = "Pollo", calories = 300.0, caloriesMin = 300.0, caloriesMax = 300.0),
                ),
            )
        )
        val totals = computeDailyTotals(logs)
        assertEquals(500.0, totals.calories, 0.0)
        assertEquals(totals.calories, totals.caloriesMin!!, 0.0)
        assertEquals(totals.calories, totals.caloriesMax!!, 0.0)
        assertFalse(totals.isEstimate)
    }

    @Test
    fun `empty input is an exact zero with a zero-width range`() {
        val totals = computeDailyTotals(emptyList())
        assertEquals(0.0, totals.caloriesMin!!, 0.0)
        assertEquals(0.0, totals.caloriesMax!!, 0.0)
        assertFalse(totals.isEstimate)
    }

    @Test
    fun `an uncertain food without a stored range still flags the estimate`() {
        val totals = computeDailyTotals(
            listOf(NutritionLog(foods = listOf(LoggedFood(foodName = "Postre", calories = 250.0, isUncertain = true))))
        )
        assertEquals(250.0, totals.caloriesMin!!, 0.0)
        assertEquals(250.0, totals.caloriesMax!!, 0.0)
        assertTrue(totals.isEstimate)
    }

    @Test
    fun `a stored range flags the estimate even when the food is not marked uncertain`() {
        val totals = computeDailyTotals(
            listOf(NutritionLog(foods = listOf(LoggedFood(foodName = "Pollo", calories = 400.0, caloriesMin = 350.0, caloriesMax = 470.0))))
        )
        assertEquals(350.0, totals.caloriesMin!!, 0.0)
        assertEquals(470.0, totals.caloriesMax!!, 0.0)
        assertTrue(totals.isEstimate)
    }

    @Test
    fun `planned logs add neither range nor estimate flag`() {
        val planned = NutritionLog(
            id = "p", status = NutritionStatus.PLANNED,
            foods = listOf(LoggedFood(foodName = "Cena", calories = 500.0, caloriesMin = 400.0, caloriesMax = 700.0, isUncertain = true)),
        )
        val consumed = NutritionLog(id = "c", foods = listOf(LoggedFood(foodName = "Pan", calories = 300.0)))
        val totals = computeDailyTotals(listOf(planned, consumed))
        assertEquals(300.0, totals.calories, 0.0)
        assertEquals(300.0, totals.caloriesMin!!, 0.0)
        assertEquals(300.0, totals.caloriesMax!!, 0.0)
        assertFalse(totals.isEstimate)
    }

    @Test
    fun `range bounds are rounded like calories`() {
        val totals = computeDailyTotals(
            listOf(
                NutritionLog(
                    foods = listOf(LoggedFood(foodName = "Batido", calories = 100.4, caloriesMin = 99.6, caloriesMax = 150.6, isUncertain = true))
                )
            )
        )
        assertEquals(100.0, totals.calories, 0.0)
        assertEquals(100.0, totals.caloriesMin!!, 0.0)
        assertEquals(151.0, totals.caloriesMax!!, 0.0)
    }

    @Test
    fun `food totals describe the foods whatever the log status`() {
        val planned = NutritionLog(
            status = NutritionStatus.PLANNED,
            foods = listOf(LoggedFood(foodName = "Cena", calories = 500.0, caloriesMin = 400.0, caloriesMax = 700.0)),
        )
        // El total del día no cuenta lo planificado...
        assertEquals(0.0, computeDailyTotals(listOf(planned)).calories, 0.0)
        // ...pero la fila de ese registro sí se describe con sus propios alimentos.
        val totals = computeFoodTotals(planned.foods)
        assertEquals(500.0, totals.calories, 0.0)
        assertEquals(400.0, totals.caloriesMin!!, 0.0)
        assertEquals(700.0, totals.caloriesMax!!, 0.0)
        assertTrue(totals.isEstimate)
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

    @Test
    fun `meal groups keep the estimate flag per meal`() {
        val logs = listOf(
            NutritionLog(id = "1", date = "2025-01-15T08:00:00", mealType = MealType.BREAKFAST,
                foods = listOf(LoggedFood(id = "f1", foodName = "Avena", calories = 300.0))),
            NutritionLog(id = "2", date = "2025-01-15T12:00:00", mealType = MealType.LUNCH,
                foods = listOf(LoggedFood(id = "f2", foodName = "Guiso", calories = 400.0, caloriesMin = 300.0, caloriesMax = 550.0, isUncertain = true))),
        )
        val groups = computeMealGroups(logs).associateBy { it.mealType }
        assertFalse(groups.getValue(MealType.BREAKFAST).totals.isEstimate)
        assertTrue(groups.getValue(MealType.LUNCH).totals.isEstimate)
        assertEquals(300.0, groups.getValue(MealType.LUNCH).totals.caloriesMin!!, 0.0)
        assertEquals(550.0, groups.getValue(MealType.LUNCH).totals.caloriesMax!!, 0.0)
        assertFalse(groups.getValue(MealType.DINNER).totals.isEstimate)
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
    fun `scale food with frito applies no factor to a row, its fat is oil in grams`() {
        val food = FoodItem(
            id = "pollo", name = "Pollo", servingSize = 100.0, unit = "g",
            calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0, cookingMethod = CookingMethod.FRITO)
        assertEquals(200.0, logged.amount, 0.01)
        assertEquals(330.0, logged.calories, 1.0) // 165 * 2 = 330 (WP-N10: was x1.10 = 363)
        assertEquals(62.0, logged.protein, 0.5)    // 31 * 2 = 62 (was x1.10 = 68.2)
        assertEquals(0.0, logged.carbs, 0.5)
        assertEquals(7.2, logged.fats, 0.5)       // 3.6 * 2 = 7.2 (the fat of frying enters as oil grams: adjustLoggedFoodForOil)
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
    fun `scale food with empanizado frito applies no factor to a row`() {
        val food = FoodItem(
            id = "merluza", name = "Merluza", servingSize = 100.0, unit = "g",
            calories = 120.0, protein = 22.0, carbs = 0.0, fats = 3.0,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 150.0, cookingMethod = CookingMethod.EMPANIZADO_FRITO)
        assertEquals(150.0, logged.amount, 0.01)
        assertEquals(180.0, logged.calories, 1.0) // 120 * 1.5 = 180 (WP-N10: was x1.20 = 216)
        assertEquals(33.0, logged.protein, 0.5) // 22 * 1.5 = 33 (was x1.10 = 36.3)
        assertEquals(4.5, logged.fats, 0.5)
    }

    @Test
    fun `scale food with cocido applies no factor to a row of unknown state`() {
        val food = FoodItem(
            id = "pollo", name = "Pollo", servingSize = 100.0, unit = "g",
            calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0, cookingMethod = CookingMethod.COCIDO)
        // WP-N10: boiling changes a raw row through its yield, never through a per-gram factor (was x0.90 kcal and x0.95 protein)
        assertEquals(330.0, logged.calories, 1.0) // 165 * 2
        assertEquals(62.0, logged.protein, 0.5) // 31 * 2
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
    fun `scale food with frito applies no factor to vegetables`() {
        val food = FoodItem(
            id = "verduras", name = "Verduras", servingSize = 100.0, unit = "g",
            calories = 28.0, protein = 2.0, carbs = 5.0, fats = 0.3,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 150.0, cookingMethod = CookingMethod.FRITO)
        // kcal: 28 * 1.5 = 42 (WP-N10: was x1.10 = 46.2, which rounded to 46.0)
        // fats: 0.3 * 1.5 = 0.45, which rounds to 0.5
        assertEquals(42.0, logged.calories, 0.1)
        assertEquals(0.5, logged.fats, 0.1)
    }

    // ─── WP-N10: una sola transformación de cocción por ficha ───────────────────

    /** kcal, protein, carbs and fats of [food] at [grams], rounded as scaleFoodByPortion does, with an optional per-gram [factor]. */
    private fun scaledMacros(food: FoodItem, grams: Double, factor: CookingFactor = CookingFactor()): List<Double> {
        val ratio = grams / NutrientBasis.grams(food)
        return listOf(
            kotlin.math.round(food.calories * factor.kcal * ratio),
            kotlin.math.round(food.protein * factor.protein * ratio * 10) / 10.0,
            kotlin.math.round(food.carbs * factor.carbs * ratio * 10) / 10.0,
            kotlin.math.round(food.fats * factor.fats * ratio * 10) / 10.0,
        )
    }

    private fun macrosOf(logged: LoggedFood) = listOf(logged.calories, logged.protein, logged.carbs, logged.fats)

    @Test
    fun `invariant every row of the catalog is transformed by exactly one of conversion, factor or nothing`() {
        val foods = com.example.kpkn.data.food.buildFoodDatabase()
        var conversions = 0
        var factors = 0
        var untouched = 0
        for (food in foods) {
            for (method in CookingMethod.entries) {
                val actual = macrosOf(scaleFoodByPortion(food, amountGrams = 100.0, cookingMethod = method))
                val expected = when (val transform = cookingTransformFor(food, method)) {
                    // The basis changes the grams of the row; nothing else touches its nutrients.
                    is CookingTransform.StateConversion -> {
                        conversions++
                        val grams = if (transform.to == FoodState.COOKED) 100.0 / transform.weightYield else 100.0 * transform.weightYield
                        scaledMacros(food, grams)
                    }
                    // Only a concentrating method on a row of unknown state carries its table factor, once.
                    is CookingTransform.ConcentrationFactor -> {
                        factors++
                        assertTrue(transform.method in CONCENTRATING_METHODS)
                        assertEquals(COOKING_FACTORS[method], transform.factor)
                        scaledMacros(food, 100.0, transform.factor)
                    }
                    CookingTransform.None -> {
                        untouched++
                        scaledMacros(food, 100.0)
                    }
                }
                assertEquals("${food.id} ${food.name} + $method", expected, actual)
            }
        }
        assertTrue("the catalog should exercise every route", conversions > 0 && factors > 0 && untouched > 0)
    }

    @Test
    fun `invariant a raw row never carries a factor with its state conversion, whatever the method`() {
        val rawRows = com.example.kpkn.data.food.buildFoodDatabase().filter { CookingStateResolver.isDbFoodRaw(it) }
        assertTrue(rawRows.size >= 10)
        val cookedMethods = CookingMethod.entries.filter { it != CookingMethod.CRUDO }
        for (food in rawRows) {
            val reference = macrosOf(scaleFoodByPortion(food, amountGrams = 100.0, cookingMethod = CookingMethod.COCIDO))
            for (method in cookedMethods) {
                assertTrue("${food.id} + $method", cookingTransformFor(food, method) is CookingTransform.StateConversion)
                val logged = macrosOf(scaleFoodByPortion(food, amountGrams = 100.0, cookingMethod = method))
                assertEquals("${food.id} + $method", reference, logged)
            }
        }
        // A raw row asked raw is the row as it is.
        for (food in rawRows.filter { !CookingStateResolver.isDbFoodCooked(it) }) {
            assertEquals(CookingTransform.None, cookingTransformFor(food, CookingMethod.CRUDO))
        }
    }

    @Test
    fun `a row of unknown state changes only with a concentrating method, once`() {
        val unknownRows = com.example.kpkn.data.food.buildFoodDatabase()
            .filter { !CookingStateResolver.isDbFoodRaw(it) && !CookingStateResolver.isDbFoodCooked(it) }
        assertTrue(unknownRows.size >= 10)
        for (food in unknownRows) {
            val plain = macrosOf(scaleFoodByPortion(food, amountGrams = 100.0))
            for (method in CookingMethod.entries) {
                val logged = macrosOf(scaleFoodByPortion(food, amountGrams = 100.0, cookingMethod = method))
                if (method in CONCENTRATING_METHODS && !CookingStateResolver.isAlreadyPreparedForMethod(food, method)) {
                    assertEquals("${food.id} + $method", scaledMacros(food, 100.0, COOKING_FACTORS.getValue(method)), logged)
                } else {
                    assertEquals("${food.id} + $method", plain, logged)
                }
            }
        }
    }

    @Test
    fun `scale food with horno on a row of unknown state applies the table factor once`() {
        val tomato = FoodItem(id = "t", name = "Tomate", servingSize = 100.0, unit = "g", calories = 18.0, protein = 0.9, carbs = 3.9, fats = 0.2)
        val logged = scaleFoodByPortion(tomato, amountGrams = 200.0, cookingMethod = CookingMethod.HORNO)
        assertEquals(41.0, logged.calories, 0.0) // 18 * 1.15 * 2 = 41.4
        assertEquals(2.0, logged.protein, 0.0) // 0.9 * 1.10 * 2 = 1.98
        assertEquals(8.2, logged.carbs, 0.0) // 3.9 * 1.05 * 2 = 8.19
        assertEquals(0.4, logged.fats, 0.0) // 0.2 * 0.95 * 2 = 0.38
    }

    @Test
    fun `scale food converts a raw row by yield alone, with no factor on top`() {
        val salmon = FoodItem(
            id = "s", name = "Salmón (crudo)", servingSize = 100.0, unit = "g",
            calories = 208.0, protein = 20.0, carbs = 0.0, fats = 13.0, cookingWeightFactor = 0.78,
        )
        // 150 g cooked = 150 / 0.78 g raw: 192.3 g x 2.08 = 400 kcal. Before WP-N10 the parrilla factor made it 420.
        for (method in listOf(CookingMethod.ASADO_PARRILLA, CookingMethod.HORNO, CookingMethod.COCIDO, CookingMethod.FRITO)) {
            val logged = scaleFoodByPortion(salmon, amountGrams = 150.0, cookingMethod = method)
            assertEquals("$method", 400.0, logged.calories, 0.0)
            assertEquals("$method", 38.5, logged.protein, 0.0)
            assertEquals("$method", 25.0, logged.fats, 0.0)
            assertEquals(150.0, logged.amount, 0.0)
        }
        val plain = scaleFoodByPortion(salmon, amountGrams = 150.0)
        assertEquals(312.0, plain.calories, 0.0) // no method: the row as it is
    }

    @Test
    fun `scale food converts a cooked row to raw by multiplying with the yield`() {
        val chicken = FoodItem(
            id = "p", name = "Pechuga de Pollo (cocida)", servingSize = 100.0, unit = "g",
            calories = 168.0, protein = 32.0, carbs = 0.0, fats = 3.2,
        )
        // 100 g raw = 75 g cooked: 75 g x 1.68 = 126 kcal and 24 g of protein.
        val logged = scaleFoodByPortion(chicken, amountGrams = 100.0, cookingMethod = CookingMethod.CRUDO)
        assertEquals(126.0, logged.calories, 0.0)
        assertEquals(24.0, logged.protein, 0.0)
    }

    @Test
    fun `createLoggedFood scales by the amount and never applies a cooking factor`() {
        val logged = createLoggedFood(
            foodName = "Plato (estimado)", amount = 200.0, calories = 160.0, protein = 10.0, carbs = 16.0, fats = 6.0,
            cookingMethod = CookingMethod.HORNO,
        )
        assertEquals(320.0, logged.calories, 0.0)
        assertEquals(20.0, logged.protein, 0.0)
        assertEquals(32.0, logged.carbs, 0.0)
        assertEquals(12.0, logged.fats, 0.0)
        assertEquals(CookingMethod.HORNO, logged.cookingMethod)
    }

    @Test
    fun `liquid food retains canonical gram unit`() {
        val food = FoodItem(
            id = "leche", name = "Leche entera", servingSize = 200.0, unit = "g",
            calories = 62.0, protein = 3.2, carbs = 4.8, fats = 3.4,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 200.0)
        assertEquals("g", logged.unit)
    }

    @Test
    fun `liquid beverage retains canonical gram unit`() {
        val food = FoodItem(
            id = "bebida", name = "Bebida energética", servingSize = 250.0, unit = "g",
            calories = 45.0, protein = 0.0, carbs = 11.0, fats = 0.0,
        )
        val logged = scaleFoodByPortion(food, amountGrams = 250.0)
        assertEquals("g", logged.unit)
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
