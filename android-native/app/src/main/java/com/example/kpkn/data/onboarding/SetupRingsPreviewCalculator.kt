package com.example.kpkn.data.onboarding

import android.content.Context
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.domain.auge.InitialRecoveryEvidencePolicy
import com.example.kpkn.domain.auge.InitialRecoveryPolicyInput
import com.example.kpkn.domain.auge.remapMuscleIntMapToPillars
import com.example.kpkn.domain.auge.remapMuscleMultiplierMapToPillars
import com.example.kpkn.domain.onboarding.RingsCalibration
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsEvidenceHandling
import com.example.kpkn.domain.onboarding.SetupRingsCheckIn
import com.example.kpkn.domain.onboarding.SetupRingsMapping
import com.example.kpkn.domain.onboarding.SetupRingsResponseMapping
import com.example.kpkn.domain.onboarding.StagedRingsCheckIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Estado de la evidencia inicial que debe usar la vista previa. */
sealed interface SetupRingsEvidenceInput {
    /** Evidencia inicial disponible (recién fabricada con datos suficientes). */
    data class Available(val evidence: InitialRecoveryEvidence) : SetupRingsEvidenceInput

    /** Evidencia preservada: se usa tal cual, sin renovar `capturedAtMs` ni prolongar su caducidad. */
    data object Preserved : SetupRingsEvidenceInput

    /** Sin evidencia: no se fabrica ninguna ficticia. */
    data object Absent : SetupRingsEvidenceInput
}

/** Destino de `Settings.initialRecoveryEvidence` para el commit del alta. */
fun SetupRingsMapping.toEvidencePatchField(): SetupPatchField<InitialRecoveryEvidence?> =
    when (evidenceHandling) {
        RingsEvidenceHandling.SET -> SetupPatchField.Set(evidence)
        RingsEvidenceHandling.CLEAR -> SetupPatchField.Clear
        RingsEvidenceHandling.UNCHANGED -> SetupPatchField.Unchanged
    }

