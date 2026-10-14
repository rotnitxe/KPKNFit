package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.screens.workout.design.WorkoutRestPanel
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRestModalUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rest_panel_shows_rest_label_suggestion_and_adjust_controls() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutRestPanel(
                    remainingSeconds = 64,
                    totalSeconds = 120,
                    nextLine = "Siguiente: Serie 2 · 80 kg × 8",
                    suggestedSeconds = 120,
                    onAdjustSeconds = {},
                    onSkip = {},
                    onUseSuggestion = {},
                )
            }
        }

        composeRule.onNodeWithText("Descanso").assertExists()
        composeRule.onNodeWithText("Descanso sugerido 2:00").assertExists()
        composeRule.onNodeWithText("Usar").assertExists()
        composeRule.onNodeWithText("−15 s").assertExists()
        composeRule.onNodeWithText("+15 s").assertExists()
    }

    @Test
    fun rest_panel_offers_skip_and_shows_zero_state() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutRestPanel(
                    remainingSeconds = 0,
                    totalSeconds = 90,
                    nextLine = null,
                    suggestedSeconds = null,
                    onAdjustSeconds = {},
                    onSkip = {},
                    onUseSuggestion = {},
                )
            }
        }

        composeRule.onNodeWithText("Listo").assertExists()
        composeRule.onNodeWithText("Saltar descanso").assertExists()
    }

    @Test
    fun rest_feedback_layer_embeds_post_exercise_feedback_without_skip_rest() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutPostExerciseFeedbackLayer(hazeState = HazeState()) {
                    Text("Feedback post ejercicio")
                }
            }
        }

        composeRule.onNodeWithText("Feedback post ejercicio").assertExists()
        composeRule.onAllNodesWithText("Saltar descanso").assertCountEquals(0)
    }
}
