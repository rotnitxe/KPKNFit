package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.computeProgramAugeSnapshot
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.MacroGoals
import com.example.kpkn.screens.auge.computeHomeAugeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** Paridad de los consumidores y conservación del pipeline anterior de Home, con reloj fijo. */
class AugeSnapshotComputerTest {
    private val zone = ZoneId.of("UTC")
    private val now = AugeGoldenCorpus.BASE_MS

    @Test
    fun homeKeepsThePreviousPipelineAcrossTheGoldenCorpus() {
        var comparisons = 0
        AugeGoldenCorpus.build(now).forEach { case ->
            case.instants.forEach { time ->
                val inputs = AugeSnapshotInputs(case.history, case.wellbeing, case.settings, AugeGoldenCorpus.EXERCISE_DB, time, zone)
                val muscles = AugeRecoveryEngine.getPerMuscleBatteries(
                    case.history, case.wellbeing, case.settings, inputs.exerciseDb, nowOverrideMs = time, zone = zone,
                )
                val articular = AugeTtcEngine.calculateArticularBatteries(
                    case.history, inputs.exerciseDb, wellbeing = case.wellbeing, nowMs = time, zone = zone,
                )
                val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
                    case.history, case.wellbeing, case.settings, inputs.exerciseDb,
                    precomputedMuscles = muscles, articularBatteries = articular, nowOverrideMs = time, zone = zone,
                )
                val dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
                    batteries, muscles, articular, case.wellbeing,
                    recentSessionCount = AugeRecoveryEngine.recentSessionCount(case.history, time),
                )
                val core = computeHomeAugeSnapshot(inputs)
                assertEquals(case.id, batteries, core.batteries)
                assertEquals(case.id, muscles, core.perMuscle)
                assertEquals(case.id, articular, core.articular)
                assertEquals(case.id, dashboard, core.dashboard)
                assertEquals(case.id, AugeRecoveryEngine.calculateDailyReadiness(dashboard, case.wellbeing), core.readiness)
                comparisons++
            }
        }
        println("[SNAPSHOT-2.3] Home anterior = computador en $comparisons instantes del corpus")
        assertTrue(comparisons >= 100)
    }

    @Test
    fun programAndHomePublishTheSameCoreForTheSameInputs() {
        val history = longHistory
        val last = history.sessions.last()
        val inputs = AugeSnapshotInputs(
            history.logs, manualWellbeing(last.completionMs - 6 * AugeGoldenCorpus.HORA_MS),
            AugeSyntheticUserHarness.settingsFor(history.user), AugeSyntheticUserHarness.exerciseDb, last.completionMs, zone,
            adaptiveCache = AugeAdaptiveCache(cnsRecoveryHours = 41.0, spinalRecoveryHours = 63.0, cnsLearningDelta = -2.0),
        )
        assertEquals(computeHomeAugeSnapshot(inputs), computeProgramAugeSnapshot(inputs))
        assertEquals(AugeSnapshotComputer.compute(inputs), computeProgramAugeSnapshot(inputs))
        val empty = inputs.copy(history = emptyList(), wellbeing = null)
        assertEquals(computeHomeAugeSnapshot(empty), computeProgramAugeSnapshot(empty))
        assertEquals(0.0, computeProgramAugeSnapshot(empty).cumulativeFatigue, 0.0)
    }

    @Test
    fun previewEqualsComputeWithTheClosedLogAfterTwelveWeeks() = checkPreview(null)

    @Test
    fun previewEqualsComputeWithManualAnchorsAndLongHistory() {
        checkPreview(manualWellbeing(longHistory.sessions.last().completionMs - 6 * AugeGoldenCorpus.HORA_MS))
    }

    private fun checkPreview(wellbeing: DailyWellbeingLog?) {
        val history = longHistory
        val session = history.sessions.last()
        val prior = history.logs.dropLast(1)
        assertTrue(prior.size >= 30)
        assertTrue(session.completionMs - AugeUtils.logDateMs(prior.first()) > 60L * AugeGoldenCorpus.DIA_MS)
        val inputs = AugeSnapshotInputs(
            prior + session.log, wellbeing, AugeSyntheticUserHarness.settingsFor(history.user),
            AugeSyntheticUserHarness.exerciseDb, session.completionMs, zone,
        )
        val core = AugeSnapshotComputer.compute(inputs)
        val baseline = AugeSnapshotComputer.compute(inputs.copy(history = prior))
        val nextDay = AugeSnapshotComputer.compute(inputs.copy(nowMs = session.completionMs + AugeGoldenCorpus.DIA_MS))
        val preview = AugeRecoveryEngine.previewPostSessionBatteries(
            prior, session.log.copy(muscularImpactV2 = null), wellbeing, inputs.settings, inputs.exerciseDb,
            // Aunque el llamador entregue la articular anterior, el log se evalúa dentro del computador.
            articularBatteries = baseline.articular,
            nowOverrideMs = session.completionMs,
            automaticImpact = session.log.muscularImpactV2,
            zone = zone,
        )
        assertEquals(core.perMuscle, preview.perMuscle)
        assertEquals(core.batteries.muscular, preview.muscular)
        assertEquals(core.batteries.cnc, preview.neural)
        assertEquals(core.batteries.spinal, preview.spinalRaw)
        assertEquals(core.dashboard.channelScore(RecoveryChannelId.STRUCTURE), preview.spinal)
        assertEquals(nextDay.batteries.cnc, preview.tomorrowNeural)
        assertEquals(nextDay.dashboard.channelScore(RecoveryChannelId.STRUCTURE), preview.tomorrowSpinal)
        assertEquals(baseline.batteries.muscular, preview.preSessionMuscular)
        assertEquals(session.log.muscularImpactV2, preview.automaticImpact)
        assertTrue("La sesión debe drenar alguna articular", core.articular.values.any { it.recoveryScore < 100 })
        println("[SNAPSHOT-2.3] 12 semanas, anclas=${wellbeing != null}, sesiones=${prior.size}, divergencia=0")
    }

    @Test
    fun nutritionUsesResolvedGoalsAndTheInjectedInstant() {
        val settings = Settings().let { it.copy(algorithmSettings = it.algorithmSettings.copy(augeEnableNutritionTracking = true)) }
        val goals = NutritionRecoveryContext(mapOf(LocalDate.of(2025, 3, 3) to DayGoalsResult.Present(MacroGoals(2_000, 100))))
        val logs = listOf(NutritionLog(date = "2025-03-03", foods = listOf(LoggedFood(calories = 1_000.0, protein = 50.0))))
        fun multiplier(s: Settings, meals: List<NutritionLog>, context: NutritionRecoveryContext, time: Long = now) =
            AugeRecoveryEngine.getNutritionMultiplier(s, meals, 3, context, time, zone)
        assertEquals(1.6, multiplier(settings, logs, goals), 0.0)
        assertEquals(1.0, multiplier(settings, emptyList(), goals), 0.0)
        assertEquals(1.0, multiplier(settings, logs, NutritionRecoveryContext()), 0.0)
        assertEquals(1.0, multiplier(Settings(), logs, goals), 0.0)
        assertEquals(1.0, multiplier(settings, logs, goals, now + 7 * AugeGoldenCorpus.DIA_MS), 0.0)
    }

    @Test
    fun explicitZoneMakesLegacyAnchorsAndNutritionIndependentOfTheMachineZone() {
        val original = TimeZone.getDefault()
        try {
            val settings = Settings().let { it.copy(algorithmSettings = it.algorithmSettings.copy(augeEnableNutritionTracking = true)) }
            val inputs = AugeSnapshotInputs(
                listOf(WorkoutLog(
                    id = "log-legacy", programId = "p", sessionId = "s", sessionName = "Pecho", date = "2025-03-02",
                    durationMinutes = 50,
                    completedExercises = AugeRealSessionFixtures.completedExercises,
                )), DailyWellbeingLog(id = "ancla-legacy", date = "2025-03-03", manualNeuralBattery = 60),
                settings, AugeGoldenCorpus.EXERCISE_DB, now, zone,
                feedbacks = listOf(PostSessionFeedback(
                    logId = "log-legacy", date = "2025-03-02", muscleFeedback = mapOf("Pectorales" to MuscleFeedbackEntry(doms = 4)),
                )),
                nutritionLogs = listOf(NutritionLog(date = "2025-03-02", foods = listOf(LoggedFood(calories = 2_000.0)))),
                nutritionContext = NutritionRecoveryContext(mapOf(LocalDate.of(2025, 3, 3) to DayGoalsResult.Present(MacroGoals(2_000, 100)))),
            )
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val first = AugeSnapshotComputer.compute(inputs)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(first, AugeSnapshotComputer.compute(inputs))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun manualWellbeing(anchor: Long) = DailyWellbeingLog(
        id = "anclas-largas", date = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate().toString(),
        manualBatteryAnchorMs = anchor, manualNeuralBattery = 62, manualSpinalBattery = 58,
        manualMuscleOverridesV2 = mapOf("Pectorales" to ManualMuscleBatteryOverride(51, anchor, null, 81)),
    )

    private companion object {
        val longHistory by lazy {
            val user = AugeSyntheticUserHarness.fullUsers()[1].copy(weeks = 12)
            AugeSyntheticUserHarness.buildHistory(user)
        }
    }
}
