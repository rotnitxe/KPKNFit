package com.example.kpkn.screens.sessioneditor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class SessionEditorFocusUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun assistantOpenAndClose_leaveGroupNameWithoutFocus() {
        composeRule.setContent {
            MaterialTheme {
                var assistantVisible by remember { mutableStateOf(false) }
                var groupName by remember { mutableStateOf("") }
                val focusManager = LocalFocusManager.current
                val keyboardController = LocalSoftwareKeyboardController.current
                Column {
                    BasicTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("group-name"),
                    )
                    Button(
                        onClick = {
                            clearSessionEditorAssistantFocus(focusManager, keyboardController)
                            assistantVisible = true
                        },
                    ) {
                        Text("Abrir asistente")
                    }
                    if (assistantVisible) {
                        Box(modifier = Modifier.testTag("assistant-sheet")) {
                            Text("Asistente")
                            Button(onClick = { assistantVisible = false }) {
                                Text("Cerrar asistente")
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("group-name").performTextInput("Grupo")
        composeRule.onNodeWithTag("group-name").assertIsFocused()
        composeRule.onNodeWithText("Abrir asistente").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("assistant-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("group-name").assertIsNotFocused()

        composeRule.onNodeWithText("Cerrar asistente").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group-name").assertIsNotFocused()
    }
}
