package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
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

        // Refinamiento 02: pantalla completa con eyebrow «PAUSA ENTRE SERIES», la última serie en su bloque y la fila del sugerido.
        composeRule.onNodeWithTag("rest-full-eyebrow", useUnmergedTree = true).assertTextEquals("PAUSA ENTRE SERIES")
        composeRule.onNodeWithText("ÚLTIMA", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("80 × 8", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Descanso sugerido", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Restar 15 segundos", useUnmergedTree = true).assertExists()
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

        // Refinamiento 02: sin insignia «manual»; el tiempo está en el eclipse y los controles principales en pantalla completa.
        composeRule.onNodeWithTag("rest-eclipse-clock", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("0:54")
        composeRule.onNodeWithText("Descanso sugerido", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("rest-minus", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("rest-pause", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("rest-plus", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("rest-end", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Restar 15 segundos", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription("Sumar 15 segundos", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Terminar descanso", useUnmergedTree = true).performClick()
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

        // Refinamiento 02: eyebrow «DESCANSO + FEEDBACK», el ejercicio, el reloj pequeño con «0:42» y el feedback en la misma superficie.
        composeRule.onNodeWithTag("rest-full-eyebrow", useUnmergedTree = true).assertTextEquals("DESCANSO + FEEDBACK")
        composeRule.onNodeWithText("Press banca", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("rest-feedback-clock", useUnmergedTree = true).assert(hasAnyDescendant(hasText("0:42")))
        composeRule.onNodeWithText("Feedback post ejercicio", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("Saltar descanso").assertCountEquals(0)
    }
}
