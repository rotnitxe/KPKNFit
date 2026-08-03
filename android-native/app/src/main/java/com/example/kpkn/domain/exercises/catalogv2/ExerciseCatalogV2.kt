package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.serialization.Serializable

/**
 * Stable identity for the v2 catalog. A configuration is explicit; runtime
 * code must never build a free Cartesian product of technical options.
 */
@Serializable
data class ExerciseCatalogV2(
    val schemaVersion: Int,
    val catalogRevision: String,
    val ontologyRevision: String,
    val families: List<ExerciseFamilyV2>,
)

@Serializable
data class ExerciseFamilyV2(
    val id: String,
    val canonicalName: String,
    val description: String,
    val definitions: List<ExerciseDefinitionV2>,
    val evidence: CatalogEvidenceV2,
    val taxonomy: List<String> = emptyList(),
)

@Serializable
data class ExerciseDefinitionV2(
    val id: String,
    val familyId: String,
    val kind: ExerciseDefinitionKindV2,
    val canonicalName: String,
    val description: String,
    val searchTerms: List<String> = emptyList(),
    val optionAxes: List<String> = emptyList(),
    val configurations: List<ExerciseConfigurationV2>,
    val defaultConfigurationId: String,
    val evidence: CatalogEvidenceV2,
)

@Serializable
enum class ExerciseDefinitionKindV2 {
    PARENT,
    SPECIALTY,
}

@Serializable
data class ExerciseConfigurationV2(
    val id: String,
    val selectedOptions: Map<String, String>,
    val displaySummary: String,
    val profile: ResolvedExerciseProfileV2,
    val evidence: CatalogEvidenceV2,
)

@Serializable
data class ResolvedExerciseProfileV2(
    val movementPatternId: String,
    val bodyRegion: ExerciseBodyRegionV2,
    val kineticChain: ExerciseKineticChainV2,
    val laterality: ExerciseLateralityV2,
    val equipmentId: String,
    val loadMode: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    val stabilizerMuscles: List<String> = emptyList(),
    /** One editorial note per listed muscle; the single source for
     * biomechanical explanations shown in the picker. */
    val muscleNotes: List<MuscleNoteV2> = emptyList(),
    val efc: Double,
    val cnc: Double,
    val ssc: Double,
    val ttc: Double,
    val axialLoadFactor: Double,
    val technicalDifficulty: Double,
    val resistanceProfile: String,
    val setupCues: List<String>,
    val executionCues: List<String>,
    val commonMistakes: List<String>,
    val performanceProfileId: String,
    /** Optional during the editorial migration; approved catalogs must populate it. */
    val richMetadata: ResolvedExerciseMetadataV2? = null,
    val replacementGroup: String? = null,
    val replacementPriority: Int? = null,
    val automationEligible: Boolean = false,
    /** Factual prose for this exact materialized configuration; never a cue. */
    val description: String = "",
    /** Articulación del patrón: MULTIARTICULAR (compuesto) o AISLADO (una
     *  articulación). Alimenta las reglas del editor de sesiones. */
    val articulationType: ExerciseArticulationTypeV2? = null,
    /** Tiempo de set-up estimado en segundos para la configuración exacta. */
    val setupTimeSeconds: Int? = null,
    /** Tier de fatiga de la configuración derivado del coste editorial (efc). */
    val fatigueTier: ExerciseFatigueTierV2? = null,
)

@Serializable
enum class ExerciseArticulationTypeV2 {
    MULTIARTICULAR,
    AISLADO,
}

@Serializable
enum class ExerciseFatigueTierV2 {
    BAJA,
    MEDIA,
    ALTA,
}

@Serializable
data class MuscleNoteV2(
    val muscleId: String,
    val note: String,
)

@Serializable
enum class ExerciseBodyRegionV2 {
    UPPER,
    LOWER,
    CORE,
    FULL,
}

@Serializable
enum class ExerciseKineticChainV2 {
    ANTERIOR,
    POSTERIOR,
    FULL,
}

@Serializable
enum class ExerciseLateralityV2 {
    BILATERAL,
    UNILATERAL,
    ALTERNATING,
    NOT_APPLICABLE,
}

@Serializable
data class CatalogEvidenceV2(
    val reviewStatus: CatalogReviewStatusV2,
    val confidence: CatalogConfidenceV2,
    val evidenceRefs: List<String>,
    val rationale: String? = null,
)

@Serializable
enum class CatalogReviewStatusV2 {
    DRAFT,
    REVIEWED,
    APPROVED,
}

@Serializable
enum class CatalogConfidenceV2 {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
data class ExerciseSelectionV2(
    val definitionId: String,
    val configurationId: String,
    val catalogRevision: String,
)

@Serializable
data class ExerciseSearchFiltersV2(
    val familyIds: Set<String> = emptySet(),
    val kinds: Set<ExerciseDefinitionKindV2> = emptySet(),
    val bodyRegions: Set<ExerciseBodyRegionV2> = emptySet(),
    val equipmentIds: Set<String> = emptySet(),
    val movementPatternIds: Set<String> = emptySet(),
    val muscleIds: Set<String> = emptySet(),
)
@Serializable
data class ExerciseSearchHitV2(
    val definitionId: String,
    val suggestedConfigurationId: String? = null,
    val matchedTerm: String? = null,
    val score: Int = 0,
)

sealed interface ExerciseCatalogStateV2 {
    data object Loading : ExerciseCatalogStateV2
    data class Ready(val catalog: ExerciseCatalogV2) : ExerciseCatalogStateV2
    data class Error(val reason: String) : ExerciseCatalogStateV2
}

sealed interface ExerciseSelectionValidationV2 {
    data class Valid(val selection: ExerciseSelectionV2) : ExerciseSelectionValidationV2
    data class Invalid(val reason: String) : ExerciseSelectionValidationV2
}
