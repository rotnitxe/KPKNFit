package com.example.kpkn.data.models

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class InitialRecoveryActivityType { STRENGTH, CARDIO, MIXED }

@Serializable
enum class InitialRecoveryIntensity { EASY, MODERATE, HARD, VERY_HARD }

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
        val axialLoad = zones.any { it.trim().equals("axial", ignoreCase = true) }
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
        ).normalized()
    }
}
