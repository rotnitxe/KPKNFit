package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.FATIGUE_ROLE_MULTIPLIERS
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.VOLUME_CONTRIBUTION_FALLBACKS
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.exercises.SmartCreateRequest
import com.example.kpkn.domain.exercises.SmartExerciseCreator
import com.example.kpkn.domain.training.VolumeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class AugeLanguageConsistencyTest {

    @Test
    fun secondary_is_half_an_effective_set_everywhere() {
        val triceps = InvolvedMuscle("Tríceps", MuscleRole.SECONDARY)
        assertEquals(0.5, resolveMuscleVolumeContribution(triceps), 0.0001)
        assertEquals(0.5, FATIGUE_ROLE_MULTIPLIERS[MuscleRole.SECONDARY]!!, 0.0001)
        assertEquals(
            VOLUME_CONTRIBUTION_FALLBACKS[MuscleRole.SECONDARY],
            FATIGUE_ROLE_MULTIPLIERS[MuscleRole.SECONDARY],
        )
        val shares = VolumeCalculator.buildPerExerciseMuscleContributions(
            listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY),
                InvolvedMuscle("Tríceps", MuscleRole.SECONDARY),
            ),
        )
        assertEquals(1.0, shares["Pectorales"]!!, 0.0001)
        assertEquals(0.5, shares["Tríceps"]!!, 0.0001)
    }

    @Test
    fun custom_from_similar_catalog_drains_pecs_not_core() {
        val catalog = listOf(
            ExerciseMuscleInfo(
                id = "bench_press",
                name = "Press de banca",
                alias = "press banca, bench press",
                equipment = "Barra",
                efc = 3.2,
                cnc = 3.5,
                ssc = 0.8,
                involvedMuscles = listOf(
                    InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0),
                    InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, 0.5),
                ),
            ),
        )
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(name = "Press banca con pausa", implementoId = "barbell"),
            catalog,
        )
        val db = mapOf(created.id.lowercase() to created)
        val completed = CompletedExercise(
            exerciseId = created.id,
            exerciseDbId = created.id,
            exerciseName = created.name,
            sets = listOf(
                CompletedSet(id = "s1", weight = 80.0, reps = 8, rpe = 8.5),
                CompletedSet(id = "s2", weight = 80.0, reps = 8, rpe = 8.5),
            ),
        )
        val drains = AugeFatigueEngine.calculateCompletedSessionMuscleDrains(
            completedExercises = listOf(completed),
            exerciseDb = db,
        )
        assertTrue(drains.keys.any { it.contains("Pectoral", ignoreCase = true) })
        assertFalse(drains.containsKey("Core"))
    }

    @Test
    fun unknown_custom_does_not_invent_core() {
        val completed = CompletedExercise(
            exerciseId = "custom:xyz",
            exerciseName = "Xyz123",
            sets = listOf(CompletedSet(id = "s1", weight = 40.0, reps = 10, rpe = 8.0)),
        )
        val involved = AugeFatigueEngine.resolveInvolvedMuscles("Xyz123", null, null)
        assertTrue(involved.isEmpty())
        val drains = AugeFatigueEngine.calculateCompletedSessionMuscleDrains(listOf(completed))
        assertFalse(drains.containsKey("Core"))
    }

    @Test
    fun column_drops_when_guard_muscles_are_fatigued() {
        val fresh = AugeRecoveryEngine.structureRingScore(90, 90, listOf(90, 88, 92))
        val exposed = AugeRecoveryEngine.structureRingScore(90, 90, listOf(40, 88, 92))
        assertTrue(exposed < fresh)
        assertEquals(78, exposed)
    }

    @Test
    fun acwr_spike_is_danger_and_lowers_remaining_recovery() {
        val now = Instant.parse("2026-08-29T12:00:00Z")
        val logs = listOf(
            logWithStress("old-1", now.minus(20, ChronoUnit.DAYS), 12.0),
            logWithStress("old-2", now.minus(18, ChronoUnit.DAYS), 12.0),
            logWithStress("old-3", now.minus(16, ChronoUnit.DAYS), 12.0),
            logWithStress("spike", now.minus(1, ChronoUnit.DAYS), 200.0),
        )
        val acwr = AugeRecoveryEngine.muscularAcwrFor(logs, now.toEpochMilli())
        assertTrue(acwr != null && acwr > 1.5)
        assertEquals(AugeClassifiers.AcwrZone.DANGER, AugeClassifiers.classifyAcwrZone(acwr!!))
        assertEquals(1.12, AugeClassifiers.muscularLoadFactorFromAcwr(acwr), 0.0001)
    }

    @Test
    fun collagen_suggestions_are_gone() {
        val suggestions = AugeTtcEngine.getTendonCompensationSuggestions(
            mapOf(ArticularBattery.LUMBAR to ArticularBatteryState(recoveryScore = 20)),
        )
        assertTrue(suggestions.none { it.message.contains("colagen", ignoreCase = true) })
        assertTrue(suggestions.none { it.title.contains("nutric", ignoreCase = true) })
    }

    private fun logWithStress(id: String, instant: Instant, stress: Double): WorkoutLog {
        val iso = instant.toString()
        return WorkoutLog(
            id = id,
            programId = "p",
            sessionId = id,
            sessionName = id,
            date = iso,
            durationMinutes = 50,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 20,
                perMuscle = mapOf(
                    "Pectorales" to MuscleSessionImpactV2(
                        stressUnits = stress,
                        capacityAtCompletion = 260.0,
                        immediateDrainPct = 20.0,
                        directStressUnits = stress,
                        indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf("Pectorales"),
                setInputHash = id,
                contextHash = id,
            ),
        )
    }
}
