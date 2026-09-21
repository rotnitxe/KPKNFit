package com.example.kpkn.screens.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class WizChatComponentsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun choiceGroupPublishesOnlyTheSelectedOption() {
        var selected = ""
        composeRule.setContent {
            WizChatChoiceGroup(
                options = listOf("Sí", "No"),
                multi = false,
                accent = WizChatTokens.orange,
                onSelect = { selected = it },
            )
        }

        composeRule.onNodeWithText("Sí").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals("Sí", selected)
    }

    @Test
    fun numberWheelDoesNotAcceptItsInitialCenterUntilConfirmed() {
        var accepted: Double? = null
        composeRule.setContent {
            WizChatNumberWheel(
                values = listOf(13.0, 14.0, 15.0),
                selected = 14.0,
                unit = "años",
                accent = Color(0xFFFFAB66),
                onSettled = { accepted = it },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Seleccionar años").assertIsDisplayed()
        assertNull(accepted)
        composeRule.onNodeWithContentDescription("Seleccionar años").performClick()
        composeRule.waitForIdle()

        assertEquals(14.0, accepted)
    }
}
