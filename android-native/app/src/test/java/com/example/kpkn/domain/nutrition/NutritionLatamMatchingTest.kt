package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.models.AmountIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLatamMatchingTest {

    @Test
    fun laminasDeQuesoGouda_areThinSlicesNotAPlate() {
        val result = SubjectivePortionEngine.resolve("láminas de queso gouda")
        assertTrue("lexicon must match láminas, was $result", result != null)
        val grams = result!!.grams
        assertTrue("láminas de queso gouda must be ~2×20 g, was $grams", grams in 35.0..50.0)
        assertTrue(grams < 150.0)

        val parsed = parseMealDescription("láminas de queso gouda")
        assertEquals(1, parsed.items.size)
        val item = parsed.items.single()
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, item.amountIntent)
        val eaten = item.amountGrams ?: 0.0
        assertTrue("parser eaten grams $eaten", eaten in 35.0..50.0)
        assertTrue(eaten < 150.0)
        val density = findFoodByNormalized("queso gouda")?.calories
            ?: findFoodByNormalized("queso cheddar")?.calories
            ?: 403.0
        val kcal = eaten / 100.0 * density
        assertTrue("láminas gouda kcal $kcal must be far below the 1200 clamp", kcal < 400.0)
    }

    @Test
    fun laminasGauda_withoutAccents() {
        val grams = SubjectivePortionEngine.resolve("laminas de queso gauda")!!.grams
        assertTrue(grams in 35.0..50.0)
    }

    @Test
    fun fetasDeJamon_andTajadasDeQueso() {
        val ham = SubjectivePortionEngine.resolve("2 fetas de jamón")!!
        assertTrue("2 fetas jamón ${ham.grams}", ham.grams in 28.0..40.0)
        val cheese = SubjectivePortionEngine.resolve("3 tajadas de queso")!!
        assertTrue("3 tajadas queso ${cheese.grams}", cheese.grams in 50.0..70.0)
        val cubes = SubjectivePortionEngine.resolve("unos cuadritos de queso")!!
        assertTrue("cuadritos ${cubes.grams}", cubes.grams in 15.0..40.0)
        val loncha = SubjectivePortionEngine.resolve("una loncha de jamón")!!
        assertTrue("loncha ${loncha.grams}", loncha.grams in 14.0..22.0)
    }

    @Test
    fun quesoFeta_isTheCheeseNotOneFetaSlice() {
        val asSlice = SubjectivePortionLexicon.resolve("queso feta")
        assertNull("queso feta must not parse as a feta-slice unit", asSlice)
        val parsed = parseMealDescription("queso feta")
        val grams = parsed.items.singleOrNull()?.amountGrams
        if (grams != null) {
            assertTrue("queso feta default must not be a 18 g lonja unless explicit, was $grams", grams >= 25.0)
        }
    }

    @Test
    fun familyFor_skipsPortionHead() {
        assertEquals("queso", FoodIdentity.familyFor("láminas de queso gouda"))
        assertEquals("queso", FoodIdentity.familyFor("laminas de queso gauda"))
    }

    @Test
    fun galletasDeChocolate_isNotABeverage() {
        val shape = InferredMealContext.inferShape("galletas de chocolate")
        assertEquals(InferredMealContext.Shape.SNACK_ITEM, shape)
        assertNotEquals(InferredMealContext.Shape.BEVERAGE, shape)
        val context = ContextDetector.detect("galletas de chocolate")
        assertNotEquals("bebida", context.assumedLabel)
        val parsed = parseMealDescription("galletas de chocolate")
        val grams = parsed.items.single().amountGrams ?: HouseholdPortions.defaultGrams(
            findFoodByNormalized("galletas de chocolate"),
            "galletas de chocolate",
        )
        assertTrue("galletas de chocolate grams $grams must be snack-sized", grams in 10.0..80.0)
        assertTrue(grams < 180.0)
        val density = findFoodByNormalized("galletas de chocolate")?.calories ?: 502.0
        val kcal = grams / 100.0 * density
        assertTrue("galletas de chocolate kcal $kcal must be snack-sized, not a drink pour", kcal in 50.0..450.0)
    }

    @Test
    fun aguacate_isNotABeverage() {
        assertNotEquals(
            InferredMealContext.Shape.BEVERAGE,
            InferredMealContext.inferShape("aguacate"),
        )
        assertNotEquals(
            InferredMealContext.Shape.BEVERAGE,
            InferredMealContext.inferShape("mantequilla"),
        )
    }

    @Test
    fun chocolateCaliente_isBeverage() {
        assertEquals(
            InferredMealContext.Shape.BEVERAGE,
            InferredMealContext.inferShape("chocolate caliente"),
        )
    }

    @Test
    fun galletaAlias_isNotWhiteBread() {
        val food = findFoodByNormalized("galletas de chocolate")
        assertFalse(
            "galleta must not resolve to pan blanco, was ${food?.id} ${food?.name}",
            food?.id == "gen019" || food?.name.equals("Pan Blanco", ignoreCase = true) == true,
        )
        assertTrue(
            food?.name?.contains("galleta", ignoreCase = true) == true ||
                food?.category == "galletas",
        )
    }
}
