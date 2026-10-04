package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N8b, cause 6 (the leftovers of N11b): a flan, a mousse and a budín are a custard of 145 kcal per 100 g (the USDA flan, FDC 167574), no cake of 310; a cola next to a plate keeps its
 * can; manjar is a spoonful of 20 g, not the 200 g of the milk its name holds; "hotdog" is the hot dog, and "mayo" is the mayonnaise row and not an estimate of
 * a generic dish (24 kcal for 15 g of mayonnaise, which is 102).
 */
class CustardSpreadAndDrinkPortionTest {

    private fun tags(text: String) = N11bTestEnv.resolve(text).filterNot { it.isExcluded }

    private fun kcal(text: String): Double = N11bTestEnv.kcal(tags(text))

    @Test
    fun `a cup of flan, mousse or budin is 174 kcal and no longer 372`() {
        // 120 g of the cup (N11b) x 1.45 kcal per gram; with the dessert profile a flan was 372 kcal and a mousse of chocolate 456
        for (text in listOf("flan", "un flan", "mousse de chocolate", "un budín", "budín de pan")) {
            val tag = tags(text).single()
            assertNull(text, tag.foodItem)
            assertEquals(text, 120.0, N11bTestEnv.grams(tag), 0.001)
            assertEquals(text, 174.0, tag.loggedFood?.calories ?: Double.NaN, 0.5)
        }
        assertEquals(348.0, kcal("dos flanes"), 0.5)
        // the other desserts are the dessert profile still: a slice of 100 g of a pie is 310 kcal
        assertEquals(310.0, kcal("pie de manzana"), 0.5)
    }

    @Test
    fun `a cola next to a plate keeps its can`() {
        // N11b gave the can next to a sandwich or a snack; "un completo y una coca cola" had no shape and logged the 100 g of the cola row (42 kcal)
        for (text in listOf("un completo y una coca cola", "una hamburguesa y una coca cola", "una pizza y una coca cola", "un completo italiano y una coca cola")) {
            val meal = tags(text)
            assertEquals(text, "off_coca_cola_original_350ml", meal.last().foodItem?.id)
            assertEquals(text, 350.0, N11bTestEnv.grams(meal.last()), 0.001)
        }
        assertEquals(220.0, N11bTestEnv.grams(tags("un completo y una coca cola").first()), 0.001)
        // alone it was the can already
        assertEquals(350.0, N11bTestEnv.grams(tags("una coca cola").single()), 0.001)
    }

    @Test
    fun `manjar is a spoonful of 20 g and dulce de leche too`() {
        for (text in listOf("manjar", "dulce de leche")) {
            val tag = tags(text).single()
            assertEquals(text, "gen109", tag.foodItem?.id)
            assertEquals(text, 20.0, N11bTestEnv.grams(tag), 0.001)
        }
        assertEquals(60.0, kcal("manjar"), 1.0) // 3 kcal per gram: it was 600 for the 200 g of the milk of its name
        // a cake of manjar is not a spread
        assertNotEquals(20.0, N11bTestEnv.grams(tags("torta de manjar").single()), 0.001)
        assertEquals(20.0, HouseholdPortions.defaultGrams(null, "manjar"), 0.0)
        assertEquals(100.0, HouseholdPortions.defaultGrams(null, "torta de manjar"), 0.0)
    }

    @Test
    fun `hotdog in one word is the hot dog of two`() {
        val one = tags("un hotdog").single()
        val two = tags("un hot dog").single()
        assertNull(one.foodItem)
        assertNull(two.foodItem)
        assertEquals(N11bTestEnv.grams(two), N11bTestEnv.grams(one), 0.0)
        assertEquals(180.0, N11bTestEnv.grams(one), 0.001)
        assertEquals(two.loggedFood?.calories ?: Double.NaN, one.loggedFood?.calories ?: Double.NaN, 0.001)
        // the sausage alone is still the sausage row
        assertEquals("gen095", tags("vienesa").single().foodItem?.id)
    }

    @Test
    fun `mayo is the mayonnaise row`() {
        val mayo = tags("mayo").single()
        assertEquals("gen065", mayo.foodItem?.id)
        assertEquals(10.0, N11bTestEnv.grams(mayo), 0.001)
        assertEquals(68.0, mayo.loggedFood?.calories ?: Double.NaN, 1.0)
        // in a sandwich it is a spoonful of 15 g, 102 kcal: the estimate of a generic dish gave 24
        val sandwich = tags("un sandwich de pollo, palta y mayo").last()
        assertEquals("gen065", sandwich.foodItem?.id)
        assertEquals(15.0, N11bTestEnv.grams(sandwich), 0.001)
        assertEquals(102.0, sandwich.loggedFood?.calories ?: Double.NaN, 1.0)
        assertTrue(kcal("papas con mayo") > 0.0)
    }
}
