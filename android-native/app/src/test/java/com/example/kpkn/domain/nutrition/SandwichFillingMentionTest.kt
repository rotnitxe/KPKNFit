package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N11b: a sandwich with one filling, or with a list of them, is expanded inside its own mention like the one with two (WP-N11). The
 * single filling of "un sandwich de queso y un jugo" was left to the estimate of a whole dish, 250 g of cheese (987 kcal with the juice),
 * and the list "de jamón, queso y palta" was estimated as a ham sandwich plus a cheese and an avocado that were counted again.
 */
class SandwichFillingMentionTest {

    private fun mention(text: String): String? = FoodCombinationParser.sandwichMention(text)

    private fun names(description: String): List<String> = N11bTestEnv.resolve(description).map { it.tag }

    // ─── The mention ────────────────────────────────────────────────────────────────

    @Test
    fun `one filling ends before a quantified mention, a drink, an addition or punctuation`() {
        assertEquals("sandwich de queso", mention("1 sandwich de queso y 1 jugo"))
        assertEquals("sandwich de jamón", mention("sandwich de jamón y un jugo"))
        assertEquals("sandwich de ave mayo", mention("1 sandwich de ave mayo y 1 jugo"))
        assertEquals("sándwich de ave mayo", mention("1 sándwich de ave mayo"))
        assertEquals("sandwich de queso", mention("sandwich de queso, café, manzana"))
        assertEquals("sandwich de queso", mention("sandwich de queso y té"))
        assertEquals("sandwich de queso", mention("sandwich de queso con café"))
        assertEquals("sandwich de queso", mention("sandwich de queso además 1 jugo"))
        assertEquals("sandwich de queso", mention("sandwich de queso. 1 jugo"))
        assertEquals("sandwich de queso", mention("sandwich de queso; 1 jugo"))
        assertEquals("sandwich de queso", mention("sandwich de queso, 2 galletas"))
        assertEquals("sandwich de queso", mention("sandwich de queso, y una manzana"))
    }

    @Test
    fun `a list of fillings is one mention, whichever way it is separated`() {
        assertEquals("sandwich de jamón, queso y palta", mention("sandwich de jamón, queso y palta"))
        assertEquals("sandwich de pollo, palta y mayo", mention("1 sandwich de pollo, palta y mayo, 1 jugo"))
        assertEquals("sandwich de lomo con palta", mention("1 sandwich de lomo con palta y 1 jugo"))
        assertEquals("sandwich de lomo, tomate y queso", mention("sandwich de lomo, tomate y queso y 1 coca cola"))
        assertEquals("sandwich de mortadela y palta y queso", mention("sandwich de mortadela y palta y queso"))
    }

    @Test
    fun `a known dish still ends where its name ends`() {
        assertEquals("sandwich de jamón y queso", mention("sandwich de jamón y queso y palta"))
        assertEquals("sandwich de jamón y queso", mention("1 sandwich de jamón y queso y 1 coca cola"))
        assertEquals("sándwich de pollo con mayonesa", mention("sándwich de pollo con mayonesa y 1 coca cola"))
    }

    @Test
    fun `every sandwich of a description is a mention`() {
        assertEquals(listOf("sandwich de jamón", "sandwich de queso"), FoodCombinationParser.sandwichMentions("un sandwich de jamón y otro sandwich de queso"))
        assertEquals(
            listOf("sandwich de queso", "sandwich de jamón y queso"),
            FoodCombinationParser.sandwichMentions("sandwich de queso y 1 jugo y 1 sandwich de jamón y queso"),
        )
        assertEquals(emptyList<String>(), FoodCombinationParser.sandwichMentions("pan con palta y un té"))
        assertEquals(emptyList<String>(), FoodCombinationParser.sandwichMentions("sandwich"))
        assertEquals(emptyList<String>(), FoodCombinationParser.sandwichMentions("sandwich de 2 huevos"))
    }

