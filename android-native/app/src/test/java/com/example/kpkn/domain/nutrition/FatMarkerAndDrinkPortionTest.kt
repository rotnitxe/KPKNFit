package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N11b: a fat marker ("mayo", "mantequilla", "aceite") is a whole word and never caps a prepared dish of the catalog that has it in its
 * name (the "Ave mayo" row is a sandwich of 180 g, and in "ave mayo y un jugo" it was logged as 15 g), and the cup of a drinks-only meal
 * is the portion of the drinks of it, not of every food (so is the 40 g of a filling in a sandwich).
 */
class FatMarkerAndDrinkPortionTest {

    private val aveMayo: FoodItem = requireNotNull(findFoodExactByNormalized("ave mayo"))
    private val mayonesa: FoodItem = requireNotNull(findFoodExactByNormalized("mayonesa"))

    private fun cap(food: FoodItem?, query: String, grams: Double) = HouseholdPortions.capEnergyDenseGuess(food, query, grams, "side")

    // ─── The fat marker ─────────────────────────────────────────────────────────────

    @Test
    fun `a fat marker is a whole word`() {
        assertEquals(200.0, cap(null, "porcion mayor", 200.0), 0.0)
        assertEquals(15.0, cap(null, "mayo", 100.0), 0.0)
        assertEquals(15.0, cap(null, "aceite de oliva", 100.0), 0.0)
        assertEquals(15.0, cap(null, "mantequilla", 100.0), 0.0)
        assertEquals(15.0, cap(mayonesa, "mayonesa", 100.0), 0.0)
    }

    @Test
    fun `a prepared dish that has a fat in its name keeps its serving, and a fat row stays a fat`() {
        assertEquals("gen185", aveMayo.id)
        assertEquals(180.0, cap(aveMayo, "ave mayo", 180.0), 0.0)
        assertEquals(180.0, HouseholdPortions.defaultGrams(aveMayo, "ave mayo"), 0.0)
        // a row that is only its 100 g denominator, and a spread that declares a serving, are fats whatever else is in their names
        assertEquals(10.0, HouseholdPortions.defaultGrams(mayonesa, "mayonesa"), 0.0)
        assertEquals(15.0, cap(requireNotNull(findStaticFoodById("gen119")), "mantequilla de almendras", 100.0), 0.0)
    }

    @Test
    fun `ave mayo with a juice is the sandwich of 180 g and the juice a glass`() {
        // probe: "ave mayo y un jugo" was 15 g (44 kcal) and "un ave mayo y un jugo" 180 g
        for (text in listOf("ave mayo y un jugo", "ave mayo y jugo", "un ave mayo y un jugo", "un sandwich de ave mayo y un jugo")) {
            val tags = N11bTestEnv.resolve(text)
            assertEquals(text, "gen185", tags.first().foodItem?.id)
            assertEquals(text, 180.0, N11bTestEnv.grams(tags.first()), 0.001)
            assertEquals(text, N11bTestEnv.grams(N11bTestEnv.resolve("un jugo").single()), N11bTestEnv.grams(tags.last()), 0.001)
        }
    }

    @Test
    fun `the condiments themselves are still a spoonful`() {
        assertEquals(10.0, N11bTestEnv.grams(N11bTestEnv.resolve("mayonesa").single()), 0.001)
        assertTrue(N11bTestEnv.grams(N11bTestEnv.resolve("mayo").single()) <= 15.0)
        assertEquals(10.0, N11bTestEnv.grams(N11bTestEnv.resolve("mantequilla").single()), 0.001)
        assertEquals(10.0, N11bTestEnv.grams(N11bTestEnv.resolve("aceite").single()), 0.001)
    }

    // ─── Drinks ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a drink is named by a drink word and milk by being the first word`() {
        for (name in listOf("jugo de naranja", "café con leche", "coca cola", "una bebida", "leche descremada", "capuchino", "té", "agua con limón", "batido de plátano")) {
            assertTrue(name, HouseholdPortions.isDrinkName(name))
        }
        for (name in listOf("dulce de leche", "arroz con leche", "tres leches", "atún al agua", "atún en agua", "tomate", "manjar", "pollo")) {
            assertFalse(name, HouseholdPortions.isDrinkName(name))
        }
    }

    @Test
    fun `the cup is for the drinks of a drinks-only meal and every other food keeps its portion`() {
        // each of these foods was weighed as a 220 g cup: 856 kcal of oats, 851 kcal of sugar, 220 g of lemon
        assertEquals(30.0, N11bTestEnv.grams(N11bTestEnv.resolve("granola con leche").first()), 0.001)
        val avena = N11bTestEnv.resolve("avena con leche y plátano")
        assertEquals(40.0, N11bTestEnv.grams(avena.first()), 0.001)
        assertEquals(120.0, N11bTestEnv.grams(avena.last()), 0.001)
        assertTrue(N11bTestEnv.grams(N11bTestEnv.resolve("café con leche y azúcar").last()) <= 40.0)
        assertTrue(N11bTestEnv.grams(N11bTestEnv.resolve("té con limón").last()) <= 100.0)
        // probe #50: the dish of the catalog keeps the 120 g of its serving
        val pan = N11bTestEnv.resolve("pan con palta y un té")
        assertEquals("cl025", pan.first().foodItem?.id)
        assertEquals(120.0, N11bTestEnv.grams(pan.first()), 0.001)
        // the drinks themselves are the cup or the glass they were
        assertEquals(220.0, N11bTestEnv.grams(N11bTestEnv.resolve("un té").single()), 0.001)
        assertEquals(220.0, N11bTestEnv.grams(N11bTestEnv.resolve("leche").single()), 0.001)
        assertEquals(250.0, N11bTestEnv.grams(N11bTestEnv.resolve("jugo de naranja").single()), 0.001)
    }

    @Test
    fun `a branded drink next to a plate is its can and a food in water is no drink`() {
        val cola = N11bTestEnv.resolve("arroz con pollo y una coca cola").last()
        assertEquals("coca cola", cola.tag)
        assertEquals(350.0, N11bTestEnv.grams(cola), 0.001)
        assertEquals(100.0, N11bTestEnv.grams(N11bTestEnv.resolve("atún al agua").single()), 0.001)
        assertTrue(N11bTestEnv.grams(N11bTestEnv.resolve("pan y atún al agua").last()) <= 100.0)
    }
}
