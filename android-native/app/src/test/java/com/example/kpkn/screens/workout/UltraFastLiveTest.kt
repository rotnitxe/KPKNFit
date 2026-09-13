package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.sessionassistant.UltraFastEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UltraFastLiveTest {

    @Test
    fun previewDoesNotMutateSession() {
        val sets = (0 until 6).map { ExerciseSet(id = "s$it", targetReps = 10) }
        val exercise = Exercise(id = "ex1", name = "Press", sets = sets)
        val session = Session(id = "s", name = "Push", exercises = listOf(exercise))
        val beforeIds = session.exercises.first().sets.map { it.id }
        UltraFastEngine.preview(session, emptyMap())
        assertEquals(beforeIds, session.exercises.first().sets.map { it.id })
        assertEquals(6, session.exercises.first().sets.size)
    }

    @Test
    fun applyDoesNotCutLoggedSetIds() {
        val sets = (0 until 6).map { ExerciseSet(id = "s$it", targetReps = 10) }
        val exercise = Exercise(id = "ex1", name = "Sentadilla", sets = sets)
        val session = Session(id = "s", name = "Push", exercises = listOf(exercise))
        val result = UltraFastEngine.apply(
            session = session,
            exerciseIndex = mapOf("ex1" to ExerciseMuscleInfo(id = "ex1", name = "Sentadilla")),
            completedSetCountByExercise = mapOf("ex1" to 3),
        )
        val transformed = result.transformedExercises.first { it.id == "ex1" }
        assertTrue(transformed.sets.size >= 3)
        assertEquals("s0", transformed.sets[0].id)
        assertEquals("s1", transformed.sets[1].id)
        assertEquals("s2", transformed.sets[2].id)
    }
}

class RelatorAssistPreviewTest {
    @Test
    fun previewUltraFastBranchDoesNotCallApply() {
        val viewModelFile = File(
            "../main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt",
        ).takeIf { it.exists() }
            ?: File("src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt")
        val source = viewModelFile.readText()
        val idx = source.indexOf("RelatorAssistActionKind.PREVIEW_ULTRAFAST")
        require(idx >= 0) { "PREVIEW_ULTRAFAST branch not found" }
        val next = source.indexOf("RelatorAssistActionKind.", startIndex = idx + "RelatorAssistActionKind.PREVIEW_ULTRAFAST".length)
        val slice = source.substring(idx, if (next > idx) next else idx + 600)
        assertTrue(slice.contains("previewUltraFast()"))
        assertTrue(!slice.contains("applyUltraFast()"))
    }
}

class WorkoutUnilateralToggleRemapTest {
    @Test
    fun bilateralCopiesToBothSides() {
        val completed = mapOf(
            workoutSetKey("ex", 0) to CompletedSet(id = "c0", weight = 20.0, reps = 10),
        )
        val remapped = remapCompletedSetsForUnilateralToggle("ex", 1, completed, toUnilateral = true)
        assertEquals("c0", remapped[workoutSetKey("ex", 0, "left")]?.id)
        assertEquals("c0", remapped[workoutSetKey("ex", 0, "right")]?.id)
        assertTrue(workoutSetKey("ex", 0) !in remapped)
    }

    @Test
    fun unilateralCollapsesToDominantSide() {
        val completed = mapOf(
            workoutSetKey("ex", 0, "left") to CompletedSet(id = "L", weight = 10.0, reps = 8),
            workoutSetKey("ex", 0, "right") to CompletedSet(id = "R", weight = 20.0, reps = 8),
        )
        val remapped = remapCompletedSetsForUnilateralToggle("ex", 1, completed, toUnilateral = false)
        assertEquals("R", remapped[workoutSetKey("ex", 0)]?.id)
        assertTrue(workoutSetKey("ex", 0, "left") !in remapped)
    }
}