    @Test
    fun `the parse of a mention is the bread and each of its fillings`() {
        val list = FoodCombinationParser.parse("sandwich de jamón, queso y palta")
        assertEquals("pan", list.baseFood)
        assertEquals(listOf("jamón", "queso", "palta"), list.accompaniments.map { it.food })
        assertTrue(list.confidence >= 0.70)
        val one = FoodCombinationParser.parse("sandwich de queso")
        assertEquals("pan", one.baseFood)
        assertEquals(listOf("queso"), one.accompaniments.map { it.food })
        val con = FoodCombinationParser.parse("sandwich de lomo con palta")
        assertEquals(listOf("lomo", "palta"), con.accompaniments.map { it.food })
    }

    // ─── The tags ───────────────────────────────────────────────────────────────────

    @Test
    fun `a sandwich of one filling and a drink is the bread, the filling and the drink`() {
        val tags = N11bTestEnv.resolve("un sandwich de queso y un jugo")
        assertEquals(listOf("pan", "queso", "jugo"), tags.map { it.tag })
        assertEquals(listOf("gen019", "gen047", "gen148"), tags.map { it.foodItem?.id })
        assertEquals(N11bTestEnv.grams(N11bTestEnv.resolve("un jugo").single()), N11bTestEnv.grams(tags.last()), 0.001)
        assertTrue("${N11bTestEnv.kcal(tags)} kcal", N11bTestEnv.kcal(tags) < 600.0)
        assertEquals(listOf("pan", "jamón", "jugo"), names("sandwich de jamón y un jugo"))
        assertEquals(listOf("pan", "queso"), names("un sandwich de queso"))
        assertEquals(listOf("pan", "jamón"), names("un sandwich de jamón"))
        assertEquals(listOf("jugo", "pan", "queso"), names("un jugo y un sandwich de queso"))
    }

    @Test
    fun `a list of fillings is the bread and each filling once`() {
        for (text in listOf("sandwich de jamón, queso y palta", "un sandwich de jamón, queso y palta")) {
            val tags = N11bTestEnv.resolve(text)
            assertEquals(text, listOf("pan", "jamón", "queso", "palta"), tags.map { it.tag })
            // every part is a row of the catalog: no estimate of a whole sandwich and no filling counted twice
            assertTrue("every part is a row of the catalog: $text", tags.all { it.foodItem != null })
            // WP-N8b: each part weighs what it does in a sandwich: bread 60 g (159 kcal) + ham 40 g (43) + cheese 30 g (121) + avocado 60 g (96) = 419 kcal;
            // with the 100 g of bread and the 100 g of ham of their standalone defaults it was 550-700.
            assertTrue("${N11bTestEnv.kcal(tags)} kcal", N11bTestEnv.kcal(tags) in 380.0..480.0)
        }
        assertEquals(listOf("pan", "jamón", "queso", "palta", "jugo"), names("un sandwich de jamón, queso y palta y un jugo"))
        assertEquals(listOf("pan", "jamón", "queso", "palta", "jugo"), names("un sandwich de jamón, queso y palta, un jugo"))
        assertEquals(listOf("té", "pan", "jamón", "queso", "palta"), names("tomé un té y un sandwich de jamón, queso y palta"))
        assertEquals(listOf("pan", "lomo", "palta", "jugo"), names("un sandwich de lomo con palta y un jugo"))
    }

    @Test
    fun `what is said after a comma and is a drink or a fruit is not a filling`() {
        assertEquals(listOf("pan", "queso", "café", "manzana"), names("un sandwich de queso, café, manzana"))
        assertEquals(listOf("pan", "queso", "café"), names("sandwich de queso, café"))
        assertEquals(listOf("pan", "jamón", "queso", "café"), names("sandwich de jamón y queso y café"))
    }

    @Test
    fun `every sandwich of a description is expanded`() {
        assertEquals(listOf("pan", "jamón", "pan", "queso"), names("un sandwich de jamón y un sandwich de queso"))
    }

