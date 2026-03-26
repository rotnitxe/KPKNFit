package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.*
import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class FoodDatabaseResolutionTest {

    // ─── Database Loading ──────────────────────────────────────────────────

    @Test
    fun `build food database has items`() {
        val db = buildFoodDatabase()
        assertTrue(db.isNotEmpty())
        assertTrue(db.size >= 60)
    }

    @Test
    fun `generic foods present`() {
        val db = buildFoodDatabase()
        val pollo = db.find { it.name.contains("Pechuga", ignoreCase = true) }
        assertNotNull(pollo)
        assertTrue(pollo!!.calories > 0)
        assertTrue(pollo.protein > 0)
    }

    @Test
    fun `chilean foods present`() {
        val db = buildFoodDatabase()
        val empanada = db.find { it.name.contains("Empanada", ignoreCase = true) }
        assertNotNull(empanada)
        assertTrue(empanada!!.tags.contains("chileno"))
    }

    // ─── Exact Lookup ──────────────────────────────────────────────────────

    @Test
    fun `find food exact match`() {
        val food = findFoodByNormalized("manzana")
        assertNotNull(food)
        assertTrue(food!!.name.lowercase().contains("manzana"))
    }

    @Test
    fun `find food with accent`() {
        val food = findFoodByNormalized("aguacate")
        assertNotNull(food)
        assertTrue(food!!.name.lowercase().contains("palta") || food.name.lowercase().contains("aguacate"))
    }

    @Test
    fun `find food partial match`() {
        val food = findFoodByNormalized("pollo")
        assertNotNull(food)
        assertTrue(food!!.name.lowercase().contains("pollo"))
    }

    @Test
    fun `find food with alias arroz`() {
        val food = findFoodByNormalized("arroz")
        assertNotNull(food)
        assertTrue(food!!.name.lowercase().contains("arroz"))
    }

    @Test
    fun `find food with alias banana`() {
        val food = findFoodByNormalized("banana")
        assertNotNull(food)
        assertTrue(food!!.name.lowercase().contains("platano") || food.name.lowercase().contains("plátano"))
    }

    @Test
    fun `find food with alias huevo`() {
        val food = findFoodByNormalized("huevos")
        assertNotNull(food)
        assertTrue(food!!.name.lowercase().contains("huevo"))
    }

    @Test
    fun `find food with alias atun`() {
        val food = findFoodByNormalized("atún")
        assertNotNull(food)
        assertTrue(food!!.name.lowercase().contains("atún"))
    }

    @Test
    fun `find food with alias leche`() {
        val food = findFoodByNormalized("leche")
        assertNotNull(food)
        assertTrue(food!!.name.lowercase().contains("leche"))
    }

    @Test
    fun `find food returns null for unknown`() {
        val food = findFoodByNormalized("xyznonexistent")
        assertNull(food)
    }

    @Test
    fun `find food case insensitive`() {
        val food = findFoodByNormalized("MANZANA")
        assertNotNull(food)
    }

    // ─── Portion References ────────────────────────────────────────────────

    @Test
    fun `get grams for tablespoon`() {
        val grams = getGramsForReference("tablespoon", null)
        assertEquals(15.0, grams, 0.01)
    }

    @Test
    fun `get grams for cup`() {
        val grams = getGramsForReference("cup", null)
        assertEquals(240.0, grams, 0.01)
    }

    @Test
    fun `get grams for palm`() {
        val grams = getGramsForReference("palm", null)
        assertTrue(grams in 80.0..100.0)
    }

    @Test
    fun `get grams for palm with protein food`() {
        val pollo = FoodItem(id = "t", name = "Pollo", protein = 25.0)
        val grams = getGramsForReference("palm", pollo)
        assertEquals(80.0, grams, 0.01)
    }

    @Test
    fun `get grams for handful`() {
        val grams = getGramsForReference("handful", null)
        assertEquals(30.0, grams, 0.01)
    }

    @Test
    fun `get grams for unknown reference`() {
        val grams = getGramsForReference("unknown", null)
        assertEquals(100.0, grams, 0.01)
    }
}
