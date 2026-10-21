package com.example.kpkn.data.onboarding

import android.content.Context
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.augeRecoveryContext
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.AugeSnapshotComputer
import com.example.kpkn.domain.auge.AugeSnapshotCore
import com.example.kpkn.domain.auge.AugeSnapshotInputs
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
import java.time.Instant
import java.time.ZoneId

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

/**
 * Ajustes 0–100 de «Tus RINGS» tal como los ve la persona: energía, columna (la del blend de Inicio) y músculos por pilar.
 * Un canal sin valor aquí cae en los 1–5 de borradores viejos o en la estimación. Las sensaciones NO son ancla: solo
 * penalizan la evidencia.
 */
data class SetupRingsManualValues(
    val energia: Int? = null,
    val columna: Int? = null,
    val musculos: Map<String, Int> = emptyMap(),
)

data class SetupRingsPreview(
    val batteries: GlobalBatteries,
    val stagedWellbeing: DailyWellbeingLog?,
    /** Cobertura por canal: procedencia, carácter estimado e incertidumbre. La Columna es la de Inicio (blend). */
    val coverage: RingsCoverage,
    /** Estimación automática de cada pilar (sin ajustes de la persona): referencia de los deslizadores del resultado. */
    val muscleEstimates: Map<String, Int> = emptyMap(),
)

/**
 * Fecha del check-in derivada del instante INYECTADO (no de `LocalDate.now()`),
 * con la zona del sistema: la vista previa y el commit deben acordar el mismo
 * día aunque se crucen medianoche.
 */
internal fun previewWellbeingDate(nowMs: Long): String =
    Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/**
 * Payload REAL que usa la vista previa: el mismo staging que consume el commit
 * (`SetupRingsResponseMapping.stageCheckIn`), anclado al instante del cálculo.
 * Devuelve `null` cuando no hay ningún campo declarado (nunca un check-in vacío).
 */
internal fun stagePreviewWellbeing(
    commitId: String,
    staged: StagedRingsCheckIn,
    nowMs: Long,
    muscleOverrides: Map<String, ManualMuscleBatteryOverride> = emptyMap(),
): DailyWellbeingLog? = if (staged.isEmpty) null else staged.toWellbeingLog(
    id = "$commitId-onboarding-wellbeing",
    date = previewWellbeingDate(nowMs),
    anchorMs = nowMs,
    muscleOverridesV2 = muscleOverrides,
)

