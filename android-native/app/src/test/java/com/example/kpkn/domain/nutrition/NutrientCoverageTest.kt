package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class NutrientCoverageTest {
    private fun sodium(name: String, amount: Double, kcal: Double = 100.0) = LoggedFood(foodName = name, calories = kcal,
        micronutrients = listOf(Micronutrient("Sodio", amount, "mg")))
    @Test fun `dos de cinco alimentos dan total conocido y cuarenta por ciento de cobertura`() {
        val foods = listOf(sodium("Uno", 10.0), sodium("Dos", 20.0)) + List(3) { LoggedFood(calories = 100.0) }
        val result = NutrientCoverage.summarize(foods, NutrientKey.SODIUM)
        assertEquals(30.0, result.totalKnown!!, 0.0)
        assertEquals(2, result.declaredCount); assertEquals(3, result.missingCount)
        assertEquals(0.4, result.declaredFraction, 0.0); assertEquals(NutrientCoverageState.PARTIAL, result.state)
    }
    @Test fun `cero declarado difiere de sin dato y entrada rapida no declara nutrientes`() {
        val zero = NutrientCoverage.summarize(listOf(sodium("Cero", 0.0)), NutrientKey.SODIUM)
        assertEquals(0.0, zero.totalKnown!!, 0.0); assertEquals(NutrientCoverageState.COMPLETE, zero.state)
        val missing = NutrientCoverage.summarize(listOf(LoggedFood(calories = 100.0)), NutrientKey.SODIUM)
        assertNull(missing.totalKnown); assertEquals(NutrientCoverageState.NO_DATA, missing.state)
        val quick = NutrientCoverage.summarize(listOf(sodium("Rapida", 200.0).copy(isQuickEntry = true)), NutrientKey.SODIUM)
        assertNull(quick.totalKnown); assertEquals(1, quick.missingCount)
    }
    @Test fun `hasta cinco contribuyentes ordenados siempre reconcilian el total`() {
        val result = NutrientCoverage.summarize((1..8).map { sodium("Alimento $it", it.toDouble()) }, NutrientKey.SODIUM)
        assertEquals(5, result.contributors.size)
        assertEquals(listOf("Alimento 8", "Alimento 7", "Alimento 6", "Alimento 5", "Otros alimentos"), result.contributors.map { it.name })
        assertEquals(4, result.contributors.last().groupedFoods)
        assertEquals(result.totalKnown!!, result.contributors.sumOf { it.amount }, 0.0)
    }
    @Test fun `datos estimados y recetas no se convierten en cobertura declarada`() {
        val foods = listOf(sodium("Estimacion", 20.0).copy(analysisSource = AnalysisSource.LOCAL_HEURISTIC),
            sodium("Receta", 30.0).copy(recipeId = "recipe"))
        val result = NutrientCoverage.summarize(foods, NutrientKey.SODIUM)
        assertEquals(50.0, result.totalKnown!!, 0.0); assertEquals(2, result.estimatedCount)
        assertEquals(0.0, result.declaredFraction, 0.0); assertEquals(NutrientCoverageState.PARTIAL, result.state)
    }
    @Test fun `la procedencia marcada incompleta sigue siendo dato debil al reabrir`() {
        val food = sodium("Ficha", 10.0).copy(evidenceJson = """{"source":"CURATED_LOCAL","sourceQuality":"flagged"}""")
        val result = NutrientCoverage.summarize(listOf(food), NutrientKey.SODIUM)
        assertEquals(1, result.estimatedCount); assertEquals(0.0, result.declaredFraction, 0.0)
        assertEquals(NutrientCoverageState.PARTIAL, result.state)
    }
    @Test fun `sin energia la cobertura indica recuento y un dia vacio no muestra cero`() {
        val result = NutrientCoverage.summarize(listOf(sodium("Con dato", 10.0, 0.0), LoggedFood()), NutrientKey.SODIUM)
        assertFalse(result.weightedByEnergy); assertEquals(0.5, result.declaredFraction, 0.0)
        assertTrue(NutrientCoverage.summarize(emptyList()).all { it.totalKnown == null && it.state == NutrientCoverageState.NO_DATA })
    }
    @Test fun `convierte magnitudes compatibles sin inventar RAE DFE ni formas vitaminicas`() {
        val food = LoggedFood(calories = 100.0, micronutrients = listOf(Micronutrient("Calcio", 0.1, "g"),
            Micronutrient("Vitamina A", 500.0, "IU"), Micronutrient("Folato", 100.0, "µg")))
        assertEquals(100.0, NutrientCoverage.summarize(listOf(food), NutrientKey.CALCIUM).totalKnown!!, 0.0)
        assertNull(NutrientCoverage.summarize(listOf(food), NutrientKey.VITAMIN_A).totalKnown)
        assertNull(NutrientCoverage.summarize(listOf(food), NutrientKey.FOLATE).totalKnown)
        val valid = food.copy(micronutrients = listOf(Micronutrient("Vitamina A", 500.0, "µg RAE"), Micronutrient("Folato", 100.0, "µg DFE")))
        assertEquals(500.0, NutrientCoverage.summarize(listOf(valid), NutrientKey.VITAMIN_A).totalKnown!!, 0.0)
        assertEquals(100.0, NutrientCoverage.summarize(listOf(valid), NutrientKey.FOLATE).totalKnown!!, 0.0)
    }
    @Test fun `no duplica columnas base y lista ni suma valores no finitos`() {
        val food = sodium("Ficha", 10.0).copy(sodiumMg = 10.0)
        assertEquals(10.0, NutrientCoverage.summarize(listOf(food), NutrientKey.SODIUM).totalKnown!!, 0.0)
        assertNull(NutrientCoverage.summarize(listOf(sodium("Malo", Double.NaN)), NutrientKey.SODIUM).totalKnown)
    }
    @Test fun `noventa por ciento se refiere a las kcal declaradas y no a cantidad de filas`() {
        val result = NutrientCoverage.summarize(listOf(sodium("Declarado", 10.0, 900.0), LoggedFood(calories = 100.0)), NutrientKey.SODIUM)
        assertEquals(0.9, result.declaredFraction, 1e-10); assertEquals(NutrientCoverageState.COMPLETE, result.state)
    }
}
