package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
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
import com.example.kpkn.data.models.ringScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AugeRecoveryEngineRingTest {

    private val exerciseDb = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench", name = "Bench Press", equipment = "barbell",
            efc = 5.0, cnc = 5.0, ssc = 2.0,
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
        ),
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Squat", equipment = "barbell",
            efc = 8.0, cnc = 7.0, ssc = 6.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)),
        ),
        "row" to ExerciseMuscleInfo(
            id = "row", name = "Barbell Row", equipment = "barbell",
            efc = 4.0, cnc = 4.0, ssc = 5.0,
            involvedMuscles = listOf(InvolvedMuscle("Dorsales", MuscleRole.PRIMARY)),
        ),
    )

    @Test
    fun fullPipeline_emptyHistory_allRingsAt100() {
        val snapshot = fullCompute(history = emptyList(), wellbeing = null)
        assertEquals(100, snapshot.ringScore(RecoveryChannelId.MUSCULAR))
        assertEquals(100, snapshot.ringScore(RecoveryChannelId.SYSTEM))
        assertEquals(100, snapshot.ringScore(RecoveryChannelId.STRUCTURE))
    }

    @Test
    fun fullPipeline_manualOverride_reflectedInRings() {
        val wellbeing = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            manualNeuralBattery = 62,
            manualMuscularBattery = 71,
            manualSpinalBattery = 49,
            manualBatteryAnchorMs = System.currentTimeMillis(),
        )
        val snapshot = fullCompute(history = heavySession(), wellbeing = wellbeing)
        assertEquals(71, snapshot.ringScore(RecoveryChannelId.MUSCULAR))
        assertEquals(62, snapshot.ringScore(RecoveryChannelId.SYSTEM))
        assertEquals(49, snapshot.ringScore(RecoveryChannelId.STRUCTURE))
    }

    @Test
    fun fullPipeline_decelerateBelow30() {
        val fatigue = extremeFatigueSession()
        val snapshot = fullCompute(history = fatigue, wellbeing = null)
        val muscular = snapshot.ringScore(RecoveryChannelId.MUSCULAR)
        assertTrue(muscular > 5)
        assertTrue(muscular < 30)
    }

    @Test
    fun fullPipeline_manualOverrideWithoutAnchor_usesNowMs() {
        val wellbeing = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            manualNeuralBattery = 80,
            manualBatteryAnchorMs = null,
        )
        val snapshot = fullCompute(history = heavySession(), wellbeing = wellbeing)
        assertEquals(80, snapshot.ringScore(RecoveryChannelId.SYSTEM))
    }

    @Test
    fun fullPipeline_dashboardWeightedCorrectly() {
        val batteries = GlobalBatteries(muscular = 70, cnc = 60, spinal = 50)
        val dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries = batteries,
            perMuscle = emptyMap(),
            articularBatteries = emptyMap(),
            wellbeing = null,
        )
        val snapshot = AugeSnapshot(batteries = batteries, dashboard = dashboard)
        val musc = snapshot.ringScore(RecoveryChannelId.MUSCULAR)
        val sys = snapshot.ringScore(RecoveryChannelId.SYSTEM)
        val str = snapshot.ringScore(RecoveryChannelId.STRUCTURE)
        val expected = (sys * 0.40 + musc * 0.35 + str * 0.25).toInt()
        assertEquals(expected, dashboard.overallScore)
    }

    private fun fullCompute(
        history: List<WorkoutLog>,
        wellbeing: DailyWellbeingLog?,
    ): AugeSnapshot {
        val settings = Settings()
        val perMuscle = AugeRecoveryEngine.getPerMuscleBatteries(
            history = history,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
        )
        val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = history,
            wellbeing = wellbeing,
            settings = settings,
            exerciseDb = exerciseDb,
            precomputedMuscles = perMuscle,
        )
        val dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries = batteries,
            perMuscle = perMuscle,
            articularBatteries = emptyMap(),
            wellbeing = wellbeing,
        )
        return AugeSnapshot(batteries = batteries, dashboard = dashboard)
    }

    private fun heavySession(): List<WorkoutLog> {
        val now = System.currentTimeMillis()
        return listOf(
            WorkoutLog(
                id = "heavy-1", programId = "p", sessionId = "s", sessionName = "Heavy",
                date = Instant.ofEpochMilli(now - 86_400_000L).toString(),
                durationMinutes = 75,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "bench", exerciseName = "Bench Press",
                        exerciseDbId = "bench", restTime = 120,
                        sets = List(5) { i ->
                            CompletedSet(id = "b-$i", weight = 100.0, reps = 5, rpe = 9.5)
                        },
                    ),
                    CompletedExercise(
                        exerciseId = "squat", exerciseName = "Squat",
                        exerciseDbId = "squat", restTime = 150,
                        sets = List(4) { i ->
                            CompletedSet(id = "s-$i", weight = 140.0, reps = 5, rpe = 9.0)
                        },
                    ),
                ),
            ),
        )
    }

    private fun extremeFatigueSession(): List<WorkoutLog> {
        val now = System.currentTimeMillis()
        return (1..7).map { day ->
            WorkoutLog(
                id = "extreme-$day", programId = "p", sessionId = "s", sessionName = "Grind",
                date = Instant.ofEpochMilli(now - (8 - day) * 86_400_000L).toString(),
                durationMinutes = 90,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "bench", exerciseName = "Bench Press",
                        exerciseDbId = "bench", restTime = 60,
                        sets = List(8) { i ->
                            CompletedSet(id = "b-$day-$i", weight = 100.0, reps = 5, rpe = 10.0)
                        },
                    ),
                    CompletedExercise(
                        exerciseId = "squat", exerciseName = "Squat",
                        exerciseDbId = "squat", restTime = 60,
                        sets = List(8) { i ->
                            CompletedSet(id = "s-$day-$i", weight = 140.0, reps = 5, rpe = 10.0)
                        },
                    ),
                ),
            )
        }
    }
}
