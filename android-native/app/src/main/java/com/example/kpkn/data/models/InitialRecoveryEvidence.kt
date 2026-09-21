package com.example.kpkn.data.models

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
    )
}

object InitialRecoveryEvidenceFactory {
    private const val DAY_MS = 24L * 60L * 60L * 1_000L

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
        fun sensationPenalty(value: Int?): Int = when (value) {
            null, 1 -> 0
            2 -> 4
            3 -> 8
            4 -> 15
            else -> 24
        }
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

    /** Canonical pillars used by AUGE's localized battery map. */
    val INITIAL_RECOVERY_MUSCLE_PILLARS: List<String> = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps",
        "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas",
        "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    )
}
