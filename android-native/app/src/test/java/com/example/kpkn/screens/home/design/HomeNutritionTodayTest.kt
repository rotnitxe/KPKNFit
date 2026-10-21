package com.example.kpkn.screens.home.design

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.MealType
import com.example.kpkn.screens.home.HomeSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Nutrición de hoy»: tocar una comida abre el registro; sin meta, «Definir objetivos» en vez de anillos; con meta, se lee si se pasa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeNutritionTodayTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `tocar Cena llama a onAddMeal con la cena`() {
        val calls = mutableListOf<MealType?>()
        rule.setContent {
            HomeNutritionToday(
                state = HomeSamples.nutritionTwoMeals(),
                onAddMeal = { calls += it },
                onOpenDay = {},
            )
        }

        rule.onNodeWithTag("home-meal-DINNER").performClick()

        assertEquals(listOf(MealType.DINNER), calls)
    }

    @Test
    fun `sin meta no pinta los anillos y ofrece definir objetivos`() {
        rule.setContent {
            HomeNutritionToday(state = HomeSamples.nutritionNoGoal(), onAddMeal = {}, onOpenDay = {})
        }

        rule.onNodeWithText("Aún no tienes objetivos.").assertExists()
        rule.onNodeWithText("Definir objetivos").assertExists()
        rule.onNodeWithTag(HOME_NUTRITION_RINGS_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `definir objetivos llama a la lambda`() {
        var defined = 0
        rule.setContent {
            HomeNutritionToday(
                state = HomeSamples.nutritionNoGoal(),
                onAddMeal = {},
                onOpenDay = {},
                onDefineGoals = { defined++ },
            )
        }

        rule.onNodeWithText("Definir objetivos").performClick()

        assertEquals(1, defined)
    }

    @Test
    fun `con meta se ven los anillos y no se ofrece definir objetivos`() {
        rule.setContent {
            HomeNutritionToday(state = HomeSamples.nutritionTwoMeals(), onAddMeal = {}, onOpenDay = {}, onDefineGoals = {})
        }

        // Los anillos van dentro de la fila clicable: su etiqueta solo aparece en el árbol sin fusionar.
        rule.onNodeWithTag(HOME_NUTRITION_RINGS_TAG, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Definir objetivos").assertDoesNotExist()
    }

    @Test
    fun `por encima de la meta se lee te pasaste`() {
        rule.setContent {
            HomeNutritionToday(state = HomeSamples.nutritionOverGoal(), onAddMeal = {}, onOpenDay = {})
        }

        rule.onNodeWithText("te pasaste", substring = true).assertExists()
    }

    @Test
    fun `el mas de la cabecera registra una comida sin hora`() {
        val calls = mutableListOf<MealType?>()
        rule.setContent {
            HomeNutritionToday(state = HomeSamples.nutritionTwoMeals(), onAddMeal = { calls += it }, onOpenDay = {})
        }

        rule.onNodeWithContentDescription("Registrar comida").performClick()

        assertEquals(listOf<MealType?>(null), calls)
    }
}
