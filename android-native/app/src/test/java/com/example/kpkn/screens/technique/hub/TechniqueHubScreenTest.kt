package com.example.kpkn.screens.technique.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xxhdpi", application = android.app.Application::class)
class TechniqueHubScreenTest {
    @get:Rule val rule = createComposeRule()
    @Test fun emptyHubKeepsImportActionAndExplainsWhereVideosComeFrom() {
        var opened = false
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                TechniqueHubScreen(TechniqueHubSamples.empty, {}, { opened = true }, {}, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("Graba una serie desde la sesión o analiza un vídeo de tu galería").assertExists()
        rule.onNodeWithTag("technique-hub-import").performClick()
        assertTrue(opened)
    }
    @Test fun exercisePendingQueueAndAutomaticSettingHaveIndependentCallbacks() {
        var exercise: String? = null
        var queue = false
        var automatic: Boolean? = null
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                TechniqueHubScreen(TechniqueHubSamples.populated, {}, {}, { exercise = it }, { queue = true }, { automatic = it }, {}, {}, {})
            }
        }
        rule.onNodeWithText("Press de banca con barra").performClick()
        rule.onNodeWithTag("technique-hub-pending").performClick()
        rule.onNodeWithContentDescription("Analizar la técnica de los vídeos").performClick()
        assertEquals("bench_press", exercise)
        assertTrue(queue)
        assertEquals(false, automatic)
    }
}
