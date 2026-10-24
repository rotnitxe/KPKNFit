package com.example.kpkn.screens.nutrition.recipes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchRow
import com.example.kpkn.screens.nutrition.design.logger.loggerSearchBasisLabel
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class RecipeSearchRowTest {
    @get:Rule val rule = createComposeRule()
    @Test fun `sin peso final la busqueda dice por racion y nunca por un gramo`() {
        val food = RecipeCalculator.asFood("no-weight", "Arroz con pollo", RecipeSamples.food.recipe!!.copy(finalWeightGrams = null))
        rule.setContent { KPKNTheme { LoggerSearchRow(food.name, 200, 1, {}, basisLabel = loggerSearchBasisLabel(food)) } }
        rule.onNodeWithText("por ración").assertExists()
        rule.onNodeWithText("por 1 g").assertDoesNotExist()
    }
    @Test fun `una ficha ordinaria conserva la base en gramos de siempre`() {
        rule.setContent { KPKNTheme { LoggerSearchRow("Arroz", 130, 100, {}) } }
        rule.onNodeWithText("por 100 g").assertExists()
        rule.onNodeWithText("por ración").assertDoesNotExist()
    }
}
