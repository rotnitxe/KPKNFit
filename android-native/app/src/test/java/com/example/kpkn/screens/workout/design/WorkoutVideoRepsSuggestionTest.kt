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

    @Test fun medium_confidence_offers_explicit_approximate_use_and_low_confidence_never_claims_a_number() {
        var used: SetVideoAnalysisState.Suggest? = null
        val value = SetVideoAnalysisUi("video", SetVideoAnalysisState.Suggest(8, 0, 0, CountConfidence.MEDIA))
        rule.setContent {
            WorkoutVideoRepsSuggestion(value, onUse = { used = it }, onCorrect = {}, onMarkFailure = {}, onOpenAnalysis = {})
        }
        rule.runOnIdle { assertEquals(null, used) }
        rule.onNodeWithText("Usar").assertDoesNotExist()
        rule.onNodeWithText("Usar ≈ 8").performClick()
        rule.runOnIdle { assertEquals(value.state, used) }
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
            SetVideoAnalysisState.Analyzing(42) to "Analizando el vídeo… 42\u00A0%",
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

    @Test fun medium_mismatch_uses_the_same_patch_as_high_confidence_without_a_separate_failure_action() {
        var patch: com.example.kpkn.screens.workout.CompletedSetPatch? = null
        var opened: String? = null
        rule.setContent {
            WorkoutCompletedVideoSuggestion(
                SetVideoAnalysisUi("medium-video", SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.MEDIA)),
                onPatch = { patch = it }, onOpenAnalysis = { opened = it },
            )
        }
        rule.runOnIdle { assertEquals(null, patch) }
        rule.onNodeWithText("Corregir").assertDoesNotExist()
        rule.onNodeWithText("Marcar fallo").assertDoesNotExist()
        rule.onNodeWithText("Corregir a ≈ 7").performClick()
        rule.runOnIdle {
            assertEquals(7, patch?.edit?.metricValue)
            assertEquals(1, patch?.partialReps)
            assertEquals(true, patch?.reachedFailure)
        }
        rule.onNodeWithText("Ver análisis").performClick()
        rule.runOnIdle { assertEquals("medium-video", opened) }
    }

    @Test fun equal_medium_counts_only_offer_analysis_and_never_a_correction() {
        rule.setContent {
            WorkoutVideoRepsSuggestion(
                SetVideoAnalysisUi("v", SetVideoAnalysisState.Mismatch(8, 8, 1, 0, CountConfidence.MEDIA, loggedPartials = 1)),
                onUse = {}, onCorrect = { error("Los conteos coinciden") }, onMarkFailure = {}, onOpenAnalysis = {},
            )
        }
        rule.onNodeWithText("Corregir a ≈ 8").assertDoesNotExist()
        rule.onNodeWithText("Ver análisis").assertExists()
    }

    @Test fun failed_state_exposes_distinct_analysis_and_explicit_retry() {
        var retried: String? = null
        var opened: String? = null
        rule.setContent {
            WorkoutVideoRepsSuggestion(
                SetVideoAnalysisUi("failed-video", SetVideoAnalysisState.Failed("decode")),
                {}, {}, {}, onOpenAnalysis = { opened = it }, onRetryAnalysis = { retried = it },
            )
        }
        rule.runOnIdle { assertEquals(null, retried) }
        rule.onNodeWithText("Ver análisis").performClick()
        rule.runOnIdle { assertEquals("failed-video", opened) }
        rule.onNodeWithText("Reintentar").performClick()
        rule.runOnIdle { assertEquals("failed-video", retried) }
    }
}
