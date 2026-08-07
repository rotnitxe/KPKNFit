package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del crash al pulsar "Analizar": el regex ancla de cantidades podía
 * degenerar exponencialmente (StackOverflowError) en consultas largas.
 * Estos tests fijan que el patrón sea lineal y que el comportamiento no cambie.
 */
class FoodTemplateMatcherRobustnessTest {

    private fun empanadasTemplate() = MealTemplate(
        id = "t1",
        name = "3 empanadas",
        foods = listOf(
            LoggedFood(foodName = "empanada", amount = 1.0, quantity = 3.0),
        ),
    )

    @Test(timeout = 2_000)
    fun `docenas de anclas de cantidad no explotan por backtracking`() {
        // Con el regex original (grupo anidado con backtracking libre) esta entrada
        // requería exploración exponencial: 40 anclas "uno" seguidas de palabras.
        val bomb = buildList {
            repeat(40) { add("uno") }
            add("pan")
            add("integral")
        }.joinToString(" ")
        val normalized = FoodTemplateMatcher.normalizeSearchText(bomb)
        // No debe lanzar ni colgar; el resultado es irrelevante.
        FoodTemplateMatcher.quantitiesMismatch(empanadasTemplate(), normalized)
    }

    @Test(timeout = 2_000)
    fun `ensalada de palabras larga se puntúa sin colgar`() {
        val salad = (1..80).joinToString(" ") { "alimento$it" }
        val score = FoodTemplateMatcher.score(
            empanadasTemplate(),
            FoodTemplateMatcher.normalizeSearchText(salad),
        )
        assertTrue(score in 0.0..1.0)
    }

    @Test(timeout = 2_000)
    fun `descripción muy larga se acota antes de evaluar`() {
        val long = ("2 huevos con pan y " + "muchas cosas mas ".repeat(80)).trim()
        val normalized = FoodTemplateMatcher.normalizeSearchText(long)
        FoodTemplateMatcher.quantitiesMismatch(empanadasTemplate(), normalized)
    }

    @Test
    fun `cantidad contradictoria sigue bloqueando el template`() {
        val query = FoodTemplateMatcher.normalizeSearchText("1 empanada")
        assertTrue(FoodTemplateMatcher.quantitiesMismatch(empanadasTemplate(), query))
        assertEquals(0.0, FoodTemplateMatcher.score(empanadasTemplate(), query), 0.0001)
    }

    @Test
    fun `misma cantidad sigue pasando la guarda de porciones`() {
        val query = FoodTemplateMatcher.normalizeSearchText("3 empanadas")
        assertFalse(FoodTemplateMatcher.quantitiesMismatch(empanadasTemplate(), query))
    }

    @Test
    fun `coincidencia sin porciones contradictorias supera el umbral`() {
        val query = FoodTemplateMatcher.normalizeSearchText("3 empanada")
        assertTrue(FoodTemplateMatcher.score(empanadasTemplate(), query) >= FoodTemplateMatcher.THRESHOLD)
    }
}
