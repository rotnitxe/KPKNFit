package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseFichaBodyweightParityTest {
    private fun log(set: CompletedSet) = WorkoutLog(
        id = "log",
        programId = "program",
        sessionId = "session",
        sessionName = "Sesión",
        date = "2026-11-09T10:00:00.000Z",
        durationMinutes = 40,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "exercise",
                exerciseName = "Ejercicio",
                exerciseDbId = "pushup",
                sets = listOf(set),
            ),
        ),
    )

    private fun set(weight: Double, mode: LoadModeV2, external: Double? = null, assisted: Double? = null) =
        CompletedSet(
            id = "set",
            weight = weight,
            reps = 5,
            loadMode = mode,
            originalLoad = when (mode) {
                LoadModeV2.ASSISTED -> assisted
                LoadModeV2.BODYWEIGHT -> 0.0
                else -> external ?: weight
            },
            recordedPayloadV3 = RecordedSetPayload(
                actualValue = 5.0,
                exerciseId = "exercise",
                loadInputMode = mode,
                unitMode = UnitModeV2.REPS,
                externalLoad = external,
                assistedLoad = assisted,
                bodyWeightSnapshot = 106.0,
                completedReps = 5,
            ),
        )

    @Test
    fun bodyweightSetShowsReferenceWithoutEstimatingOneRm() {
        val summary = exerciseHistorySummary(listOf(log(set(0.0, LoadModeV2.BODYWEIGHT))), setOf("pushup"))
        assertNotNull(summary)
        assertEquals(0.0, summary!!.bestSet?.weight ?: -1.0, 0.0)
        assertNull(summary.estimatedRmKg)
    }

    @Test
    fun lastreSetIsReferenceButNotOneRm() {
        val summary = exerciseHistorySummary(
            listOf(log(set(40.0, LoadModeV2.LASTRE, external = 40.0))),
            setOf("pushup"),
        )
        assertNotNull(summary)
        assertEquals(40.0, summary!!.bestSet?.weight ?: -1.0, 0.0)
        assertNull(summary.estimatedRmKg)
    }

    @Test
    fun conventionalLoadStillEstimatesOneRm() {
        val summary = exerciseHistorySummary(
            listOf(log(set(100.0, LoadModeV2.LOAD, external = 100.0))),
            setOf("pushup"),
        )
        assertNotNull(summary)
        assertNotNull(summary!!.estimatedRmKg)
    }
}
