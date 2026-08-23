package com.example.kpkn.screens.sessioneditor

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * Dismisses editor text input before a modal assistant is composed. Keeping
 * this as one operation prevents Android's insertion handle from surviving
 * underneath the overlay and makes the behavior easy to exercise in a UI test.
 */
internal fun clearSessionEditorAssistantFocus(
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
) {
    focusManager.clearFocus(force = true)
    keyboardController?.hide()
}
