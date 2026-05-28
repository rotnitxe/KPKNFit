package com.example.kpkn.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExerciseMuscleInfo(
    val id: String,
    val name: String,
    val alias: String? = null,
    val description: String? = null,
    val involvedMuscles: List<InvolvedMuscle> = emptyList(),
    val equipment: String? = null,
    val category: String? = null, // "Fuerza", "Hipertrofia", etc.
    val type: String? = null, // "Básico", "Accesorio", "Aislamiento"
    val force: String? = null, // "Empuje", "Tirón", "Bisagra", etc.
    val chain: String? = null, // "anterior", "posterior", "full"
    val bodyPart: String? = null, // "upper", "lower", "full"
    val tier: String? = null, // "T1", "T2", "T3"
    val isCustom: Boolean = false,

    // AUGE Metrics
    val efc: Double? = null, // Fatiga Local/Cost (1-5)
    val cnc: Double? = null, // Neural Cost (1-5)
    val ssc: Double? = null, // Structural/Spinal Cost (0-2)
    val ttc: Double? = null, // Tendon Stress (weeks to recover)
    val axialLoadFactor: Double? = null,

    // Difficulty
    val technicalDifficulty: Double? = null, // 1-5
    val coreInvolvement: String? = null, // "high", "medium", "low"
    val bracingRecommended: Boolean? = null,
    val strapsRecommended: Boolean? = null,

    // Kinesiology
    val resistanceProfile: ResistanceProfile? = null,
    val anatomicalConsiderations: List<AnatomicalConsideration>? = null,
    val commonMistakes: List<CommonMistake>? = null,
    val setupCues: List<String>? = null,
    val executionCues: List<String>? = null,

    // Progressions
    val progressions: List<Progression>? = null,
    val regressions: List<Progression>? = null,
    val recommendedMobility: List<String>? = null,

    // Periodization
    val periodizationNotes: List<PeriodizationNote>? = null,

    // Transfer & Injury
    val functionalTransfer: String? = null,
    val sportsRelevance: List<String>? = null,
    val injuryRisk: InjuryRisk? = null,

    // SFR & Stars
    val sfr: ScoreJustification? = null,
    val primeStars: ScoreJustification? = null,
    val bodybuildingScore: Double? = null,

    // Community
    val communityOpinion: List<String>? = null,
    val aiCoachAnalysis: AiCoachAnalysis? = null,

    // Media
    val images: List<String>? = null,
    val videos: List<String>? = null,

    // Setup
    val setupDetails: SetupDetails? = null,
    val setupTime: Int? = null, // seconds
    val averageRestSeconds: Int? = null,
)

@Serializable
data class InvolvedMuscle(
    val muscle: String,
    val role: MuscleRole = MuscleRole.PRIMARY,
    @SerialName("activation")
    val volumeContribution: Double? = null,
    val emphasis: String? = null, // e.g. "anterior", "superior"
)

@Serializable
enum class MuscleRole {
    @SerialName("primary") PRIMARY,
    @SerialName("secondary") SECONDARY,
    @SerialName("stabilizer") STABILIZER,
    @SerialName("neutralizer") NEUTRALIZER,
}

@Serializable
data class ResistanceProfile(
    val curve: String? = null, // "ascending", "descending", "bell", "linear", "constant"
    val peakTensionPoint: String? = null,
    val description: String? = null,
)

@Serializable
data class AnatomicalConsideration(
    val trait: String,
    val advice: String,
)

@Serializable
data class CommonMistake(
    val mistake: String,
    val correction: String,
)

@Serializable
data class Progression(
    val name: String,
    val description: String,
)

@Serializable
data class PeriodizationNote(
    val phase: String,
    val suitability: Double, // 0-1
    val notes: String,
)

@Serializable
data class InjuryRisk(
    val level: Double,
    val details: String,
)

@Serializable
data class ScoreJustification(
    val score: Double,
    val justification: String,
)

@Serializable
data class AiCoachAnalysis(
    val summary: String,
    val pros: List<String>,
    val cons: List<String>,
)

@Serializable
data class SetupDetails(
    val seatPosition: String? = null,
    val pinPosition: String? = null,
    val equipmentNotes: String? = null,
)

val HYPERTROPHY_ROLE_MULTIPLIERS = mapOf(
    MuscleRole.PRIMARY to 1.0,
    MuscleRole.SECONDARY to 0.5,
    MuscleRole.STABILIZER to 0.0,
    MuscleRole.NEUTRALIZER to 0.0
)

val VOLUME_CONTRIBUTION_FALLBACKS = mapOf(
    MuscleRole.PRIMARY to 1.0,
    MuscleRole.SECONDARY to 0.5,
    MuscleRole.STABILIZER to 0.0,
    MuscleRole.NEUTRALIZER to 0.0
)

val FATIGUE_ROLE_MULTIPLIERS = mapOf(
    MuscleRole.PRIMARY to 1.0,
    MuscleRole.SECONDARY to 0.5,
    MuscleRole.STABILIZER to 0.25,
    MuscleRole.NEUTRALIZER to 0.1
)

fun resolveMuscleVolumeContribution(
    involvement: InvolvedMuscle,
    capAtOne: Boolean = true,
): Double {
    val fallback = VOLUME_CONTRIBUTION_FALLBACKS[involvement.role] ?: 0.0
    val raw = involvement.volumeContribution ?: fallback
    return if (capAtOne) raw.coerceIn(0.0, 1.0) else raw.coerceAtLeast(0.0)
}
