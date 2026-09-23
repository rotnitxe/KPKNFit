package com.example.kpkn.screens.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.swipeLeft
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.WizChatMessage
import com.example.kpkn.domain.onboarding.WizChatStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
                accent = WizChatTokens.blue,
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
    fun shortAcceptedAnswerDoesNotShowAnExtraEditButton() {
        composeRule.setContent {
            WizChatMessageBubble(WizChatMessage("answer:age", WizChatStage.PROFILE, true, "18 años")) { }
        }
        composeRule.onNodeWithText("18 años").assertIsDisplayed()
        composeRule.onNodeWithText("Cambiar").assertDoesNotExist()
    }

    @Test
    fun compactChoicesRemainAvailableInsideANarrowMessage() {
        composeRule.setContent {
            Box(Modifier.width(304.dp)) {
                WizChatChoiceGroup(listOf("Mujer", "Hombre", "Otro", "Prefiero no responder"),
                    multi = false, accent = WizChatTokens.blue, compact = true, onSelect = {})
            }
        }
        composeRule.onNodeWithText("Prefiero no responder").assertIsDisplayed()
        composeRule.onNodeWithText("Mujer").assertIsDisplayed()
    }

    @Test
    fun welcomeCarouselOffersSelectableViews() {
        composeRule.setContent { SetupWelcomeScreen(onStart = {}) }
        composeRule.onNodeWithContentDescription("Vista 3 de 3").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Vista 3 de 3").assertIsSelected()
    }

    @Test
    fun numberWheelFirstTapOnASideNumberSendsThatExactValueOnce() {
        var accepted: Double? = null
        var sends = 0
        composeRule.setContent {
            WizChatNumberWheel(
                values = listOf(13.0, 14.0, 15.0),
                selected = 14.0,
                unit = "años",
                accent = Color(0xFFFFAB66),
                onSettled = { accepted = it; sends++ },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("15").performClick()
        composeRule.waitForIdle()

        assertEquals(15.0, accepted!!, 0.0001)
        assertEquals(1, sends)
    }

    @Test
    fun numberWheelScrollChangesOnlyTheCandidateWithoutSending() {
        var accepted: Double? = null
        composeRule.setContent {
            WizChatNumberWheel(
                values = (13..80).map(Int::toDouble),
                selected = 40.0,
                unit = "años",
                accent = Color(0xFFFFAB66),
                onSettled = { accepted = it },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Seleccionar años").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertNull(accepted)
    }

    @Test
    fun numberWheelHidesTheRedundantInstructionText() {
        composeRule.setContent {
            WizChatNumberWheel(
                values = listOf(13.0, 14.0),
                selected = null,
                unit = "años",
                accent = Color(0xFFFFAB66),
                onSettled = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Toca el valor central para elegir", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Desliza y toca el valor central", substring = true).assertDoesNotExist()
    }

    @Test
    fun weightRuleTapOnAVisibleNumberSendsThatExactValue() {
        var confirmed: Double? = null
        composeRule.setContent {
            WizChatWeightRule(unit = "kg", selected = 70.0, accent = WizChatTokens.blue, onConfirm = { confirmed = it })
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("72").performClick()
        composeRule.waitForIdle()

        assertEquals(72.0, confirmed!!, 0.0001)
    }

    @Test
    fun weightRuleDragAdjustsWithTenthsWithoutSending() {
        var confirmed: Double? = null
        composeRule.setContent {
            WizChatWeightRule(unit = "kg", selected = 70.0, accent = WizChatTokens.blue, onConfirm = { confirmed = it })
        }
        composeRule.waitForIdle()
        val before = composeRule.onNode(hasText(" kg", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text

        composeRule.onNode(hasContentDescription("Regla de peso en kilogramos")).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertNull(confirmed)
        val after = composeRule.onNode(hasText(" kg", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assertNotEquals(before, after)
    }

    @Test
    fun weightRuleCenterValueShowsExactTenthAndConfirmsIt() {
        var confirmed: Double? = null
        composeRule.setContent {
            WizChatWeightRule(unit = "kg", selected = 71.3, accent = WizChatTokens.blue, onConfirm = { confirmed = it })
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("71,3 kg").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(71.3, confirmed!!, 0.0001)
    }
}
