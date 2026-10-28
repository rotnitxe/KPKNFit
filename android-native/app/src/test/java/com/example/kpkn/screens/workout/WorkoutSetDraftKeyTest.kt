package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSetDraftKeyTest {

    @Test
    fun everySetAndSideOwnsADistinctDraftKey() {
        val keys = listOf(
            workoutSetKey("squat", 0),
            workoutSetKey("squat", 1),
            workoutSetKey("squat", 0, "left"),
            workoutSetKey("squat", 0, "right"),
            workoutSetKey("bench", 0),
        )
        assertEquals(keys.size, keys.distinct().size)
        assertEquals("squat_0", workoutSetKey("squat", 0))
        assertEquals("squat_1", workoutSetKey("squat", 1))
        assertEquals("squat_0_L", workoutSetKey("squat", 0, "left"))
        assertEquals("squat_0_R", workoutSetKey("squat", 0, "right"))
    }

    @Test
    fun editingASetNeverLeaksIntoItsNeighbourOrTheOtherSide() {
        val drafts = mutableMapOf(
            workoutSetKey("squat", 0) to WorkoutSetDraft(weightText = "100", valueText = "5"),
            workoutSetKey("squat", 0, "left") to WorkoutSetDraft(weightText = "40", valueText = "8"),
            workoutSetKey("squat", 0, "right") to WorkoutSetDraft(weightText = "42,5", valueText = ""),
        )
        drafts[workoutSetKey("squat", 0, "left")] = drafts.getValue(workoutSetKey("squat", 0, "left"))
            .copy(valueText = "9")
        assertEquals("5", drafts.getValue(workoutSetKey("squat", 0)).valueText)
        assertEquals("9", drafts.getValue(workoutSetKey("squat", 0, "left")).valueText)
        assertEquals("", drafts.getValue(workoutSetKey("squat", 0, "right")).valueText)
        assertEquals("42,5", drafts.getValue(workoutSetKey("squat", 0, "right")).weightText)
        assertNull(drafts[workoutSetKey("squat", 1)])
    }

    @Test
    fun togglingToUnilateralKeepsRecordedEvidenceAndRemapsOnlyPendingDrafts() {
        val completed = mapOf(
            workoutSetKey("squat", 0) to com.example.kpkn.data.models.CompletedSet("set-0", weight = 100.0, reps = 5),
            workoutSetKey("bench", 0) to com.example.kpkn.data.models.CompletedSet("set-b", weight = 80.0, reps = 5),
        )
        val drafts = mapOf(
            workoutSetKey("squat", 0) to WorkoutSetDraft(weightText = "100", valueText = "5", isDirty = true),
            workoutSetKey("squat", 1) to WorkoutSetDraft(weightText = "100", valueText = "5", isDirty = true),
        )
        val keptCompleted = remapCompletedSetsForUnilateralToggle("squat", 2, completed, toUnilateral = true)
        assertEquals(completed, keptCompleted)

        val remapped = remapIndexKeyedMapForUnilateralToggle("squat", 2, drafts, toUnilateral = true)
        assertEquals(drafts.getValue(workoutSetKey("squat", 0)), remapped[workoutSetKey("squat", 0, "left")])
        assertEquals(drafts.getValue(workoutSetKey("squat", 0)), remapped[workoutSetKey("squat", 0, "right")])
        assertEquals(drafts.getValue(workoutSetKey("squat", 1)), remapped[workoutSetKey("squat", 1, "left")])
        assertEquals(drafts.getValue(workoutSetKey("squat", 1)), remapped[workoutSetKey("squat", 1, "right")])
        assertNull(remapped[workoutSetKey("squat", 0)])
    }
}
