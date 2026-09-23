package com.example.kpkn.data.onboarding

import android.content.Context
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.domain.auge.remapMuscleMultiplierMapToPillars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class SetupRingsPreview(
    val batteries: GlobalBatteries,
    val stagedWellbeing: DailyWellbeingLog?,
)

/** Runs the same recovery engines and uses the same persisted inputs as Home,
 * without writing a check-in or teaching the adaptive cache while previewing. */
class SetupRingsPreviewCalculator(private val context: Context) {
    suspend fun calculate(
        settings: Settings,
        evidence: InitialRecoveryEvidence,
        commitId: String,
        manualMuscles: Map<String, Int>,
        manualEnergy: Int?,
        manualStructure: Int?,
        discomforts: List<String>?,
        nowMs: Long,
    ): SetupRingsPreview {
        val auge = AugeRepository.getInstance(context)
        val history = ProgramRepository.getInstance().history.value
        val today = auge.getTodayWellbeing()
        val active = auge.getActiveWellbeingWithManualOverrides()
        val todayHasManual = today != null && (today.manualNeuralBattery != null ||
            today.manualSpinalBattery != null || today.manualMuscularBattery != null ||
            today.manualMuscleBatteries.isNotEmpty() || today.manualMuscleOverridesV2.isNotEmpty())
        val existing = if (active != null && !todayHasManual) active else today
        val feedbacks = auge.getPostSessionFeedbacks()
        val sleep = auge.getLastNSleepLogs(7)
        val nutrition = NutritionRepository.getInstance().nutritionLogs.value
        val cache = auge.getAdaptiveCache().let { it.copy(muscleDrainMultipliers = remapMuscleMultiplierMapToPillars(it.muscleDrainMultipliers)) }
        val catalog = exerciseCatalogSnapshot().associateBy { it.id.lowercase() }
        val candidateSettings = settings.copy(initialRecoveryEvidence = evidence)
        val automaticWellbeing = existing?.copy(manualMuscularBattery = null,
            manualMuscleBatteries = emptyMap(), manualMuscleOverridesV2 = emptyMap())
        return withContext(Dispatchers.Default) {
            val autoMuscles = AugeRecoveryEngine.getPerMuscleBatteries(
                history, automaticWellbeing, candidateSettings, catalog, sleep, nutrition, feedbacks, cache,
                nowOverrideMs = nowMs,
            )
            fun battery(level: Int?) = level?.let { (100 - (it.coerceIn(1, 5) - 1) * 25).coerceIn(0, 100) }
            val manual = manualMuscles.mapValues { (muscle, level) ->
                val automatic = autoMuscles[muscle]?.recoveryScore
                    ?: error("No se pudo estimar la batería automática de $muscle")
                ManualMuscleBatteryOverride(battery(level) ?: 0, nowMs, null, automatic)
            }
            val fields = buildSet {
                if (manual.isNotEmpty()) add("muscle_batteries")
                if (manualEnergy != null) add("energy")
                if (manualStructure != null) add("structure")
                if (discomforts != null) add("discomforts")
            }
            val incoming = if (fields.isEmpty()) null else DailyWellbeingLog(
                id = "$commitId-onboarding-wellbeing", date = LocalDate.now().toString(),
                manualMuscleBatteries = manual.mapValues { it.value.battery },
                manualMuscleOverridesV2 = manual, manualBatteryAnchorMs = nowMs,
                manualNeuralBattery = battery(manualEnergy), manualSpinalBattery = battery(manualStructure),
                preWorkoutDiscomforts = discomforts.orEmpty(), source = WellbeingSource.ONBOARDING_INITIAL,
                capturedFields = fields,
            )
            val onCommitToday = if (incoming == null) existing else today
            val proposed = if (onCommitToday == null || incoming == null) incoming ?: onCommitToday else onCommitToday.copy(
                manualMuscleBatteries = onCommitToday.manualMuscleBatteries + incoming.manualMuscleBatteries,
                manualMuscleOverridesV2 = onCommitToday.manualMuscleOverridesV2 + incoming.manualMuscleOverridesV2,
                manualBatteryAnchorMs = if (manual.isNotEmpty()) nowMs else onCommitToday.manualBatteryAnchorMs,
                manualNeuralBattery = if (manualEnergy != null) incoming.manualNeuralBattery else onCommitToday.manualNeuralBattery,
                manualSpinalBattery = if (manualStructure != null) incoming.manualSpinalBattery else onCommitToday.manualSpinalBattery,
                preWorkoutDiscomforts = discomforts ?: onCommitToday.preWorkoutDiscomforts,
            )
            val normalized = proposed?.copy(manualMuscleBatteries = remapMuscleIntMapToPillars(proposed.manualMuscleBatteries))
            val muscles = AugeRecoveryEngine.getPerMuscleBatteries(history, normalized, candidateSettings,
                catalog, sleep, nutrition, feedbacks, cache, nowOverrideMs = nowMs)
            val articular = AugeTtcEngine.calculateArticularBatteries(history, catalog, feedbacks, normalized)
            val batteries = AugeRecoveryEngine.calculateGlobalBatteries(history, normalized, candidateSettings,
                catalog, sleep, nutrition, feedbacks, cache, precomputedMuscles = muscles,
                articularBatteries = articular, nowOverrideMs = nowMs)
            SetupRingsPreview(batteries, incoming)
        }
    }
}
