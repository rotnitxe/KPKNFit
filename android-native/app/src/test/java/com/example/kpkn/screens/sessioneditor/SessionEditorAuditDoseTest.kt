package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import org.junit.Assert.*
import org.junit.Test

class SessionEditorAuditDoseTest {
    @Test fun distanceDoseUsesMetersInsteadOfOldRepetitions() {
        val exercise = Exercise(id = "e", name = "Arrastre", trainingMode = TrainingMode.DISTANCE,
            sets = listOf(ExerciseSet(id = "s", targetReps = 8, plannedTargetV2 = 400.0)))
        assertEquals("1 × 400 m", sessionExerciseListDose(exercise, includeRest = false))
    }

    @Test fun customDoseUsesItsQuantityAndUnit() {
        val exercise = Exercise(id = "e", name = "Pasos", trainingMode = TrainingMode.CUSTOM, customUnit = "pasos",
            sets = listOf(ExerciseSet(id = "s", targetReps = 8, plannedTargetV2 = 12.5)))
        assertEquals("1 × 12,5 pasos", sessionExerciseListDose(exercise, includeRest = false))
    }

    @Test fun deletedSupersetSlotsAreNotCountedAsWorkingSets() {
        val session = Session(id = "s", name = "Sesión", exercises = listOf(Exercise(id = "e", name = "E", sets = listOf(
            ExerciseSet(id = "a", targetReps = 8), ExerciseSet(id = "hole", isEmptySlot = true),
        ))))
        assertEquals(1, SessionDurationEstimator.estimate(session).toSessionTimeBreakdown(session).totalSetCount)
    }

    @Test fun normalizingAllSevenModesHandlesEmptyAndThirtySetSessions() {
        TrainingMode.entries.forEach { mode ->
            listOf(0, 1, 30).forEach { count ->
                val sets = List(count) { ExerciseSet(id = "set-$it", targetReps = 8, targetDuration = 45, plannedTargetV2 = 20.0) }
                val result = Session(id = "s", name = "Sesión", description = null, exercises = listOf(
                    Exercise(id = "e", name = "Ejercicio sin ficha", trainingMode = mode, sets = sets),
                )).normalizedForEditorSave()
                assertEquals("$mode / $count", count.coerceAtLeast(1), result.exercises.single().sets.size)
                assertEquals(mode, result.exercises.single().trainingMode)
            }
        }
    }

    @Test fun deletedSlotsDoNotAddExecutionOrRestToDuration() {
        val exercise = Exercise(id = "e", name = "E", restTime = 90,
            sets = listOf(ExerciseSet(id = "a", targetReps = 8)))
        val base = Session(id = "s", name = "Sesión", exercises = listOf(exercise))
        val withHole = base.copy(exercises = listOf(exercise.copy(sets = exercise.sets + ExerciseSet(id = "hole", isEmptySlot = true))))
        assertEquals(SessionDurationEstimator.estimate(base).totalSeconds, SessionDurationEstimator.estimate(withHole).totalSeconds)
    }

    @Test fun supersetRestDoesNotDependOnWhichMemberHasMoreSets() {
        val group = SupersetGroup(id = "g", exerciseOrder = listOf("a", "b"), rounds = 3,
            restBetweenExercises = 10, restAfterSuperset = 30)
        fun member(id: String, count: Int) = Exercise(id = id, name = id, supersetGroupRef = "g",
            sets = List(count) { ExerciseSet(id = "$id-$it", targetReps = 8) })
        val a = member("a", 1)
        val b = member("b", 3)
        assertEquals(70, calculateSessionTimeBreakdown(listOf(a, b), listOf(group)).restSeconds)
        assertEquals(70, calculateSessionTimeBreakdown(listOf(b, a), listOf(group)).restSeconds)
    }

    @Test fun aSetRestOverrideIsUsedExceptAfterTheLastSet() {
        val exercise = Exercise(id = "e", name = "E", restTime = 90, sets = listOf(
            ExerciseSet(id = "a", targetReps = 8, restAfterSeconds = 10),
            ExerciseSet(id = "b", targetReps = 8, restAfterSeconds = 20),
            ExerciseSet(id = "c", targetReps = 8, restAfterSeconds = 999),
        ))
        assertEquals(30, calculateSessionTimeBreakdown(listOf(exercise), emptyList()).restSeconds)
    }
}
