package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRestModalUiTest {

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
        composeRule.onNodeWithText("Último set").assertExists()
        composeRule.onNodeWithText("PLAN DE SESIÓN").assertExists()
        composeRule.onNodeWithText("Usar Dinámico").assertExists()
    }

    @Test
    fun rest_modal_shows_reliability_fallback_messages() {
        composeRule.setContent {
            MaterialTheme {
                RestTimerOverlay(
                    state = WorkoutRestModalState(
                        exerciseName = "Press banca",
                        plannedSeconds = 90,
                        suggestedSeconds = 120,
                        activeSeconds = 120,
                        notificationsEnabled = false,
                        exactAlarmGranted = false,
                        soundReady = false,
                    ),
                    remainingSeconds = 20,
                    hazeState = HazeState(),
                    onDecrease = {},
                    onIncrease = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Alertas del descanso").assertExists()
        composeRule.onNodeWithText("Notificaciones desactivadas").assertExists()
        composeRule.onNodeWithText("Alarma exacta no disponible").assertExists()
        composeRule.onNodeWithText("Audio silencioso").assertExists()
    }

    @Test
    fun rest_modal_shows_manual_badge_and_primary_controls() {
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
                    onDecrease = {},
                    onIncrease = {},
                    onSkip = {},
                    onUseAdaptive = {},
                )
            }
        }

        composeRule.onNodeWithText("Manual").assertExists()
        composeRule.onNodeWithText("PLAN DE SESIÓN").assertExists()
        composeRule.onNodeWithText("Usar Dinámico").assertExists()
        composeRule.onNodeWithText("Ajustar tiempo").assertExists()
        composeRule.onNodeWithText("Saltar descanso").assertExists()
    }
}
