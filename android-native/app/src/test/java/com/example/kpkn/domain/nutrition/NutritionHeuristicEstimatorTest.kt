package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import org.junit.Assert.*
import org.junit.Test

class NutritionHeuristicEstimatorTest {

    @Test
    fun `estimate pollo frito has higher fat than pollo simple`() {
        val polloBase = estimateNutritionByKeyword("pollo")
        val polloFrito = estimateNutritionByKeyword("pollo frito")
        assertNotNull(polloBase)
        assertNotNull(polloFrito)
        assertTrue("Frito should have higher kcal", polloFrito!!.calories > polloBase!!.calories)
        assertTrue("Frito should have higher fat", polloFrito.fats > polloBase.fats)
    }

    @Test
    fun `estimate pescado frito has higher fat`() {
        val pescadoFrito = estimateNutritionByKeyword("pescado frito")
        assertNotNull(pescadoFrito)
        assertTrue(pescadoFrito!!.fats > 5.0)
    }

    @Test
    fun `estimate empanizado boosts carbs and fat`() {
        val polloEmpanizado = estimateNutritionByKeyword("pollo empanizado")
        assertNotNull(polloEmpanizado)
        assertTrue(polloEmpanizado!!.fats > 5.0)
        assertTrue(polloEmpanizado.carbs > 1.0)
    }

    @Test
    fun `estimate pollo a la plancha reduces fat`() {
        val polloPlancha = estimateNutritionByKeyword("pollo a la plancha")
        assertNotNull(polloPlancha)
        assertTrue(polloPlancha!!.fats < 5.0)
    }

    @Test
    fun `estimate salteado boosts fat`() {
        val verdurasSalteadas = estimateNutritionByKeyword("verduras salteadas")
        assertNotNull(verdurasSalteadas)
        assertTrue(verdurasSalteadas!!.fats > 0.5)
    }

    @Test
    fun `estimate unknown food with frito keyword`() {
        val algoFrito = estimateNutritionByKeyword("algo frito desconocido")
        assertNotNull(algoFrito)
        assertTrue(algoFrito!!.fats > 10.0)
    }

    @Test
    fun `estimate without cooking keyword returns base profile`() {
        val pollo = estimateNutritionByKeyword("pollo")
        assertNotNull(pollo)
        assertEquals(165.0, pollo!!.calories, 1.0)
        assertEquals(31.0, pollo.protein, 1.0)
    }

    @Test
    fun `pad thai ramen y pho son plato de fideos no MIXED seco`() {
        val pad = NutritionHeuristicEstimator.estimatePer100g("pad thai")
        assertEquals(165.0, pad.calories, 1.0)
        assertTrue("pad thai protein ${pad.protein}", pad.protein in 5.0..12.0)
        val ramen = NutritionHeuristicEstimator.estimatePer100g("ramen")
        assertEquals(165.0, ramen.calories, 1.0)
        val pho = NutritionHeuristicEstimator.estimatePer100g("pho")
        assertEquals(165.0, pho.calories, 1.0)
    }

    // ─── WP-N10: la fritura añade grasa en gramos y las kcal siguen 4/4/9 ──────

    @Test
    fun `frito adds six grams of fat and its kcal follow 4-4-9, not a multiplier`() {
        val base = estimateNutritionByKeyword("pollo")!!
        val frito = estimateNutritionByKeyword("pollo frito")!!
        assertEquals(base.fats + 6.0, frito.fats, 0.001) // was x2
        assertEquals(base.protein, frito.protein, 0.001)
        assertEquals(base.carbs, frito.carbs, 0.001)
        assertEquals(base.calories + 9.0 * 6.0, frito.calories, 0.001) // was x1.10
    }

    @Test
    fun `empanizado adds the frying fat and fifteen grams of breading carbs`() {
        val base = estimateNutritionByKeyword("pollo")!!
        val empanizado = estimateNutritionByKeyword("pollo empanizado")!!
        assertEquals(base.fats + 6.0, empanizado.fats, 0.001)
        assertEquals(base.carbs + 15.0, empanizado.carbs, 0.001)
        assertEquals(base.protein, empanizado.protein, 0.001)
        assertEquals(base.calories + 9.0 * 6.0 + 4.0 * 15.0, empanizado.calories, 0.001)
    }

    @Test
    fun `the dry methods keep their table factor, once`() {
        val base = estimateNutritionByKeyword("pollo")!!
        val plancha = estimateNutritionByKeyword("pollo a la plancha")!!
        assertEquals(base.calories * 1.00, plancha.calories, 0.001)
        assertEquals(base.protein * 1.05, plancha.protein, 0.001)
        assertEquals(base.fats * 0.95, plancha.fats, 0.001)
        val horno = estimateNutritionByKeyword("pollo gratinado")!!
        assertEquals(base.calories * 1.15, horno.calories, 0.001)
    }

    @Test
    fun `withCookingMethod acts once on a dish with no row and never twice`() {
        val base = NutritionHeuristicEstimator.estimatePer100g("xyz plato")
        val fried = NutritionHeuristicEstimator.withCookingMethod(base, "xyz plato", CookingMethod.FRITO)
        assertEquals(base.fats + 6.0, fried.fats, 0.001)
        assertEquals(base.calories + 54.0, fried.calories, 0.001)
        val breaded = NutritionHeuristicEstimator.withCookingMethod(base, "xyz plato", CookingMethod.EMPANIZADO_FRITO)
        assertEquals(base.carbs + 15.0, breaded.carbs, 0.001)
        val baked = NutritionHeuristicEstimator.withCookingMethod(base, "xyz plato", CookingMethod.HORNO)
        assertEquals(base.calories * 1.15, baked.calories, 0.001)
        // Boiling, steaming, stewing and raw change nothing: their effect is the yield of a raw row.
        for (method in listOf(CookingMethod.COCIDO, CookingMethod.VAPOR, CookingMethod.OLLA, CookingMethod.GUISADO, CookingMethod.CRUDO, null)) {
            assertEquals("$method", base, NutritionHeuristicEstimator.withCookingMethod(base, "xyz plato", method))
        }
        // The name carries the word: estimatePer100g already applied it, the method must not apply it again.
        val named = NutritionHeuristicEstimator.estimatePer100g("pollo frito")
        assertEquals(named, NutritionHeuristicEstimator.withCookingMethod(named, "pollo frito", CookingMethod.FRITO))
    }

    @Test
    fun `salteado de verduras carries the frying fat in grams`() {
        val verduras = NutritionHeuristicEstimator.estimatePer100g("verduras")
        val salteadas = estimateNutritionByKeyword("verduras salteadas")!!
        assertEquals(verduras.fats + 6.0, salteadas.fats, 0.001)
        assertEquals(verduras.calories + 54.0, salteadas.calories, 0.001)
    }
}
