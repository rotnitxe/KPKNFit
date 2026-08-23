package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AugeEditorCompletedParityTest {
    @Test
    fun equivalentPlanAndCompletedExecution_matchGlobalAndLocalImpact() {
        val planned = Session(
            id = "golden-session",
            name = "golden",
            exercises = AugeRealSessionFixtures.completedExercises.map { completed ->
                Exercise(
                    id = completed.exerciseId,
                    name = completed.exerciseName,
                    exerciseDbId = completed.exerciseDbId,
                    effectiveMuscles = AugeRealSessionFixtures.exerciseDb[completed.exerciseDbId]?.involvedMuscles,
                    sets = completed.sets.map { set ->
                        ExerciseSet(
                            id = set.id,
                            targetReps = set.reps,
                            targetRPE = set.rpe,
                            targetRIR = set.rir,
                            weight = set.weight,
                        )
                    },
                )
            },
        )
        val iso = "2026-08-23T17:25:00-04:00"
        val plannedImpact = MuscularSessionImpactEngine.evaluate(
            MuscularSessionImpactEngine.fromPlannedSession(planned, iso, AugeRealSessionFixtures.exerciseDb, Settings()),
            AugeRealSessionFixtures.exerciseDb,
            Settings(),
        )
        val completedImpact = MuscularSessionImpactEngine.evaluate(
            MuscularSessionImpactEngine.fromCompletedExercises(AugeRealSessionFixtures.completedExercises, iso, AugeRealSessionFixtures.exerciseDb, Settings()),
            AugeRealSessionFixtures.exerciseDb,
            Settings(),
        )
        assertTrue(kotlin.math.abs(plannedImpact.globalMuscularDrain - completedImpact.globalMuscularDrain) <= 2)
        assertEquals(plannedImpact.involvedVolumeMuscles, completedImpact.involvedVolumeMuscles)
        plannedImpact.involvedVolumeMuscles.forEach { muscle ->
            assertTrue(kotlin.math.abs(
                plannedImpact.perMuscle[muscle]!!.immediateDrainPct - completedImpact.perMuscle[muscle]!!.immediateDrainPct,
            ) <= 2.0)
        }
    }
}
