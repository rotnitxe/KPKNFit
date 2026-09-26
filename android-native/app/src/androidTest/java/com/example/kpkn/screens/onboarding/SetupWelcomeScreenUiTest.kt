package com.example.kpkn.screens.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Cobertura de la pantalla de bienvenida productiva, preservada íntegramente
 * desde el retirado `WizChatComponentsUiTest` (ese archivo mezclaba tests de
 * bienvenida con tests exclusivos de los componentes de chat muertos).
 */
class SetupWelcomeScreenUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun welcomeShowsThePhoneAndStartsOnlyOnExplicitTap() {
        var started = false
        composeRule.setContent { SetupWelcomeScreen(onStart = { started = true }) }
        composeRule.onNodeWithContentDescription("Vista de un teléfono KPKN con pantalla blanca").assertIsDisplayed()
        composeRule.onNodeWithText("Comenzar").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(true, started)
        composeRule.onNodeWithText("WIZCHAT").assertDoesNotExist()
    }

    @Test
    fun welcomeCarouselOffersSelectableViews() {
        composeRule.setContent { SetupWelcomeScreen(onStart = {}) }
        composeRule.onNodeWithContentDescription("Vista 3 de 3").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Vista 3 de 3").assertIsSelected()
    }
}
