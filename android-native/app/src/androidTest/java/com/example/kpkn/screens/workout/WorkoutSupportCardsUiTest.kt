package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.workout.components.WorkoutWarmupDisplaySet
import com.example.kpkn.screens.workout.components.WorkoutWarmupSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSupportCardsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun warmup_sheet_shows_anchor_and_completion_action() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutWarmupSheet(
                    exercise = Exercise(
                        id = "bench",
                        name = "Press banca",
                        warmupSets = listOf(
                            WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 50.0, targetReps = 10),
                            WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 75.0, targetReps = 4),
                        ),
                    ),
                    warmupSets = listOf(
                        WorkoutWarmupDisplaySet(percentage = 50.0, reps = 10, targetWeight = 40.0),
                        WorkoutWarmupDisplaySet(percentage = 75.0, reps = 4, targetWeight = 60.0),
                    ),
                    workingWeight = 80.0,
                    isCompleted = false,
                    onDismiss = {},
                    onMarkCompleted = {},
                )
            }
        }

        composeRule.onNodeWithText("Warm-up inteligente").assertExists()
                        composeRule.onNodeWithText("80 kg estimados para la serie efectiva").assertExists()
        composeRule.onNodeWithText("Aproximacion 1").assertExists()
        composeRule.onNodeWithText("Marcar warm-up listo").assertExists()
    }
}
