package com.example.kpkn.screens.nutrition.recipes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.SearchConfidence
import com.example.kpkn.data.models.SearchSource
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchRow
import com.example.kpkn.screens.nutrition.design.logger.loggerSearchCaption
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class RecipeSearchRowTest {
    @get:Rule val rule = createComposeRule()

    private fun candidate(food: FoodItem) =
        FoodCandidate(food.id, food.name, 1.0, SearchConfidence.HIGH, SearchSource.LOCAL, food)

    @Test fun `sin peso final la fila dice una racion y nunca un gramo`() {
        val food = RecipeCalculator.asFood("no-weight", "Arroz con pollo", RecipeSamples.food.recipe!!.copy(finalWeightGrams = null))
        val caption = loggerSearchCaption(candidate(food), food.name)
        assertTrue(caption, caption.startsWith("1 ración"))
        assertFalse(caption, caption.contains("1 g"))
        rule.setContent {
            KPKNTheme { LoggerSearchRow(food.name, caption, 200, false, null, onAdd = {}, onOpen = {}) }
        }
        rule.onNodeWithText("1 ración · Receta").assertExists()
        rule.onNodeWithText("por 1 g").assertDoesNotExist()
    }

    @Test fun `una ficha ordinaria conserva sus gramos y su origen`() {
        val food = FoodItem(id = "rice", name = "Arroz", calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3, servingSize = 100.0)
        val caption = loggerSearchCaption(candidate(food), "arroz")
        assertTrue(caption, caption.contains(" g"))
        assertTrue(caption, caption.endsWith(" · KPKN"))
        assertFalse(caption, caption.contains("por 100 g"))
        rule.setContent {
            KPKNTheme { LoggerSearchRow(food.name, caption, 130, true, {}, onAdd = {}, onOpen = {}) }
        }
        rule.onNodeWithText(caption).assertExists()
        rule.onNodeWithText("por ración").assertDoesNotExist()
    }
}
