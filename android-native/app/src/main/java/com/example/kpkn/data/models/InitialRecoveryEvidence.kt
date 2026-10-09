package com.example.kpkn.data.models

import com.example.kpkn.domain.auge.InitialRecoveryV2Model
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

object InitialRecoveryEvidenceFactory {
    private const val DAY_MS = 24L * 60L * 60L * 1_000L

    /** Las series por grupo se piden solo para los días de las últimas 72 h (hoy, ayer y anteayer). */
    private const val RECENT_GROUP_DAYS_BACK = 2

    fun fromInputs(
        capturedAtMs: Long,
        recencyDays: Int,
        sessions: Int,
        type: InitialRecoveryActivityType,
        intensity: InitialRecoveryIntensity,
        zones: List<String> = emptyList(),
        sensations: InitialRecoverySensations = InitialRecoverySensations(),
        sourceId: String = "initial-recovery",
        muscleScope: InitialRecoveryMuscleScope = InitialRecoveryMuscleScope.UNKNOWN,
        selectedMuscles: List<String> = emptyList(),
        axialExposure: InitialRecoveryAxialExposure = InitialRecoveryAxialExposure(),
        activityTypeState: InitialRecoveryResponseState = InitialRecoveryResponseState.DECLARED,
    ): InitialRecoveryEvidence {
        val cleanSessions = sessions.coerceIn(0, 14)
        val days = recencyDays.coerceIn(0, 14)
        val normalizedSensations = sensations.normalized()
        val intensityLoad = when (intensity) {
            InitialRecoveryIntensity.EASY -> 3
            InitialRecoveryIntensity.MODERATE -> 8
            InitialRecoveryIntensity.HARD -> 16
            InitialRecoveryIntensity.VERY_HARD -> 24
        }
        val recencyFraction = (14 - days).toDouble() / 14.0
        val trainingLoad = if (cleanSessions == 0) {
            0
        } else {
            ((intensityLoad + cleanSessions * 3) * recencyFraction).roundToInt()
        }
        val muscularLoad = when (type) {
            InitialRecoveryActivityType.STRENGTH -> trainingLoad
            InitialRecoveryActivityType.CARDIO -> trainingLoad / 2
            InitialRecoveryActivityType.MIXED -> trainingLoad
        }
        val systemLoad = when (type) {
            InitialRecoveryActivityType.STRENGTH -> trainingLoad / 2
            InitialRecoveryActivityType.CARDIO -> trainingLoad
            InitialRecoveryActivityType.MIXED -> trainingLoad
        }
        val axialLoad = zones.any { it.trim().equals("axial", ignoreCase = true) } ||
            axialExposure.state == InitialRecoveryResponseState.DECLARED &&
            (axialExposure.sessions ?: 0) > 0
        val structureLoad = if (axialLoad) trainingLoad else 0
        val sensationCount = normalizedSensations.run {
            listOf(muscular, energy, structure).count { it != null }
        }
        val confidence = (
            20 +
                cleanSessions * 5 +
                (if (days <= 3) 12 else 0) +
                sensationCount * 8
            ).coerceIn(0, 82)
        val canonicalMuscles = selectedMuscles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val perMuscle = when (muscleScope) {
            InitialRecoveryMuscleScope.SELECTED -> canonicalMuscles.associateWith { (100 - muscularLoad - sensationPenalty(normalizedSensations.muscular)).coerceIn(0, 100) }
            InitialRecoveryMuscleScope.FULL_BODY -> INITIAL_RECOVERY_MUSCLE_PILLARS.associateWith { (100 - muscularLoad - sensationPenalty(normalizedSensations.muscular)).coerceIn(0, 100) }
            InitialRecoveryMuscleScope.UNKNOWN -> emptyMap()
        }
        return InitialRecoveryEvidence(
            capturedAtMs = capturedAtMs,
            coveredFromMs = capturedAtMs - days * DAY_MS,
            coveredToMs = capturedAtMs,
            expiresAtMs = capturedAtMs + 14L * DAY_MS,
            sessions = cleanSessions,
            activityType = type,
            intensity = intensity,
            zones = zones,
            sensations = normalizedSensations,
            muscularScore = 100 - muscularLoad - sensationPenalty(normalizedSensations.muscular),
            systemScore = 100 - systemLoad - sensationPenalty(normalizedSensations.energy),
            structureScore = 100 - structureLoad - sensationPenalty(normalizedSensations.structure),
            confidence = confidence,
            sourceId = sourceId,
            muscleScope = muscleScope,
            perMuscleScores = perMuscle,
            axialExposure = axialExposure,
            activityTypeState = activityTypeState,
        ).normalized()
    }

    /** Penalización en puntos de una sensación (1–5). Sin dato o 1 = sin penalización. */
    internal fun sensationPenalty(value: Int?): Int = when (value) {
        null, 1 -> 0
        2 -> 4
        3 -> 8
        4 -> 15
        else -> 24
    }

