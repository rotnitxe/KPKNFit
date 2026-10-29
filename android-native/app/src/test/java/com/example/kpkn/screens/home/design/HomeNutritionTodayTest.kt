package com.example.kpkn.screens.home.design

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.MealType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kpkn.screens.home.HomeSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `sin plan el mas de la cabecera registra una comida sin hora`() {
        val calls = mutableListOf<MealType?>()
        rule.setContent {
            HomeNutritionToday(state = HomeSamples.nutritionNoGoal(), onAddMeal = { calls += it }, onOpenDay = {})
        }

        rule.onNodeWithContentDescription("Registrar comida").performClick()

        assertEquals(listOf<MealType?>(null), calls)
    }

    @Test
    fun `con plan la barra que comiste sustituye al mas`() {
        rule.setContent {
            HomeNutritionToday(state = HomeSamples.nutritionTwoMeals(), onAddMeal = {}, onOpenDay = {})
        }

        rule.onNodeWithContentDescription("Registrar comida").assertDoesNotExist()
        assertTrue(homeShowsQuickLogger(showNutrition = true, kcalGoal = 2300))
        assertFalse(homeShowsQuickLogger(showNutrition = true, kcalGoal = null))
        assertFalse(homeShowsQuickLogger(showNutrition = false, kcalGoal = 2300))
        assertTrue(homeNutritionShowsAddAction(null))
        assertFalse(homeNutritionShowsAddAction(2300))
    }

    @Test
    fun `la barra que comiste ofrece analizar solo con texto`() {
        var analyzed = 0
        var dictated = 0
        var text by mutableStateOf("")
        rule.setContent {
            HomeQuickLoggerBar(
                text = text,
                onTextChange = { text = it },
                onAnalyze = { analyzed++ },
                onDictate = { dictated++ },
                listening = false,
            )
        }

        rule.onNodeWithText("¿Qué comiste? Escríbelo aquí", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Analizar").assertDoesNotExist()
        rule.onNodeWithContentDescription("Dictar").performClick()
        rule.runOnIdle { text = "Pollo con arroz" }
        rule.onNodeWithText("Analizar").performClick()

        assertEquals(1, dictated)
        assertEquals(1, analyzed)
    }
}
