package com.example.kpkn.screens.technique.importer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.technique.hub.TechniqueHubSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xxhdpi", application = android.app.Application::class)
class VideoImportContentTest {
    @get:Rule val rule = createComposeRule()
    @Test fun analyzeCommitsTheActiveNumberCellBeforeCapturingTheLoad() {
        val state = mutableStateOf(TechniqueHubSamples.importing.copy(weightText = "80"))
        var analyzedLoad: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                VideoImportContent(
                    state = state.value,
                    recentExercises = emptyList(),
                    onPickVideo = {},
                    onChooseRecent = {},
                    onOpenCatalog = {},
                    onWeightChange = { state.value = state.value.copy(weightText = it) },
                    onAnalyze = { analyzedLoad = state.value.weightText },
                )
            }
        }
        rule.onNodeWithTag("technique-import-load").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("95")
        assertEquals("80", state.value.weightText)
        rule.onNodeWithTag("technique-import-analyze").performClick()
        assertEquals("95", analyzedLoad)
    }
}
