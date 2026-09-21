package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation

/**
 * Pure source of truth for the volume calibration used by the program UI and
 * the first-run wizard. Keep the scales and bounds aligned with the established
 * calibration sheet; this class only removes the Compose dependency.
 */
data class VolumeCalibrationOutput(
    val mode: ProgramMode,
    val score: AthleteProfileScore,
    val recommendations: List<VolumeRecommendation>,
)

object VolumeCalibrationEngine {
    const val REVISION = "volume-calibration-v1"

    fun calculate(
        style: TrainingStyle,
        technique: Int,
        consistency: Int,
        strength: Int,
        mobility: Int,
    ): VolumeCalibrationOutput {
        val cleanTechnique = technique.coerceIn(1, 3)
        val cleanConsistency = consistency.coerceIn(1, 3)
        val cleanStrength = strength.coerceIn(1, 3)
        val cleanMobility = mobility.coerceIn(1, 3)
        val totalScore = cleanTechnique + cleanConsistency + cleanStrength + cleanMobility
        val profileLevel = if (totalScore >= 8) AthleteProfileLevel.ADVANCED else AthleteProfileLevel.BEGINNER
        val optimalSets = when (style) {
            TrainingStyle.BODYBUILDER -> if (profileLevel == AthleteProfileLevel.ADVANCED) 18 else 15
            TrainingStyle.POWERBUILDER -> if (profileLevel == AthleteProfileLevel.ADVANCED) 16 else 14
            TrainingStyle.POWERLIFTER -> if (profileLevel == AthleteProfileLevel.ADVANCED) 14 else 12
        } + when {
            totalScore >= 11 -> 1
            totalScore <= 5 -> -1
            else -> 0
        }
        val scale = optimalSets.toFloat() / 15f
        val recommendations = BASE_RECOMMENDATIONS.map { recommendation ->
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(recommendation.muscleGroup)
            val floor = floorFor(canonical)
            val ceiling = ceilingFor(canonical)
            val min = (recommendation.minEffectiveVolume * scale).toInt()
                .coerceAtLeast(floor.minEffective)
                .coerceAtMost(ceiling.minEffective)
            val adaptive = (recommendation.maxAdaptiveVolume * scale).toInt()
                .coerceAtLeast(maxOf(min + 3, floor.maxAdaptive))
                .coerceAtMost(ceiling.maxAdaptive)
            val recoverable = (recommendation.maxRecoverableVolume * scale).toInt()
                .coerceAtLeast(maxOf(adaptive + 3, floor.maxRecoverable))
                .coerceAtMost(ceiling.maxRecoverable)
            recommendation.copy(
                minEffectiveVolume = min,
                maxAdaptiveVolume = adaptive,
                maxRecoverableVolume = recoverable,
            )
        }
        return VolumeCalibrationOutput(
            mode = style.toProgramMode(),
            score = AthleteProfileScore(
                technicalScore = cleanTechnique,
                consistencyScore = cleanConsistency,
                strengthScore = cleanStrength,
                mobilityScore = cleanMobility,
                trainingStyle = style,
                totalScore = totalScore,
                profileLevel = profileLevel,
            ),
            recommendations = recommendations,
        )
    }

    fun effectiveRecommendations(
        programRecommendations: List<VolumeRecommendation>,
        globalRecommendations: List<VolumeRecommendation>,
        generalRecommendations: List<VolumeRecommendation> = emptyList(),
    ): List<VolumeRecommendation> = when {
        programRecommendations.isNotEmpty() -> programRecommendations
        globalRecommendations.isNotEmpty() -> globalRecommendations
        else -> generalRecommendations
    }

    private data class Bounds(val minEffective: Int, val maxAdaptive: Int, val maxRecoverable: Int)

    private fun floorFor(muscle: String): Bounds = when (muscle) {
        "Glúteos" -> Bounds(9, 14, 18)
        "Deltoides" -> Bounds(10, 16, 22)
        "Pectorales", "Cuádriceps" -> Bounds(8, 12, 18)
        "Dorsales" -> Bounds(10, 14, 20)
        "Isquiosurales" -> Bounds(6, 10, 16)
        "Bíceps", "Tríceps", "Pantorrillas" -> Bounds(6, 10, 16)
        else -> Bounds(4, 8, 12)
    }

    private fun ceilingFor(muscle: String): Bounds = when (muscle) {
        "Deltoides" -> Bounds(14, 24, 30)
        "Glúteos" -> Bounds(12, 20, 26)
        "Dorsales" -> Bounds(14, 22, 28)
        "Pectorales", "Cuádriceps" -> Bounds(12, 20, 26)
        else -> Bounds(10, 18, 24)
    }

    private val BASE_RECOMMENDATIONS = listOf(
        VolumeRecommendation("Cuadriceps", 8, 12, 18, 3),
        VolumeRecommendation("Isquiosurales", 6, 10, 16, 3),
        VolumeRecommendation("Gluteos", 9, 13, 19, 3),
        VolumeRecommendation("Pectorales", 8, 12, 18, 3),
        VolumeRecommendation("Dorsales", 10, 14, 20, 4),
        VolumeRecommendation("Trapecio", 6, 10, 16, 3),
        VolumeRecommendation("Erectores Espinales", 4, 8, 12, 2),
        VolumeRecommendation("Deltoides", 12, 18, 26, 4),
        VolumeRecommendation("Biceps", 6, 10, 16, 3),
        VolumeRecommendation("Triceps", 6, 10, 16, 3),
        VolumeRecommendation("Abdomen", 4, 8, 14, 4),
        VolumeRecommendation("Pantorrillas", 6, 10, 18, 4),
    )
}

fun TrainingStyle.toProgramMode(): ProgramMode = when (this) {
    TrainingStyle.POWERLIFTER -> ProgramMode.POWERLIFTING
    TrainingStyle.POWERBUILDER -> ProgramMode.POWERBUILDING
    TrainingStyle.BODYBUILDER -> ProgramMode.HYPERTROPHY
}
