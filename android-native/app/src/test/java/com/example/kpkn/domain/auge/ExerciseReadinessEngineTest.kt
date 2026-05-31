package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseReadinessEngineTest {

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
}
