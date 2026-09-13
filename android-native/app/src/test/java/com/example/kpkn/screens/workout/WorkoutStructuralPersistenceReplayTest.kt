package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStructuralPersistenceReplayTest {

    @Test
    fun addSupersetOfExistingIds_writesGroupAndDoesNotNoOp() {
        val press = Exercise(id = "press", name = "Press", sets = listOf(ExerciseSet(id = "p0")))
        val row = Exercise(id = "row", name = "Remo", sets = listOf(ExerciseSet(id = "r0")))
        val programSession = Session(id = "sess", name = "Push", exercises = listOf(press, row))
        val group = SupersetGroup(id = "g1", exerciseOrder = listOf("press", "row"))
        val change = PendingStructuralChange.AddSuperset(
            groupId = "g1",
            afterExerciseId = "press",
            newExerciseIds = listOf("press", "row"),
            newExerciseNames = listOf("Press", "Remo"),
            supersetConfig = CatalogSupersetConfig(rounds = 3, restBetweenExercisesSeconds = 45, restAfterSupersetSeconds = 90),
            group = group,
        )

        val updated = replayAddSupersetOnModeSession(
            modeSession = programSession,
            liveExercisesById = mapOf("press" to press, "row" to row),
            change = change,
            insertAfter = ::insertAfter,
            insertEnd = { session, exercise -> session.copy(exercises = session.exercises + exercise) },
        )

        assertTrue(updated !== programSession)
        val created = updated.allSupersetGroups().firstOrNull { it.id == "g1" }
        assertNotNull(created)
        assertEquals(listOf("press", "row"), created?.exerciseOrder)
        assertEquals(2, updated.exercises.size)
    }

    @Test
    fun dissolveReplay_doesNotCopyLiveWeights() {
        val livePress = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "p0", weight = 120.0, targetReps = 8)),
            supersetGroupRef = "g1",
        )
        val programPress = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "p0", weight = 80.0, targetReps = 8)),
            supersetGroupRef = "g1",
        )
        val programRow = Exercise(
            id = "row",
            name = "Remo",
            sets = listOf(ExerciseSet(id = "r0", weight = 70.0)),
            supersetGroupRef = "g1",
        )
        val program = Session(
            id = "sess",
            name = "Push",
            exercises = listOf(programPress, programRow),
            supersetGroups = listOf(SupersetGroup(id = "g1", exerciseOrder = listOf("press", "row"))),
        )
        val dissolved = com.example.kpkn.domain.workout.SupersetRules.dissolve(program, "g1")
        assertEquals(80.0, dissolved.exercises.first { it.id == "press" }.sets.first().weight ?: 0.0, 0.001)
        assertTrue(dissolved.allSupersetGroups().none { it.id == "g1" })
        assertEquals(120.0, livePress.sets.first().weight ?: 0.0, 0.001)
    }

    private fun insertAfter(session: Session, currentId: String, newExercise: Exercise): Session {
        val idx = session.exercises.indexOfFirst { it.id == currentId }
        if (idx == -1) return session.copy(exercises = session.exercises + newExercise)
        return session.copy(exercises = session.exercises.toMutableList().apply { add(idx + 1, newExercise) })
    }
}