/** Propuesta del alta: el check-in que se guardaría y los datos que el motor lee con él. */
private class RingsProposal(
    val incoming: DailyWellbeingLog?,
    val normalized: DailyWellbeingLog?,
    val core: AugeSnapshotCore,
) {
    val muscles get() = core.perMuscle
    val articular get() = core.articular
}

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
        manual: SetupRingsManualValues = SetupRingsManualValues(),
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
        val catalog = catalogExerciseIndex()
        val candidateSettings = when (evidenceInput) {
            is SetupRingsEvidenceInput.Available -> settings.copy(initialRecoveryEvidence = evidenceInput.evidence)
            // Conservada tal cual: no renueva su fecha ni prolonga su caducidad.
            SetupRingsEvidenceInput.Preserved -> settings
            SetupRingsEvidenceInput.Absent -> settings.copy(initialRecoveryEvidence = null)
        }
        val automaticWellbeing = existing?.copy(manualMuscularBattery = null,
            manualMuscleBatteries = emptyMap(), manualMuscleOverridesV2 = emptyMap())
        val zone = ZoneId.systemDefault()
        val inputs = AugeSnapshotInputs(
            history, automaticWellbeing, candidateSettings, catalog, nowMs, zone,
            sleep, nutrition, feedbacks, cache,
            NutritionRepository.getInstance().augeRecoveryContext(candidateSettings, nowMs, zone),
        )
        return withContext(Dispatchers.Default) {
            val autoMuscles = AugeSnapshotComputer.compute(inputs).perMuscle
            // Mismo merge por campos que el commit del alta, para que la vista previa no diverja del guardado.
            fun proposalFor(staged: StagedRingsCheckIn): RingsProposal {
                val overrides = staged.manualMuscleBatteries.mapValues { (muscle, battery) ->
                    val automatic = autoMuscles[muscle]?.recoveryScore
                        ?: error("No se pudo estimar la batería automática de $muscle")
                    ManualMuscleBatteryOverride(battery, nowMs, null, automatic)
                }
                val incoming = stagePreviewWellbeing(commitId, staged, nowMs, overrides)
                val onCommitToday = if (incoming == null) existing else today
                val proposed = if (incoming == null) onCommitToday else mergeSetupWellbeing(onCommitToday, incoming)
                val normalized = proposed?.copy(manualMuscleBatteries = remapMuscleIntMapToPillars(proposed.manualMuscleBatteries))
                return RingsProposal(
                    incoming = incoming,
                    normalized = normalized,
                    core = AugeSnapshotComputer.compute(inputs.copy(wellbeing = normalized)),
                )
            }
            // Los 1–5 de borradores viejos solo entran donde no hay ajuste 0–100 del mismo canal o pilar. Las sensaciones
            // del check-in NO anclan: el alta lleva solo lo que la persona mueve.
            val legacy = SetupRingsResponseMapping.stageCheckIn(
                checkIn = SetupRingsCheckIn(),
                manualMuscleLevels = manualMuscles.filterKeys { it !in manual.musculos },
                manualEnergyLevel = manualEnergy.takeIf { manual.energia == null },
                manualStructureLevel = manualStructure.takeIf { manual.columna == null },
                discomforts = discomforts,
            )
            val withoutColumna = SetupRingsResponseMapping.withRingAdjustments(legacy, manual.energia, null, manual.musculos)
            val first = proposalFor(withoutColumna)
            // La Columna guardada es la batería cruda que da el valor elegido en el blend de Inicio. Se invierte con el
            // suelo articular y los guardianes de ESTA propuesta: la Columna no entra en músculos ni en articulares.
            val columnaBattery = manual.columna?.let { desired ->
                val (floor, guards) = AugeRecoveryEngine.structureInputs(first.muscles, first.articular)
                AugeRecoveryEngine.invertStructureRingScore(desired, floor, guards)
            }
            val settled = if (columnaBattery == null) first else proposalFor(
                SetupRingsResponseMapping.withRingAdjustments(withoutColumna, null, columnaBattery, emptyMap()),
            )
            val batteries = settled.core.batteries
            // Columna de Inicio: el mismo blend (`structureRingScore`) sobre la batería cruda, no la cruda sola.
            val (artFloor, guardScores) = AugeRecoveryEngine.structureInputs(settled.muscles, settled.articular)
            val structure = AugeRecoveryEngine.structureRingScore(batteries.spinal, artFloor, guardScores)
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
                wellbeing = settled.normalized,
                contribution = contribution,
                subjective = checkIn,
                score = { channel ->
                    when (channel) {
                        com.example.kpkn.data.models.RecoveryChannelId.MUSCULAR -> batteries.muscular
                        com.example.kpkn.data.models.RecoveryChannelId.SYSTEM -> batteries.cnc
                        com.example.kpkn.data.models.RecoveryChannelId.STRUCTURE -> structure
                    }
                },
            )
            SetupRingsPreview(
                batteries = batteries,
                stagedWellbeing = settled.incoming,
                coverage = coverage,
                muscleEstimates = autoMuscles.mapValues { (_, status) -> status.recoveryScore },
            )
        }
    }
}

/**
 * Check-in declarado que comparten vista previa y commit (un solo staging, así
 * no se previsualiza una cosa y se guarda otra).
 *
 * Se deposita en la fila de check-in en AMBOS calibrados:
 *  - [RingsCalibration.PARTIAL_CHECK_IN]: es el ÚNICO registro de lo declarado
 *    (no hay evidencia que lo conserve).
 *  - [RingsCalibration.FULL_EVIDENCE]: la evidencia conserva las sensaciones y,
 *    además, la fila deja constancia durable de lo declarado (procedencia por
 *    canal vía `declaredCheckInChannels` y origen ONBOARDING_INITIAL), que es lo
 *    que Home usa mucho después, cuando la evidencia ya caducó o se quitó. El
 *    motor aplica UNA fuente por canal: el ajuste manual del día desactiva la
 *    mezcla de la estimación inicial de ESE canal
 *    (`muscularEstimated = isEstimated && manual == null`), por lo que no hay
 *    doble fatiga; los canales sin check-in siguen usando la evidencia.
 *  - [RingsCalibration.NONE] (sin sensaciones; OMITTED/PRESERVE): no se fabrica
 *    nada: el desconocimiento jamás se convierte en check-in.
 */
fun SetupRingsMapping.previewCheckIn(): SetupRingsCheckIn =
    if (calibration == RingsCalibration.NONE) SetupRingsCheckIn() else checkIn
