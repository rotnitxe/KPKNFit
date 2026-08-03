package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealTemplate
import org.junit.Assert.*
import org.junit.Test

class FoodTemplateMatcherTest {

    private fun template(
        name: String = "Desayuno · Avena 100g + Leche 250ml + Huevo",
        description: String = "avena 100g · leche 250ml · huevo · 480 kcal",
        foods: List<Triple<String, Double, Double>> = listOf(
            Triple("avena", 100.0, 1.0),
            Triple("leche", 250.0, 1.0),
            Triple("huevo", 60.0, 1.0),
        ),
    ) = MealTemplate(
        id = "t1",
        name = name,
        description = description,
        foods = foods.map { (name, grams, qty) ->
            LoggedFood(
                id = "f_$name",
                foodName = name,
                amount = grams,
                unit = "g",
                quantity = qty,
            )
        },
        totalCalories = 480.0,
        totalProtein = 25.0,
        totalCarbs = 50.0,
        totalFats = 18.0,
    )

    @Test
    fun `misma comida con orden distinto matchea por tokens y alimentos`() {
        val t = template()
        val score = FoodTemplateMatcher.score(t, FoodTemplateMatcher.normalizeSearchText("avena con leche y un huevo"))
        assertTrue("score=$score debe superar el umbral ${FoodTemplateMatcher.THRESHOLD}", score >= FoodTemplateMatcher.THRESHOLD)
    }

    @Test
    fun `query identica al template da score 1`() {
        val t = template()
        val query = FoodTemplateMatcher.normalizeSearchText("avena 100g leche 250ml huevo")
        assertEquals(1.0, FoodTemplateMatcher.score(t, query), 0.001)
    }

    @Test
    fun `cantidad contradictoria anula el match`() {
        val t = template(
            name = "Almuerzo · 3 Empanadas",
            description = "3 empanadas · 900 kcal",
            foods = listOf(Triple("empanada", 360.0, 3.0)),
        )
        val score = FoodTemplateMatcher.score(t, FoodTemplateMatcher.normalizeSearchText("1 empanada"))
        assertEquals("1 empanada vs template de 3 debe ser mismatch", 0.0, score, 0.001)
    }

    @Test
    fun `cantidad distinta en la query no alcanza el umbral y se parsea normal`() {
        val t = template(
            name = "Almuerzo · 3 Empanadas",
            description = "3 empanadas · 900 kcal",
            foods = listOf(Triple("empanada", 360.0, 3.0)),
        )
        val score = FoodTemplateMatcher.score(t, FoodTemplateMatcher.normalizeSearchText("2 empanadas"))
        assertTrue(
            "A3: cantidades distintas -> parseo normal (score=$score debe quedar bajo ${FoodTemplateMatcher.THRESHOLD})",
            score < FoodTemplateMatcher.THRESHOLD,
        )
    }

    @Test
    fun `comida distinta no matchea`() {
        val t = template()
        val score = FoodTemplateMatcher.score(t, FoodTemplateMatcher.normalizeSearchText("pasta con salsa bolognesa"))
        assertTrue("score=$score debe quedar bajo el umbral", score < FoodTemplateMatcher.THRESHOLD)
    }

    @Test
    fun `query en blanco no matchea`() {
        val t = template()
        assertEquals(0.0, FoodTemplateMatcher.score(t, ""), 0.001)
        assertEquals(0.0, FoodTemplateMatcher.score(t, FoodTemplateMatcher.normalizeSearchText("")), 0.001)
    }

    @Test
    fun `template sin foods ni descripcion no matchea`() {
        val t = MealTemplate(id = "t2", name = "Comida guardada", description = "Registro vacío")
        val score = FoodTemplateMatcher.score(t, FoodTemplateMatcher.normalizeSearchText("comida guardada"))
        assertTrue("score=$score debe quedar bajo el umbral", score < FoodTemplateMatcher.THRESHOLD)
    }

    @Test
    fun `normalizacion quita tildes mayusculas y ruido`() {
        assertEquals(
            "pollo con papas fritas",
            FoodTemplateMatcher.normalizeSearchText("  Pollo con Papás Fritas! "),
        )
    }
}
