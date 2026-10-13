package com.example.kpkn.screens.home.design

import androidx.compose.ui.test.junit4.createComposeRule
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

/** «Nutrición de hoy»: tocar una comida abre el registro en esa comida, y la meta se lee como «sin objetivo» o «por encima». */
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
    fun `sin meta se lee sin objetivo`() {
        rule.setContent {
            HomeNutritionToday(state = HomeSamples.nutritionNoGoal(), onAddMeal = {}, onOpenDay = {})
        }

        rule.onNodeWithText("sin objetivo", substring = true).assertExists()
    }

    @Test
    fun `por encima de la meta se lee por encima`() {
        rule.setContent {
            HomeNutritionToday(state = HomeSamples.nutritionOverGoal(), onAddMeal = {}, onOpenDay = {})
        }

        rule.onNodeWithText("por encima", substring = true).assertExists()
    }
}
