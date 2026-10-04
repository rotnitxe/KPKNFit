package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.BrandedSnackCatalog
import com.example.kpkn.data.food.findStaticFoodById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N8b, cause 4 of the blind corpus (#49, #59): "una empanada de pino al horno y una de queso" is two empanadas, and an "empanada de queso" is its own
 * dish or an estimate, never the cheddar of its filling nor the "Empanada de queso boliviana" of another country; the Greek yogurt is found by the
 * Spanish spelling of its name.
 */
class ElidedHeadNounTest {

    private fun tags(text: String): List<String> = parseMealDescription(text).items.map { it.tag }

    // ─── The omitted noun ───────────────────────────────────────────────────────────

    @Test
    fun `una de queso after an empanada is another empanada`() {
        assertEquals(listOf("empanada de pino", "empanada de queso"), tags("una empanada de pino al horno y una de queso"))
        assertEquals(listOf("empanada de pino", "empanada de queso"), tags("una empanada de pino y una de queso"))
        val second = parseMealDescription("una empanada de pino al horno y una de queso").items[1]
        assertEquals(1.0, second.quantity, 0.0)
        assertEquals(180.0, second.amountGrams ?: Double.NaN, 0.001) // one empanada, like the first one
        assertEquals(listOf("pizza de jamón", "pizza de queso"), tags("una pizza de jamón y una de queso"))
    }

    @Test
    fun `the noun is inherited only when it is there and in a closed list, and the count says how many`() {
        // the noun comes in the singular and the count carries the number, as in "2 empanada de queso"
        val twoOfEach = parseMealDescription("dos empanadas de pino y dos de queso").items
        assertEquals(listOf("empanadas de pino", "empanada de queso"), twoOfEach.map { it.tag })
        assertEquals(2.0, twoOfEach[1].quantity, 0.0)
        val cheeseOnly = parseMealDescription("una empanada de pino y dos de queso").items
        assertEquals(listOf("empanada de pino", "empanada de queso"), cheeseOnly.map { it.tag })
        assertEquals(2.0, cheeseOnly[1].quantity, 0.0)
        val oneCheese = parseMealDescription("dos empanadas de pino y una de queso").items
        assertEquals(listOf("empanadas de pino", "empanada de queso"), oneCheese.map { it.tag })
        assertEquals(1.0, oneCheese[1].quantity, 0.0)
        assertEquals(listOf("sandwich de jamón", "sandwich de queso"), tags("un sandwich de jamón y uno de queso"))
        // no noun before it, or a noun that is not named after its filling
        assertEquals(listOf("empanada", "queso"), tags("una empanada y una de queso"))
        assertEquals(listOf("cazuela de vacuno", "pollo"), tags("una cazuela de vacuno y una de pollo"))
        // a quantity that is not alone, or a "de" that is no ellipsis, is left as it was
        assertEquals(listOf("empanada de pino", "queso"), tags("una empanada de pino y queso"))
    }

    // ─── An empanada de queso is an estimate ────────────────────────────────────────

    @Test
    fun `an empanada de queso is an estimate of 180 g and a question and no cheese, no cheddar and no boliviana`() {
        for (text in listOf("una empanada de queso", "empanada de queso")) {
            val tag = N11bTestEnv.resolve(text).single()
            assertNull(text, tag.foodItem)
            assertEquals(text, "empanada de queso", tag.tag)
            assertTrue(text, tag.hasMaterialQuestion())
            assertEquals(text, NutritionSourceKind.HEURISTIC_ESTIMATE, tag.nutritionSource)
        }
        val tag = N11bTestEnv.resolve("una empanada de queso").single()
        assertEquals(180.0, N11bTestEnv.grams(tag), 0.001)
        // the empanada profile: 232 kcal per 100 g, 418 kcal for 180 g, within 20 % of the 380 kcal of a published cheese empanada
        assertEquals(417.6, tag.loggedFood?.calories ?: Double.NaN, 1.0)
        val meal = N11bTestEnv.resolve("una empanada de pino al horno y una de queso")
        assertEquals(listOf("cl001", null), meal.map { it.foodItem?.id })
        assertEquals(listOf(450.0, 417.6), meal.map { it.loggedFood?.calories ?: Double.NaN }.map { Math.round(it * 10) / 10.0 })
        assertEquals(listOf(180.0, 180.0), meal.map { N11bTestEnv.grams(it) })
    }

    @Test
    fun `a dish of the protected lexicon is its own row or an estimate and a longer name is another dish`() {
        val boliviana = checkNotNull(BrandedSnackCatalog.buildProgrammaticCatalog().firstOrNull { it.id == "sn_bo_empanada_queso" })
        assertFalse(FoodIdentity.matchesDeclaredIdentity("empanada de queso", boliviana))
        assertFalse(FoodIdentity.matchesDeclaredIdentity("empanadas de queso", boliviana))
        // said with its qualifier, or as a word that is no protected dish, a longer name is the dish
        assertTrue(FoodIdentity.matchesDeclaredIdentity("empanada de queso boliviana", boliviana))
        // a name and its "(...)" qualifier are one dish; a dish with its own row is accepted as it was
        val tresLeches = checkNotNull(BrandedSnackCatalog.buildProgrammaticCatalog().firstOrNull { it.id == "sn_cr_tresleches" })
        assertTrue(FoodIdentity.matchesDeclaredIdentity("tres leches", tresLeches))
        assertTrue(FoodIdentity.matchesDeclaredIdentity("empanada de pino", checkNotNull(findStaticFoodById("cl001"))))
        assertTrue(FoodIdentity.matchesDeclaredIdentity("arroz con leche", checkNotNull(findStaticFoodById("cl023"))))
        assertTrue(FoodIdentity.matchesDeclaredIdentity("porotos con riendas", checkNotNull(findStaticFoodById("cl029"))))
        // a food that is no protected dish keeps matching the regional row that has it as its first words
        val tostones = BrandedSnackCatalog.buildProgrammaticCatalog().firstOrNull { it.name.startsWith("Tostones") }
        assertNotNull(tostones)
        assertTrue(FoodIdentity.matchesDeclaredIdentity("tostones", checkNotNull(tostones)))
    }

    // ─── The Greek yogurt ───────────────────────────────────────────────────────────

    @Test
    fun `yogur griego is the Greek yogurt row, with its pot and no question`() {
        for (text in listOf("yogur griego", "yogurt griego", "yogur griego natural")) {
            val tag = N11bTestEnv.resolve(text).single()
            assertEquals(text, "gen017", tag.foodItem?.id)
            assertFalse(text, tag.hasMaterialQuestion())
        }
        val meal = N11bTestEnv.resolve("un yogur griego con granola")
        assertEquals(listOf("gen017", "gen091"), meal.map { it.foodItem?.id })
        assertEquals(125.0, N11bTestEnv.grams(meal[0]), 0.001)
        assertEquals(121.0, meal[0].loggedFood?.calories ?: Double.NaN, 1.0)
        assertEquals(250.0, N11bTestEnv.grams(N11bTestEnv.resolve("2 yogures griegos").single()), 0.001)
        // the plain yogurt is not the Greek one
        assertEquals("gen087", N11bTestEnv.resolve("yogur natural").single().foodItem?.id)
    }
}
