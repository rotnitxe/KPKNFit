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
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.data.models.WellbeingSource
import java.time.ZoneId
import kotlinx.serialization.Serializable

enum class RingsCompletion { INCOMPLETE, UNKNOWN, PRESERVE, OMITTED, VALID }

/**
 * Respuesta a «¿Qué días entrenaste?» (RINGS v2). [NOT_ANSWERED] es el borrador sin esa pregunta (o sin responder): se mapea
 * con la ley de siempre. [NONE] es «No entrené»; [UNKNOWN], «No lo recuerdo».
 */
@Serializable
enum class RingsSessionDaysState { NOT_ANSWERED, DECLARED, NONE, UNKNOWN }

/** Respuesta a «¿Qué ejercicios principales hiciste?» (RINGS v2). [NONE] es «Ninguno de estos»; [UNKNOWN], «No lo sé». */
@Serializable
enum class RingsMainLiftsState { NOT_ANSWERED, DECLARED, NONE, UNKNOWN }

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

    /** Informativo: las tres sensaciones a la vez. El mapeo NO las exige. */
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
    /** RINGS v2: respuesta de días. [RingsSessionDaysState.NOT_ANSWERED] (borrador v1) mapea con la ley de siempre. */
    val daysState: RingsSessionDaysState = RingsSessionDaysState.NOT_ANSWERED,
    /** RINGS v2: días entrenados, 0 = hoy … 6. */
    val trainedDays: Set<Int> = emptySet(),
    /** RINGS v2: ejercicios principales (vacío con «Ninguno de estos» o «No lo sé»). */
    val mainLifts: Set<RingsLiftPattern> = emptySet(),
    /** RINGS v2: series por grupo; los grupos con «No lo sé» no figuran. */
    val groupSets: Map<RingsMuscleGroup, Int> = emptyMap(),
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
    val savesRealCheckIn: Boolean get() = calibration != RingsCalibration.NONE || declaresDiscomforts

    /**
     * Molestias declaradas de forma real: lista de ids o «sin molestias»
     * explícito. Ese payload se persiste aunque la calibración sea [RingsCalibration.NONE]
     * (p. ej. solo molestias sin historial ni sensaciones); el no respondido y lo
     * omitido no escriben nada.
     */
    val declaresDiscomforts: Boolean get() =
        discomfortResponse == RingsDiscomfortResponse.NONE || discomfortResponse == RingsDiscomfortResponse.DECLARED
}

object SetupRingsMapper {
    /** Días hacia atrás (0 = hoy) que cuentan como «últimas 72 h» para pedir las series por grupo: hoy, ayer y anteayer. */
    const val GROUP_SETS_MAX_DAYS_BACK: Int = 2

