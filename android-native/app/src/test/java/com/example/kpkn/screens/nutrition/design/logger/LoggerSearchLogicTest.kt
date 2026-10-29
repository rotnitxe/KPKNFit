package com.example.kpkn.screens.nutrition.design.logger

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.RecipeData
import com.example.kpkn.data.models.RecipeIngredient
import com.example.kpkn.data.models.SearchConfidence
import com.example.kpkn.data.models.SearchSource
import com.example.kpkn.domain.nutrition.RecipeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lógica de la búsqueda del registro (lienzo s-start y s-results): los filtros, el origen y la ración de cada fila y el pie de «Recetas».
 * Puro: sin pantalla.
 */
class LoggerSearchLogicTest {

    private fun candidate(food: FoodItem) = FoodCandidate(
        foodId = food.id,
        displayName = food.name,
        score = 1.0,
        confidence = SearchConfidence.HIGH,
        source = SearchSource.LOCAL,
        food = food,
    )

    private val propio = candidate(FoodItem(id = "m", name = "Yogur casero", isCustom = true))
    private val receta = candidate(
        FoodItem(
            id = "r",
            name = "Yogur con miel",
            recipe = RecipeData(listOf(RecipeIngredient(LoggedFood(foodName = "Yogur", amount = 100.0))), servings = 1),
        ),
    )
    private val conMarca = candidate(FoodItem(id = "b", name = "Yogur griego", brand = "Hacendado"))
    private val marcaVacia = candidate(FoodItem(id = "v", name = "Yogur natural", brand = "  "))
    private val sinMarca = candidate(FoodItem(id = "s", name = "Yogur"))
    private val todos = listOf(propio, receta, conMarca, marcaVacia, sinMarca)

    @Test
    fun `todo deja la lista como esta`() {
        assertEquals(todos, filterLoggerCandidates(todos, LoggerSearchFilter.ALL))
    }

    @Test
    fun `mios son los alimentos propios y las recetas`() {
        assertEquals(listOf(propio, receta), filterLoggerCandidates(todos, LoggerSearchFilter.MINE))
    }

    @Test
    fun `con marca y sin marca se reparten la lista`() {
        assertEquals(listOf(conMarca), filterLoggerCandidates(todos, LoggerSearchFilter.BRANDED))
        assertEquals(listOf(propio, receta, marcaVacia, sinMarca), filterLoggerCandidates(todos, LoggerSearchFilter.UNBRANDED))
    }

    @Test
    fun `los filtros se llaman como en el lienzo`() {
        assertEquals(listOf("Todo", "Míos", "Con marca", "Sin marca"), LoggerSearchFilter.entries.map { it.label })
    }

    @Test
    fun `el origen es la marca o receta o mio o KPKN`() {
        assertEquals("Hacendado", loggerSearchOrigin(conMarca.food))
        assertEquals("Hacendado", loggerSearchOrigin(conMarca.food.copy(isCustom = true)))
        assertEquals("Receta", loggerSearchOrigin(receta.food))
        assertEquals("Mío", loggerSearchOrigin(propio.food))
        assertEquals("KPKN", loggerSearchOrigin(sinMarca.food))
        assertEquals("KPKN", loggerSearchOrigin(marcaVacia.food))
    }

    @Test
    fun `la fila usa la misma racion que el mas y sus kcal`() {
        val huevo = findStaticFoodById("gen007")!!
        val portion = loggerSearchPortion(huevo, "huevos")
        assertEquals(50.0, portion.grams, 0.0)
        assertTrue(portion.label, portion.label.startsWith("50 g · "))
        assertTrue(portion.label, portion.label.contains("huevo"))
        assertTrue(portion.kcal > 0)
        assertTrue(loggerSearchCaption(candidate(huevo), "huevos").startsWith("${portion.label} · "))
    }

    @Test
    fun `la leche de un envase se escala a un vaso`() {
        val leche = GlobalFoodEntity(
            foodId = "off_1",
            name = "Leche descremada",
            brand = "Colun",
            calories = 35.0,
            protein = 3.4,
            carbs = 4.9,
            fats = 0.1,
            source = "OFF Chile",
            sourcePriority = 80,
            nutritionBasis = "PER_100G_AS_SOLD",
        ).toFoodItem()
        val portion = loggerSearchPortion(leche, "leche")
        assertEquals(200.0, portion.grams, 0.0)
        assertEquals(70.0, portion.kcal.toDouble(), 2.0)
        assertTrue(portion.label, portion.label.startsWith("200 "))
        assertTrue(loggerSearchCaption(candidate(leche), "leche").endsWith(" · Colun"))
    }

    @Test
    fun `una receta sin peso final dice una racion y nunca un gramo`() {
        val food = RecipeCalculator.asFood(
            "no-weight",
            "Arroz con pollo",
            RecipeData(listOf(RecipeIngredient(LoggedFood(foodName = "Arroz", amount = 100.0, calories = 130.0))), servings = 2),
        )
        val portion = loggerSearchPortion(food, "arroz con pollo")
        assertEquals("1 ración", portion.label)
        assertEquals(food.calories.toInt(), portion.kcal)
        assertFalse(portion.label.contains(" g"))
        assertEquals("1 ración · Receta", loggerSearchCaption(candidate(food), "arroz con pollo"))
    }

    @Test
    fun `el pie del atajo de recetas cuenta las recetas`() {
        assertEquals("Ninguna todavía", loggerRecipesCaption(0))
        assertEquals("Tu receta", loggerRecipesCaption(1))
        assertEquals("Tus 4 recetas", loggerRecipesCaption(4))
    }
}
