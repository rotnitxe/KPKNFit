package com.example.kpkn.screens.sessioneditor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Lightweight Compose smoke for F3.7 invalid-zone feedback (no full SessionEditorScreen).
 */
class SessionEditorInvalidDropBannerUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun invalidDropBanner_showsAndHidesWithVisibilityFlag() {
        var visible by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    SessionEditorInvalidDropBanner(
                        visible = visible,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("session_editor_invalid_drop_banner").assertDoesNotExist()
        composeRule.runOnUiThread { visible = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("session_editor_invalid_drop_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Zona no válida").assertIsDisplayed()
        composeRule.runOnUiThread { visible = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("session_editor_invalid_drop_banner").assertDoesNotExist()
    }
}
