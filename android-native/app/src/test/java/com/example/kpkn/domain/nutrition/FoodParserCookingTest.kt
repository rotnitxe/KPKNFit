package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FoodParserCookingTest {

    @Test
    fun `detects plancha`() {
        val result = parseMealDescription("200g de pollo a la plancha")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.PLANCHA, result.items[0].cookingMethod)
    }

    @Test
    fun `detects horno`() {
        val result = parseMealDescription("salmon al horno")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.HORNO, result.items[0].cookingMethod)
    }

    @Test
    fun `detects frito`() {
        val result = parseMealDescription("huevo frito")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.FRITO, result.items[0].cookingMethod)
    }

    @Test
    fun `detects cocido`() {
        val result = parseMealDescription("arroz cocido")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.COCIDO, result.items[0].cookingMethod)
    }

    @Test
    fun `detects crudo`() {
        val result = parseMealDescription("atun crudo")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.CRUDO, result.items[0].cookingMethod)
    }

    @Test
    fun `detects salteado`() {
        val result = parseMealDescription("salteado de verduras")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.SALTEADO, result.items[0].cookingMethod)
    }

    @Test
    fun `detects ahumado`() {
        val result = parseMealDescription("salmon ahumado")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.AHUMADO, result.items[0].cookingMethod)
    }

    @Test
    fun `detects vapor`() {
        val result = parseMealDescription("brócoli al vapor")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.VAPOR, result.items[0].cookingMethod)
    }

    @Test
    fun `detects olla`() {
        val result = parseMealDescription("lentejas a la olla")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.OLLA, result.items[0].cookingMethod)
    }

    @Test
    fun `detects asado parrilla`() {
        val result = parseMealDescription("carne a la parrilla")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.ASADO_PARRILLA, result.items[0].cookingMethod)
    }

    @Test
    fun `detects guisado`() {
        val result = parseMealDescription("pollo guisado")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.GUISADO, result.items[0].cookingMethod)
    }

    @Test
    fun `detects empanizado`() {
        val result = parseMealDescription("merluza empanizada")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.EMPANIZADO_FRITO, result.items[0].cookingMethod)
    }

    @Test
    fun `no cooking method returns null`() {
        val result = parseMealDescription("200g de pollo")
        assertEquals(1, result.items.size)
        assertNull(result.items[0].cookingMethod)
    }

    @Test
    fun `extracts grams and cooking method together`() {
        val result = parseMealDescription("150g de salmon a la plancha")
        assertEquals(1, result.items.size)
        assertEquals(150.0, result.items[0].amountGrams!!, 0.1)
        assertEquals(CookingMethod.PLANCHA, result.items[0].cookingMethod)
    }

    @Test
    fun `detects cooking in multi-item description`() {
        val result = parseMealDescription("pollo a la plancha con arroz cocido")
        assertEquals(2, result.items.size)
        val pollo = result.items.find { it.tag.contains("pollo") }
        val arroz = result.items.find { it.tag.contains("arroz") }
        assertNotNull(pollo)
        assertNotNull(arroz)
        assertEquals(CookingMethod.PLANCHA, pollo!!.cookingMethod)
        assertEquals(CookingMethod.COCIDO, arroz!!.cookingMethod)
    }
}
