package com.example.kpkn.screens.workout

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.components.UltraFastRecordSheet
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UltraFastRecordDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun plannedEffortDoesNotEnableRecordButExplicitMinimumDoes() {
        var recorded: Triple<Double, Int, Int?>? = null
        val exercise = Exercise(id = "quick-exercise", name = "Curl con mancuernas",
            sets = (0 until 4).map { ExerciseSet(id = "quick-set-" + it, weight = 20.0, targetReps = 8,
                targetRIR = 2, unitModeV2 = UnitModeV2.REPS) })
        compose.setContent { KPKNTheme {
            UltraFastRecordSheet(exercise = exercise, completedSets = emptyMap(), currentSetIndex = 0, busy = false,
                onTechnique = {}, onRecord = { weight, reps, rir -> recorded = Triple(weight, reps, rir) }, onDismiss = {})
        } }
        compose.onNodeWithTag("ultra-record-submit").assertIsNotEnabled()
        for (index in 0 until 4) compose.onNodeWithTag("ultra-record-row-" + index, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Más RIR").performScrollTo().performClick()
        compose.onNodeWithTag("ultra-record-submit").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(Triple(20.0, 8, 0), recorded) }
    }
}
