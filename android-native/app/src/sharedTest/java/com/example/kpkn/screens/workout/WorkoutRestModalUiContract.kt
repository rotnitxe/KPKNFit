package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

abstract class WorkoutRestModalUiContract {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rest_modal_shows_last_set_and_rest_choice_actions() {
        composeRule.setContent {
            MaterialTheme {
                RestTimerOverlay(
                    state = WorkoutRestModalState(
                        exerciseName = "Press banca",
                        plannedSeconds = 90,
                        suggestedSeconds = 120,
                        activeSeconds = 120,
                    ),
                    remainingSeconds = 64,
                    hazeState = HazeState(),
                    pendingRestSuggestion = PendingRestSuggestion(
                        plannedSeconds = 90,
                        adaptiveSeconds = 120,
                        exerciseName = "Press banca",
                        exerciseId = "press",
                        lastSet = CompletedSet(id = "last", weight = 80.0, reps = 8, rpe = 9.0),
                        advancedFeedback = null,
                    ),
                    onDecrease = {},
                    onIncrease = {},
                    onSkip = {},
                    onUseAdaptive = {},
                )
            }
        }

        composeRule.onNodeWithText("DESCANSO").assertExists()
        composeRule.onNodeWithText("ÚLTIMA SERIE").assertExists()
        composeRule.onNodeWithText("DESCANSO SUGERIDO").assertExists()
        composeRule.onNodeWithText("Ajustar tiempo").assertExists()
    }

    @Test
    fun rest_modal_shows_manual_badge_and_primary_controls() {
        var decreases = 0
        var increases = 0
        var skips = 0
        composeRule.setContent {
            MaterialTheme {
                RestTimerOverlay(
                    state = WorkoutRestModalState(
                        exerciseName = "Sentadilla frontal",
                        plannedSeconds = 120,
                        suggestedSeconds = 105,
                        activeSeconds = 105,
                        isManualOverride = true,
                    ),
                    remainingSeconds = 54,
                    hazeState = HazeState(),
                    pendingRestSuggestion = PendingRestSuggestion(
                        plannedSeconds = 120,
                        adaptiveSeconds = 105,
                        exerciseName = "Sentadilla frontal",
                        exerciseId = "squat",
                        lastSet = CompletedSet(id = "last", weight = 100.0, reps = 5),
                        advancedFeedback = null,
                    ),
                    onDecrease = { decreases++ },
                    onIncrease = { increases++ },
                    onSkip = { skips++ },
                    onUseAdaptive = {},
                )
            }
        }

        composeRule.onNodeWithText("de 1:45 · manual").assertIsDisplayed()
        composeRule.onNodeWithText("DESCANSO SUGERIDO").assertExists()
        composeRule.onNodeWithText("Ajustar tiempo").assertExists()
        composeRule.onNodeWithText("Terminar descanso").assertExists()
        composeRule.onNodeWithContentDescription("Restar 15 segundos").performClick()
        composeRule.onNodeWithContentDescription("Sumar 15 segundos").performClick()
        composeRule.onNodeWithText("Terminar descanso").performClick()
        composeRule.runOnIdle { assertEquals(1, decreases); assertEquals(1, increases); assertEquals(1, skips) }
    }

    @Test
    fun rest_modal_can_embed_post_exercise_feedback_without_second_popup() {
        composeRule.setContent {
            MaterialTheme {
                RestTimerOverlay(
                    state = WorkoutRestModalState(
                        exerciseName = "Press banca",
                        plannedSeconds = 90,
                        suggestedSeconds = 90,
                        activeSeconds = 90,
                    ),
                    remainingSeconds = 42,
                    hazeState = HazeState(),
                    onDecrease = {},
                    onIncrease = {},
                    onSkip = {},
                    postExerciseFeedbackContent = {
                        Text("Feedback post ejercicio")
                    },
                )
            }
        }

        // Cabecera del feedback (LV6, F2): el ejercicio y «Ejercicio terminado · Descanso 0:42». El «Saltar descanso» lo pone el panel.
        composeRule.onNodeWithText("Press banca").assertExists()
        composeRule.onNodeWithText("Ejercicio terminado · Descanso 0:42").assertExists()
        composeRule.onNodeWithText("Feedback post ejercicio").assertExists()
        composeRule.onAllNodesWithText("Saltar descanso").assertCountEquals(0)
    }
}
