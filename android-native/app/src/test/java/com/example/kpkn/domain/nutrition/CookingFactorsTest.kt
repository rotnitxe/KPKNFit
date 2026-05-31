package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class CookingFactorsTest {

    @Test
    fun `crudo is baseline`() {
        val factor = COOKING_FACTORS[CookingMethod.CRUDO]!!
        assertEquals(1.00, factor.kcal, 0.01)
        assertEquals(1.00, factor.fats, 0.01)
        assertEquals(1.00, factor.carbs, 0.01)
        assertEquals(1.00, factor.protein, 0.01)
    }

    @Test
    fun `cocido reduces kcal and protein from water dilution`() {
        val cocido = COOKING_FACTORS[CookingMethod.COCIDO]!!
        assertEquals(0.90, cocido.kcal, 0.01)
        assertEquals(0.95, cocido.protein, 0.01)
        assert(cocido.waterChange > 0) { "cocido should gain water" }
    }

    @Test
    fun `vapor slight kcal drop`() {
        assertEquals(0.95, COOKING_FACTORS[CookingMethod.VAPOR]!!.kcal, 0.01)
        assert(COOKING_FACTORS[CookingMethod.VAPOR]!!.waterChange > 0)
    }

    @Test
    fun `plancha fat loss from rendering`() {
        val f = COOKING_FACTORS[CookingMethod.PLANCHA]!!
        assertEquals(1.00, f.kcal, 0.01)
        assertEquals(0.95, f.fats, 0.01)
        assertEquals(1.05, f.protein, 0.01)
    }

    @Test
    fun `frito water loss concentrates`() {
        val f = COOKING_FACTORS[CookingMethod.FRITO]!!
        assertEquals(1.10, f.kcal, 0.01)
        assertEquals(1.00, f.fats, 0.01)
        assertEquals(1.10, f.protein, 0.01)
        assertEquals(-0.20, f.waterChange, 0.01)
    }

    @Test
    fun `empanizado frito applies water loss and breading carbs`() {
        val f = COOKING_FACTORS[CookingMethod.EMPANIZADO_FRITO]!!
        assertEquals(1.20, f.kcal, 0.01)
        assertEquals(1.00, f.fats, 0.01)
        assertEquals(1.20, f.carbs, 0.01)
        assertEquals(1.10, f.protein, 0.01)
    }

    @Test
    fun `asado parrilla fat drips off`() {
        val f = COOKING_FACTORS[CookingMethod.ASADO_PARRILLA]!!
        assertEquals(1.05, f.kcal, 0.01)
        assertEquals(0.90, f.fats, 0.01)
        assertEquals(1.10, f.protein, 0.01)
    }

    @Test
    fun `horno concentrates macros`() {
        val f = COOKING_FACTORS[CookingMethod.HORNO]!!
        assertEquals(1.15, f.kcal, 0.01)
        assertEquals(0.95, f.fats, 0.01)
        assertEquals(1.10, f.protein, 0.01)
    }

    @Test
    fun `guisado adds fat`() {
        val f = COOKING_FACTORS[CookingMethod.GUISADO]!!
        assertEquals(1.30, f.kcal, 0.01)
        assertEquals(1.20, f.fats, 0.01)
    }

    @Test
    fun `applyCookingToMacros adjusts correctly`() {
        val result = applyCookingToMacros(165.0, 31.0, 0.0, 3.6, CookingMethod.FRITO)
        assertEquals(181.5, result.calories, 0.1)
        assertEquals(34.1, result.protein, 0.1)
        assertEquals(3.6, result.fats, 0.1)
    }

    @Test
    fun `applyCookingToMacros with null returns original`() {
        val result = applyCookingToMacros(100.0, 20.0, 50.0, 3.0, null)
        assertEquals(100.0, result.calories, 0.01)
        assertEquals(20.0, result.protein, 0.01)
        assertEquals(50.0, result.carbs, 0.01)
    }

    @Test
    fun `all 11 methods have factors`() {
        assertEquals(11, COOKING_FACTORS.size)
        for (method in CookingMethod.entries) {
            assert(COOKING_FACTORS.containsKey(method)) { "Missing factor for $method" }
        }
    }

    @Test
    fun `waterChange negative for dry methods`() {
        assert(COOKING_FACTORS[CookingMethod.PLANCHA]!!.waterChange < 0)
        assert(COOKING_FACTORS[CookingMethod.HORNO]!!.waterChange < 0)
        assert(COOKING_FACTORS[CookingMethod.FRITO]!!.waterChange < 0)
    }

    @Test
    fun `waterChange positive for wet methods`() {
        assert(COOKING_FACTORS[CookingMethod.COCIDO]!!.waterChange > 0)
        assert(COOKING_FACTORS[CookingMethod.VAPOR]!!.waterChange > 0)
        assert(COOKING_FACTORS[CookingMethod.OLLA]!!.waterChange > 0)
    }

    @Test
    fun `isLikelyLiquid detects liquids`() {
        assert(isLikelyLiquid("agua"))
        assert(isLikelyLiquid("leche entera"))
        assert(isLikelyLiquid("bebida energética"))
        assert(isLikelyLiquid("jugo de naranja"))
        assert(isLikelyLiquid("café con leche"))
        assert(isLikelyLiquid("aceite de oliva"))
        assert(isLikelyLiquid("refresco"))
        assert(!isLikelyLiquid("pollo"))
        assert(!isLikelyLiquid("arroz"))
        assert(!isLikelyLiquid("manzana"))
    }
}
