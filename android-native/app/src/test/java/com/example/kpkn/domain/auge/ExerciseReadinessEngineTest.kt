package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.MovementPatternReadiness
import com.example.kpkn.data.models.RecoveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseReadinessEngineTest {

    private fun muscleRecovery(vararg pairs: Pair<String, Int>): Map<String, MuscleRecoveryStatus> =
        pairs.associate { (id, score) ->
            id to MuscleRecoveryStatus(
                muscleName = id,
                recoveryScore = score,
                hoursToRecovery = 0,
                hoursSinceLastSession = 0,
                effectiveSets = 0,
                status = RecoveryStatus.RECOVERING,
            )
        }

    private fun exerciseReadiness(
        id: String,
        name: String,
        patternId: String,
        score: Int,
        spinal: Int = 90,
        muscles: List<String> = listOf("Pectorales"),
    ): ExerciseReadiness = ExerciseReadiness(
        exerciseId = id,
        exerciseName = name,
        overallScore = score,
        muscularComponent = score,
        cnsComponent = score,
        spinalComponent = spinal,
        articularComponent = 90,
        structuralComponent = 90,
        relatedArticular = emptyList(),
        muscularWeight = 0.4,
        cnsWeight = 0.3,
        spinalWeight = 0.3,
        articularWeight = 0.0,
        setsPenaltyFactor = 1.0,
        intensityPenaltyFactor = 1.0,
        ermProximityFactor = 1.0,
        patternId = patternId,
        involvedMuscleIds = muscles,
    )

    private fun sessionExercise(id: String, name: String, sets: Int): Exercise =
        Exercise(
            id = id,
            name = name,
            sets = List(sets) { index -> ExerciseSet(id = "s$index") },
        )

    private fun pattern(
        id: String,
        totalSets: Int,
        score: Int,
        muscles: List<String>,
    ): MovementPatternReadiness = MovementPatternReadiness(
        patternId = id,
        patternLabel = id,
        overallScore = score,
        exerciseCount = 1,
        totalSets = totalSets,
        contributingMuscles = muscles,
        averageMuscleRecovery = score,
    )

    @Test
    fun `penalty 1_0 when no unresolved discomforts`() {
        val muscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY))
        val result = ExerciseReadinessEngine.computeDiscomfortPenaltyFactor(muscles, emptyList())
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `penalty 1_0 when no overlapping articulations`() {
        val muscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY))
        // knee_patellar → KNEE, but Pectorales → SHOULDER → no overlap
        val result = ExerciseReadinessEngine.computeDiscomfortPenaltyFactor(
            muscles, listOf("knee_patellar")
        )
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `penalty 0_95 when one overlapping articulation`() {
        val muscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY))
        // shoulder_anterior → SHOULDER; Pectorales → SHOULDER → 1 overlap
        val result = ExerciseReadinessEngine.computeDiscomfortPenaltyFactor(
            muscles, listOf("shoulder_anterior")
        )
        assertEquals(0.95, result, 0.001)
    }

    @Test
    fun `penalty 0_90 when two overlapping articulations`() {
        val muscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY))
        // Cuádriceps → KNEE, HIP
        // knee_patellar → KNEE (overlap ✓), lumbar → HIP (overlap ✓) → 2 overlaps
        val result = ExerciseReadinessEngine.computeDiscomfortPenaltyFactor(
            muscles, listOf("knee_patellar", "lumbar")
        )
        assertEquals(0.90, result, 0.001)
    }

    @Test
    fun `penalty 0_95 when three discomforts with only one overlapping`() {
        val muscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY))
        // Pectorales → SHOULDER only
        // shoulder_anterior → SHOULDER (overlap ✓)
        // knee_patellar → KNEE (no overlap)
        // lumbar → HIP (no overlap)
        // Only 1 overlap → 0.95
        val result = ExerciseReadinessEngine.computeDiscomfortPenaltyFactor(
            muscles, listOf("shoulder_anterior", "knee_patellar", "lumbar")
        )
        assertEquals(0.95, result, 0.001)
    }

    @Test
    fun `penalty 1_0 when exercise has no muscular articular mapping`() {
        // "unknown_muscle" no está en MUSCLE_TO_ARTICULAR → exerciseArticulars vacío
        val muscles = listOf(InvolvedMuscle("unknown_muscle", MuscleRole.PRIMARY))
        val result = ExerciseReadinessEngine.computeDiscomfortPenaltyFactor(
            muscles, listOf("shoulder_anterior")
        )
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `penalty 1_0 when discomfort not in catalog`() {
        val muscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY))
        val result = ExerciseReadinessEngine.computeDiscomfortPenaltyFactor(
            muscles, listOf("nonexistent_id")
        )
        assertEquals(1.0, result, 0.001)
    }

    // ─── Coaching por patrón dominante ─────────────────────────────────────────

    @Test
    fun `coaching null when no patterns`() {
        assertNull(
            ExerciseReadinessEngine.buildPatternCoaching(
                patternReadiness = emptyList(),
                exerciseReadinessMap = emptyMap(),
                sessionExercises = emptyList(),
                perMuscle = emptyMap(),
            )
        )
    }

    @Test
    fun `coaching green when dominant pattern fully fresh`() {
        val rdMap = mapOf("ex1" to exerciseReadiness("ex1", "Press Banca", "Empuje", 88))
        val coaching = ExerciseReadinessEngine.buildPatternCoaching(
            patternReadiness = listOf(pattern("Empuje", totalSets = 6, score = 88, muscles = listOf("Pectorales"))),
            exerciseReadinessMap = rdMap,
            sessionExercises = listOf(sessionExercise("ex1", "Press Banca", 6)),
            perMuscle = muscleRecovery("Pectorales" to 90),
        )
        assertEquals(CoachingTone.GREEN, coaching?.tone)
        assertEquals("Empuje", coaching?.dominantPatternLabel)
        assertEquals(100, coaching?.dominantSharePercent)
        assertTrue(coaching?.headline?.contains("listo para tus presses") == true)
        assertTrue(coaching?.detail?.contains("press banca") == true)
        assertTrue(coaching?.detail?.contains("100% de las series") == true)
        assertTrue(coaching?.detail?.contains("Sigue el plan tal cual") == true)
    }

    @Test
    fun `coaching green names the two main exercises of the session`() {
        val rdMap = mapOf(
            "ex1" to exerciseReadiness("ex1", "Press Banca", "Empuje", 90),
            "ex2" to exerciseReadiness("ex2", "Press Militar", "Empuje", 88),
        )
        val coaching = ExerciseReadinessEngine.buildPatternCoaching(
            patternReadiness = listOf(pattern("Empuje", totalSets = 6, score = 90, muscles = listOf("Pectorales"))),
            exerciseReadinessMap = rdMap,
            sessionExercises = listOf(
                sessionExercise("ex1", "Press Banca", 4),
                sessionExercise("ex2", "Press Militar", 2),
            ),
            perMuscle = muscleRecovery("Pectorales" to 90),
        )
        assertTrue(coaching?.detail?.contains("press banca y press militar") == true)
    }

    @Test
    fun `coaching selects dominant pattern by sets percentage`() {
        val coaching = ExerciseReadinessEngine.buildPatternCoaching(
            patternReadiness = listOf(
                pattern("Empuje", totalSets = 8, score = 90, muscles = listOf("Pectorales")),
                pattern("Bisagra", totalSets = 2, score = 30, muscles = listOf("Isquiotibiales")),
            ),
            exerciseReadinessMap = mapOf(
                "ex1" to exerciseReadiness("ex1", "Press Banca", "Empuje", 90),
                "ex2" to exerciseReadiness("ex2", "Peso Muerto", "Bisagra", 30, spinal = 25),
            ),
            sessionExercises = listOf(
                sessionExercise("ex1", "Press Banca", 8),
                sessionExercise("ex2", "Peso Muerto", 2),
            ),
            perMuscle = emptyMap(),
        )
        assertEquals("Empuje", coaching?.dominantPatternLabel)
        assertEquals(80, coaching?.dominantSharePercent)
        assertEquals(CoachingTone.GREEN, coaching?.tone)
        assertTrue(coaching?.detail?.contains("También llevas") != true)
    }

    @Test
    fun `coaching mentions secondary pattern when it weighs at least 25 percent`() {
        val coaching = ExerciseReadinessEngine.buildPatternCoaching(
            patternReadiness = listOf(
                pattern("Empuje", totalSets = 7, score = 90, muscles = listOf("Pectorales")),
                pattern("Sentadilla", totalSets = 3, score = 65, muscles = listOf("Cuádriceps")),
            ),
            exerciseReadinessMap = mapOf(
                "ex1" to exerciseReadiness("ex1", "Press Banca", "Empuje", 90),
                "ex2" to exerciseReadiness("ex2", "Sentadilla", "Sentadilla", 65, muscles = listOf("Cuádriceps")),
            ),
            sessionExercises = listOf(
                sessionExercise("ex1", "Press Banca", 7),
                sessionExercise("ex2", "Sentadilla", 3),
            ),
            perMuscle = muscleRecovery("Cuádriceps" to 70),
        )
        assertTrue(coaching?.detail?.contains("También llevas sentadillas (30% de las series) al 70%") == true)
    }

    @Test
    fun `coaching red recommends lower loads with exercise name`() {
        val coaching = ExerciseReadinessEngine.buildPatternCoaching(
            patternReadiness = listOf(
                pattern("Bisagra", totalSets = 5, score = 40, muscles = listOf("Isquiotibiales", "Espalda Baja")),
            ),
            exerciseReadinessMap = mapOf(
                "ex1" to exerciseReadiness(
                    "ex1", "Peso Muerto Rumano", "Bisagra", 40,
                    spinal = 30, muscles = listOf("Isquiotibiales", "Espalda Baja"),
                ),
            ),
            sessionExercises = listOf(sessionExercise("ex1", "Peso Muerto Rumano", 5)),
            perMuscle = muscleRecovery("Isquiotibiales" to 40),
        )
        assertEquals(CoachingTone.RED, coaching?.tone)
        assertTrue(coaching?.headline?.contains("columna") == true)
        assertTrue(coaching?.detail?.contains("baja 15-20% las cargas en peso muerto rumano") == true)
    }

    @Test
    fun `coaching amber warns about first sets`() {
        val coaching = ExerciseReadinessEngine.buildPatternCoaching(
            patternReadiness = listOf(
                pattern("Sentadilla", totalSets = 4, score = 62, muscles = listOf("Cuádriceps")),
            ),
            exerciseReadinessMap = mapOf(
                "ex1" to exerciseReadiness(
                    "ex1", "Sentadilla", "Sentadilla", 62,
                    muscles = listOf("Cuádriceps"),
                ),
            ),
            sessionExercises = listOf(sessionExercise("ex1", "Sentadilla", 4)),
            perMuscle = muscleRecovery("Cuádriceps" to 60),
        )
        assertEquals(CoachingTone.AMBER, coaching?.tone)
        assertTrue(coaching?.headline?.contains("A medias") == true)
        assertTrue(coaching?.detail?.contains("calentamiento extra") == true)
        assertTrue(coaching?.detail?.contains("60%") == true)
        assertTrue(coaching?.detail?.contains("baja ~10%") == true)
    }
}
