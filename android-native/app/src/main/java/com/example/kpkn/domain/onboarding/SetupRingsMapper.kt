package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryAxialExposure
import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoveryMuscleScope
import com.example.kpkn.data.models.InitialRecoveryResponseState
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.WellbeingSource

enum class RingsCompletion { INCOMPLETE, UNKNOWN, PRESERVE, OMITTED, VALID }

/**
 * Evidencia histórica declarada en el alta. [UNKNOWN] es desconocimiento:
 * jamás equivale a ausencia de ejercicio ni a `sessions = 0`.
 */
enum class RingsHistoryState {
    /** No se informó. No implica que no se haya entrenado. */
    UNKNOWN,

    /** Ausencia declarada explícitamente (`recentTraining = false`). */
    DECLARED_ABSENT,

    /** Entrenamiento reciente declarado con datos. */
    DECLARED,
}

/**
 * Respuesta de molestias. [NONE] es una lista vacía explícita («sin molestias»);
 * [NOT_ANSWERED] y [OMITTED] son desconocimiento y no deben convertirse en lista vacía.
 */
enum class RingsDiscomfortResponse { NOT_ANSWERED, NONE, DECLARED, OMITTED }

/**
 * Check-in subjetivo declarado (sensaciones 1-5). Un campo `null` está
 * «no informado»: nunca se completa con un valor por defecto.
 */
data class SetupRingsCheckIn(
    val muscular: Int? = null,
    val energy: Int? = null,
    val structure: Int? = null,
) {
    val isDeclared: Boolean get() = muscular != null || energy != null || structure != null
    val isComplete: Boolean get() = muscular != null && energy != null && structure != null
}

/** Cuánta calibración deja el mapeo, separando evidencia histórica y check-in subjetivo. */
enum class RingsCalibration {
    /** Ni evidencia ni check-in: no se fabrica nada. */
    NONE,

    /** Check-in subjetivo real, sin fabricar `InitialRecoveryEvidence`. */
    PARTIAL_CHECK_IN,

    /** Evidencia inicial fabricada con datos suficientes. */
    FULL_EVIDENCE,
}

/** Destino de `Settings.initialRecoveryEvidence` (SET/CLEAR/UNCHANGED = Set/Clear/Unchanged). */
enum class RingsEvidenceHandling { SET, CLEAR, UNCHANGED }

data class SetupRingsInput(
    val startAction: String? = null,
    val recentTraining: Boolean? = null,
    val recentTrainingUnknown: Boolean = false,
    val sessions: Int? = null,
    val recencyDays: Int? = null,
    val activityType: InitialRecoveryActivityType? = null,
    val intensity: InitialRecoveryIntensity? = null,
    val muscleFeeling: Int? = null,
    val energy: Int? = null,
    val structureFeeling: Int? = null,
    val axialState: InitialRecoveryResponseState = InitialRecoveryResponseState.UNKNOWN,
    val axialSessions: Int? = null,
    val axialIntensity: InitialRecoveryIntensity? = null,
    val muscleScope: InitialRecoveryMuscleScope = InitialRecoveryMuscleScope.UNKNOWN,
    val selectedMuscles: List<String> = emptyList(),
    val discomfortIds: List<String> = emptyList(),
    val capturedAtMs: Long? = null,
    val discomfortResponse: RingsDiscomfortResponse = RingsDiscomfortResponse.NOT_ANSWERED,
)

data class SetupRingsMapping(
    val completion: RingsCompletion,
    val evidence: InitialRecoveryEvidence? = null,
    val discomfortIds: List<String> = emptyList(),
    val historyState: RingsHistoryState = RingsHistoryState.UNKNOWN,
    val checkIn: SetupRingsCheckIn = SetupRingsCheckIn(),
    val discomfortResponse: RingsDiscomfortResponse = RingsDiscomfortResponse.NOT_ANSWERED,
    val calibration: RingsCalibration = RingsCalibration.NONE,
    val evidenceHandling: RingsEvidenceHandling = RingsEvidenceHandling.UNCHANGED,
    val capturedAtMs: Long? = null,
) {
    /** Guarda un check-in real (evidencia completa o calibración parcial). */
    val savesRealCheckIn: Boolean get() = calibration != RingsCalibration.NONE
}

