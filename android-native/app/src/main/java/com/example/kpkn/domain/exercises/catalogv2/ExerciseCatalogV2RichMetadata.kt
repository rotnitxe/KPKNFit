package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.serialization.Serializable

/**
 * Typed metadata envelope for one resolved configuration.
 *
 * The flat fields on [ResolvedExerciseProfileV2] remain available while the
 * editorial source migrates to this envelope.  The sections are deliberately
 * explicit: consumers must read the resolved configuration and cannot rebuild
 * metadata from a parent name or an arbitrary chip map.
 */
@Serializable
data class ExerciseIdentityMetadataV2(
    val catalogRevision: String,
    val familyId: String,
    val definitionId: String,
    val configurationId: String,
    val canonicalName: String,
    val searchTerms: List<String> = emptyList(),
    val kind: ExerciseDefinitionKindV2,
    val performanceProfileId: String,
)

@Serializable
data class ExerciseAnatomyMetadataV2(
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    val stabilizerMuscles: List<String> = emptyList(),
    val muscleNotes: List<MuscleNoteV2> = emptyList(),
    val targetRegions: List<String> = emptyList(),
    val jointActions: List<String> = emptyList(),
    val jointInvolvement: List<JointInvolvementV2> = emptyList(),
    val muscleLengthBias: String? = null,
    val volumeContribution: String? = null,
    val stabilizationDemand: String? = null,
)

@Serializable
data class ExerciseBiomechanicsMetadataV2(
    val movementPatternId: String,
    val bodyRegion: ExerciseBodyRegionV2,
    val kineticChain: ExerciseKineticChainV2,
    val laterality: ExerciseLateralityV2,
    val equipmentId: String,
    val loadMode: String,
    val resistanceProfile: String,
    val rangeOfMotion: String? = null,
    val stability: String? = null,
    val relevantJoints: List<String> = emptyList(),
    val relevantTendons: List<String> = emptyList(),
)

@Serializable
data class ExerciseProgrammingMetadataV2(
    val role: String? = null,
    val objectives: List<String> = emptyList(),
    val suitableRepRanges: List<String> = emptyList(),
    val indicativeRestSeconds: IntRangeV2? = null,
    val fatigueCost: String? = null,
    val recoveryCost: String? = null,
    val requiredEquipment: List<String> = emptyList(),
    val setupTransitionCost: String? = null,
    val splitSuitability: List<String> = emptyList(),
)

@Serializable
data class ExerciseFatigueMetadataV2(
    val efc: Double,
    val cnc: Double,
    val ssc: Double,
    val ttc: Double,
    val axialLoadFactor: Double,
    val technicalDifficulty: Double,
    val localCost: String? = null,
    val systemicCost: String? = null,
)

@Serializable
data class ExerciseReplacementMetadataV2(
    val replacementGroup: String? = null,
    val replacementPriority: Int? = null,
    val compatibleEquipmentIds: List<String> = emptyList(),
    val preservesIntent: List<String> = emptyList(),
)

@Serializable
data class ExerciseCoachingMetadataV2(
    val setup: List<String>,
    val execution: List<String>,
    val cues: List<String> = emptyList(),
    val commonMistakes: List<String>,
    val progressions: List<String> = emptyList(),
    val regressions: List<String> = emptyList(),
    val relevantMobility: List<String> = emptyList(),
)

@Serializable
data class ExerciseSafetyMetadataV2(
    val risks: List<String> = emptyList(),
    val precautions: List<String> = emptyList(),
    val medicalDisclaimerRequired: Boolean = false,
)

@Serializable
data class ExerciseDisplayMetadataV2(
    val displayName: String,
    val displaySummary: String,
    val selectedOptions: Map<String, String> = emptyMap(),
)

@Serializable
data class ExerciseEditorialMetadataV2(
    val description: String = "",
    val benefits: List<String> = emptyList(),
    val technique: String = "",
    val variantRationale: String = "",
)

@Serializable
data class IntRangeV2(
    val min: Int,
    val max: Int,
) {
    init {
        require(min >= 0) { "range min must be non-negative" }
        require(max >= min) { "range max must be >= min" }
    }
}

@Serializable
data class ResolvedExerciseMetadataV2(
    val identity: ExerciseIdentityMetadataV2,
    val anatomy: ExerciseAnatomyMetadataV2,
    val biomechanics: ExerciseBiomechanicsMetadataV2,
    val programming: ExerciseProgrammingMetadataV2,
    val fatigue: ExerciseFatigueMetadataV2,
    val replacement: ExerciseReplacementMetadataV2,
    val coaching: ExerciseCoachingMetadataV2,
    val safety: ExerciseSafetyMetadataV2,
    val display: ExerciseDisplayMetadataV2,
    val editorial: ExerciseEditorialMetadataV2 = ExerciseEditorialMetadataV2(),
    val evidenceConfidence: CatalogConfidenceV2,
)

/** Build the typed envelope without inventing values absent from the profile. */
fun ResolvedExerciseProfileV2.toRichMetadata(
    catalogRevision: String,
    family: ExerciseFamilyV2,
    definition: ExerciseDefinitionV2,
    configuration: ExerciseConfigurationV2,
): ResolvedExerciseMetadataV2 = ResolvedExerciseMetadataV2(
    identity = ExerciseIdentityMetadataV2(
        catalogRevision = catalogRevision,
        familyId = family.id,
        definitionId = definition.id,
        configurationId = configuration.id,
        canonicalName = definition.canonicalName,
        searchTerms = definition.searchTerms,
        kind = definition.kind,
        performanceProfileId = performanceProfileId,
    ),
    anatomy = ExerciseAnatomyMetadataV2(
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        stabilizerMuscles = stabilizerMuscles,
        muscleNotes = muscleNotes,
        jointInvolvement = jointInvolvement,
    ),
    biomechanics = ExerciseBiomechanicsMetadataV2(
        movementPatternId = movementPatternId,
        bodyRegion = bodyRegion,
        kineticChain = kineticChain,
        laterality = laterality,
        equipmentId = equipmentId,
        loadMode = loadMode,
        resistanceProfile = resistanceProfile,
    ),
    programming = ExerciseProgrammingMetadataV2(),
    fatigue = ExerciseFatigueMetadataV2(
        efc = efc,
        cnc = cnc,
        ssc = ssc,
        ttc = ttc,
        axialLoadFactor = axialLoadFactor,
        technicalDifficulty = technicalDifficulty,
    ),
    replacement = ExerciseReplacementMetadataV2(
        replacementGroup = replacementGroup,
        replacementPriority = replacementPriority,
    ),
    coaching = ExerciseCoachingMetadataV2(
        setup = setupCues,
        execution = executionCues,
        commonMistakes = commonMistakes,
    ),
    safety = ExerciseSafetyMetadataV2(),
    display = ExerciseDisplayMetadataV2(
        displayName = definition.canonicalName,
        displaySummary = configuration.displaySummary,
        selectedOptions = configuration.selectedOptions,
    ),
    editorial = ExerciseEditorialMetadataV2(
        description = description,
        benefits = benefits,
        technique = techniqueSummary,
        variantRationale = variantRationale,
    ),
    evidenceConfidence = configuration.evidence.confidence,
)
