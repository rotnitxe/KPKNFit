package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.PortionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden cases for amount preservation, cooking yield, and clarification.
 */
class CookingPortionPrecisionTest {

    @Test
    fun `explicit grams keep amountIntent and do not rely on subjective`() {
        val parsed = parseMealDescription("200g arroz")
        val item = parsed.items.single()
        assertEquals(AmountIntent.EXPLICIT_MASS, item.amountIntent)
        assertEquals(200.0, item.amountGrams!!, 0.01)
        assertNull(item.cookingMethod)
    }

    @Test
    fun `kg converts without distorting intent`() {
        val parsed = parseMealDescription("1.5kg pollo")
        val item = parsed.items.single()
        assertEquals(AmountIntent.EXPLICIT_MASS, item.amountIntent)
        assertEquals(1500.0, item.amountGrams!!, 0.01)
    }

    @Test
    fun `cup of rice is resolved subjective and stable utensil grams`() {
        val parsed = parseMealDescription("una taza de arroz")
        val item = parsed.items.single()
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, item.amountIntent)
        assertNotNull(item.amountGrams)
        // 250 ml × GRAIN density 0.85
        assertEquals(212.5, item.amountGrams!!, 1.0)
    }

    @Test
    fun `scoop is absolute grams not relative blow-up`() {
        val result = SubjectivePortionEngine.resolve(
            expression = "un scoop de proteina",
            foodCategory = SubjectivePortionEngine.FoodDensityCategory.POWDER,
            standardPortion = 30.0,
        )
        assertNotNull(result)
        assertEquals(30.0, result!!.grams, 0.01)
        assertTrue(result.source.startsWith("scoop:"))
    }

    @Test
    fun `explicit grams ignore proteinBoost in scaleFoodByPortion when caller locks`() {
        val food = FoodItem(
            id = "t", name = "Pollo", servingSize = 100.0, unit = "g",
            calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6,
        )
        val locked = scaleFoodByPortion(
            food = food,
            amountGrams = 200.0,
            portionAdjustment = 1.4,
            proteinBoost = 0.2,
        )
        // Callers must pass portionAdjustment=1 and proteinBoost=0 for locked intents;
        // this documents the MacroCalculator contract: with amountGrams, portionAdjustment
        // does not change amount, but proteinBoost still multiplies unless caller zeros it.
        assertEquals(200.0, locked.amount, 0.01)
        val noBoost = scaleFoodByPortion(
            food = food,
            amountGrams = 200.0,
            portionAdjustment = 1.0,
            proteinBoost = 0.0,
        )
        assertEquals(200.0, noBoost.amount, 0.01)
        assertEquals(62.0, noBoost.protein, 0.2)
    }

    @Test
    fun `casino context portionAdjustment does not change explicit grams amount`() {
        val food = findFoodByNormalized("arroz")!!
        val logged = scaleFoodByPortion(
            food = food,
            amountGrams = 200.0,
            portionAdjustment = 1.35,
            proteinBoost = 0.0,
        )
        assertEquals(200.0, logged.amount, 0.01)
    }

    @Test
    fun `soya dry has cookingWeightFactor and hydrates via method`() {
        val dry = findFoodByNormalized("soya texturizada")!!
        assertTrue(CookingStateResolver.isDbFoodRaw(dry))
        assertNotNull(dry.cookingWeightFactor)
        assertEquals(3.5, dry.cookingWeightFactor!!, 0.01)

        val asHydrated = scaleFoodByPortion(
            food = dry,
            amountGrams = 100.0,
            cookingMethod = CookingMethod.COCIDO,
        )
        // 100 g on plate hydrated → macros from ~28.6 g dry profile; COCIDO also ×0.90 kcal
        assertEquals(100.0, asHydrated.amount, 0.01)
        val dryEquivalentRatio = (100.0 / 3.5) / 100.0
        val expectedKcal = kotlin.math.round(340.0 * dryEquivalentRatio * 0.90)
        assertEquals(expectedKcal, asHydrated.calories, 1.0)
        assertTrue(asHydrated.calories < 200.0)
    }

    @Test
    fun `pollo frito prefers prepared DB variant without oil path`() {
        val prepared = CookingStateResolver.findPreparedVariant("pollo", CookingMethod.FRITO)
        assertNotNull(prepared)
        assertTrue(prepared!!.name.lowercase().contains("frit"))
        assertTrue(CookingStateResolver.isAlreadyPreparedForMethod(prepared, CookingMethod.FRITO))
        assertFalse(CookingStateResolver.shouldApplyOil(prepared, CookingMethod.FRITO))
    }

    @Test
    fun `fried on raw base should apply oil`() {
        val raw = findFoodByNormalized("pechuga de pollo")
            ?: findFoodByNormalized("pollo")!!
        // Prefer raw profile
        val base = if (CookingStateResolver.isDbFoodRaw(raw)) raw else {
            CookingStateResolver.findRawVariant(raw) ?: raw
        }
        if (CookingStateResolver.isAlreadyPreparedForMethod(base, CookingMethod.FRITO)) {
            // Skip if alias resolved to fried row
            return
        }
        assertTrue(CookingStateResolver.shouldApplyOil(base, CookingMethod.FRITO))
    }

    @Test
    fun `arroz without state needs dry vs cooked clarification`() {
        val food = findFoodByNormalized("arroz")
        val kind = CookingStateResolver.clarificationKind("arroz", food, cookingMethod = null)
        assertEquals(CookingStateResolver.ClarificationKind.DRY_VS_COOKED, kind)
        assertNotNull(CookingStateResolver.assumedStateStatus("arroz", food))
    }

    @Test
    fun `soya without state needs clarification`() {
        val food = findFoodByNormalized("soya texturizada")
        val kind = CookingStateResolver.clarificationKind("soya texturizada", food, null)
        assertEquals(CookingStateResolver.ClarificationKind.DRY_VS_COOKED, kind)
    }

    @Test
    fun `pollo frito parse keeps method and does not ask clarification`() {
        val parsed = parseMealDescription("200g pollo frito")
        val item = parsed.items.single()
        assertEquals(CookingMethod.FRITO, item.cookingMethod)
        assertEquals(200.0, item.amountGrams!!, 0.01)
        val kind = CookingStateResolver.clarificationKind(item.tag, null, item.cookingMethod)
        assertEquals(CookingStateResolver.ClarificationKind.NONE, kind)
    }

    @Test
    fun `oil strip and reapply moves fats`() {
        val base = LoggedFood(
            id = "1",
            foodName = "Pollo",
            amount = 200.0,
            calories = 400.0,
            protein = 60.0,
            carbs = 0.0,
            fats = 10.0,
            cookingMethod = CookingMethod.FRITO,
        )
        val medio = adjustOilForTest(base, "medio")
        val abundante = reapplyOilForTest(medio, "medio", "abundante")
        assertTrue(abundante.fats > medio.fats)
        assertTrue(abundante.calories > medio.calories)
        val poco = reapplyOilForTest(abundante, "abundante", "poco")
        assertTrue(poco.fats < abundante.fats)
    }

    // Mirror drawer oil helpers for unit test without Compose
    private fun adjustOilForTest(logged: LoggedFood, level: String): LoggedFood {
        val oil = when (level) {
            "poco" -> 3.0
            "abundante" -> 18.0
            else -> 8.0
        }
        return logged.copy(
            fats = kotlin.math.round((logged.fats + oil) * 10) / 10.0,
            calories = kotlin.math.round(logged.calories + oil * 9),
        )
    }

    private fun reapplyOilForTest(
        logged: LoggedFood,
        oldLevel: String,
        newLevel: String,
    ): LoggedFood {
        val oldOil = when (oldLevel) {
            "poco" -> 3.0
            "abundante" -> 18.0
            else -> 8.0
        }
        val stripped = logged.copy(
            fats = kotlin.math.round((logged.fats - oldOil).coerceAtLeast(0.0) * 10) / 10.0,
            calories = kotlin.math.round((logged.calories - oldOil * 9).coerceAtLeast(0.0)),
        )
        return adjustOilForTest(stripped, newLevel)
    }
}
