package com.example.kpkn.screens.home.design

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.screens.home.HomeSamples
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Cuerpo» de Inicio: con tendencia, la cifra es la tendencia y lleva su etiqueta; sin ella, el último peso, como antes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeBodyTrendLabelTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `con tendencia se ve la etiqueta Tendencia y la descripcion dice la tendencia y el ultimo pesaje`() {
        val state = HomeSamples.bodyWithTrendAndGoal().copy(weightKg = 78.4, trendKg = 78.0)
        rule.setContent { HomeBodyToday(state = state, onOpen = {}) }

        rule.onNodeWithText("Tendencia", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Tendencia del peso: 78,0 kilos, último pesaje 78,4 kilos", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `sin tendencia no hay etiqueta y la cifra es el ultimo peso`() {
        val state = HomeSamples.bodyWithTrendAndGoal().copy(weightKg = 78.4, trendKg = null)
        rule.setContent { HomeBodyToday(state = state, onOpen = {}) }

        rule.onNodeWithText("Tendencia", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Peso: 78,4 kilos", substring = true).assertIsDisplayed()
    }
}
