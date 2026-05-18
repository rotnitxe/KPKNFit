package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.WarmupSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseIdentityTest {

    @Test
    fun replacement_resets_incompatible_load_and_context_data() {
        val old = Exercise(
            id = "slot-1",
            name = "Press banca",
            exerciseDbId = "bench",
            reference1RM = 180.0,
            warmupSets = listOf(WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 50.0, targetReps = 5)),
            sets = listOf(
                ExerciseSet(
                    id = "s1",
                    targetReps = 8,
                    weight = 160.0,
                    targetPercentageRM = 85.0,
                    completedReps = 8,
                    completedRPE = 9.0,
                    loadModeV2 = LoadModeV2.LOAD,
                    contextKeyV2 = "bench-context",
                    setupId = "rack-1",
                    leftTarget = UnilateralTarget(weight = 80.0, targetReps = 8),
                    rightTarget = UnilateralTarget(weight = 82.5, targetReps = 8),
                    dropSets = listOf(DropSetData(weight = 120.0, reps = 6)),
                    restPauses = listOf(RestPauseData(restTime = 20, reps = 3)),
                )
            ),
        )
        val replacement = ExerciseMuscleInfo(
            id = "tren_superior_press_inclinado_maquina_convergente",
            name = "Press inclinado en máquina convergente",
            equipment = "Máquina",
        )

        val updated = old.replacedWithCatalogExercise(replacement)
        val set = updated.sets.single()

        assertEquals(replacement.name, updated.name)
        assertEquals(replacement.id, updated.exerciseDbId)
        assertNull(set.weight)
        assertNull(set.targetPercentageRM)
        assertNull(set.completedReps)
        assertNull(set.completedRPE)
        assertNull(set.contextKeyV2)
        assertNull(set.setupId)
        assertNull(set.leftTarget?.weight)
        assertNull(set.rightTarget?.weight)
        assertTrue(set.dropSets.isEmpty())
        assertTrue(set.restPauses.isEmpty())
        assertTrue(updated.warmupSets.isEmpty())
        assertNull(updated.reference1RM)
    }

    @Test
    fun bodyweight_replacement_defaults_to_bodyweight_load_mode() {
        val old = Exercise(
            id = "slot-1",
            name = "Curl",
            sets = listOf(ExerciseSet(id = "s1", targetReps = 10, weight = 30.0)),
        )
        val replacement = ExerciseMuscleInfo(
            id = "tren_inferior_sentadilla_sissy",
            name = "Sentadilla Sissy",
            equipment = "Peso corporal",
        )

        val updated = old.replacedWithCatalogExercise(replacement)

        assertEquals(LoadModeV2.BODYWEIGHT, updated.sets.single().loadModeV2)
        assertNull(updated.sets.single().weight)
    }
}
