package com.example.kpkn.domain.energy

import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.PostExerciseFeedback
import org.junit.Assert.*
import org.junit.Test

class TrainingEnergyEngineTest {

    private fun buildCompletedSet(
        weight: Double = 60.0,
        reps: Int = 8,
        rpe: Double? = 7.0,
        isFailure: Boolean = false,
        isFailedSet: Boolean = false,
    ): CompletedSet = CompletedSet(
        id = "",
        weight = weight,
        reps = reps,
        rpe = rpe,
        isFailure = isFailure,
        isFailedSet = isFailedSet,
    )

    private fun buildCompletedExercise(
        name: String,
        sets: List<CompletedSet>,
        exerciseDbId: String = name.lowercase(),
        restTime: Int = 90,
    ): CompletedExercise = CompletedExercise(
        exerciseId = name.lowercase(),
        exerciseName = name,
        exerciseDbId = exerciseDbId,
        restTime = restTime,
        sets = sets,
    )

    private fun buildSettings(weight: Double? = 80.0): Settings =
        Settings(userVitals = UserVitals(weight = weight))

    @Test
    fun `high RPE clearly increases kcal per set vs moderate RPE`() {
        val settings = buildSettings()
        val highRpeExercises = listOf(
            buildCompletedExercise("Sentadilla", listOf(
                buildCompletedSet(weight = 80.0, reps = 8, rpe = 9.5),
            )),
        )
        val highRpe = TrainingEnergyEngine.estimateLiveSession(highRpeExercises, settings)

        val moderateExercises = listOf(
            buildCompletedExercise("Sentadilla", listOf(
                buildCompletedSet(weight = 80.0, reps = 8, rpe = 7.0),
            )),
        )
        val moderateRpe = TrainingEnergyEngine.estimateLiveSession(moderateExercises, settings)

        assertTrue(
            "RPE alto debe generar mas kcal que RPE moderado (${highRpe.totalKcal.mid} vs ${moderateRpe.totalKcal.mid})",
            highRpe.totalKcal.mid > moderateRpe.totalKcal.mid,
        )
    }

    @Test
    fun `squat generates more kcal than curl with same tonnage`() {
        val settings = buildSettings(weight = 80.0)
        val squatExercises = listOf(
            buildCompletedExercise("Sentadilla", listOf(
                buildCompletedSet(weight = 100.0, reps = 8, rpe = 8.0),
            ), exerciseDbId = "sentadilla", restTime = 90),
        )
        val curlExercises = listOf(
            buildCompletedExercise("Curl de Biceps", listOf(
                buildCompletedSet(weight = 100.0, reps = 8, rpe = 8.0),
            ), exerciseDbId = "curl_de_biceps", restTime = 90),
        )

        val squatResult = TrainingEnergyEngine.estimateLiveSession(squatExercises, settings)
        val curlResult = TrainingEnergyEngine.estimateLiveSession(curlExercises, settings)

        assertTrue(
            "Sentadilla (${squatResult.totalKcal.mid} kcal) debe generar mas kcal que curl (${curlResult.totalKcal.mid} kcal) con mismo tonelaje",
            squatResult.totalKcal.mid > curlResult.totalKcal.mid,
        )
    }

    @Test
    fun `missing planned weight does not invent kcal`() {
        val settings = buildSettings(weight = 80.0)
        val session = Session(
            id = "test-session",
            name = "Test",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Curl de Biceps",
                    sets = listOf(
                        ExerciseSet(id = "s1", weight = null, targetReps = 8, targetRPE = null),
                    ),
                ),
            ),
        )

        val result = TrainingEnergyEngine.estimatePlannedSession(session, settings)

        assertEquals(0, result.totalKcal.mid)
        assertEquals(EnergyConfidence.LOW, result.confidence)
        val hasMissingWeightNote = result.notes.any { it.lowercase().contains("falta peso planificado") }
        assertTrue(
            "Debe incluir nota sobre falta de peso planificado, notas: ${result.notes}",
            hasMissingWeightNote,
        )
    }

    @Test
    fun `poor technique increases epoc and total kcal mid`() {
        val settings = buildSettings(weight = 80.0)
        val tenHeavySets = (1..10).map {
            buildCompletedSet(weight = 150.0, reps = 10, rpe = 9.5)
        }
        val exercises = listOf(
            buildCompletedExercise("Sentadilla", tenHeavySets),
        )

        val postFeedback = mapOf(
            "sentadilla" to PostExerciseFeedback(
                exerciseId = "sentadilla",
                exerciseName = "Sentadilla",
                technicalQuality = 2,
            ),
        )

        val goodResult = TrainingEnergyEngine.estimateCompletedSession(
            completedExercises = exercises,
            settings = settings,
            postExerciseFeedback = emptyMap(),
        )
        val poorResult = TrainingEnergyEngine.estimateCompletedSession(
            completedExercises = exercises,
            settings = settings,
            postExerciseFeedback = postFeedback,
        )

        assertTrue(
            "Tecnica pobre debe aumentar EPOC kcal mid (${poorResult.epocKcal.mid} vs ${goodResult.epocKcal.mid})",
            poorResult.epocKcal.mid > goodResult.epocKcal.mid,
        )
        assertTrue(
            "Tecnica pobre debe aumentar total kcal mid (${poorResult.totalKcal.mid} vs ${goodResult.totalKcal.mid})",
            poorResult.totalKcal.mid > goodResult.totalKcal.mid,
        )
    }

}

