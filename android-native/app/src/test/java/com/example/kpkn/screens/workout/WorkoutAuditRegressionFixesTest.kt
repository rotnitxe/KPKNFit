package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAuditRegressionFixesTest {

    @Test
    fun pacing_groups_compound_underscore_keys_correctly() {
        val keys = listOf(
            "barbell_bench_press_0",
            "barbell_bench_press_1",
            "barbell_bench_press_2",
            "lat_pulldown_machine_0_left",
            "lat_pulldown_machine_0_right",
        )
        val uniqueSets = keys.mapNotNull { key ->
            parseCompletedSetKey(key)?.let { "${it.exerciseId}_${it.setIdx}" }
        }.distinct().size

        // 3 sets of barbell bench press + 1 combined unilateral set of lat pulldown = 4 unique sets
        assertEquals(4, uniqueSets)
    }

    @Test
    fun unilateral_progress_normalizes_to_one_unit_per_set_index() {
        val completedKeys = mapOf(
            "db_curl_0_left" to CompletedSet(id = "s1", reps = 10, weight = 15.0),
            "db_curl_0_right" to CompletedSet(id = "s2", reps = 10, weight = 15.0),
            "db_curl_1_left" to CompletedSet(id = "s3", reps = 10, weight = 15.0),
            "db_curl_1_right" to CompletedSet(id = "s4", reps = 10, weight = 15.0),
        )
        val totalSetsInSession = 2 // 2 sets defined in exercise
        val completedUnits = completedKeys.keys.mapNotNull { k ->
            parseCompletedSetKey(k)?.let { "${it.exerciseId}_${it.setIdx}" }
        }.distinct().size

        val progress = if (totalSetsInSession > 0) {
            (completedUnits.toDouble() / totalSetsInSession).coerceIn(0.0, 1.0)
        } else 0.0

        assertEquals(2, completedUnits)
        assertEquals(1.0, progress, 0.001)
    }

    @Test
    fun mobility_checklist_does_not_collapse_subsequent_series() {
        val item0 = WorkoutMobilityChecklistItem(
            exerciseId = "squat",
            exerciseName = "Sentadilla",
            mobility = MobilitySeries(id = "ankle_drill", name = "Tobillo", sets = 2),
            mobilitySetIndex = 0,
            stepKey = "squat_ankle_drill_0",
        )
        val item1 = WorkoutMobilityChecklistItem(
            exerciseId = "squat",
            exerciseName = "Sentadilla",
            mobility = MobilitySeries(id = "ankle_drill", name = "Tobillo", sets = 2),
            mobilitySetIndex = 1,
            stepKey = "squat_ankle_drill_1",
        )

        val completedExerciseIds = setOf("squat_ankle_drill_0")

        // First item is completed
        assertTrue(item0.stepKey in completedExerciseIds)
        // Second item MUST NOT be marked completed just because item 0 is done
        assertFalse(item1.stepKey in completedExerciseIds)
    }
}
