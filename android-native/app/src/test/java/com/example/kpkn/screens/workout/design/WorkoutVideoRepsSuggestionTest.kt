package com.example.kpkn.screens.workout.design

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutVideoRepsSuggestionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun high_confidence_requires_explicit_use_and_never_applies_on_composition() {
        var uses = 0
        val value = SetVideoAnalysisUi("video", SetVideoAnalysisState.Suggest(8, 1, 0, CountConfidence.ALTA))
        rule.setContent {
            WorkoutVideoRepsSuggestion(value, onUse = { uses++ }, onCorrect = {}, onMarkFailure = {}, onOpenAnalysis = {})
        }
        rule.runOnIdle { assertEquals(0, uses) }
        rule.onNodeWithText("Usar").performClick()
        rule.runOnIdle { assertEquals(1, uses) }
    }

    @Test fun medium_confidence_offers_review_and_low_confidence_never_claims_a_number() {
        var opened: String? = null
        val value = SetVideoAnalysisUi("video", SetVideoAnalysisState.Suggest(8, 0, 0, CountConfidence.MEDIA))
        rule.setContent {
            WorkoutVideoRepsSuggestion(value, onUse = {}, onCorrect = {}, onMarkFailure = {}, onOpenAnalysis = { opened = it })
        }
        rule.onNodeWithText("Usar").assertDoesNotExist()
        rule.onNodeWithText("Revisar").performClick()
        rule.runOnIdle { assertEquals("video", opened) }
        assertEquals("Vídeo no concluyente", workoutVideoRepsText(SetVideoAnalysisState.Suggest(27, 4, 2, CountConfidence.BAJA)))
    }

    @Test fun correction_and_failure_are_distinct_explicit_actions() {
        val patches = mutableListOf<com.example.kpkn.screens.workout.CompletedSetPatch>()
        var opened: String? = null
        rule.setContent {
            WorkoutCompletedVideoSuggestion(
                SetVideoAnalysisUi("right-side-video", SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.ALTA)),
                onPatch = { patches += it }, onOpenAnalysis = { opened = it },
            )
        }
        rule.runOnIdle { assertEquals(0, patches.size) }
        rule.onNodeWithText("Corregir").performClick()
        rule.runOnIdle {
            assertEquals(7, patches.single().edit.metricValue)
            assertEquals(1, patches.single().partialReps)
            assertEquals(true, patches.single().reachedFailure)
        }
        rule.onNodeWithText("Marcar fallo").performClick()
        rule.runOnIdle {
            assertEquals(null, patches.last().edit.metricValue)
            assertEquals(null, patches.last().partialReps)
            assertEquals(true, patches.last().reachedFailure)
        }
        rule.onNodeWithContentDescription("Ver análisis").performClick()
        rule.runOnIdle { assertEquals("right-side-video", opened) }
    }

    @Test fun failed_attempts_alone_never_offer_to_replace_the_count_with_zero() {
        var failures = 0
        rule.setContent {
            WorkoutVideoRepsSuggestion(
                SetVideoAnalysisUi("v", SetVideoAnalysisState.Suggest(0, 0, 1, CountConfidence.ALTA)),
                onUse = { error("No debe ofrecer Usar") }, onCorrect = {},
                onMarkFailure = { failures++ }, onOpenAnalysis = {},
            )
        }
        rule.onNodeWithText("Usar").assertDoesNotExist()
        rule.onNodeWithText("El vídeo muestra 1 intento al fallo").assertExists()
        rule.onNodeWithText("Marcar fallo").performClick()
        rule.runOnIdle { assertEquals(1, failures) }
    }

    @Test fun zero_mismatch_only_marks_failure_and_preserves_registered_counts() {
        var patch: com.example.kpkn.screens.workout.CompletedSetPatch? = null
        rule.setContent {
            WorkoutCompletedVideoSuggestion(
                SetVideoAnalysisUi("v", SetVideoAnalysisState.Mismatch(8, 0, 0, 2, CountConfidence.ALTA)),
                onPatch = { patch = it }, onOpenAnalysis = {},
            )
        }
        rule.onNodeWithText("Corregir").assertDoesNotExist()
        rule.onNodeWithText("Marcar fallo").performClick()
        rule.runOnIdle {
            assertEquals(null, patch?.edit?.metricValue)
            assertEquals(null, patch?.partialReps)
            assertEquals(true, patch?.reachedFailure)
        }
    }

    @Test fun passive_states_and_low_confidence_never_expose_apply_actions() {
        val state = androidx.compose.runtime.mutableStateOf<com.example.kpkn.screens.workout.SetVideoAnalysisState>(SetVideoAnalysisState.Analyzing(42))
        rule.setContent {
            WorkoutVideoRepsSuggestion(SetVideoAnalysisUi("v", state.value), {}, {}, {}, {})
        }
        val cases = listOf(
            SetVideoAnalysisState.Analyzing(42) to "Analizando el vídeo… 42 %",
            SetVideoAnalysisState.Agrees(8) to "El vídeo confirma 8",
            SetVideoAnalysisState.Unsupported("Esta serie se registra por tiempo") to "Esta serie se registra por tiempo",
            SetVideoAnalysisState.Inconclusive to "Vídeo no concluyente",
            SetVideoAnalysisState.Suggest(27, 4, 2, CountConfidence.BAJA) to "Vídeo no concluyente",
            SetVideoAnalysisState.Mismatch(8, 27, 4, 2, CountConfidence.BAJA) to "Vídeo no concluyente",
        )
        cases.forEach { (next, text) ->
            rule.runOnIdle { state.value = next }
            rule.onNodeWithText(text).assertExists()
            rule.onNodeWithText("Usar").assertDoesNotExist()
            rule.onNodeWithText("Corregir").assertDoesNotExist()
            rule.onNodeWithText("Marcar fallo").assertDoesNotExist()
        }
    }

    @Test fun medium_mismatch_requires_review_even_when_it_detects_failure() {
        var opened: String? = null
        rule.setContent {
            WorkoutVideoRepsSuggestion(
                SetVideoAnalysisUi("medium-video", SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.MEDIA)),
                onUse = { error("No debe aplicar") }, onCorrect = { error("No debe corregir") },
                onMarkFailure = { error("No debe marcar fallo sin revisar") }, onOpenAnalysis = { opened = it },
            )
        }
        rule.onNodeWithText("Corregir").assertDoesNotExist()
        rule.onNodeWithText("Marcar fallo").assertDoesNotExist()
        rule.onNodeWithText("Revisar").performClick()
        rule.runOnIdle { assertEquals("medium-video", opened) }
    }
}
