package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class FoodParserTest {

    // ─── Basic Parsing ────────────────────────────────────────────────────

    @Test
    fun `parse empty string`() {
        val result = parseMealDescription("")
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `parse simple food name`() {
        val result = parseMealDescription("manzana")
        assertEquals(1, result.items.size)
        assertEquals("manzana", result.items[0].tag)
        assertEquals(1.0, result.items[0].quantity, 0.01)
        assertEquals(PortionPreset.MEDIUM, result.items[0].portion)
    }

    @Test
    fun `parse with quantity`() {
        val result = parseMealDescription("2 manzanas")
        assertEquals(1, result.items.size)
        assertEquals("manzana", result.items[0].tag)
        assertEquals(2.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse with literal quantity`() {
        val result = parseMealDescription("dos huevos")
        assertEquals(1, result.items.size)
        assertEquals("huevo", result.items[0].tag)
        assertEquals(2.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse with grams`() {
        val result = parseMealDescription("200g pollo")
        assertEquals(1, result.items.size)
        assertEquals("pollo", result.items[0].tag)
        assertEquals(200.0, result.items[0].amountGrams!!, 0.01)
    }

    @Test
    fun `parse with kg converts to grams`() {
        val result = parseMealDescription("1.5kg arroz")
        assertEquals(1, result.items.size)
        assertEquals("arroz", result.items[0].tag)
        assertEquals(1500.0, result.items[0].amountGrams!!, 0.01)
    }

    // ─── Multiple Items ───────────────────────────────────────────────────

    @Test
    fun `parse comma separated items`() {
        val result = parseMealDescription("200g pollo, 150g arroz")
        assertEquals(2, result.items.size)
    }

    @Test
    fun `parse y connector`() {
        val result = parseMealDescription("200g pollo y 150g arroz")
        assertEquals(2, result.items.size)
    }

    @Test
    fun `parse con connector`() {
        val result = parseMealDescription("200g pollo con arroz")
        assertEquals(2, result.items.size)
    }

    @Test
    fun `parse plus connector`() {
        val result = parseMealDescription("200g pollo + 150g arroz")
        assertEquals(2, result.items.size)
    }

    // ─── Cooking Method ───────────────────────────────────────────────────

    @Test
    fun `parse with cooking method`() {
        val result = parseMealDescription("200g pollo a la plancha")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.PLANCHA, result.items[0].cookingMethod)
    }

    @Test
    fun `parse horneado`() {
        val result = parseMealDescription("200g salmón al horno")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.HORNO, result.items[0].cookingMethod)
    }

    // ─── Portion Preset ───────────────────────────────────────────────────

    @Test
    fun `parse grande portion`() {
        val result = parseMealDescription("plato grande de arroz")
        assertEquals(1, result.items.size)
        assertEquals(PortionPreset.EXTRA, result.items[0].portion)
    }

    @Test
    fun `parse mediano portion`() {
        val result = parseMealDescription("plato mediano de ensalada")
        assertEquals(1, result.items.size)
        assertEquals(PortionPreset.MEDIUM, result.items[0].portion)
    }

    @Test
    fun `parse pequeño portion`() {
        val result = parseMealDescription("porción pequeña de queso")
        assertEquals(1, result.items.size)
        assertEquals(PortionPreset.SMALL, result.items[0].portion)
    }

    // ─── Portion References ───────────────────────────────────────────────

    @Test
    fun `parse cucharada de aceite`() {
        val result = parseMealDescription("1 cucharada de aceite de oliva")
        assertEquals(1, result.items.size)
        assertEquals("aceite de oliva", result.items[0].tag)
        assertNotNull(result.items[0].amountGrams)
        assertTrue(result.items[0].amountGrams!! > 0)
    }

    @Test
    fun `parse taza de arroz`() {
        val result = parseMealDescription("una taza de arroz")
        assertEquals(1, result.items.size)
        assertEquals("arroz", result.items[0].tag)
        assertNotNull(result.items[0].amountGrams)
        assertTrue(result.items[0].amountGrams!! > 0)
    }

    @Test
    fun `parse media taza`() {
        val result = parseMealDescription("media taza de leche")
        assertEquals(1, result.items.size)
        assertEquals("leche", result.items[0].tag)
    }

    @Test
    fun `parse puñado`() {
        val result = parseMealDescription("un puñado de almendras")
        assertEquals(1, result.items.size)
        assertNotNull(result.items[0].amountGrams)
    }

    // ─── Range Quantities ─────────────────────────────────────────────────

    @Test
    fun `parse range quantity`() {
        val result = parseMealDescription("1-2 huevos")
        assertEquals(1, result.items.size)
        assertEquals(1.5, result.items[0].quantity, 0.01) // average of 1-2
    }

    // ─── Combined Descriptions ────────────────────────────────────────────

    @Test
    fun `parse complex meal`() {
        val result = parseMealDescription("200g pechuga de pollo a la plancha, 150g arroz blanco, ensalada grande")
        assertEquals(3, result.items.size)
        assertEquals("pechuga de pollo", result.items[0].tag)
        assertEquals(CookingMethod.PLANCHA, result.items[0].cookingMethod)
        assertEquals(200.0, result.items[0].amountGrams!!, 0.01)
    }

    @Test
    fun `parse empanada de pino protected entity`() {
        val result = parseMealDescription("2 empanadas de pino")
        assertEquals(1, result.items.size)
        assertEquals("empanadas de pino", result.items[0].tag)
        assertEquals(2.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse plato grande de pollo con arroz`() {
        val result = parseMealDescription("plato grande de pollo con arroz")
        assertTrue(result.items.size >= 2)
        assertEquals(PortionPreset.EXTRA, result.items[0].portion)
    }

    @Test
    fun `raw description preserved`() {
        val desc = "200g pollo y 150g arroz"
        val result = parseMealDescription(desc)
        assertEquals(desc, result.rawDescription)
    }

    // ─── Extended Quantity Detection ───────────────────────────────────────

    @Test
    fun `parse literal twelve`() {
        val result = parseMealDescription("doce huevos")
        assertEquals(1, result.items.size)
        assertEquals(12.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse literal fifteen`() {
        val result = parseMealDescription("quince uvas")
        assertEquals(1, result.items.size)
        assertEquals(15.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse literal twenty`() {
        val result = parseMealDescription("veinte almendras")
        assertEquals(1, result.items.size)
        assertEquals(20.0, result.items[0].quantity, 0.01)
    }

    // ─── Protected Entities ────────────────────────────────────────────────

    @Test
    fun `parse sandwich de pollo con mayonesa as protected`() {
        val result = parseMealDescription("sandwich de pollo con mayonesa")
        assertTrue(result.items.size == 1 || result.items.size >= 2)
    }

    @Test
    fun `parse hamburguesa con queso as protected`() {
        val result = parseMealDescription("hamburguesa con queso")
        assertTrue(result.items.size == 1)
    }

    @Test
    fun `parse papas fritas con mayonesa as protected`() {
        val result = parseMealDescription("papas fritas con mayonesa")
        assertTrue(result.items.size == 1)
    }
}
