package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRestModalUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rest_modal_shows_recovery_and_rest_choice_actions() {
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
                    recoveryStatus = RestRecoveryStatus(
                        recoveryFraction = 0.68,
                        recoveryPercent = 68,
                        difficultyTier = 4,
                        isReady = false,
                    ),
                    coachMessage = CoachMessage(
                        key = "warning",
                        title = "Fatiga en aumento",
                        body = "Conviene exprimir un poco mas el descanso antes de la siguiente serie.",
                        severity = CoachSeverity.WARNING,
                    ),
                    onDecrease = {},
                    onIncrease = {},
                    onSkip = {},
                    onUseAdaptive = {},
                )
            }
        }

        composeRule.onNodeWithText("DESCANSO")
        composeRule.onNodeWithText("Recuperación estimada 68%")
        composeRule.onNodeWithText("Usar base")
        composeRule.onNodeWithText("Usar sugerido (+30s)")
        composeRule.onNodeWithText("Fatiga en aumento")
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
                    recoveryStatus = null,
                    coachMessage = null,
                    onDecrease = {},
                    onIncrease = {},
                    onSkip = {},
                )
            }
        }

        composeRule.onNodeWithText("Alertas del descanso")
        composeRule.onNodeWithText("Notificaciones desactivadas")
        composeRule.onNodeWithText("Alarma exacta no disponible")
        composeRule.onNodeWithText("Audio silencioso")
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
                    recoveryStatus = null,
                    coachMessage = null,
                    onDecrease = {},
                    onIncrease = {},
                    onSkip = {},
                    onUseAdaptive = {},
                )
            }
        }

        composeRule.onNodeWithText("Manual").assertExists()
        composeRule.onNodeWithText("Usar base").assertExists()
        composeRule.onNodeWithText("Usar sugerido (-15s)").assertExists()
        composeRule.onNodeWithText("-15").assertExists()
        composeRule.onNodeWithText("+15").assertExists()
        composeRule.onNodeWithText("Saltar").assertExists()
    }
}
