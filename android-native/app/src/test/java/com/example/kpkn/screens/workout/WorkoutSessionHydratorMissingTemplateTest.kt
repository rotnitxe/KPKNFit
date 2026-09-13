package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionHydratorMissingTemplateTest {

    @Test
    fun snapshotHydratesWhenTemplateMissing() {
        val snapshotSession = Session(
            id = "sess",
            name = "Vivo",
            exercises = listOf(Exercise(id = "ex", name = "Press", sets = listOf(ExerciseSet(id = "s0")))),
        )
        val snapshot = OngoingWorkoutState(programId = "prog", session = snapshotSession, startTime = 1L)
        val resolved = resolveHydrationSession(
            foundInProgram = null,
            snapshot = snapshot,
            programId = "prog",
            sessionId = "sess",
        )
        assertNotNull(resolved)
        assertEquals("Vivo", resolved!!.first.name)
        assertTrue(resolved.second)
    }

    @Test
    fun otherSessionSnapshotDoesNotHydrate() {
        val snapshot = OngoingWorkoutState(
            programId = "prog",
            session = Session(id = "other", name = "Otra"),
            startTime = 1L,
        )
        assertNull(
            resolveHydrationSession(
                foundInProgram = null,
                snapshot = snapshot,
                programId = "prog",
                sessionId = "sess",
            ),
        )
    }

    @Test
    fun programTemplateWinsOverSnapshot() {
        val template = Session(id = "sess", name = "Plantilla")
        val snapshot = OngoingWorkoutState(
            programId = "prog",
            session = Session(id = "sess", name = "Vivo"),
            startTime = 1L,
        )
        val resolved = resolveHydrationSession(template, snapshot, "prog", "sess")
        assertEquals("Plantilla", resolved!!.first.name)
        assertFalse(resolved.second)
    }
}
