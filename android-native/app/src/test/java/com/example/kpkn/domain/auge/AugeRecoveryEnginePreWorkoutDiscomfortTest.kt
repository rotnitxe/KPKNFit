package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AugeRecoveryEnginePreWorkoutDiscomfortTest {

    /** Reloj explícito (WP 1.7): lunes 2025-03-03 10:00 UTC, mucho antes de hoy. Ninguna prueba lee el reloj real. */
    private val ahora = Instant.parse("2025-03-03T10:00:00Z").toEpochMilli()
    private val fecha = "2025-03-03"

    private val exerciseDb = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench",
            name = "Bench",
            equipment = "barbell",
            efc = 5.0,
            cnc = 5.0,
            ssc = 2.0,
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
        )
    )

    private fun heavyWorkoutLog(timestampMs: Long): WorkoutLog =
        WorkoutLog(
            id = "log-heavy",
            programId = "program",
            sessionId = "session",
            sessionName = "Heavy",
            date = Instant.ofEpochMilli(timestampMs).toString(),
            durationMinutes = 70,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "bench",
                    exerciseName = "Bench",
                    exerciseDbId = "bench",
                    restTime = 90,
                    sets = List(5) { index ->
                        CompletedSet(
                            id = "set-$index",
                            weight = 120.0,
                            reps = 5,
                            rpe = 10.0,
                        )
                    },
                )
            ),
        )

    @Test
    fun testPreWorkoutDiscomfortAppliesPenaltyToMuscles() {
        val now = ahora
        val settings = Settings()
        val history = listOf(heavyWorkoutLog(now - 8L * 3600 * 1000))

        val wellbeingNoDiscomfort = DailyWellbeingLog(
            id = "today",
            date = fecha,
            preWorkoutDiscomforts = emptyList()
        )
        val base = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = history,
            wellbeing = wellbeingNoDiscomfort,
            settings = settings,
            exerciseDb = exerciseDb,
            precomputedCapacity = 150.0,
            nowOverride = now,
        )

        val wellbeingWithDiscomfort = DailyWellbeingLog(
            id = "today",
            date = fecha,
            preWorkoutDiscomforts = listOf("shoulder_anterior")
        )
        val penalized = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = history,
            wellbeing = wellbeingWithDiscomfort,
            settings = settings,
            exerciseDb = exerciseDb,
            precomputedCapacity = 150.0,
            nowOverride = now,
        )

        // Penalized score should be strictly lower than base score
        assertTrue(
            "Penalized score (${penalized.recoveryScore}) should be less than base score (${base.recoveryScore})",
            penalized.recoveryScore < base.recoveryScore,
        )
    }

    @Test
    fun testPreWorkoutDiscomfortAppliesPenaltyToArticularBatteries() {
        val wellbeingNoDiscomfort = DailyWellbeingLog(
            id = "today",
            date = fecha,
            preWorkoutDiscomforts = emptyList()
        )
        val baseArticular = AugeTtcEngine.calculateArticularBatteries(
            history = emptyList(),
            exerciseDb = exerciseDb,
            wellbeing = wellbeingNoDiscomfort,
            nowMs = ahora,
        )

        // shoulder_anterior relates to ArticularBattery.SHOULDER
        val wellbeingWithDiscomfort = DailyWellbeingLog(
            id = "today",
            date = fecha,
            preWorkoutDiscomforts = listOf("shoulder_anterior")
        )
        val penalizedArticular = AugeTtcEngine.calculateArticularBatteries(
            history = emptyList(),
            exerciseDb = exerciseDb,
            wellbeing = wellbeingWithDiscomfort,
            nowMs = ahora,
        )

        val baseShoulder = baseArticular[ArticularBattery.SHOULDER]?.recoveryScore ?: 100
        val penalizedShoulder = penalizedArticular[ArticularBattery.SHOULDER]?.recoveryScore ?: 100

        assertTrue("Penalized shoulder joint score ($penalizedShoulder) should be less than base score ($baseShoulder)", penalizedShoulder < baseShoulder)
    }
}
