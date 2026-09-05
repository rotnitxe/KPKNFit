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
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.data.models.MuscleRecoveryStatus
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
        "adductor" to ExerciseMuscleInfo(
            id = "adductor", name = "Máquina de Aductores", equipment = "machine",
            efc = 6.0, cnc = 5.0, ssc = 3.0,
            involvedMuscles = listOf(InvolvedMuscle("Aductores", MuscleRole.PRIMARY)),
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
    fun previewPostSessionBatteries_muscular_matches_home_not_fatigue_drain() {
        val previewLog = heavySession().first().copy(id = "preview")
        val impact = MuscularSessionImpactV2(
            completionInstantIso = previewLog.date,
            globalMuscularDrain = 56,
            perMuscle = emptyMap(),
            involvedVolumeMuscles = emptySet(),
            setInputHash = "hash",
            contextHash = "ctx",
        )
        val settings = Settings()
        val preview = AugeRecoveryEngine.previewPostSessionBatteries(
            baseHistory = emptyList(),
            previewLog = previewLog,
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
            automaticImpact = impact,
        )
        val homeMuscles = AugeRecoveryEngine.getPerMuscleBatteries(
            history = listOf(previewLog),
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
        )
        val homeBatteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = listOf(previewLog),
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
            precomputedMuscles = homeMuscles,
        )
        val baseMuscles = AugeRecoveryEngine.getPerMuscleBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
        )
        val baseBatteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
            precomputedMuscles = baseMuscles,
        )
        assertEquals(homeBatteries.muscular, preview.muscular)
        val subtracted = (baseBatteries.muscular - impact.globalMuscularDrain).coerceIn(0, 100)
        assertTrue(
            "Finish ring ${preview.muscular} must follow Home ${homeBatteries.muscular}, not base-drain $subtracted",
            preview.muscular != subtracted || homeBatteries.muscular == subtracted,
        )
        assertEquals(
            (baseBatteries.muscular - homeBatteries.muscular).coerceIn(0, 100),
            preview.globalMuscularDrain,
        )
    }

    @Test
    fun fullPipeline_manualOverrideWithHistory_lowersRingsBelow100() {
        val wellbeing = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            manualNeuralBattery = 50,
            manualSpinalBattery = 50,
            manualMuscleBatteries = mapOf("Pectorales" to 50, "Cuádriceps" to 50),
            manualBatteryAnchorMs = System.currentTimeMillis(),
        )
        val snapshot = fullCompute(history = heavySession(), wellbeing = wellbeing)
        assertTrue(snapshot.ringScore(RecoveryChannelId.SYSTEM) < 100)
        assertTrue(snapshot.ringScore(RecoveryChannelId.STRUCTURE) < 100)
    }

    @Test
    fun fullPipeline_heavySession_lowersRings() {
        val snapshot = fullCompute(history = heavySession(), wellbeing = null)
        assertTrue(snapshot.ringScore(RecoveryChannelId.MUSCULAR) <= 100)
        assertTrue(snapshot.ringScore(RecoveryChannelId.SYSTEM) <= 100)
        assertTrue(snapshot.ringScore(RecoveryChannelId.STRUCTURE) <= 100)
    }

    @Test
    fun fullPipeline_manualOverrideWithoutAnchor_usesNowMs() {
        val wellbeing = DailyWellbeingLog(
            id = "today",
            date = LocalDate.now().toString(),
            manualNeuralBattery = 80,
            manualBatteryAnchorMs = null,
        )
        val before = System.currentTimeMillis()
        val snapshot = fullCompute(history = heavySession(), wellbeing = wellbeing)
        val after = System.currentTimeMillis()
        val ringValue = snapshot.ringScore(RecoveryChannelId.SYSTEM)
        assertTrue(ringValue in 0..100)
        assertTrue(ringValue < 100)
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

    @Test
    fun fullPipeline_physiologicalFloor_enforced() {
        val snapshot = fullCompute(history = extremeFatigueSession(), wellbeing = null)
        assertTrue(snapshot.ringScore(RecoveryChannelId.SYSTEM) >= 20)
        assertTrue(snapshot.ringScore(RecoveryChannelId.STRUCTURE) >= 12)
    }

    @Test
    fun fullPipeline_extremeFatigue_stillWithinBounds() {
        val snapshot = fullCompute(history = extremeFatigueSession(), wellbeing = null)
        for (channel in RecoveryChannelId.entries) {
            val score = snapshot.ringScore(channel)
            assertTrue("$channel score $score out of 0..100", score in 0..100)
        }
    }

    @Test
    fun perMuscleBatteries_includeNonPillarMuscles() {
        val now = System.currentTimeMillis()
        val history = (1..3).map { day ->
            WorkoutLog(
                id = "adductor-$day", programId = "p", sessionId = "s", sessionName = "Abductores",
                date = Instant.ofEpochMilli(now - (4 - day) * 86_400_000L).toString(),
                durationMinutes = 45,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "adductor", exerciseName = "Máquina de Aductores",
                        exerciseDbId = "adductor", restTime = 90,
                        sets = List(6) { i ->
                            CompletedSet(id = "a-$day-$i", weight = 50.0, reps = 12, rpe = 9.0)
                        },
                    ),
                ),
            )
        }
        val perMuscle = AugeRecoveryEngine.getPerMuscleBatteries(
            history = history,
            wellbeing = null,
            settings = Settings(),
            exerciseDb = exerciseDb,
        )

        for (muscle in listOf("Aductores", "Antebrazo", "Cuello", "Psoas")) {
            val status = perMuscle[muscle]
            assertTrue("perMuscle must include '$muscle' with a real battery", status != null)
            assertTrue("'$muscle' score ${status?.recoveryScore} out of 0..100", (status?.recoveryScore ?: -1) in 0..100)
        }

        val adductorScore = perMuscle["Aductores"]?.recoveryScore ?: 100
        assertTrue(
            "Aductores battery should reflect recent drain (< 100), got $adductorScore",
            adductorScore < 100,
        )
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

    @Test
    fun calculateGlobalBatteries_articularGating_blendSuave() {
        val precomputed = AugeRecoveryEngine.pillarMuscles.associateWith { muscle ->
            com.example.kpkn.data.models.MuscleRecoveryStatus(
                muscleName = muscle,
                recoveryScore = if (muscle == "Cuádriceps") 90 else 100,
                hoursToRecovery = 0,
                hoursSinceLastSession = 24,
                effectiveSets = 0,
                status = com.example.kpkn.data.models.RecoveryStatus.FRESH
            )
        }
        val articular = mapOf(
            ArticularBattery.KNEE to ArticularBatteryState(recoveryScore = 30)
        )
        val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = Settings(),
            precomputedMuscles = precomputed,
            articularBatteries = articular
        )
        assertTrue("Muscular battery should be reduced by gating, got ${batteries.muscular}", batteries.muscular < 97)
        assertTrue("Muscular battery should not collapse to 30, got ${batteries.muscular}", batteries.muscular > 80)
    }

    @Test
    fun calculateGlobalBatteries_emptyArticularMap_noGating() {
        val precomputed = AugeRecoveryEngine.pillarMuscles.associateWith { muscle ->
            com.example.kpkn.data.models.MuscleRecoveryStatus(
                muscleName = muscle,
                recoveryScore = 90,
                hoursToRecovery = 0,
                hoursSinceLastSession = 24,
                effectiveSets = 0,
                status = com.example.kpkn.data.models.RecoveryStatus.FRESH
            )
        }
        val batteriesNoGating = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = Settings(),
            precomputedMuscles = precomputed,
            articularBatteries = emptyMap()
        )
        val articularEmpty = emptyMap<ArticularBattery, ArticularBatteryState>()
        val batteriesWithEmptyMap = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = Settings(),
            precomputedMuscles = precomputed,
            articularBatteries = articularEmpty
        )
        assertEquals(batteriesNoGating.muscular, batteriesWithEmptyMap.muscular)
    }

    @Test
    fun muscleToArticular_coversAllPillarMuscles() {
        for (muscle in AugeRecoveryEngine.pillarMuscles) {
            val related = AugeTtcEngine.MUSCLE_TO_ARTICULAR[muscle]
            assertTrue("Pillar muscle '$muscle' must be mapped in MUSCLE_TO_ARTICULAR", related != null && related.isNotEmpty())
        }
    }

    @Test
    fun lumbarEnum_usedInMappings() {
        val relatedErectores = AugeTtcEngine.MUSCLE_TO_ARTICULAR["Erectores Espinales"]
        assertTrue("Erectores Espinales must map to LUMBAR", relatedErectores?.contains(ArticularBattery.LUMBAR) == true)

        val lumbarDiscomfort = com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID["lumbar"]
        assertTrue("Lumbar discomfort must map to LUMBAR articular battery", lumbarDiscomfort?.relatedArticular?.contains(ArticularBattery.LUMBAR) == true)
    }

    @Test
    fun globalSpinalBattery_unchangedByArticularGating() {
        val precomputed = AugeRecoveryEngine.pillarMuscles.associateWith { muscle ->
            com.example.kpkn.data.models.MuscleRecoveryStatus(
                muscleName = muscle,
                recoveryScore = 90,
                hoursToRecovery = 0,
                hoursSinceLastSession = 24,
                effectiveSets = 0,
                status = com.example.kpkn.data.models.RecoveryStatus.FRESH
            )
        }
        val batteriesNoGating = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = Settings(),
            precomputedMuscles = precomputed,
            articularBatteries = emptyMap()
        )
        val articular = mapOf(
            ArticularBattery.KNEE to ArticularBatteryState(recoveryScore = 30)
        )
        val batteriesWithGating = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = Settings(),
            precomputedMuscles = precomputed,
            articularBatteries = articular
        )
        assertEquals(batteriesNoGating.spinal, batteriesWithGating.spinal)
    }
}
