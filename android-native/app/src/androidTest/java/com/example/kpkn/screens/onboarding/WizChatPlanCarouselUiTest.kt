package com.example.kpkn.screens.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class WizChatPlanCarouselUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun candidate(id: String, title: String) = SetupPlanCandidate(
        id = id,
        title = title,
        subtitle = "16 semanas · 4 días",
        description = "Descripción sencilla de $title",
        source = "PROTOCOL",
        reasons = listOf("Encaja con tus 4 días de $title"),
        details = "Detalles técnicos de $title",
    )

    @Test
    fun carouselShowsTheMainCardWithItsReasonsAndAPeekOfTheNext() {
        composeRule.setContent {
            WizChatPlanCarousel(listOf(candidate("a", "Plan Alfa"), candidate("b", "Plan Beta")), WizChatTokens.blue) { }
        }
        composeRule.onNodeWithContentDescription("Plan Plan Alfa").assertIsDisplayed()
        composeRule.onNodeWithText("Plan Alfa").assertIsDisplayed()
        composeRule.onNodeWithText("Descripción sencilla de Plan Alfa").assertIsDisplayed()
        composeRule.onNodeWithText("✓ Encaja con tus 4 días de Plan Alfa").assertIsDisplayed()
        composeRule.onNodeWithText("Plan Beta").assertExists()
    }

    @Test
    fun chooseButtonSendsTheExactCandidateId() {
        var chosen: String? = null
        composeRule.setContent {
            WizChatPlanCarousel(listOf(candidate("a", "Plan Alfa"), candidate("b", "Plan Beta")), WizChatTokens.blue) { chosen = it }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Elegir este plan")[0].performClick()
        composeRule.waitForIdle()

        assertEquals("a", chosen)
    }

    @Test
    fun swipingTheCarouselNeverSelectsAPlan() {
        var chosen: String? = null
        composeRule.setContent {
            WizChatPlanCarousel(listOf(candidate("a", "Plan Alfa"), candidate("b", "Plan Beta")), WizChatTokens.blue) { chosen = it }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Plan Plan Alfa").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertNull(chosen)
    }

    @Test
    fun detailsExpandPerCardWithoutSelecting() {
        var chosen: String? = null
        composeRule.setContent {
            WizChatPlanCarousel(listOf(candidate("a", "Plan Alfa")), WizChatTokens.blue) { chosen = it }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Ver detalles").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Detalles técnicos de Plan Alfa").assertIsDisplayed()
        assertNull(chosen)
    }
}
