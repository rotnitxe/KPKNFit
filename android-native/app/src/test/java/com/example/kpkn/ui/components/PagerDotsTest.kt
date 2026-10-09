package com.example.kpkn.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El indicador de páginas público: cada punto se anuncia como «Página N de M» y, con [PagerDots.onSelect], cambia de página. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class PagerDotsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun interactiveDotsAnnouncePageAndSelectIt() {
        var selected = -1
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                PagerDots(count = 4, current = 1, onSelect = { selected = it })
            }
        }
        rule.onNodeWithContentDescription("Página 2 de 4").assertExists()
        rule.onNodeWithContentDescription("Página 3 de 4").performClick()
        rule.waitForIdle()
        assertEquals(2, selected)
    }

    @Test
    fun staticDotsOnlyReportTheCurrentPage() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                PagerDots(count = 4, current = 2)
            }
        }
        rule.onNodeWithContentDescription("Página 3 de 4").assertExists()
    }
}
