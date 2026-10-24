package com.example.kpkn.data.models

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class InitialRecoveryActivityType { STRENGTH, CARDIO, MIXED }

@Serializable
enum class InitialRecoveryIntensity { EASY, MODERATE, HARD, VERY_HARD }

@Serializable
enum class InitialRecoveryMuscleScope { UNKNOWN, SELECTED, FULL_BODY }

@Serializable
enum class InitialRecoveryResponseState { UNKNOWN, DECLARED, DERIVED }

@Serializable
data class InitialRecoveryAxialExposure(
    val state: InitialRecoveryResponseState = InitialRecoveryResponseState.UNKNOWN,
    val sessions: Int? = null,
    val intensity: InitialRecoveryIntensity? = null,
    val recencyDays: Int? = null,
)

@Serializable
data class InitialRecoverySensations(
    val muscular: Int? = null,
    val energy: Int? = null,
    val structure: Int? = null,
) {
    fun normalized(): InitialRecoverySensations = InitialRecoverySensations(
        muscular = muscular?.coerceIn(1, 5),
        energy = energy?.coerceIn(1, 5),
        structure = structure?.coerceIn(1, 5),
    )
}

/** Patrones principales de la pregunta «¿Qué ejercicios principales hiciste?» de RINGS. */
@Serializable
enum class RingsLiftPattern { SQUAT, DEADLIFT, HINGE_ROW, BENCH, OVERHEAD, PULL_UP, LUNGE, CARDIO_LONG, INTERVALS }

/** Grupos de la pregunta «¿Cuántas series hiciste por grupo?» de RINGS. */
@Serializable
enum class RingsMuscleGroup(val label: String) {
    CHEST("Pecho"), BACK("Espalda"), SHOULDERS("Hombros"), ARMS("Brazos"), LEGS("Piernas"), CORE("Core"),
}

/** Carga de un día entrenado, en unidades del motor, por pilar muscular. Sin dato, vale 0. */
@Serializable
data class InitialRecoveryDayLoad(
    val dayStartMs: Long = 0L,
    val muscular: Map<String, Double> = emptyMap(),
    val energy: Double = 0.0,
    val spinal: Double = 0.0,
) {
    fun normalized(): InitialRecoveryDayLoad = copy(
        muscular = muscular.mapValues { (_, value) -> if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0 },
        energy = if (energy.isFinite()) energy.coerceAtLeast(0.0) else 0.0,
        spinal = if (spinal.isFinite()) spinal.coerceAtLeast(0.0) else 0.0,
    )
}

@Serializable
data class InitialRecoveryEvidence(
    val capturedAtMs: Long,
    val coveredFromMs: Long,
    val coveredToMs: Long,
    val expiresAtMs: Long,
    val sessions: Int,
    val activityType: InitialRecoveryActivityType,
    val intensity: InitialRecoveryIntensity,
    val zones: List<String> = emptyList(),
    val sensations: InitialRecoverySensations = InitialRecoverySensations(),
    val muscularScore: Int,
    val systemScore: Int,
    val structureScore: Int,
    val confidence: Int,
    val estimatorVersion: Int = 1,
    val sourceId: String = "initial-recovery",
    val muscleScope: InitialRecoveryMuscleScope = InitialRecoveryMuscleScope.UNKNOWN,
    val perMuscleScores: Map<String, Int> = emptyMap(),
    val axialExposure: InitialRecoveryAxialExposure = InitialRecoveryAxialExposure(),
    val contractRevision: String = "initial-recovery-v2",
    val adjustmentSource: String? = null,
    val activityTypeState: InitialRecoveryResponseState = InitialRecoveryResponseState.DECLARED,
    /** Cargas por día entrenado (solo estimatorVersion ≥ 2). Vacío en la evidencia v1. */
    val dayLoads: List<InitialRecoveryDayLoad> = emptyList(),
    /** false si no hay patrones axiales ni sensación de columna: la Columna queda «Sin calibrar». */
    val structureKnown: Boolean = true,
    /** true si no hubo series por grupo y la carga muscular sale de la intensidad y los patrones. */
    val muscleGeneric: Boolean = false,
) {
    fun normalized(): InitialRecoveryEvidence = copy(
        coveredFromMs = minOf(coveredFromMs, coveredToMs),
        coveredToMs = maxOf(coveredFromMs, coveredToMs),
        expiresAtMs = maxOf(expiresAtMs, capturedAtMs),
        sessions = sessions.coerceIn(0, 14),
        zones = zones.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        sensations = sensations.normalized(),
        muscularScore = muscularScore.coerceIn(0, 100),
        systemScore = systemScore.coerceIn(0, 100),
        structureScore = structureScore.coerceIn(0, 100),
        confidence = confidence.coerceIn(0, 100),
        perMuscleScores = perMuscleScores.mapKeys { it.key.trim() }.filterKeys { it.isNotEmpty() }
            .mapValues { (_, value) -> value.coerceIn(0, 100) },
        axialExposure = axialExposure.copy(
            sessions = axialExposure.sessions?.coerceIn(0, 14),
            recencyDays = axialExposure.recencyDays?.coerceIn(0, 14),
        ),
        dayLoads = dayLoads.map { it.normalized() },
    )
}
