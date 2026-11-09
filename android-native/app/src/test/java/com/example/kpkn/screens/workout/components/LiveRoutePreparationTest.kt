package com.example.kpkn.screens.workout.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class LiveRoutePreparationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun preparationPillsShowProgressAndNavigateToTheChosenStep() {
        val exercise = Exercise(
            id = "squat",
            name = "Sentadilla",
            warmupSets = listOf(
                WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 50.0, targetReps = 8),
                WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.7, targetReps = 4),
            ),
            mobilitySeries = listOf(MobilitySeries(id = "hip", name = "Cadera", sets = 2)),
        )
        var selectedStep: String? = null
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    LiveRouteSheetContent(
                        exercises = listOf(exercise),
                        completedSets = emptyMap(),
                        completedExercises = emptyList(),
                        omittedSetKeys = emptySet(),
                        currentExerciseId = exercise.id,
                        supersetGroups = emptyList(),
                        liveEnergySummary = null,
                        bodyWeight = null,
                        startTimeMs = 0L,
                        targetMinutes = null,
                        calibratedLoads = emptyMap(),
                        onSelectExercise = {},
                        onSelectSuperset = {},
                        onSelectSet = null,
                        onOpenExerciseOptions = null,
                        onOpenSupersetOptions = null,
                        onReorder = null,
                        onAddExercise = null,
                        warmupCompletedIds = setOf("squat_warmup_w1"),
                        mobilityCompletedIds = setOf("squat_hip"),
                        activeStepKey = "squat_hip_set_1",
                        onSelectStep = { selectedStep = it },
                    )
                }
            }
        }

        rule.onNodeWithTag("live-route-mobility-squat-hip").assertIsDisplayed()
        rule.onNodeWithTag("live-route-warmup-squat-w1").assertIsDisplayed()
        rule.onNodeWithText("50% · 8 rep").assertIsDisplayed()
        rule.onNodeWithText("70% · 4 rep").assertIsDisplayed()
        rule.onNodeWithTag("live-route-warmup-squat-w2").assertIsDisplayed().performClick()
        assertEquals("squat_warmup_w2", selectedStep)
    }
}
