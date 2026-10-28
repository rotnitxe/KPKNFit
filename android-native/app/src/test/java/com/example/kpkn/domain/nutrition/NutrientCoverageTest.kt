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
    private fun snap(vararg entries: Pair<String, Double>, amount: Double = 100.0, unit: String = "g") =
        LoggedFood(foodName = "Con instantanea", amount = amount, unit = unit, calories = 100.0, nutrientsPer100g = mapOf(*entries))
    @Test fun `un cero de la instantanea es cero declarado aunque el campo legado sea cero`() {
        val result = NutrientCoverage.summarize(listOf(snap("sodium" to 0.0).copy(sodiumMg = 0.0)), NutrientKey.SODIUM)
        assertEquals(0.0, result.totalKnown!!, 0.0); assertEquals(1, result.declaredCount)
        assertEquals(NutrientCoverageState.COMPLETE, result.state)
    }
    @Test fun `sin la clave en la instantanea rige la regla vieja del campo legado`() {
        val result = NutrientCoverage.summarize(listOf(snap("potassium" to 50.0).copy(sodiumMg = 120.0)), NutrientKey.SODIUM)
        assertEquals(120.0, result.totalKnown!!, 0.0)
    }
    @Test fun `la instantanea se escala por la cantidad en gramos`() {
        val result = NutrientCoverage.summarize(listOf(snap("vitamin_d" to 2.0, amount = 250.0)), NutrientKey.VITAMIN_D)
        assertEquals(5.0, result.totalKnown!!, 1e-9); assertEquals(1, result.declaredCount)
    }
    @Test fun `con unidad distinta de gramos la instantanea cae a la regla vieja`() {
        val food = snap("sodium" to 50.0, amount = 250.0, unit = "ml")
        assertNull(NutrientCoverage.summarize(listOf(food), NutrientKey.SODIUM).totalKnown)
        assertEquals(80.0, NutrientCoverage.summarize(listOf(food.copy(sodiumMg = 80.0)), NutrientKey.SODIUM).totalKnown!!, 0.0)
    }
    @Test fun `un registro incierto con instantanea es estimado y la entrada rapida sigue sin dato`() {
        val uncertain = NutrientCoverage.summarize(listOf(snap("sodium" to 10.0).copy(isUncertain = true)), NutrientKey.SODIUM)
        assertEquals(10.0, uncertain.totalKnown!!, 0.0); assertEquals(1, uncertain.estimatedCount); assertEquals(0, uncertain.declaredCount)
        val quick = NutrientCoverage.summarize(listOf(snap("sodium" to 10.0).copy(isQuickEntry = true)), NutrientKey.SODIUM)
        assertNull(quick.totalKnown); assertEquals(1, quick.missingCount)
    }
    @Test fun `grasa saturada y cafeina legadas salen de sus campos`() {
        val food = LoggedFood(calories = 100.0, fatBreakdown = FatBreakdown(saturated = 4.0, trans = 0.5), caffeineMg = 80.0, sugar = 12.0)
        assertEquals(4.0, NutrientCoverage.summarize(listOf(food), NutrientKey.SAT_FAT).totalKnown!!, 0.0)
        assertEquals(0.5, NutrientCoverage.summarize(listOf(food), NutrientKey.TRANS_FAT).totalKnown!!, 0.0)
        assertNull(NutrientCoverage.summarize(listOf(food), NutrientKey.MONO_FAT).totalKnown)
        assertEquals(80.0, NutrientCoverage.summarize(listOf(food), NutrientKey.CAFFEINE).totalKnown!!, 0.0)
        assertEquals(12.0, NutrientCoverage.summarize(listOf(food), NutrientKey.SUGARS).totalKnown!!, 0.0)
    }
    @Test fun `los alias de las claves nuevas leen la lista de micronutrientes`() {
        val food = LoggedFood(calories = 100.0, micronutrients = listOf(Micronutrient("Fósforo", 300.0, "mg"), Micronutrient("Colina", 0.1, "g")))
        assertEquals(300.0, NutrientCoverage.summarize(listOf(food), NutrientKey.PHOSPHORUS).totalKnown!!, 0.0)
        assertEquals(100.0, NutrientCoverage.summarize(listOf(food), NutrientKey.CHOLINE).totalKnown!!, 1e-9)
    }
    @Test fun `F4-9 energia de la instantanea cercana a las kcal del registro sigue declarada`() {
        val rice = snap("energy" to 130.0, "sodium" to 1.0).copy(calories = 131.0)
        val result = NutrientCoverage.summarize(listOf(rice), NutrientKey.SODIUM)
        assertEquals(1, result.declaredCount); assertEquals(0, result.estimatedCount)
    }
    @Test fun `F4-9 frito con aceite supera ambos umbrales y pasa a estimado`() {
        val fried = snap("energy" to 130.0, "sodium" to 1.0, amount = 200.0).copy(calories = 400.0)
        val result = NutrientCoverage.summarize(listOf(fried), NutrientKey.SODIUM)
        assertEquals(2.0, result.totalKnown!!, 1e-9); assertEquals(0, result.declaredCount); assertEquals(1, result.estimatedCount)
    }
    @Test fun `F4-9 una diferencia de mas de 15 por ciento pero de menos de 25 kcal sigue declarada`() {
        val small = snap("energy" to 40.0, "sodium" to 1.0).copy(calories = 60.0)
        val result = NutrientCoverage.summarize(listOf(small), NutrientKey.SODIUM)
        assertEquals(1, result.declaredCount); assertEquals(0, result.estimatedCount)
    }
    @Test fun `F4-9 sin energia en la instantanea la regla no aplica`() {
        val noEnergy = snap("sodium" to 1.0).copy(calories = 900.0)
        val result = NutrientCoverage.summarize(listOf(noEnergy), NutrientKey.SODIUM)
        assertEquals(1, result.declaredCount); assertEquals(0, result.estimatedCount)
    }
}