object SetupRingsMapper {
    fun map(input: SetupRingsInput, nowMs: Long): SetupRingsMapping {
        val action = input.startAction?.trim()?.lowercase()
        val checkIn = SetupRingsCheckIn(input.muscleFeeling, input.energy, input.structureFeeling)
        val historyState = when {
            input.recentTrainingUnknown || input.recentTraining == null -> RingsHistoryState.UNKNOWN
            input.recentTraining == false -> RingsHistoryState.DECLARED_ABSENT
            else -> RingsHistoryState.DECLARED
        }
        val discomfortIds = input.discomfortIds.distinct()
        val discomfortResponse = when {
            input.discomfortResponse != RingsDiscomfortResponse.NOT_ANSWERED -> input.discomfortResponse
            discomfortIds.isNotEmpty() -> RingsDiscomfortResponse.DECLARED
            else -> RingsDiscomfortResponse.NOT_ANSWERED
        }
        if (action == "omit" || action == "remove" || action?.contains("quitar") == true) {
            return SetupRingsMapping(
                completion = RingsCompletion.OMITTED,
                discomfortIds = discomfortIds,
                historyState = historyState,
                checkIn = checkIn,
                discomfortResponse = discomfortResponse,
                evidenceHandling = RingsEvidenceHandling.CLEAR,
            )
        }
        if (action == "keep" || action == "preserve" || action?.contains("conservar") == true || action?.contains("dejar") == true || action == "leave_uncalibrated" || action == "leave_un_calibrated") {
            return SetupRingsMapping(
                completion = RingsCompletion.PRESERVE,
                discomfortIds = discomfortIds,
                historyState = historyState,
                checkIn = checkIn,
                discomfortResponse = discomfortResponse,
                evidenceHandling = RingsEvidenceHandling.UNCHANGED,
            )
        }
        if (historyState == RingsHistoryState.UNKNOWN) {
            // Sin evidencia histórica no se fabrica nada: ni sesiones = 0 ni
            // InitialRecoveryEvidence. Un check-in subjetivo declarado sí es real
            // y se guarda como calibración parcial.
            return SetupRingsMapping(
                completion = RingsCompletion.UNKNOWN,
                evidence = null,
                discomfortIds = discomfortIds,
                historyState = historyState,
                checkIn = checkIn,
                discomfortResponse = discomfortResponse,
                calibration = if (checkIn.isDeclared) RingsCalibration.PARTIAL_CHECK_IN else RingsCalibration.NONE,
                evidenceHandling = RingsEvidenceHandling.UNCHANGED,
                capturedAtMs = if (checkIn.isDeclared) input.capturedAtMs ?: nowMs else input.capturedAtMs,
            )
        }
        if (!checkIn.isComplete) {
            return SetupRingsMapping(
                completion = RingsCompletion.INCOMPLETE,
                discomfortIds = discomfortIds,
                historyState = historyState,
                checkIn = checkIn,
                discomfortResponse = discomfortResponse,
                evidenceHandling = RingsEvidenceHandling.UNCHANGED,
            )
        }
        val recent = input.recentTraining
        val sessions = if (recent == false) 0 else input.sessions?.takeIf { it in 1..7 } ?: return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = discomfortIds, historyState = historyState, checkIn = checkIn, discomfortResponse = discomfortResponse)
        val recency = if (recent == false) 0 else input.recencyDays?.takeIf { it in 0..6 } ?: return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = discomfortIds, historyState = historyState, checkIn = checkIn, discomfortResponse = discomfortResponse)
        val type = if (recent == false) InitialRecoveryActivityType.MIXED else input.activityType ?: return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = discomfortIds, historyState = historyState, checkIn = checkIn, discomfortResponse = discomfortResponse)
        val intensity = if (recent == false) InitialRecoveryIntensity.MODERATE else input.intensity ?: return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = discomfortIds, historyState = historyState, checkIn = checkIn, discomfortResponse = discomfortResponse)
        val capturedAt = input.capturedAtMs ?: nowMs
        val axial = InitialRecoveryAxialExposure(
            state = input.axialState,
            sessions = if (recent == false) 0 else input.axialSessions,
            intensity = input.axialIntensity,
            recencyDays = if (recent == false) 0 else recency,
        )
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = capturedAt,
            recencyDays = recency,
            sessions = sessions,
            type = type,
            intensity = intensity,
            sensations = InitialRecoverySensations(input.muscleFeeling, input.energy, input.structureFeeling),
            muscleScope = input.muscleScope,
            selectedMuscles = input.selectedMuscles,
            axialExposure = axial,
            activityTypeState = if (recent == false) InitialRecoveryResponseState.DERIVED else InitialRecoveryResponseState.DECLARED,
        )
        return SetupRingsMapping(
            completion = RingsCompletion.VALID,
            evidence = evidence,
            discomfortIds = discomfortIds,
            historyState = historyState,
            checkIn = checkIn,
            discomfortResponse = discomfortResponse,
            calibration = RingsCalibration.FULL_EVIDENCE,
            evidenceHandling = RingsEvidenceHandling.SET,
            capturedAtMs = capturedAt,
        )
    }
}

