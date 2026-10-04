package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.findStaticFoodById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N8b, cause 3 of the blind corpus (#28, #50, #54): the parts of a sandwich built from loose foods weigh what they do inside it, not what the food
 * weighs on a plate. The expansion of N11b took the standalone default of each part: 100 g of pan, 100 g of jamón and 150 g of pollo, against the 60 g,
 * 40 g and 60 g of the references (two slices of sandwich bread, two of ham, thin slices of chicken); the cheese, one slice, was 30 g already.
 */
class SandwichPartPortionTest {

    private fun parts(text: String): List<Pair<String, Double>> =
        N11bTestEnv.resolve(text).filterNot { it.isExcluded }.map { it.tag to N11bTestEnv.grams(it) }

    @Test
    fun `the bread, the ham and the chicken of a sandwich are two slices, two slices and thin slices`() {
        assertEquals(listOf("pan" to 60.0, "jamón" to 40.0, "queso" to 30.0), parts("un sandwich de jamón y queso"))
        assertEquals(listOf("pan" to 60.0, "pollo" to 60.0), parts("un sandwich de pollo"))
        assertEquals(listOf("pan" to 60.0, "jamón" to 40.0, "queso" to 30.0, "palta" to 60.0), parts("sandwich de jamón, queso y palta"))
    }

    @Test
    fun `blind corpus 28 keeps its cola next to the sandwich and the whole is about 470 kcal`() {
        val tags = N11bTestEnv.resolve("un sandwich de jamón y queso y una coca cola")
        assertEquals(listOf("pan", "jamón", "queso", "coca cola"), tags.map { it.tag })
        assertEquals(listOf(60.0, 40.0, 30.0, 350.0), tags.map { N11bTestEnv.grams(it) })
        // bread 159 + ham 43 + cheese 121 + cola 147: 470 kcal (it was 640)
        assertEquals(470.0, N11bTestEnv.kcal(tags), 3.0)
    }

    @Test
    fun `a sauce is still a spoonful and an egg or a prepared dish keeps its piece`() {
        val mayo = N11bTestEnv.resolve("un sandwich de pollo, palta y mayo").last()
        assertEquals("mayo", mayo.tag)
        assertTrue("${N11bTestEnv.grams(mayo)} g", N11bTestEnv.grams(mayo) <= 15.0)
        assertEquals(50.0, N11bTestEnv.grams(N11bTestEnv.resolve("un sandwich de huevo").last()), 0.001)
        // the sandwich that the catalog has a row for is not taken apart: its serving is the whole sandwich
        val avePalta = N11bTestEnv.resolve("un ave palta").single()
        assertEquals("cl037", avePalta.foodItem?.id)
        assertEquals(180.0, N11bTestEnv.grams(avePalta), 0.001)
    }

    @Test
    fun `the portion of a part by what it is`() {
        fun grams(name: String, bread: Boolean = false) = HouseholdPortions.sandwichPartGrams(findFoodExactByNormalized(name), name, bread)
        assertEquals(60.0, HouseholdPortions.sandwichPartGrams(null, "pan", isBread = true) ?: Double.NaN, 0.0)
        for (name in listOf("jamón", "salame", "mortadela", "pavo", "fiambre")) assertEquals(name, 40.0, grams(name) ?: Double.NaN, 0.0)
        for (name in listOf("queso", "queso gouda", "queso mantecoso")) assertEquals(name, 30.0, grams(name) ?: Double.NaN, 0.0)
        for (name in listOf("pollo", "carne", "atún", "salmón")) assertEquals(name, 60.0, grams(name) ?: Double.NaN, 0.0)
        assertEquals(60.0, grams("palta") ?: Double.NaN, 0.0)
        for (name in listOf("tomate", "lechuga", "cebolla")) assertEquals(name, 40.0, grams(name) ?: Double.NaN, 0.0)
        // what has a piece or a serving of its own is left to the caller
        assertNull(grams("huevo"))
        assertNull(HouseholdPortions.sandwichPartGrams(findStaticFoodById("cl037"), "ave palta", false))
    }

    @Test
    fun `a bread with a filling that has a row of its own is its serving and not a bread plus a filling`() {
        // blind corpus #50: "pan con palta" stays the 120 g of its row
        val tags = N11bTestEnv.resolve("pan con palta y un té")
        assertEquals("cl025", tags.first().foodItem?.id)
        assertEquals(120.0, N11bTestEnv.grams(tags.first()), 0.001)
    }
}
