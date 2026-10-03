package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N11b: a dessert or a hot piece with no row of its own weighs the piece that is eaten, not the generic plate of 350 g (or the
 * 250 g of a "torta"): a pie of 350 g came to 1085 kcal once the dessert profile had its right density.
 */
class DessertPiecePortionTest {

    private fun dish(name: String): Double = HouseholdPortions.heuristicDishGrams(name)

    private fun assertGrams(expected: Double, vararg names: String) {
        for (name in names) assertEquals(name, expected, dish(name), 0.0)
    }

    // ─── The table ──────────────────────────────────────────────────────────────────

    @Test
    fun `a slice of a cake or a pie is 100 g`() {
        assertGrams(
            100.0,
            "torta", "tortas", "torta de chocolate", "torta tres leches", "pie", "pies", "pie de manzana", "pie de limón",
            "kuchen", "kuchenes", "mil hojas", "milhojas", "cheesecake", "chesecake", "cheesecake de frambuesa", "tres leches",
        )
    }

    @Test
    fun `a queque, a brownie and an alfajor are the small pieces they are`() {
        assertGrams(70.0, "queque", "queques", "queque de naranja")
        assertGrams(60.0, "brownie", "brownies", "brownie de chocolate")
        assertGrams(45.0, "alfajor", "alfajores", "alfajor de maicena")
    }

    @Test
    fun `a cup of flan, mousse or pudding is 120 g`() {
        assertGrams(120.0, "flan", "flanes", "flan de caramelo", "mousse", "mousse de chocolate", "budín", "budín de pan", "budin")
    }

    @Test
    fun `a helado with no row is 100 g and an iced drink is no helado`() {
        assertGrams(100.0, "helado", "helados", "helado de pistacho y chocolate blanco")
        for (drink in listOf("té helado", "café helado", "jugo helado", "un te helado")) {
            assertNull(drink, HouseholdPortions.pieceGrams(FoodIdentity.normalize(drink)))
        }
        assertEquals(100.0, dish("helado de té"), 0.0)
    }

    @Test
    fun `a completo is 220 g and a hot dog or a choripan is 180 g`() {
        assertGrams(220.0, "completo", "completos", "completo italiano", "completo palta", "plato completo")
        assertGrams(180.0, "hot dog", "hot dogs", "hotdog", "perro caliente", "choripán", "choripan", "choripanes")
    }

    @Test
    fun `what is not a piece keeps the generic dish and a keyword is a whole word`() {
        assertEquals(HouseholdPortions.HEURISTIC_DISH_GRAMS, dish("xyz plato"), 0.0)
        assertEquals(250.0, dish("sandwich"), 0.0)
        assertEquals(250.0, dish("hamburguesa"), 0.0)
        // tortilla holds "torta" no more than pierna holds "pie" or compra holds "pan"
        for (name in listOf("tortilla", "pierna", "compota", "alfajorcito")) assertNull(name, HouseholdPortions.pieceGrams(name))
    }

    // ─── The meal ───────────────────────────────────────────────────────────────────

    @Test
    fun `a pie of apple lands near 300 kcal whether it is counted or not`() {
        for (text in listOf("pie de manzana", "un pie de manzana", "un pie", "pie de limón")) {
            val tag = N11bTestEnv.resolve(text).single()
            assertNull(text, tag.foodItem)
            assertEquals(text, 100.0, N11bTestEnv.grams(tag), 0.001)
            assertTrue("$text: ${tag.loggedFood?.calories}", (tag.loggedFood?.calories ?: 0.0) in 250.0..400.0)
        }
    }

    @Test
    fun `a slice that was said keeps the grams that were said`() {
        assertEquals(60.0, N11bTestEnv.grams(N11bTestEnv.resolve("un trozo de torta de chocolate").single()), 0.001)
        assertEquals(60.0, N11bTestEnv.grams(N11bTestEnv.resolve("un trozo de torta tres leches").single()), 0.001)
        assertEquals(150.0, N11bTestEnv.grams(N11bTestEnv.resolve("una porción de pie de limón").single()), 0.001)
    }

    @Test
    fun `a torta and a brownie are no longer a plate`() {
        assertEquals(100.0, N11bTestEnv.grams(N11bTestEnv.resolve("torta de chocolate").single()), 0.001)
        val brownie = N11bTestEnv.resolve("un brownie").single()
        assertEquals(60.0, N11bTestEnv.grams(brownie), 0.001)
        assertEquals(3.10 * 60.0, brownie.loggedFood?.calories ?: 0.0, 1.0)
        assertEquals(120.0, N11bTestEnv.grams(N11bTestEnv.resolve("un flan").single()), 0.001)
    }

