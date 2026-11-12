package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetDraftTagScopeTest {
    @Test fun `blank edited load is preserved per tag and does not leak to another tag`() {
        val baseKey = workoutSetKey("exercise", 0)
        val emptyDraft = WorkoutSetDraft(tagId = "hammer", weightText = "", isDirty = true, weightManuallyFixed = true)
        val firstTagState = mapOf(baseKey to emptyDraft)
        val movementState = switchWorkoutSetDraftTagContext(firstTagState, "exercise", "hammer", "movement")
        assertNull(movementState[baseKey])
        assertTrue(movementState.isNotEmpty())
        assertEquals("", movementState[tagScopedWorkoutSetDraftKey(baseKey, "hammer")]?.weightText)

        val movementDraft = WorkoutSetDraft(tagId = "movement", weightText = "82,5", isDirty = true)
        val withMovement = movementState + (baseKey to movementDraft)
        val returnedToHammer = switchWorkoutSetDraftTagContext(withMovement, "exercise", "movement", "hammer")
        assertEquals("", returnedToHammer[baseKey]?.weightText)
        assertEquals("movement", returnedToHammer[tagScopedWorkoutSetDraftKey(baseKey, "movement")]?.tagId)
    }

    @Test fun `new tag begins without carrying the previous tag draft`() {
        val baseKey = workoutSetKey("exercise", 1)
        val source = mapOf(baseKey to WorkoutSetDraft(tagId = "hammer", weightText = "100", isDirty = true))
        val target = switchWorkoutSetDraftTagContext(source, "exercise", "hammer", "movement")
        assertNull(target[baseKey])
        assertEquals("100", target[tagScopedWorkoutSetDraftKey(baseKey, "hammer")]?.weightText)
    }
}