data class SetupRingsPreview(
    val batteries: GlobalBatteries,
    val stagedWellbeing: DailyWellbeingLog?,
    /** Cobertura por canal: procedencia, carácter estimado e incertidumbre. */
    val coverage: RingsCoverage,
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
    ): SetupRingsPreview = calculate(
        settings = settings,
        evidenceInput = SetupRingsEvidenceInput.Available(evidence),
        commitId = commitId,
        manualMuscles = manualMuscles,
        manualEnergy = manualEnergy,
        manualStructure = manualStructure,
        discomforts = discomforts,
        nowMs = nowMs,
    )

    suspend fun calculate(
        settings: Settings,
        evidenceInput: SetupRingsEvidenceInput,
        commitId: String,
        manualMuscles: Map<String, Int>,
        manualEnergy: Int?,
        manualStructure: Int?,
        discomforts: List<String>?,
        nowMs: Long,
        checkIn: SetupRingsCheckIn = SetupRingsCheckIn(),
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
        val candidateSettings = when (evidenceInput) {
            is SetupRingsEvidenceInput.Available -> settings.copy(initialRecoveryEvidence = evidenceInput.evidence)
            // Conservada tal cual: no renueva su fecha ni prolonga su caducidad.
            SetupRingsEvidenceInput.Preserved -> settings
            SetupRingsEvidenceInput.Absent -> settings.copy(initialRecoveryEvidence = null)
        }
        val automaticWellbeing = existing?.copy(manualMuscularBattery = null,
            manualMuscleBatteries = emptyMap(), manualMuscleOverridesV2 = emptyMap())
        return withContext(Dispatchers.Default) {
            val autoMuscles = AugeRecoveryEngine.getPerMuscleBatteries(
                history, automaticWellbeing, candidateSettings, catalog, sleep, nutrition, feedbacks, cache,
                nowOverrideMs = nowMs,
            )
            // Mapeo de respuestas compartido con el commit: escala 1-5 → batería 0-100.
            val staged: StagedRingsCheckIn = SetupRingsResponseMapping.stageCheckIn(
                checkIn = checkIn,
                manualMuscleLevels = manualMuscles,
                manualEnergyLevel = manualEnergy,
                manualStructureLevel = manualStructure,
                discomforts = discomforts,
            )
            val manual = staged.manualMuscleBatteries.mapValues { (muscle, battery) ->
                val automatic = autoMuscles[muscle]?.recoveryScore
                    ?: error("No se pudo estimar la batería automática de $muscle")
                ManualMuscleBatteryOverride(battery, nowMs, null, automatic)
            }
            val incoming = if (staged.isEmpty) null else staged.toWellbeingLog(
                id = "$commitId-onboarding-wellbeing",
                date = LocalDate.now().toString(),
                anchorMs = nowMs,
                muscleOverridesV2 = manual,
            )
            val onCommitToday = if (incoming == null) existing else today
            // Mismo merge por campos que SetupPersistence.mergeWellbeing.
            val proposed = if (onCommitToday == null || incoming == null) incoming ?: onCommitToday else onCommitToday.copy(
                manualMuscleBatteries = if ("muscle_batteries" in incoming.capturedFields) onCommitToday.manualMuscleBatteries + incoming.manualMuscleBatteries else onCommitToday.manualMuscleBatteries,
                manualMuscleOverridesV2 = if ("muscle_batteries" in incoming.capturedFields) onCommitToday.manualMuscleOverridesV2 + incoming.manualMuscleOverridesV2 else onCommitToday.manualMuscleOverridesV2,
                manualBatteryAnchorMs = if ("muscle_batteries" in incoming.capturedFields) incoming.manualBatteryAnchorMs else onCommitToday.manualBatteryAnchorMs,
                manualNeuralBattery = if ("energy" in incoming.capturedFields) incoming.manualNeuralBattery else onCommitToday.manualNeuralBattery,
                manualSpinalBattery = if ("structure" in incoming.capturedFields) incoming.manualSpinalBattery else onCommitToday.manualSpinalBattery,
                preWorkoutDiscomforts = if ("discomforts" in incoming.capturedFields) incoming.preWorkoutDiscomforts else onCommitToday.preWorkoutDiscomforts,
            )
            val normalized = proposed?.copy(manualMuscleBatteries = remapMuscleIntMapToPillars(proposed.manualMuscleBatteries))
            val muscles = AugeRecoveryEngine.getPerMuscleBatteries(history, normalized, candidateSettings,
                catalog, sleep, nutrition, feedbacks, cache, nowOverrideMs = nowMs)
            val articular = AugeTtcEngine.calculateArticularBatteries(history, catalog, feedbacks, normalized)
            val batteries = AugeRecoveryEngine.calculateGlobalBatteries(history, normalized, candidateSettings,
                catalog, sleep, nutrition, feedbacks, cache, precomputedMuscles = muscles,
                articularBatteries = articular, nowOverrideMs = nowMs)
            // Cobertura honesta por canal reutilizando la política de evidencia
            // (mismo cálculo que consume el motor, sin estimadores paralelos).
            val contribution = InitialRecoveryEvidencePolicy.resolve(
                InitialRecoveryPolicyInput(
                    evidence = candidateSettings.initialRecoveryEvidence,
                    nowMs = nowMs,
                    workoutLogs = history,
                ),
            )
            val coverage = RingsCoverage.evaluate(
                historyIsEmpty = history.isEmpty(),
                wellbeing = normalized,
                contribution = contribution,
                subjective = checkIn,
                score = { channel ->
                    when (channel) {
                        com.example.kpkn.data.models.RecoveryChannelId.MUSCULAR -> batteries.muscular
                        com.example.kpkn.data.models.RecoveryChannelId.SYSTEM -> batteries.cnc
                        com.example.kpkn.data.models.RecoveryChannelId.STRUCTURE -> batteries.spinal
                    }
                },
            )
            SetupRingsPreview(batteries, incoming, coverage)
        }
    }
}

/** Solo la calibración parcial deposita la sensación subjetiva en el check-in. */
fun SetupRingsMapping.previewCheckIn(): SetupRingsCheckIn =
    if (calibration == RingsCalibration.PARTIAL_CHECK_IN) checkIn else SetupRingsCheckIn()