    /**
     * [zone] fecha los días declarados de RINGS v2 (ver `InitialRecoveryEvidenceFactory.fromInputsV2`); el ViewModel pasa la
     * del sistema y el núcleo no lee el reloj del dispositivo. Sin respuesta de días ([RingsSessionDaysState.NOT_ANSWERED])
     * el mapeo es exactamente el de siempre (ley v1).
     */
    fun map(input: SetupRingsInput, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): SetupRingsMapping {
        val action = input.startAction?.trim()?.lowercase()
        val checkIn = SetupRingsCheckIn(input.muscleFeeling, input.energy, input.structureFeeling)
        val historyState = when (input.daysState) {
            RingsSessionDaysState.DECLARED -> RingsHistoryState.DECLARED
            RingsSessionDaysState.NONE -> RingsHistoryState.DECLARED_ABSENT
            RingsSessionDaysState.UNKNOWN -> RingsHistoryState.UNKNOWN
            RingsSessionDaysState.NOT_ANSWERED -> when {
                input.recentTrainingUnknown || input.recentTraining == null -> RingsHistoryState.UNKNOWN
                input.recentTraining == false -> RingsHistoryState.DECLARED_ABSENT
                else -> RingsHistoryState.DECLARED
            }
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
        // Historia conocida: las sensaciones son OPCIONALES. Un campo no
        // informado viaja como `null` en la evidencia (la fábrica lo admite y
        // cuenta solo los no nulos) y jamás se completa con un valor por
        // defecto. Lo obligatorio es la evidencia histórica (sesiones, recencia,
        // tipo, intensidad), que NUNCA se sintetiza: si falta, el alta queda
        // incompleta, pero el check-in declarado sí se conserva como parcial
        // válido en lugar de perderse.
        fun incompleteHistory(): SetupRingsMapping = SetupRingsMapping(
            completion = RingsCompletion.INCOMPLETE,
            discomfortIds = discomfortIds,
            historyState = historyState,
            checkIn = checkIn,
            discomfortResponse = discomfortResponse,
            calibration = if (checkIn.isDeclared) RingsCalibration.PARTIAL_CHECK_IN else RingsCalibration.NONE,
            evidenceHandling = RingsEvidenceHandling.UNCHANGED,
            capturedAtMs = if (checkIn.isDeclared) input.capturedAtMs ?: nowMs else input.capturedAtMs,
        )
        // RINGS v2: días declarados o «No entrené» → evidencia por días con las curvas del motor. Las sensaciones penalizan
        // cada canal también sin días. «No lo recuerdo» ya salió arriba como historial desconocido.
        if (input.daysState == RingsSessionDaysState.DECLARED || input.daysState == RingsSessionDaysState.NONE) {
            val capturedAt = input.capturedAtMs ?: nowMs
            val sensations = InitialRecoverySensations(input.muscleFeeling, input.energy, input.structureFeeling)
            val evidence = if (input.daysState == RingsSessionDaysState.NONE) {
                InitialRecoveryEvidenceFactory.fromInputsV2(
                    capturedAtMs = capturedAt,
                    zone = zone,
                    trainedDays = emptySet(),
                    intensity = InitialRecoveryIntensity.MODERATE,
                    lifts = emptySet(),
                    groupSets = emptyMap(),
                    sensations = sensations,
                )
            } else {
                // Con días hacen falta la intensidad y al menos un día; si no, el alta queda incompleta (el check-in se conserva).
                val intensity = input.intensity ?: return incompleteHistory()
                if (input.trainedDays.isEmpty()) return incompleteHistory()
                InitialRecoveryEvidenceFactory.fromInputsV2(
                    capturedAtMs = capturedAt,
                    zone = zone,
                    trainedDays = input.trainedDays,
                    intensity = intensity,
                    lifts = input.mainLifts,
                    groupSets = input.groupSets,
                    sensations = sensations,
                )
            }
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
        val recent = input.recentTraining
        val sessions = if (recent == false) 0 else input.sessions?.takeIf { it in 1..7 } ?: return incompleteHistory()
        val recency = if (recent == false) 0 else input.recencyDays?.takeIf { it in 0..6 } ?: return incompleteHistory()
        val type = if (recent == false) InitialRecoveryActivityType.MIXED else input.activityType ?: return incompleteHistory()
        val intensity = if (recent == false) InitialRecoveryIntensity.MODERATE else input.intensity ?: return incompleteHistory()
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

    /**
     * Pone sobre el check-in lo que la persona mueve en «Tus RINGS», con el valor que ve (0–100, sin la escala 1–5): un 80
     * se guarda como 80. [energia] y [columnaBattery] (la batería cruda de la Columna, ya invertida) sustituyen el ancla de
     * [base] solo si vienen; [musculos] (pilar → 0–100) se suma a los de [base].
     */
    fun withRingAdjustments(
        base: StagedRingsCheckIn,
        energia: Int?,
        columnaBattery: Int?,
        musculos: Map<String, Int>,
    ): StagedRingsCheckIn = base.copy(
        manualNeuralBattery = energia?.coerceIn(0, 100) ?: base.manualNeuralBattery,
        manualSpinalBattery = columnaBattery?.coerceIn(0, 100) ?: base.manualSpinalBattery,
        manualMuscleBatteries = base.manualMuscleBatteries + musculos.mapValues { (_, value) -> value.coerceIn(0, 100) },
        capturedFields = base.capturedFields + buildSet {
            if (energia != null) add("energy")
            if (columnaBattery != null) add("structure")
            if (musculos.isNotEmpty()) add("muscle_batteries")
        },
    )
}
