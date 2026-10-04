package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.PortionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N8b, causes 1 and 2 of the blind corpus (#8, #12, #13, #20, #26, #40, #41, #46, #48, #54, #60): a dish of the catalog that is named with no amount is
 * eaten in the serving its row declares ("porotos con riendas" is its 350 g, not the 99 g of a side), the portions that a main plate gives to the rest of
 * its foods are the household ones (rice 190 g, chicken 125 g before the meal factor), a salad on a plate is a side, a cooked legume is a cup, a fat is a
 * spoonful, an asado is a serving of 150 g and a plate said without its article is a plate. Every number is written out next to its source.
 */
class DishServingPortionTest {

    private fun tags(text: String) = N11bTestEnv.resolve(text).filterNot { it.isExcluded }

    private fun grams(text: String): Double = N11bTestEnv.grams(tags(text).first())

    private fun gramsOf(text: String, tag: String): Double = N11bTestEnv.grams(tags(text).first { it.tag == tag })

    // ─── A dish with no amount is eaten in its serving ──────────────────────────────

    @Test
    fun `a dish named with no amount is eaten in the serving its row declares`() {
        // id, what is typed, the serving of the row in grams (the blind corpus #40 was 99 g: 90 g x 1.1 of a side)
        val dishes = listOf(
            Triple("cl029", "porotos con riendas", 350.0),
            Triple("cl003", "pastel de choclo", 250.0),
            Triple("cl019", "merluza frita", 150.0),
            Triple("cl015", "charquicán", 350.0),
            Triple("cl033", "humitas", 200.0),
            Triple("cl034", "pastel de papa", 250.0),
            Triple("cl037", "ave palta", 180.0),
            Triple("cl036", "sandwich de pavita", 150.0),
            Triple("cl028", "ensalada chilena", 150.0),
            Triple("cl007", "porotos granados", 350.0),
            Triple("cl008", "curanto", 400.0),
            Triple("cl023", "arroz con leche", 200.0),
            Triple("cl024", "leche asada", 150.0),
            Triple("cl021", "calzones rotos", 80.0),
        )
        for ((id, text, serving) in dishes) {
            val tag = tags(text).single()
            assertEquals(text, id, tag.foodItem?.id)
            assertEquals(text, serving, N11bTestEnv.grams(tag), 0.001)
            // the row's own energy for its own serving: no density is invented
            val row = checkNotNull(findStaticFoodById(id))
            assertEquals(text, row.calories, tag.loggedFood?.calories ?: Double.NaN, 1.0)
        }
    }

    @Test
    fun `a dish declared in millilitres is eaten as the grams of those millilitres`() {
        for ((id, text) in listOf("cl030" to "cazuela de vacuno", "cl031" to "cazuela de pollo", "cl004" to "cazuela", "cl005" to "mote con huesillo")) {
            val row = checkNotNull(findStaticFoodById(id))
            assertEquals("ml", row.unit)
            val tag = tags(text).single()
            assertEquals(text, id, tag.foodItem?.id)
            assertEquals(text, NutrientBasis.massForServingUnits(row, row.servingSize), N11bTestEnv.grams(tag), 0.001)
            assertEquals(text, row.calories, tag.loggedFood?.calories ?: Double.NaN, 1.0)
        }
    }

    @Test
    fun `a dish keeps its serving next to other foods, whatever the role of a word of its name`() {
        // "pollo" of the cazuela and "papa" of the pastel are no chicken and no starch of a plate
        val cazuela = checkNotNull(findStaticFoodById("cl031"))
        assertEquals(NutrientBasis.massForServingUnits(cazuela, cazuela.servingSize), gramsOf("cazuela de pollo y ensalada chilena", "cazuela de pollo"), 0.001)
        assertEquals(250.0, gramsOf("pastel de papa y un té", "pastel de papa"), 0.001)
        assertEquals(350.0, gramsOf("porotos con riendas y un té", "porotos con riendas"), 0.001)
        assertEquals(250.0, gramsOf("pastel de choclo y ensalada chilena", "pastel de choclo"), 0.001)
    }

    @Test
    fun `a salad that shares a main plate is a side and a lone one is its serving`() {
        // ensalada chilena: 150 g on its own (cl028), the 99 g (90 g x 1.1) of a side on a plate (blind corpus #48: 100 g)
        assertEquals(150.0, grams("ensalada chilena"), 0.001)
        assertEquals(99.0, gramsOf("arroz con pollo y ensalada chilena", "ensalada chilena"), 0.01)
        assertEquals(99.0, gramsOf("pastel de choclo y ensalada chilena", "ensalada chilena"), 0.01)
        // an estimate is the same: 150 g alone (blind corpus #26) and a side on a plate
        assertEquals(150.0, grams("ensalada de repollo"), 0.001)
        assertEquals(99.0, gramsOf("arroz con pollo y ensalada de repollo", "ensalada de repollo"), 0.01)
        // two of them are two servings
        assertEquals(300.0, grams("2 ensaladas de repollo"), 0.001)
    }

    @Test
    fun `the cheese in the name of a dish does not cap it`() {
        // blind corpus #54: "un pan con queso y tomate" was 40 g of cheese, the energy-dense cap of a snack
        val pan = checkNotNull(findStaticFoodById("cl026"))
        assertEquals(100.0, HouseholdPortions.capEnergyDenseGuess(pan, "pan con queso", 100.0, "side"), 0.0)
        assertEquals(100.0, HouseholdPortions.defaultGrams(pan, "pan con queso"), 0.0)
        val meal = tags("un pan con queso y tomate")
        assertEquals(listOf("cl026", "gen026"), meal.map { it.foodItem?.id })
        assertEquals(100.0, N11bTestEnv.grams(meal[0]), 0.001)
        assertEquals(40.0, N11bTestEnv.grams(meal[1]), 0.001)
        // the loose cheese is still capped
        assertEquals(40.0, HouseholdPortions.capEnergyDenseGuess(null, "queso", 150.0, "side"), 0.0)
    }

    // ─── The portions of a main plate ───────────────────────────────────────────────

    @Test
    fun `rice and chicken of a lunch plate are the household portions and not 242 and 154 g`() {
        // blind corpus #41, #48, #60: rice 242 g and chicken 154 g, 20 % above the 200 g and 130 g of the references. 190 g x 1.1 and 125 g x 1.1.
        val lunch = tags("arroz con pollo")
        assertEquals(209.0, N11bTestEnv.grams(lunch.first { it.tag == "arroz" }), 0.01)
        assertEquals(137.5, N11bTestEnv.grams(lunch.first { it.tag == "pollo" }), 0.01)
        // the meal factor still scales them: dinner x0.9
        val dinner = N11bTestEnv.resolveAt("arroz con pollo", MealType.DINNER)
        assertEquals(171.0, N11bTestEnv.grams(dinner.first { it.tag == "arroz" }), 0.01)
        assertEquals(112.5, N11bTestEnv.grams(dinner.first { it.tag == "pollo" }), 0.01)
        // and they stay within 25 % of the references in every context
        for (grams in listOf(209.0, 171.0)) assertTrue(grams in 150.0..250.0)
        for (grams in listOf(137.5, 112.5)) assertTrue(grams in 97.5..162.5)
    }

    @Test
    fun `a plate said without its article is the plate`() {
        // blind corpus #20: "plato grande de arroz" was 150 g, 1.25 x the rice of a serving, against the 296 g of the references
        val plate = grams("un plato de arroz")
        assertEquals(plate, grams("plato de arroz"), 0.001)
        assertEquals(grams("un plato grande de arroz"), grams("plato grande de arroz"), 0.001)
        assertEquals(1.25 * plate, grams("plato grande de arroz"), 0.1) // the engine rounds a plate to 0.1 g
        val item = parseMealDescription("plato grande de arroz").items.single()
        assertEquals("arroz", item.tag)
        assertEquals(PortionPreset.LARGE, item.portion)
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, item.amountIntent)
        // a small plate keeps its own scale (the portion engine has no small plate) and a plural is not one plate
        assertEquals(0.75 * grams("arroz"), grams("plato chico de arroz"), 0.001)
    }

    @Test
    fun `a cooked legume is a plate of a cup and a dry, raw or green one is not`() {
        // blind corpus #13 and #41: lentils were 120 g and 99 g, the cup of cooked lentils of USDA is 198 g (FDC 172421)
        for (text in listOf("lentejas", "un guiso de lentejas", "garbanzos", "porotos negros")) {
            val tag = tags(text).single()
            assertEquals(text, 198.0, N11bTestEnv.grams(tag), 0.001)
        }
        assertEquals(198.0, gramsOf("lentejas con arroz", "lenteja"), 0.001)
        assertEquals(229.7, tags("lentejas").single().loggedFood?.calories ?: Double.NaN, 0.5)
        val lentils = checkNotNull(findStaticFoodById("gen012"))
        assertEquals(198.0, HouseholdPortions.defaultGrams(lentils, "lentejas"), 0.0)
        assertTrue(HouseholdPortions.hasClassDefault(lentils, "lentejas"))
        // what is not a cooked plate: raw or dry, a green bean (a vegetable), a dish and a soup, and what was said
        val raw = checkNotNull(findStaticFoodById("gen012c"))
        assertNotEquals(198.0, HouseholdPortions.defaultGrams(raw, "lentejas crudas"), 0.0)
        assertNotEquals(198.0, HouseholdPortions.defaultGrams(null, "porotos negros secos"), 0.0)
        assertNotEquals(198.0, HouseholdPortions.defaultGrams(null, "porotos verdes"), 0.0)
        assertNotEquals(198.0, HouseholdPortions.defaultGrams(null, "sopa de lentejas"), 0.0)
        assertEquals(100.0, grams("100 g de lentejas"), 0.001)
    }

    @Test
    fun `a fat in a meal is a spoonful of 10 g and an asado is a serving of 150 g`() {
        // blind corpus #46: the butter of two marraquetas was 15 g, the energy-dense cap of a fat, against two pats of 5 g (USDA household weight)
        val butter = tags("me comí dos marraquetas con mantequilla").first { it.tag == "mantequilla" }
        assertEquals(10.0, N11bTestEnv.grams(butter), 0.001)
        assertEquals(71.7, butter.loggedFood?.calories ?: Double.NaN, 0.5)
        assertEquals(10.0, grams("mantequilla"), 0.001)
        // blind corpus #12: "asado" was 100 g; a serving of grilled beef rib is 150 g cooked (USDA FDC 168676), and the row is raw: converted once
        val asado = tags("asado").single()
        assertEquals("gen093c", asado.foodItem?.id)
        assertEquals(150.0, N11bTestEnv.grams(asado), 0.001)
        assertEquals(535.7, asado.loggedFood?.calories ?: Double.NaN, 1.0) // 150 g cooked / 0.7 = 214.3 g raw x 2.5 kcal/g
        assertEquals(200.0, grams("200 g de asado"), 0.001)
        assertEquals(150.0, FoodStapleOntology.householdDefaultGrams("asado de tira") ?: Double.NaN, 0.0)
    }

    @Test
    fun `papas a lo pobre are a plate of 250 g with the energy of its fried egg and its fried onion`() {
        // blind corpus #8: 242 g of boiled potato, 206 kcal, against the 630 kcal of 150 g of fries, a fried egg, fried onion and 5 g of oil
        val papas = tags("pollo a la plancha y papas a lo pobre").first { it.tag.contains("pobre") }
        assertNull(papas.foodItem)
        assertEquals(250.0, N11bTestEnv.grams(papas), 0.001)
        assertEquals(617.5, papas.loggedFood?.calories ?: Double.NaN, 1.0)
        assertEquals(250.0, grams("papas a lo pobre"), 0.001)
        // two plates would be 1235 kcal: the cap of an item without a kilogram (1200 kcal) takes the grams down
        val two = tags("2 papas a lo pobre").single()
        assertEquals(1200.0, two.loggedFood?.calories ?: Double.NaN, 1.0)
        assertTrue(N11bTestEnv.grams(two) < 500.0)
    }
}
