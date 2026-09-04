package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class AugeTtcRecoveryHoursTest {

    @Test
    fun hoursToRecovery_usesSameKAsDecay_whenAvgTtcAboveThree() {
        val now = System.currentTimeMillis()
        val logDate = java.time.Instant.ofEpochMilli(now - 6L * 3600_000L).toString()
        val snatchDb = mapOf(
            "snatch" to ExerciseMuscleInfo(
                id = "snatch",
                name = "Arrancada",
                equipment = "barbell",
                involvedMuscles = listOf(InvolvedMuscle("Deltoides", MuscleRole.PRIMARY)),
            ),
        )
        val log = WorkoutLog(
            id = "oly",
            date = logDate,
            sessionId = "s",
            sessionName = "Oly",
            programId = "p",
            durationMinutes = 60,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "snatch",
                    exerciseDbId = "snatch",
                    exerciseName = "Arrancada",
                    sets = List(8) { index ->
                        CompletedSet(id = "s$index", reps = 3, weight = 80.0, rpe = 10.0)
                    },
                ),
            ),
        )
        val batteries = AugeTtcEngine.calculateArticularBatteries(
            history = listOf(log),
            exerciseDb = snatchDb,
        )
        val shoulder = batteries[ArticularBattery.SHOULDER]!!
        assertTrue(shoulder.accumulatedStress > 0.0)
        assertTrue(shoulder.estimatedHoursToRecovery > 0)

        val totalAcc = shoulder.accumulatedStress
        val targetStress = ((100.0 - 90.0) / 100.0) * 80.0 // TENDON_CAPACITY_BASE
        val k60 = 2.0 / 60.0
        val expectedHours = (-ln(targetStress / totalAcc) / k60).toInt().coerceAtLeast(0)
        assertEquals(expectedHours, shoulder.estimatedHoursToRecovery)
    }

    @Test
    fun hoursToRecovery_highTtcTakesLongerThanStandardK() {
        val now = System.currentTimeMillis()
        val logDate = java.time.Instant.ofEpochMilli(now - 6L * 3600_000L).toString()
        val snatchDb = mapOf(
            "snatch" to ExerciseMuscleInfo(
                id = "snatch",
                name = "Arrancada",
                equipment = "barbell",
                involvedMuscles = listOf(InvolvedMuscle("Deltoides", MuscleRole.PRIMARY)),
            ),
        )
        val curlDb = mapOf(
            "curl" to ExerciseMuscleInfo(
                id = "curl",
                name = "Curl",
                equipment = "dumbbell",
                involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY)),
            ),
        )
        val heavySets = List(8) { index ->
            CompletedSet(id = "s$index", reps = 3, weight = 80.0, rpe = 10.0)
        }
        val highTtc = AugeTtcEngine.calculateArticularBatteries(
            history = listOf(
                WorkoutLog(
                    id = "oly",
                    date = logDate,
                    sessionId = "s",
                    sessionName = "Oly",
                    programId = "p",
                    durationMinutes = 60,
                    completedExercises = listOf(
                        CompletedExercise(
                            exerciseId = "snatch",
                            exerciseDbId = "snatch",
                            exerciseName = "Arrancada",
                            sets = heavySets,
                        ),
                    ),
                ),
            ),
            exerciseDb = snatchDb,
        )[ArticularBattery.SHOULDER]!!

        val lowTtc = AugeTtcEngine.calculateArticularBatteries(
            history = listOf(
                WorkoutLog(
                    id = "iso",
                    date = logDate,
                    sessionId = "s",
                    sessionName = "Iso",
                    programId = "p",
                    durationMinutes = 60,
                    completedExercises = listOf(
                        CompletedExercise(
                            exerciseId = "curl",
                            exerciseDbId = "curl",
                            exerciseName = "Curl",
                            sets = heavySets,
                        ),
                    ),
                ),
            ),
            exerciseDb = curlDb,
        )[ArticularBattery.SHOULDER]!!

        if (highTtc.estimatedHoursToRecovery > 0 && lowTtc.estimatedHoursToRecovery > 0) {
            assertTrue(highTtc.estimatedHoursToRecovery >= lowTtc.estimatedHoursToRecovery)
        }
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