    @Test
    fun `a completo and the other hot pieces are one whole piece`() {
        assertEquals(220.0, N11bTestEnv.grams(N11bTestEnv.resolve("completo palta").single()), 0.001)
        // probe #51: the completo that has no mayonnaise has no row, and it was 220 g before this change as well
        val sinMayo = N11bTestEnv.resolve("un completo italiano sin mayo")
        assertEquals(220.0, N11bTestEnv.grams(sinMayo.first()), 0.001)
        assertEquals(418.0, sinMayo.first().loggedFood?.calories ?: 0.0, 1.0)
        for ((text, grams) in listOf("hot dog" to 180.0, "un hot dog" to 180.0, "un perro caliente" to 180.0, "choripán" to 180.0, "un choripán" to 180.0)) {
            assertEquals(text, grams, N11bTestEnv.grams(N11bTestEnv.resolve(text).single()), 0.001)
        }
    }

    @Test
    fun `an estimate that is counted is that many pieces and a fraction is not a slice`() {
        assertEquals(120.0, N11bTestEnv.grams(N11bTestEnv.resolve("dos brownies").single()), 0.001)
        // the parser read "un hot dog" with the unit weight of the vienesa it found first (50 g)
        assertEquals(360.0, N11bTestEnv.grams(N11bTestEnv.resolve("dos hot dogs").single()), 0.001)
        assertEquals(360.0, N11bTestEnv.grams(N11bTestEnv.resolve("2 choripanes").single()), 0.001)
        // "medio pie" is half of a pie, not half of a slice: the count of a fraction is not read
        assertEquals(100.0, N11bTestEnv.grams(N11bTestEnv.resolve("medio pie").single()), 0.001)
    }

    // ─── A row that is only its denominator ─────────────────────────────────────────

    private fun perHundredRow(name: String, unit: String = "g") = FoodItem(
        id = "t_" + name.lowercase().replace(' ', '_'), name = name, brand = "Genérico", servingSize = 100.0, unit = unit,
        calories = 390.0, protein = 6.0, carbs = 52.0, fats = 16.0, nutritionBasis = "PER_100G_AS_SOLD", source = "KPKN Curated (test)",
    )

    @Test
    fun `a dessert row that is only its 100 g denominator weighs the piece, not 100 g`() {
        assertEquals(45.0, HouseholdPortions.defaultGrams(perHundredRow("Alfajor casero"), "un alfajor"), 0.0)
        assertEquals(100.0, HouseholdPortions.defaultGrams(perHundredRow("Helado"), "helado"), 0.0)
        assertEquals(100.0, HouseholdPortions.defaultGrams(perHundredRow("Kuchen"), "un kuchen"), 0.0)
        // a row that declares a piece keeps it: the queque of the catalog is a piece of 70 g
        val queque = requireNotNull(findFoodExactByNormalized("queque"))
        assertEquals(70.0, HouseholdPortions.defaultGrams(queque, "un queque"), 0.0)
        // and a food that is no piece is not touched
        assertEquals(100.0, HouseholdPortions.defaultGrams(perHundredRow("Vegetal raro"), "vegetal raro"), 0.0)
    }

    @Test
    fun `the count of pieces is read from two and up and only for an estimate or a denominator row`() {
        assertEquals(60.0, HouseholdPortions.countedUnitGrams(null, "brownies", 2.0) ?: Double.NaN, 0.0)
        assertTrue(HouseholdPortions.countAppliesTo(null, "brownies", 2.0))
        assertTrue(HouseholdPortions.countAppliesTo(perHundredRow("Helado"), "helados", 3.0))
        assertEquals(false, HouseholdPortions.countAppliesTo(null, "brownie", 1.0))
        assertEquals(false, HouseholdPortions.countAppliesTo(null, "pie", 0.5))
        // the queque is countable by its own unit: that path is the same as before
        assertNotNull(HouseholdPortions.countedUnitGrams(requireNotNull(findFoodExactByNormalized("queque")), "queques", 2.0))
        // nothing else gained a unit weight: nuts, dishes and drinks are as they were
        assertNull(HouseholdPortions.countedUnitGrams(null, "almendras", 2.0))
        assertNull(HouseholdPortions.countedUnitGrams(null, "sandwich de pollo", 2.0))
    }

    @Test
    fun `two helados of the catalog are two portions of its row`() {
        val one = N11bTestEnv.resolve("un helado").single()
        val two = N11bTestEnv.resolve("2 helados de vainilla").single()
        assertEquals("gen193", two.foodItem?.id)
        assertEquals(2.0 * N11bTestEnv.grams(one), N11bTestEnv.grams(two), 0.001)
    }

    @Test
    fun `a dessert next to a sandwich is the slice and not the 40 g of a filling`() {
        val pie = N11bTestEnv.resolve("un sandwich de jamón y queso y pie de limón").last()
        assertEquals("pie de limón", pie.tag)
        assertNull(pie.foodItem)
        assertEquals(100.0, N11bTestEnv.grams(pie), 0.001)
    }

    @Test
    fun `a dessert in a meal is the piece and not the filling of the plate`() {
        val tags = N11bTestEnv.resolve("almorcé arroz con pollo y de postre un pie de manzana")
        val pie = tags.last()
        assertNull(pie.foodItem)
        assertTrue("${N11bTestEnv.grams(pie)} g", N11bTestEnv.grams(pie) in 60.0..120.0)
    }
}
