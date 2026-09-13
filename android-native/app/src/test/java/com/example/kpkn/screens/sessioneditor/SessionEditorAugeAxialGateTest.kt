package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorAugeAxialGateTest {
    private val db = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench",
            name = "Press Banca",
            equipment = "barra",
            efc = 3.2,
            cnc = 3.5,
            ssc = 1.2,
            axialLoadFactor = 0.0,
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 1.0),
            ),
        ),
        "squat" to ExerciseMuscleInfo(
            id = "squat",
            name = "Sentadilla",
            equipment = "barra",
            efc = 4.2,
            cnc = 4.5,
            ssc = 1.0,
            axialLoadFactor = 1.0,
            involvedMuscles = listOf(
                InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, volumeContribution = 1.0),
            ),
        ),
        "custom-hinge" to ExerciseMuscleInfo(
            id = "custom-hinge",
            name = "Bisagra custom",
            equipment = "barra",
            efc = 4.0,
            cnc = 4.0,
            ssc = 1.0,
            axialLoadFactor = null,
            involvedMuscles = listOf(
                InvolvedMuscle("Isquiotibiales", MuscleRole.PRIMARY, volumeContribution = 1.0),
            ),
        ),
    )

    @Test
    fun benchInsightAndMuscleMapHaveZeroSpinal_squatDoesNot() {
        val bench = compute("bench", weight = 90.0)
        val squat = compute("squat", weight = 140.0)
        val benchInsight = bench.exerciseInsights.single()
        val squatInsight = squat.exerciseInsights.single()
        assertEquals(0, benchInsight.spinal)
        assertTrue("squat insight spinal=${squatInsight.spinal}", squatInsight.spinal > 0)
        assertTrue(
            "bench muscle spinal ${bench.muscleSpinalDrain}",
            bench.muscleSpinalDrain.values.all { it == 0 },
        )
        assertTrue(
            "squat muscle spinal ${squat.muscleSpinalDrain}",
            squat.muscleSpinalDrain.values.any { it > 0 },
        )
        assertTrue("predicted bench spinal=${bench.drain.spinal}", bench.drain.spinal <= 1)
        assertTrue(
            "predicted squat spinal=${squat.drain.spinal} bench=${bench.drain.spinal}",
            squat.drain.spinal > bench.drain.spinal,
        )
    }

    @Test
    fun missingAxialKeepsHeuristicSsc() {
        val custom = compute("custom-hinge", weight = 120.0)
        assertTrue(
            "custom without axial must keep spinal, insight=${custom.exerciseInsights}",
            custom.exerciseInsights.single().spinal > 0,
        )
    }

    private fun compute(id: String, weight: Double): SessionAugeComputation {
        val info = db.getValue(id)
        val session = Session(
            id = "s",
            name = info.name,
            exercises = listOf(
                Exercise(
                    id = id,
                    name = info.name,
                    exerciseDbId = id,
                    restTime = 150,
                    sets = List(5) { i ->
                        ExerciseSet(
                            id = "$id-$i",
                            targetReps = 5,
                            targetRPE = 8.5,
                            weight = weight,
                        )
                    },
                ),
            ),
        )
        return computeSessionAugeComputation(
            session = session,
            exerciseIndex = db,
            settings = Settings(),
            programLogs = emptyList(),
            programId = "p",
            mesoIndex = 0,
        )
    }
}