/** Payload puro del check-in declarado; solo toca los campos realmente informados. */
data class StagedRingsCheckIn(
    val manualMuscularBattery: Int? = null,
    val manualNeuralBattery: Int? = null,
    val manualSpinalBattery: Int? = null,
    val manualMuscleBatteries: Map<String, Int> = emptyMap(),
    /** `null` = molestias no informadas (el merge por campos no toca lo existente). */
    val preWorkoutDiscomforts: List<String>? = null,
    val capturedFields: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = capturedFields.isEmpty()

    fun toWellbeingLog(
        id: String,
        date: String,
        anchorMs: Long,
        muscleOverridesV2: Map<String, ManualMuscleBatteryOverride> = emptyMap(),
    ): DailyWellbeingLog = DailyWellbeingLog(
        id = id,
        date = date,
        manualMuscularBattery = manualMuscularBattery,
        manualMuscleBatteries = manualMuscleBatteries,
        manualMuscleOverridesV2 = muscleOverridesV2,
        manualBatteryAnchorMs = anchorMs,
        manualNeuralBattery = manualNeuralBattery,
        manualSpinalBattery = manualSpinalBattery,
        preWorkoutDiscomforts = preWorkoutDiscomforts.orEmpty(),
        source = WellbeingSource.ONBOARDING_INITIAL,
        capturedFields = capturedFields,
    )
}

/**
 * Mapeo de respuestas compartido entre la vista previa y el commit del alta,
 * para que no se previsualice una cosa y se guarde otra.
 */
object SetupRingsResponseMapping {
    /** Escala subjetiva 1-5 → batería 0-100. Nunca pases la escala cruda como porcentaje. */
    fun batteryFromLevel(level: Int?): Int? =
        level?.let { (100 - (it.coerceIn(1, 5) - 1) * 25).coerceIn(0, 100) }

    /**
     * Molestias al check-in: [RingsDiscomfortResponse.NONE] es una lista vacía
     * explícita; no informado/omitido es `null` y el merge por campos no toca lo existente.
     */
    fun discomfortField(response: RingsDiscomfortResponse, discomfortIds: List<String>): List<String>? =
        when (response) {
            RingsDiscomfortResponse.NONE -> emptyList()
            RingsDiscomfortResponse.DECLARED -> discomfortIds.distinct()
            RingsDiscomfortResponse.NOT_ANSWERED, RingsDiscomfortResponse.OMITTED -> null
        }

    /**
     * Construye el payload del check-in declarado. Solo se capturan los campos
     * informados (`capturedFields`); un desconocimiento nunca se convierte en
     * ausencia ni en valor por defecto. Los ajustes manuales mandan sobre la
     * sensación de su mismo canal.
     */
    fun stageCheckIn(
        checkIn: SetupRingsCheckIn = SetupRingsCheckIn(),
        manualMuscleLevels: Map<String, Int> = emptyMap(),
        manualEnergyLevel: Int? = null,
        manualStructureLevel: Int? = null,
        discomforts: List<String>? = null,
    ): StagedRingsCheckIn {
        val muscular = batteryFromLevel(checkIn.muscular)
        val energy = batteryFromLevel(manualEnergyLevel ?: checkIn.energy)
        val structure = batteryFromLevel(manualStructureLevel ?: checkIn.structure)
        val muscles = manualMuscleLevels.mapValues { (_, level) -> batteryFromLevel(level) ?: 0 }
        val fields = buildSet {
            if (muscles.isNotEmpty()) add("muscle_batteries")
            if (energy != null) add("energy")
            if (structure != null) add("structure")
            if (discomforts != null) add("discomforts")
            if (muscular != null) add("muscular")
        }
        return StagedRingsCheckIn(
            manualMuscularBattery = muscular,
            manualNeuralBattery = energy,
            manualSpinalBattery = structure,
            manualMuscleBatteries = muscles,
            preWorkoutDiscomforts = discomforts,
            capturedFields = fields,
        )
    }
}
