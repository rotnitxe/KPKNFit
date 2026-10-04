package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N8b / N10c (blind corpus #4 and #49): the cooking of a food is applied once and the two ways in which it overshot are closed.
 *  - A complete dish row (a prepared dish or a recipe estimate) holds the cooking its name says: "empanada de pino al horno" is the 450 kcal of the row,
 *    not 450 x 1.15 (rule b of [cookingTransformFor] is for a row of unknown state, and a dish is not one).
 *  - A FATTY raw fish does not concentrate its energy by the whole yield of its row: salmon is 208 kcal per 100 g raw and 206 cooked (USDA FDC 175167
 *    and 175168), and its yield, 0.78, made 150 g cooked 400 kcal against the 309 of the reference. [cookingNutrientYield] floors it at 0.95.
 */
class CompleteDishAndFattyFishCookingTest {

    private fun row(id: String): FoodItem = checkNotNull(findStaticFoodById(id)) { id }

    // ─── Rule (b) does not reach a complete dish ────────────────────────────────────

    @Test
    fun `a complete dish row takes no per gram factor of a concentrating method`() {
        val prepared = row("cl001") // Empanada de Pino, a prepared dish
        val recipe = row("gen183") // Chacarero, a recipe estimate
        assertEquals("RECIPE_ESTIMATE", recipe.source)
        for (dish in listOf(prepared, recipe)) {
            assertTrue(dish.id, HouseholdPortions.isCompleteDish(dish))
            for (method in CONCENTRATING_METHODS) assertEquals("${dish.id} + $method", CookingTransform.None, cookingTransformFor(dish, method))
            val plain = scaleFoodByPortion(dish, amountGrams = dish.servingSize)
            val baked = scaleFoodByPortion(dish, amountGrams = dish.servingSize, cookingMethod = CookingMethod.HORNO)
            assertEquals(dish.id, plain.calories, baked.calories, 0.0)
        }
        // a row of unknown state that is no dish keeps its factor, once
        val bread = row("gen019")
        assertFalse(HouseholdPortions.isCompleteDish(bread))
        assertTrue(cookingTransformFor(bread, CookingMethod.HORNO) is CookingTransform.ConcentrationFactor)
        assertFalse(HouseholdPortions.isCompleteDish(row("gen009")))
    }

    @Test
    fun `an empanada de pino al horno is the 450 kcal of its row`() {
        // blind corpus #49: 518 kcal (450 x 1.15) against the 430 of the references
        val tag = N11bTestEnv.resolve("una empanada de pino al horno").single()
        assertEquals("cl001", tag.foodItem?.id)
        assertEquals(CookingMethod.HORNO, tag.cookingMethod)
        assertEquals(450.0, tag.loggedFood?.calories ?: Double.NaN, 0.0)
        assertEquals(180.0, N11bTestEnv.grams(tag), 0.001)
        assertEquals(CookingTransform.None, cookingTransformFor(row("cl001"), CookingMethod.HORNO))
    }

    // ─── The yield of a fatty fish ──────────────────────────────────────────────────

    @Test
    fun `the yield of a fatty raw fish never concentrates its nutrients by more than 1 over 0,95`() {
        val salmon = row("gen009")
        assertEquals(0.78, cookingWeightYield(salmon), 0.0) // the physical yield of the row is unchanged
        assertEquals(0.95, cookingNutrientYield(salmon), 0.0)
        val cooked = scaleFoodByPortion(salmon, amountGrams = 100.0, cookingMethod = CookingMethod.ASADO_PARRILLA)
        assertEquals(219.0, cooked.calories, 0.5) // 208 / 0.95
        assertTrue(cooked.calories <= salmon.calories / 0.95 + 0.5)
        // and the cooked pair of USDA, 206 kcal per 100 g against the raw 208, is within 7 % of it
        assertEquals(206.0, cooked.calories, 206.0 * 0.07)
        // a yield above the floor is never lowered, and a fish with less than 10 g of fat keeps its own
        val steady = salmon.copy(cookingWeightFactor = 0.97)
        assertEquals(0.97, cookingNutrientYield(steady), 0.0)
        val lean = salmon.copy(id = "lean", name = "Merluza (cruda)", fats = 9.9, cookingWeightFactor = 0.8)
        assertEquals(0.8, cookingNutrientYield(lean), 0.0)
        val fatty = salmon.copy(id = "fatty", name = "Jurel (crudo)", fats = 10.0, cookingWeightFactor = 0.8)
        assertEquals(0.95, cookingNutrientYield(fatty), 0.0)
    }

    @Test
    fun `meat and poultry keep the yield of their row, fat or lean`() {
        // chicken breast: 120 kcal raw, 165 cooked (FDC 171077 and 171477): the yield is right
        assertEquals(0.75, cookingNutrientYield(row("gen003")), 0.0)
        // beef rib: the row is 250 kcal raw and /0.7 is 357 kcal per 100 g cooked, the 352 of a broiled USDA rib (FDC 168676); 20 g of fat per 100 g
        val asado = row("gen093c")
        assertTrue(asado.fats >= 10.0)
        assertEquals(0.7, cookingNutrientYield(asado), 0.0)
        assertEquals(357.0, scaleFoodByPortion(asado, amountGrams = 100.0, cookingMethod = CookingMethod.ASADO_PARRILLA).calories, 0.5)
    }

    @Test
    fun `a cooked row of a fatty fish asked raw uses the same yield and gives the raw row back`() {
        // 100 g of raw salmon from the cooked row (218 kcal per 100 g cooked) are 95 g of it: 207 kcal, the 208 of the raw row (USDA pair 208 / 206)
        val plancha = row("gen009p")
        assertEquals(CookingTransform.StateConversion(FoodState.COOKED, FoodState.RAW, 0.95), cookingTransformFor(plancha, CookingMethod.CRUDO))
        assertEquals(207.0, scaleFoodByPortion(plancha, amountGrams = 100.0, cookingMethod = CookingMethod.CRUDO).calories, 0.5)
    }
}
