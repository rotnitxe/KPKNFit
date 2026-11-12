package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorLiveMergeTest {
    private fun base() = Session(
        id = "session", name = "Día A",
        exercises = listOf(Exercise(id = "exercise", name = "Press", sets = listOf(
            ExerciseSet(id = "set", targetReps = 8, weight = 80.0),
        ))),
    )

    @Test fun `disjoint editor and live fields merge by stable ids`() {
        val base = base()
        val local = base.copy(exercises = base.exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { it.copy(targetReps = 10) })
        })
        val remote = base.copy(exercises = base.exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { it.copy(weight = 82.5) })
        })
        val merged = mergeSessionThreeWay(base, local, remote)
        val set = kotlinx.serialization.json.Json.decodeFromJsonElement(Session.serializer(), merged.merged)
            .exercises.single().sets.single()
        assertTrue(merged.conflicts.isEmpty())
        assertEquals(10, set.targetReps)
        assertEquals(82.5, set.weight!!, 0.0)
    }

    @Test fun `same field produces an explicit local or remote choice`() {
        val base = base()
        val local = base.copy(exercises = listOf(base.exercises.single().copy(name = "Press inclinado")))
        val remote = base.copy(exercises = listOf(base.exercises.single().copy(name = "Press máquina")))
        val unresolved = mergeSessionThreeWay(base, local, remote)
        assertTrue(unresolved.conflicts.any { it.path.endsWith(".name") })
        val conflictPath = unresolved.conflicts.single { it.path.endsWith(".name") }.path
        val remoteChosen = mergeSessionThreeWay(base, local, remote, mapOf(conflictPath to true))
        val merged = kotlinx.serialization.json.Json.decodeFromJsonElement(Session.serializer(), remoteChosen.merged)
        assertEquals("Press máquina", merged.exercises.single().name)
    }
}
