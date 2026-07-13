package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.channelScore
import com.example.kpkn.data.models.ringScore
import kotlin.math.min
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AugeRecoveryEngineManualOverrideTest {
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

    @Test
    fun manualAnchorAfterWorkoutDoesNotCountThatWorkoutAgain() {
        val anchorMs = System.currentTimeMillis() - 60_000L
        val wellbeing = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            manualNeuralBattery = 61,
            manualSpinalBattery = 58,
            manualMuscleBatteries = mapOf("Pectorales" to 54),
            manualBatteryAnchorMs = anchorMs,
        )
        val priorWorkout = heavyWorkoutLog(anchorMs - 60_000L)

        val emptySystem = AugeRecoveryEngine.calculateSystemicFatigue(
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(),
            exerciseDb = exerciseDb,
        ).first
        val withPriorSystem = AugeRecoveryEngine.calculateSystemicFatigue(
            history = listOf(priorWorkout),
            wellbeing = wellbeing,
            settings = Settings(),
            exerciseDb = exerciseDb,
        ).first
        val emptySpinal = AugeRecoveryEngine.calculateSpinalBattery(
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(),
            exerciseDb = exerciseDb,
        )
        val withPriorSpinal = AugeRecoveryEngine.calculateSpinalBattery(
            history = listOf(priorWorkout),
            wellbeing = wellbeing,
            settings = Settings(),
            exerciseDb = exerciseDb,
        )
        val emptyMuscle = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = emptyList(),
            wellbeing = wellbeing,
            settings = Settings(),
            exerciseDb = exerciseDb,
        ).recoveryScore
        val withPriorMuscle = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = listOf(priorWorkout),
            wellbeing = wellbeing,
            settings = Settings(),
            exerciseDb = exerciseDb,
        ).recoveryScore

        assertEquals(emptySystem, withPriorSystem)
        assertEquals(emptySpinal, withPriorSpinal)
        assertEquals(emptyMuscle, withPriorMuscle)
    }

    @Test
    fun manualDashboardValuesAreTheRingScoresUsedByHome() {
        val wellbeing = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            manualMuscularBattery = 62,
            manualNeuralBattery = 71,
            manualSpinalBattery = 49,
        )
        val batteries = GlobalBatteries(muscular = 95, cnc = 96, spinal = 97)
        val dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries = batteries,
            perMuscle = emptyMap(),
            articularBatteries = emptyMap(),
            wellbeing = wellbeing,
        )
        val snapshot = AugeSnapshot(
            batteries = batteries,
            dashboard = dashboard,
        )

        assertEquals(95, dashboard.channelScore(RecoveryChannelId.MUSCULAR))
        assertEquals(96, dashboard.channelScore(RecoveryChannelId.SYSTEM))
        val structureExpected = min(97, ((97 * 0.6) + (100 * 0.4)).toInt()).coerceIn(0, 100)
        assertEquals(structureExpected, dashboard.channelScore(RecoveryChannelId.STRUCTURE))
        assertEquals(95, snapshot.ringScore(RecoveryChannelId.MUSCULAR))
        assertEquals(96, snapshot.ringScore(RecoveryChannelId.SYSTEM))
        assertEquals(structureExpected, snapshot.ringScore(RecoveryChannelId.STRUCTURE))
    }

    @Test
    fun legacyWellbeingWithoutManualAnchorStillDecodes() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<DailyWellbeingLog>(
            """
            {
              "id": "legacy",
              "date": "2026-05-24",
              "sleepQuality": 3,
              "stressLevel": 3,
              "doms": 1,
              "motivation": 3,
              "sleepHours": 7.5,
              "manualNeuralBattery": 80,
              "manualSpinalBattery": 77,
              "manualMuscleBatteries": {}
            }
            """.trimIndent(),
        )

        assertNull(decoded.manualBatteryAnchorMs)
        assertEquals(80, decoded.manualNeuralBattery)
        assertEquals(77, decoded.manualSpinalBattery)
    }

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
}
