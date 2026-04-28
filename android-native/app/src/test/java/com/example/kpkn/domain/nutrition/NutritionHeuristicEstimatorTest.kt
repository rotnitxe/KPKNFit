package com.example.kpkn.domain.nutrition

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
}
