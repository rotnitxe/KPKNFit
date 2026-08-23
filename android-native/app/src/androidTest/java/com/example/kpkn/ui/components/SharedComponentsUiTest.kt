package com.example.kpkn.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SharedComponentsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun swipeDelete_revealsCompactActionAndInvokesDelete() {
        var deleted = false
        composeRule.setContent {
            MaterialTheme {
                SwipeToDeleteCard(onDelete = { deleted = true }) {
                    Box(modifier = androidx.compose.ui.Modifier.height(72.dp)) {
                        androidx.compose.material3.Text("Exercise")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Exercise").performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Eliminar").assertIsDisplayed()
        composeRule.onNodeWithText("Eliminar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Eliminar").performClick()
        composeRule.waitForIdle()

        assertTrue(deleted)
    }
}
