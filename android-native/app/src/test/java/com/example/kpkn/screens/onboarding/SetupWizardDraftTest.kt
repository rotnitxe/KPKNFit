package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SetupWizardDraftTest {
    @Test
    fun draftKeepsCompleteExercisesAndStablePayloadIdentity() {
        val exercise = Exercise("exercise-1", "Sentadilla", sets = listOf(ExerciseSet("set-1", targetReps = 8)))
        val item = SetupExerciseDraft("item-1", exercise, ExerciseMuscleInfo("exercise-1", "Sentadilla"))
        val first = SetupWizardDraft(draftId = "setup-wizard:full", commitId = "payload-1", sessions = listOf(SetupSessionDraft(1, "Día 1", listOf(item))))
        val second = first.copy(revision = 2, name = "Ana")

        assertEquals("Sentadilla", second.sessions.single().exercises.single().exercise.name)
        assertEquals("payload-1", second.commitId)
        assertNotEquals(first.revision, second.revision)
    }
}
