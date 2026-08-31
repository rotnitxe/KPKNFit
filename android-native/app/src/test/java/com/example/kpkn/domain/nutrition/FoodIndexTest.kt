package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FoodIndexTest {

    @Test
    fun `normalizeSearch strips diacritics`() {
        assertEquals("arroz", FoodIndex.normalizeSearch("arroz"))
        assertEquals("papa", FoodIndex.normalizeSearch("papá"))
        assertEquals("nino", FoodIndex.normalizeSearch("niño"))
    }

    @Test
    fun `tokenize removes stopwords`() {
        val tokens = FoodIndex.tokenize("arroz de pollo con sal")
        assertTrue(tokens.contains("arroz"))
        assertTrue(tokens.contains("pollo"))
        assertTrue(tokens.contains("sal"))
        // Stopwords removed
        assertTrue(!tokens.contains("de"))
        assertTrue(!tokens.contains("con"))
    }

    @Test
    fun `tokenize filters short tokens`() {
        val tokens = FoodIndex.tokenize("a de la el arroz")
        assertTrue(!tokens.contains("a"))
        assertTrue(!tokens.contains("de"))
        assertTrue(!tokens.contains("la"))
        assertTrue(!tokens.contains("el"))
        assertTrue(tokens.contains("arroz"))
    }

    @Test
    fun `generateTrigrams creates correct trigrams`() {
        val trigrams = FoodIndex.generateTrigrams("hola")
        // "$hola$" → "$ho", "hol", "ola", "la$"
        assertTrue(trigrams.contains("\$ho"))
        assertTrue(trigrams.contains("hol"))
        assertTrue(trigrams.contains("ola"))
        assertTrue(trigrams.contains("la\$"))
    }

    @Test
    fun `generateTrigrams for short token`() {
        val trigrams = FoodIndex.generateTrigrams("ab")
        assertEquals(setOf("ab"), trigrams)
    }

    // ─── E16/IT2: custom foods indexados en runtime ─────────────────────────

    private fun customFood() = FoodItem(
        id = "custom1",
        name = "Almuerzo de Mamá",
        brand = null,
        servingSize = 150.0,
        unit = "g",
        calories = 300.0,
        protein = 15.0,
        carbs = 30.0,
        fats = 12.0,
        searchAliases = listOf("almuerzo de mama", "almuerzo de la mama"),
    )

    @Test
    fun `addStaticFood indexa un custom food y lo encuentra por alias`() {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = emptyList(), staticAliases = emptyMap())
        index.addStaticFood(customFood())

        assertEquals(1, index.size())
        val exact = index.exactMatches("almuerzo de la mama")
        assertEquals("el custom food se encuentra por alias normalizado", "custom1", exact.first().foodId)
        assertTrue("búsqueda por tokens también lo encuentra", index.search("almuerzo").contains("custom1"))
    }

    @Test
    fun `addStaticFood es idempotente y reemplaza por id`() {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = emptyList(), staticAliases = emptyMap())
        index.addStaticFood(customFood())
        index.addStaticFood(customFood())
        assertEquals("mismo id no duplica", 1, index.size())
    }

    @Test
    fun `addStaticFood no rompe alimentos ya indexados`() {
        val base = FoodItem(
            id = "gen001", name = "Manzana", brand = "Genérico", servingSize = 100.0, unit = "g",
            calories = 52.0, protein = 0.3, carbs = 14.0, fats = 0.2,
        )
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = listOf(base), staticAliases = emptyMap())
        index.addStaticFood(customFood())
        assertTrue(index.search("manzana").contains("gen001"))
        assertTrue(index.search("almuerzo").contains("custom1"))
    }

    @Test
    fun `search with exact local name does not require fuzzy expansion`() {
        val tomato = FoodItem(
            id = "gen026", name = "Tomate", brand = "Genérico", servingSize = 100.0, unit = "g",
            calories = 18.0, protein = 0.9, carbs = 3.9, fats = 0.2,
        )
        val pizza = FoodItem(
            id = "off1", name = "Pizza de Tomate", brand = "OFF", servingSize = 100.0, unit = "g",
            calories = 266.0, protein = 11.0, carbs = 33.0, fats = 10.0,
        )
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = listOf(tomato, pizza), staticAliases = emptyMap())
        val hits = index.search("tomate")
        assertTrue(hits.contains("gen026"))
        assertFalse("exact LOCAL tomate no debe expandir a pizza", hits.contains("off1"))
    }
}
