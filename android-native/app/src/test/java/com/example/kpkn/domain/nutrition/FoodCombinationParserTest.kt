package com.example.kpkn.domain.nutrition

import org.junit.Assert.*
import org.junit.Test

class FoodCombinationParserTest {

    @Test
    fun `parse known dish sandwich de pollo con mayonesa`() {
        val result = FoodCombinationParser.parse("sandwich de pollo con mayonesa")
        assertTrue(result.isKnownDish)
        assertTrue(result.confidence >= 0.70)
        assertEquals("pan", result.baseFood)
        val sauces = result.accompaniments.filter { it.role == FoodCombinationParser.Role.SAUCE }
        assertTrue(sauces.any { it.food == "mayonesa" })
        assertTrue(sauces.find { it.food == "mayonesa" }!!.proportion <= 0.25)
    }

    @Test
    fun `parse sandwich de pollo con mayo`() {
        val result = FoodCombinationParser.parse("sandwich de pollo con mayo")
        assertTrue(result.isKnownDish)
        assertTrue(result.accompaniments.any { it.food == "mayonesa" })
    }

    @Test
    fun `parse sandwich de atun con mayonesa`() {
        val result = FoodCombinationParser.parse("sandwich de atún con mayonesa")
        assertTrue(result.isKnownDish)
        assertTrue(result.accompaniments.any { it.role == FoodCombinationParser.Role.SAUCE })
    }

    @Test
    fun `parse hamburguesa con mayonesa`() {
        val result = FoodCombinationParser.parse("hamburguesa con mayonesa")
        assertTrue(result.isKnownDish)
        assertTrue(result.accompaniments.any { it.food == "mayonesa" })
    }

    @Test
    fun `parse papas fritas con mayonesa`() {
        val result = FoodCombinationParser.parse("papas fritas con mayonesa")
        assertTrue(result.isKnownDish)
        assertTrue(result.accompaniments.any { it.food == "mayonesa" && it.role == FoodCombinationParser.Role.SAUCE })
    }

    @Test
    fun `mayonesa inferred as SAUCE role`() {
        val result = FoodCombinationParser.parse("pan con mayonesa")
        val mayo = result.accompaniments.find { it.food == "mayonesa" }
        assertNotNull(mayo)
        assertEquals(FoodCombinationParser.Role.SAUCE, mayo!!.role)
    }

    @Test
    fun `ketchup inferred as SAUCE role`() {
        val result = FoodCombinationParser.parse("pan con ketchup")
        val ketchup = result.accompaniments.find { it.food == "ketchup" }
        assertNotNull(ketchup)
        assertEquals(FoodCombinationParser.Role.SAUCE, ketchup!!.role)
    }

    @Test
    fun `queso inferred as TOPPING role`() {
        val result = FoodCombinationParser.parse("pan con queso")
        val queso = result.accompaniments.find { it.food == "queso" }
        assertNotNull(queso)
        assertEquals(FoodCombinationParser.Role.TOPPING, queso!!.role)
    }

    @Test
    fun `generic con_doble pattern for unknown combo`() {
        val result = FoodCombinationParser.parse("barra de proteina con mani")
        assertFalse(result.isKnownDish)
        assertTrue(result.confidence >= 0.50)
        assertTrue(result.accompaniments.isNotEmpty())
    }

    @Test
    fun `sandwich de pollo con queso has both proteins and topping`() {
        val result = FoodCombinationParser.parse("sandwich de pollo con queso")
        assertTrue(result.isKnownDish)
        val polloComponent = listOf(result.baseFood) + result.accompaniments.map { it.food }
        assertTrue(polloComponent.any { it.contains("pollo") })
        assertTrue(polloComponent.any { it.contains("queso") })
    }

    @Test
    fun `sausage proportion is small`() {
        val result = FoodCombinationParser.parse("sandwich de pollo con ketchup")
        val ketchup = result.accompaniments.find { it.food == "ketchup" }
        assertNotNull(ketchup)
        assertTrue(ketchup!!.proportion <= 0.15)
    }
}
