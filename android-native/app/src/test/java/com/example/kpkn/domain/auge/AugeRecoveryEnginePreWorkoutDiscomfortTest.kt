package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AugeRecoveryEnginePreWorkoutDiscomfortTest {

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
        val now = System.currentTimeMillis()
        val settings = Settings()
        val history = listOf(heavyWorkoutLog(now - 30L * 3600 * 1000)) // 30 hours ago

        // 1. Calculate base muscle score without discomfort
        val wellbeingNoDiscomfort = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            preWorkoutDiscomforts = emptyList()
        )
        val baseScore = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = history,
            wellbeing = wellbeingNoDiscomfort,
            settings = settings,
            exerciseDb = exerciseDb,
            precomputedCapacity = 200.0
        ).recoveryScore

        // 2. Calculate muscle score with pre-workout discomfort affecting "Pectorales" (shoulder_anterior relates to Pectorales)
        val wellbeingWithDiscomfort = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            preWorkoutDiscomforts = listOf("shoulder_anterior")
        )
        val penalizedScore = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = history,
            wellbeing = wellbeingWithDiscomfort,
            settings = settings,
            exerciseDb = exerciseDb,
            precomputedCapacity = 200.0
        ).recoveryScore

        // Penalized score should be strictly lower than base score
        assertTrue("Penalized score ($penalizedScore) should be less than base score ($baseScore)", penalizedScore < baseScore)
    }

    @Test
    fun testPreWorkoutDiscomfortAppliesPenaltyToArticularBatteries() {
        val wellbeingNoDiscomfort = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            preWorkoutDiscomforts = emptyList()
        )
        val baseArticular = AugeTtcEngine.calculateArticularBatteries(
            history = emptyList(),
            exerciseDb = exerciseDb,
            wellbeing = wellbeingNoDiscomfort
        )

        // shoulder_anterior relates to ArticularBattery.SHOULDER
        val wellbeingWithDiscomfort = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            preWorkoutDiscomforts = listOf("shoulder_anterior")
        )
        val penalizedArticular = AugeTtcEngine.calculateArticularBatteries(
            history = emptyList(),
            exerciseDb = exerciseDb,
            wellbeing = wellbeingWithDiscomfort
        )

        val baseShoulder = baseArticular[ArticularBattery.SHOULDER]?.recoveryScore ?: 100
        val penalizedShoulder = penalizedArticular[ArticularBattery.SHOULDER]?.recoveryScore ?: 100

        assertTrue("Penalized shoulder joint score ($penalizedShoulder) should be less than base score ($baseShoulder)", penalizedShoulder < baseShoulder)
    }
}
