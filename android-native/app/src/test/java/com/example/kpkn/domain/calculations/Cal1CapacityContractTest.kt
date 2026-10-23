package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.screens.workout.LiveSetPrPolicy
import org.junit.Assert.*
import org.junit.Test

class Cal1CapacityContractTest {
    @Test fun partialWorkIsKeptSeparateFromTheCapacityOfFullRepetitions() {
        val entry = SetEntryV2("e", setIndex = 0, loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
            actualValue = 9.0, completedReps = 8, loggedLoad = 100.0, contextKey = "e", amrapOverride = true)
        val evaluation = WorkoutPerformanceHomologationEngine.evaluate(entry, null)
        assertEquals(calculateHybrid1RM(100.0, 8), evaluation.homologated.estimatedRm!!, 0.0)
        val partialsOnly = WorkoutPerformanceHomologationEngine.evaluate(entry.copy(completedReps = 0), null)
        assertNull(partialsOnly.homologated.estimatedRm)
    }

    @Test fun amrapLabelDoesNotChangeCapacityInHistoryRecordsEditorHomologationOrSuggestions() {
        for (reps in listOf(1, 3, 5, 8, 10, 15, 25)) {
            val capacity = calculateHybrid1RM(100.0, reps)
            assertEquals(capacity, calculateHybrid1RM(100.0, reps, isAmrap = true), 0.0)
            val entry = SetEntryV2("e", setIndex = 0, loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
                actualValue = reps.toDouble(), loggedLoad = 100.0, amrapOverride = true, contextKey = "same-context")
            val evaluated = WorkoutPerformanceHomologationEngine.evaluate(entry, null)
            val done = CompletedSet("done", weight = 100.0, reps = reps, purpose = SetPurpose.AMRAP,
                amrapPerformed = true, homologatedResultV3 = evaluated.homologated)
            val log = WorkoutLog("l", "p", "s", "Sesión", "2026-10-20", 30,
                completedExercises = listOf(CompletedExercise("e", "Press", canonicalExerciseId = "e", sets = listOf(done))))
            val exercise = Exercise("e", "Press", canonicalExerciseId = "e")
            assertEquals(capacity, resolveReferenceCapacity(exercise, listOf(log))!!, 0.0)
            assertEquals(capacity, resolveReferenceCapacity(exercise.copy(prFor1RM = PrReference(100.0, reps)))!!, 0.0)
            assertEquals(capacity, evaluated.homologated.estimatedRm!!, 0.0)
            assertEquals(capacity, LoadSuggestionEngine.estimatedCapacity(done)!!, 0.0)
            assertEquals(CAPACITY_ESTIMATOR_VERSION, evaluated.homologated.estimatorVersion)
            assertTrue(LiveSetPrPolicy.isPrSeries(null, done, "e", listOf(
                SessionMilestone("pr", "e", "Press", "pr_e1rm", "PR", capacity),
            )))
        }
    }

    @Test fun oldBonusSnapshotRemainsStoredButCurrentCapacityIsRecomputed() {
        val entry = SetEntryV2("e", setIndex = 0, loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
            actualValue = 8.0, loggedLoad = 100.0, contextKey = "e")
        val current = WorkoutPerformanceHomologationEngine.evaluate(entry, null).homologated
        val old = current.copy(estimatedRm = 127.2, estimatorVersion = 1)
        val done = CompletedSet("old", weight = 100.0, reps = 8, amrapPerformed = true, homologatedResultV3 = old)
        assertEquals(124.1, LoadSuggestionEngine.estimatedCapacity(done)!!, 0.0)
        assertEquals(127.2, done.homologatedResultV3!!.estimatedRm!!, 0.0)
        assertEquals(1, done.homologatedResultV3.estimatorVersion)
    }

    @Test fun tmPercentageUsesOnlyTrainingMaxAndNeverTheDeclaredOneRm() {
        val set = ExerciseSet("tm", targetPercentageRM = 80.0, loadBasis = LoadBasis.PERCENT_TM)
        val exercise = Exercise("e", "Press", trainingMode = TrainingMode.RM,
            reference1RM = 100.0, referenceTrainingMax = 90.0, sets = listOf(set))
        assertEquals(72.0, calculateSuggestedLoad(exercise, set)!!, 0.0)
        assertEquals(72.0, calculateSuggestedLoad(exercise.copy(reference1RM = 200.0), set)!!, 0.0)
        assertNull(calculateSuggestedLoad(exercise.copy(referenceTrainingMax = null), set))
        assertEquals(80.0, calculateSuggestedLoad(exercise, set.copy(loadBasis = LoadBasis.PERCENT_1RM))!!, 0.0)
    }
}
