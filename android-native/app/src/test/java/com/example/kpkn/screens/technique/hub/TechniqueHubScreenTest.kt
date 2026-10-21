package com.example.kpkn.screens.technique.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.technique.hub.design.TechniqueSettingsModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        rule.onNodeWithText("Graba una serie desde el Modo visual de la sesión o analiza un vídeo de tu galería").assertExists()
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
        rule.onNodeWithContentDescription("Analizar la técnica de los vídeos").performScrollTo().performClick()
        assertEquals("bench_press", exercise)
        assertTrue(queue)
        assertEquals(false, automatic)
    }

    @Test fun headerCountsAllFinishedVideosAndRecentStripScrollsToTheTenthAnalysisAt130() {
        var opened: String? = null
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalWizardReducedMotion provides true, LocalDensity provides Density(density.density, 1.3f)) {
                TechniqueHubScreen(TechniqueHubSamples.populated, {}, {}, {}, {}, {}, {}, {}, {}, onOpenAnalysis = { opened = it })
            }
        }
        rule.onNodeWithTag("technique-hub-summary").assertIsDisplayed()
        rule.onNodeWithText("10").assertExists()
        rule.onNodeWithText("2").assertExists()
        rule.onNodeWithTag("technique-recent-recent-0").assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val description = rule.onNodeWithTag("technique-recent-recent-0").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(description.contains("analizando 42\u00A0%"))
        rule.onNodeWithTag("technique-recent-recent-9").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("recent-9", opened)
        val detail = rule.onNodeWithText("6 vídeos · último", substring = true, useUnmergedTree = true)
        detail.performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        detail.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val text = layout.layoutInput.text.text
        assertEquals(text.trimEnd().length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        repeat(layout.lineCount) { assertFalse(text, layout.isLineEllipsized(it)) }
        // El párrafo nativo puede reservar ancho vacío: cada carácter debe caber sin tolerancias.
        text.indices.filterNot { text[it].isWhitespace() }.forEach { offset ->
            val glyph = layout.getBoundingBox(offset)
            assertTrue("$text; carácter $offset: $glyph fuera de ${layout.size}",
                glyph.left >= 0f && glyph.top >= 0f && glyph.right <= layout.size.width && glyph.bottom <= layout.size.height)
        }
        assertTrue(layouts.single().layoutInput.text.text.contains("≈\u00A08 reps"))
        rule.onNodeWithText("vídeo no concluyente", substring = true).assertExists()
    }

    @Test fun videosStillAnalyzingAreVisibleBeforeThereAreAnyFinishedExercises() {
        val state = TechniqueHubSamples.empty.copy(recentVideos = TechniqueHubSamples.recent.take(1))
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                TechniqueHubScreen(state, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithTag("technique-recent-recent-0").assertIsDisplayed()
        rule.onNodeWithText("Tu técnica empieza aquí").assertDoesNotExist()
    }

    @Test fun customCalibrationUsesCommaAndNonbreakingUnits() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                TechniqueSettingsModule(TechniqueHubSamples.settings, true, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("42,5").assertExists()
        rule.onNodeWithText("45\u00A0cm").assertExists()
        rule.onNodeWithText("1\u00A0min").assertExists()
    }
}