    /**
     * Evidencia v2 de RINGS (puntos 20–22). Cada día entrenado (0 = hoy … 6) se fecha a las 12:00 de ese día en
     * [zone]: supuesto, la hora exacta no se pregunta. Las cargas están en unidades del motor (ver
     * [InitialRecoveryV2Model]). Las puntuaciones guardadas son el estado a la hora de captura. Sin días entrenados
     * no hay cargas: cada canal baja solo por sus sensaciones (100 − penalización de [sensationPenalty]).
     */
    fun fromInputsV2(
        capturedAtMs: Long,
        zone: ZoneId,
        trainedDays: Set<Int>,
        intensity: InitialRecoveryIntensity,
        lifts: Set<RingsLiftPattern>,
        groupSets: Map<RingsMuscleGroup, Int>,
        sensations: InitialRecoverySensations = InitialRecoverySensations(),
        sourceId: String = "initial-recovery",
    ): InitialRecoveryEvidence {
        val normalizedSensations = sensations.normalized()
        val today = Instant.ofEpochMilli(capturedAtMs).atZone(zone).toLocalDate()
        val days = trainedDays.filter { it in 0..6 }.distinct().sorted()
        val recentDays = days.filter { it <= RECENT_GROUP_DAYS_BACK }
        // Sin series por grupo, la carga muscular se estima con los patrones y la intensidad (muscleGeneric).
        val setsByGroup = if (groupSets.isNotEmpty()) {
            groupSets.filterValues { it > 0 }
        } else {
            InitialRecoveryV2Model.genericGroupSets(lifts)
        }
        val recentMuscular = InitialRecoveryV2Model.muscularShare(setsByGroup, intensity, recentDays.size)
        val energy = InitialRecoveryV2Model.energyLoad(intensity, lifts)
        val spinal = InitialRecoveryV2Model.spinalLoad(intensity, lifts)
        val dayLoads = days.map { back ->
            InitialRecoveryDayLoad(
                dayStartMs = today.minusDays(back.toLong()).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                muscular = if (back <= RECENT_GROUP_DAYS_BACK) recentMuscular else emptyMap(),
                energy = energy,
                spinal = spinal,
            )
        }
        val pillarValues = INITIAL_RECOVERY_MUSCLE_PILLARS.map { pillar ->
            InitialRecoveryV2Model.pillarBattery(pillar, dayLoads, normalizedSensations, capturedAtMs, capturedAtMs)
        }
        val axial = lifts.any { it in InitialRecoveryV2Model.AXIAL_LIFTS }
        val cardio = lifts.any { it in InitialRecoveryV2Model.CARDIO_LIFTS }
        val strength = lifts.any { it !in InitialRecoveryV2Model.CARDIO_LIFTS }
        val sensationCount = listOf(normalizedSensations.muscular, normalizedSensations.energy, normalizedSensations.structure)
            .count { it != null }
        val confidence = (
            20 +
                days.size * 5 +
                (if (recentDays.isNotEmpty()) 12 else 0) +
                sensationCount * 8
            ).coerceIn(0, 82)
        val coveredFromMs = days.lastOrNull()
            ?.let { today.minusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
            ?: capturedAtMs
        return InitialRecoveryEvidence(
            capturedAtMs = capturedAtMs,
            coveredFromMs = coveredFromMs,
            coveredToMs = capturedAtMs,
            expiresAtMs = capturedAtMs + 14L * DAY_MS,
            sessions = days.size,
            activityType = when {
                strength && cardio -> InitialRecoveryActivityType.MIXED
                cardio -> InitialRecoveryActivityType.CARDIO
                else -> InitialRecoveryActivityType.STRENGTH
            },
            intensity = intensity,
            zones = if (axial) listOf("axial") else emptyList(),
            sensations = normalizedSensations,
            muscularScore = InitialRecoveryV2Model.muscularBattery(pillarValues).roundToInt(),
            systemScore = InitialRecoveryV2Model.energyBattery(dayLoads, normalizedSensations, capturedAtMs, capturedAtMs)
                .roundToInt(),
            structureScore = InitialRecoveryV2Model.spinalBattery(dayLoads, normalizedSensations, capturedAtMs, capturedAtMs)
                .roundToInt(),
            confidence = confidence,
            estimatorVersion = 2,
            sourceId = sourceId,
            muscleScope = InitialRecoveryMuscleScope.FULL_BODY,
            perMuscleScores = INITIAL_RECOVERY_MUSCLE_PILLARS.zip(pillarValues)
                .associate { (pillar, value) -> pillar to value.roundToInt() },
            axialExposure = InitialRecoveryAxialExposure(
                state = if (axial) InitialRecoveryResponseState.DECLARED else InitialRecoveryResponseState.UNKNOWN,
                sessions = days.size,
                intensity = intensity,
                recencyDays = days.firstOrNull(),
            ),
            contractRevision = "initial-recovery-v3",
            activityTypeState = InitialRecoveryResponseState.DECLARED,
            dayLoads = dayLoads,
            structureKnown = days.isEmpty() || axial || normalizedSensations.structure != null,
            muscleGeneric = groupSets.isEmpty() && recentDays.isNotEmpty(),
        ).normalized()
    }

    /** Canonical pillars used by AUGE's localized battery map. */
    val INITIAL_RECOVERY_MUSCLE_PILLARS: List<String> = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps",
        "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas",
        "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    )
}