    @Test
    fun `a mayo that is only a filling is not the sandwich ave mayo`() {
        val tags = N11bTestEnv.resolve("un sandwich de pollo, palta y mayo")
        assertEquals(listOf("pan", "pollo", "palta", "mayo"), tags.map { it.tag })
        val mayo = tags.last()
        assertTrue("${mayo.foodItem?.id}", mayo.foodItem?.id != "gen185")
        assertTrue("${N11bTestEnv.grams(mayo)} g", N11bTestEnv.grams(mayo) <= 15.0)
        assertTrue("${N11bTestEnv.kcal(tags)} kcal", N11bTestEnv.kcal(tags) < 800.0)
        // the sauce of a known dish is a spoonful too
        val ketchup = N11bTestEnv.resolve("sandwich de pollo con ketchup").last()
        assertTrue("${N11bTestEnv.grams(ketchup)} g", N11bTestEnv.grams(ketchup) <= 30.0)
    }

    @Test
    fun `a filling that is a prepared dish is no ingredient`() {
        val tags = N11bTestEnv.resolve("sandwich de ave y palta")
        assertEquals(listOf("pan", "ave", "palta"), tags.map { it.tag })
        assertTrue(tags.none { it.foodItem?.id == "gen185" || it.foodItem?.id == "cl037" })
        // "ave palta" is itself the sandwich: its bread is in it, so the mention is not taken apart
        val dish = N11bTestEnv.resolve("un sandwich de ave palta y un jugo")
        assertTrue(dish.none { it.tag == "pan" })
        assertEquals(2, dish.size)
    }

    @Test
    fun `a sandwich that has a row of its own is that row and keeps its serving`() {
        val luco = N11bTestEnv.resolve("un sandwich de barros luco y un jugo")
        assertEquals(listOf("gen184", "gen148"), luco.map { it.foodItem?.id })
        assertTrue(luco.none { it.tag == "pan" })
        val aveMayo = N11bTestEnv.resolve("un sandwich de ave mayo y un jugo")
        assertEquals("gen185", aveMayo.first().foodItem?.id)
        assertEquals(180.0, N11bTestEnv.grams(aveMayo.first()), 0.001)
        val pavita = N11bTestEnv.resolve("un sandwich de pavita y una manzana")
        assertEquals("cl036", pavita.first().foodItem?.id)
        assertEquals(150.0, N11bTestEnv.grams(pavita.first()), 0.001)
    }

    @Test
    fun `a sandwich whose row the resolver accepts for the whole mention is not taken apart`() {
        val row = FoodItem(
            id = "t_sandwich_lomito", name = "Sándwich de lomito de la casa", servingSize = 200.0, unit = "g", calories = 250.0,
            protein = 14.0, carbs = 24.0, fats = 11.0, tags = listOf("preparacion"), searchAliases = listOf("sandwich de lomito"),
        )
        val tags = N11bTestEnv.resolveWith("un sandwich de lomito y un jugo", row)
        assertEquals("t_sandwich_lomito", tags.first().foodItem?.id)
        assertTrue(tags.none { it.tag == "pan" })
        // the same words with no such row are the bread and the filling
        assertEquals(listOf("pan", "lomito", "jugo"), names("un sandwich de lomito y un jugo"))
    }

    @Test
    fun `a drink next to a sandwich keeps its own portion`() {
        // probe #28: the cola of the supermarket was logged as 40 g and 17 kcal
        val cola = N11bTestEnv.resolve("un sandwich de jamón y queso y una coca cola")
        assertEquals(listOf("pan", "jamón", "queso", "coca cola"), cola.map { it.tag })
        assertEquals(350.0, N11bTestEnv.grams(cola.last()), 0.001)
        assertEquals(147.0, cola.last().loggedFood?.calories ?: 0.0, 1.0)
        val te = N11bTestEnv.resolve("un sandwich de jamón y queso y un té").last()
        assertEquals(220.0, N11bTestEnv.grams(te), 0.001)
        val jugo = N11bTestEnv.resolve("un sandwich de jamón y queso y un jugo").last()
        assertEquals(N11bTestEnv.grams(N11bTestEnv.resolve("un jugo").single()), N11bTestEnv.grams(jugo), 0.001)
        assertEquals(220.0, N11bTestEnv.grams(N11bTestEnv.resolve("sandwich de jamón y queso y café").last()), 0.001)
    }
}
