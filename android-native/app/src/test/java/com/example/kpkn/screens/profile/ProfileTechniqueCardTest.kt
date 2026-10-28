package com.example.kpkn.screens.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.technique.hub.TechniqueHubSamples
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class ProfileTechniqueCardTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyModuleKeepsTheVisualModeHintAndOpensTheHub() {
        var opened = false
        rule.setContent { KPKNTheme { ProfileTechniqueCard(ProfileTechniqueUiState(), { opened = true }) } }
        rule.onNodeWithText("Graba una serie desde el Modo visual de la sesión o analiza un vídeo de tu galería").assertExists()
        rule.onNodeWithTag("profile-technique-open").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(opened)
    }

    @Test fun summaryConclusionAndActionFitAt130AndThumbsOpenTheirOwnAnalysis() {
        var opened: String? = null
        var conclusionColor = Color.Unspecified
        val state = ProfileTechniqueUiState(
            recentVideos = TechniqueHubSamples.recent.take(3), analyzedVideoCount = 12,
            lastRecordedAtMs = TechniqueHubSamples.recent[1].analysis.recordedAtMs,
            latestConclusion = TechniqueHubSamples.recent[1].analysis,
        )
        rule.setContent {
            KPKNTheme {
                conclusionColor = com.example.kpkn.screens.onboarding.design.WizardColors.text
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f), LocalWizardReducedMotion provides true) {
                    Box(Modifier.padding(16.dp)) { ProfileTechniqueCard(state, {}, { opened = it }) }
                }
            }
        }
        listOf("profile-technique-summary", "profile-technique-conclusion").forEach { tag ->
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertWhole(layouts.single())
            if (tag == "profile-technique-conclusion") assertEquals(conclusionColor, layouts.single().layoutInput.style.color)
        }
        val actionLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Abrir técnica en vídeo").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(actionLayouts) }
        assertWhole(actionLayouts.single())
        rule.onNodeWithTag("technique-recent-recent-1").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("recent-1", opened)
        val lowDescription = rule.onNodeWithTag("technique-recent-recent-2").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(lowDescription.contains("Vídeo no concluyente"))
    }

    private fun assertWhole(layout: TextLayoutResult) {
        val text = layout.layoutInput.text.text
        assertEquals(text.trimEnd().length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        repeat(layout.lineCount) { assertFalse(text, layout.isLineEllipsized(it)) }
        // Como en la hoja del grabador: comprobar los caracteres, no el espacio vacío que reserva el párrafo nativo.
        text.indices.filterNot { text[it].isWhitespace() }.forEach { offset ->
            val glyph = layout.getBoundingBox(offset)
            assertTrue("$text; carácter $offset: $glyph fuera de ${layout.size}",
                glyph.left >= 0f && glyph.top >= 0f && glyph.right <= layout.size.width && glyph.bottom <= layout.size.height)
        }
    }
}
