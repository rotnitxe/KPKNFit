package com.example.kpkn.domain.exercises.catalogv2

import java.util.Locale

/** Machine-readable audit of the catalog-to-Aprende contract. */
data class AprendeCatalogAuditReport(
    val catalogRevision: String,
    val ontologyRevision: String,
    val familyCount: Int,
    val definitionCount: Int,
    val configurationCount: Int,
    val richMetadataCount: Int,
    val editorialCoverageCount: Int,
    val muscleNoteCoverageCount: Int,
    val jointCoverageCount: Int,
    val shortDescriptionCount: Int,
    val shortBenefitCount: Int,
    val shortTechniqueCount: Int,
    val shortVariantRationaleCount: Int,
    val duplicateDescriptionCount: Int,
    val duplicateTechniqueCount: Int,
    val duplicateVariantRationaleCount: Int,
    val desynchronizedMetadataCount: Int,
    val reverseLinkConsistencyIssueCount: Int,
    val unmappedMuscleIds: Set<String>,
    val unmappedPatternIds: Set<String>,
    val unknownJointIds: Set<String>,
    val invalidLegacyMappings: Map<String, String>,
    val explicitlyRemovedLegacyIds: Set<String>,
    val sourceSha256: String? = null,
) {
    val passes: Boolean
        get() = definitionCount > 0 &&
            configurationCount > 0 &&
            richMetadataCount == configurationCount &&
            editorialCoverageCount == configurationCount &&
            muscleNoteCoverageCount == configurationCount &&
            jointCoverageCount == configurationCount &&
            shortDescriptionCount == 0 &&
            shortBenefitCount == 0 &&
            shortTechniqueCount == 0 &&
            shortVariantRationaleCount == 0 &&
            duplicateDescriptionCount == 0 &&
            duplicateTechniqueCount == 0 &&
            duplicateVariantRationaleCount == 0 &&
            desynchronizedMetadataCount == 0 &&
            reverseLinkConsistencyIssueCount == 0 &&
            unmappedMuscleIds.isEmpty() &&
            unmappedPatternIds.isEmpty() &&
            unknownJointIds.isEmpty() &&
            invalidLegacyMappings.isEmpty()
}

/**
 * Canonical reverse index derived from every approved configuration. The
 * configuration id is the identity exposed by Aprende, so variants never
 * collapse into a parent name while building anatomy/pattern relations.
 */
data class AprendeCatalogReverseIndex(
    val exerciseIdsByMuscle: Map<String, Set<String>>,
    val exerciseIdsByJoint: Map<String, Set<String>>,
    val exerciseIdsByPattern: Map<String, Set<String>>,
)

fun buildAprendeCatalogReverseIndex(catalog: ExerciseCatalogV2): AprendeCatalogReverseIndex {
    val muscles = linkedMapOf<String, MutableSet<String>>()
    val joints = linkedMapOf<String, MutableSet<String>>()
    val patterns = linkedMapOf<String, MutableSet<String>>()
    catalog.families
        .flatMap { it.definitions }
        .flatMap { it.configurations }
        .forEach { configuration ->
            val profile = configuration.profile
            (profile.primaryMuscles + profile.secondaryMuscles + profile.stabilizerMuscles)
                .distinct()
                .forEach { muscle -> muscles.getOrPut(muscle) { linkedSetOf() }.add(configuration.id) }
            profile.jointInvolvement
                .map { it.jointId }
                .distinct()
                .forEach { joint -> joints.getOrPut(joint) { linkedSetOf() }.add(configuration.id) }
            patterns.getOrPut(profile.movementPatternId) { linkedSetOf() }.add(configuration.id)
        }
    return AprendeCatalogReverseIndex(
        exerciseIdsByMuscle = muscles.mapValues { it.value.toSet() },
        exerciseIdsByJoint = joints.mapValues { it.value.toSet() },
        exerciseIdsByPattern = patterns.mapValues { it.value.toSet() },
    )
}

fun auditAprendeCatalog(
    catalog: ExerciseCatalogV2,
    wikiLabMuscleIds: Set<String>,
    wikiLabPatternIds: Set<String>,
    wikiLabJointIds: Set<String> = AprendeOntology.wikiLabJointIds,
    legacyExerciseIds: Set<String> = AprendeOntology.allLegacyExerciseDecisions.keys,
    sourceSha256: String? = null,
): AprendeCatalogAuditReport {
    val contexts = catalog.families.flatMap { family ->
        family.definitions.flatMap { definition ->
            definition.configurations.map { configuration ->
                ConfigurationAuditContext(family, definition, configuration)
            }
        }
    }
    val definitions = contexts.map { it.definition }.distinctBy { it.id }
    val configurations = contexts.map { it.configuration }
    val profiles = configurations.map { it.profile }
    val muscleIds = profiles.flatMap {
        it.primaryMuscles + it.secondaryMuscles + it.stabilizerMuscles
    }.toSet()
    val patternIds = profiles.map { it.movementPatternId }.toSet()
    val jointIds = profiles.flatMap { it.jointInvolvement.map { joint -> joint.jointId } }.toSet()
    val reverseIndex = buildAprendeCatalogReverseIndex(catalog)
    val knownExerciseIds = (definitions.map { it.id } + configurations.map { it.id }).toSet()
    val invalidLegacy = AprendeOntology.allLegacyExerciseDecisions
        .filterKeys { it in legacyExerciseIds }
        .mapNotNull { (legacyId, mappedId) ->
            mappedId?.takeUnless { it in knownExerciseIds }?.let { legacyId to it }
        }
        .toMap()
    val removedLegacy = AprendeOntology.allLegacyExerciseDecisions
        .filterKeys { it in legacyExerciseIds }
        .filterValues { it == null }
        .keys

    return AprendeCatalogAuditReport(
        catalogRevision = catalog.catalogRevision,
        ontologyRevision = catalog.ontologyRevision,
        sourceSha256 = sourceSha256,
        familyCount = catalog.families.size,
        definitionCount = definitions.size,
        configurationCount = configurations.size,
        richMetadataCount = profiles.count { it.richMetadata != null },
        editorialCoverageCount = profiles.count {
            it.description.isNotBlank() && it.benefits.isNotEmpty() && it.techniqueSummary.isNotBlank()
        },
        muscleNoteCoverageCount = profiles.count {
            it.muscleNotes.isNotEmpty() &&
                it.muscleNotes.map { note -> note.muscleId }.toSet() ==
                    (it.primaryMuscles + it.secondaryMuscles + it.stabilizerMuscles).toSet()
        },
        jointCoverageCount = profiles.count {
            it.jointInvolvement.isNotEmpty() &&
                it.jointInvolvement.map { joint -> joint.jointId }.toSet() ==
                    it.richMetadata?.biomechanics?.relevantJoints.orEmpty().toSet()
        },
        shortDescriptionCount = profiles.count { it.description.trim().length < MIN_DESCRIPTION_CHARS },
        shortBenefitCount = profiles.count {
            it.benefits.size < MIN_BENEFIT_COUNT ||
                it.benefits.any { benefit -> benefit.trim().length < MIN_BENEFIT_CHARS }
        },
        shortTechniqueCount = profiles.count { it.techniqueSummary.trim().length < MIN_TECHNIQUE_CHARS },
        shortVariantRationaleCount = profiles.count {
            it.variantRationale.trim().length < MIN_VARIANT_RATIONALE_CHARS
        },
        duplicateDescriptionCount = duplicateValueCount(profiles.map { it.description }),
        duplicateTechniqueCount = duplicateValueCount(profiles.map { it.techniqueSummary }),
        duplicateVariantRationaleCount = duplicateValueCount(profiles.map { it.variantRationale }),
        desynchronizedMetadataCount = contexts.count { context ->
            context.hasDesynchronizedMirrors(catalog.catalogRevision)
        },
        reverseLinkConsistencyIssueCount = contexts.sumOf { context ->
            val configurationId = context.configuration.id
            val profile = context.configuration.profile
            val muscleIssues = (profile.primaryMuscles + profile.secondaryMuscles + profile.stabilizerMuscles)
                .distinct()
                .count { muscle -> configurationId !in reverseIndex.exerciseIdsByMuscle[muscle].orEmpty() }
            val jointIssues = profile.jointInvolvement
                .map { it.jointId }
                .distinct()
                .count { joint -> configurationId !in reverseIndex.exerciseIdsByJoint[joint].orEmpty() }
            val patternIssues = if (configurationId in reverseIndex.exerciseIdsByPattern[profile.movementPatternId].orEmpty()) 0 else 1
            muscleIssues + jointIssues + patternIssues
        },
        unmappedMuscleIds = muscleIds.filter { sourceId ->
            AprendeOntology.wikiLabMuscleId(sourceId)?.let { it in wikiLabMuscleIds } != true
        }.toSet(),
        unmappedPatternIds = patternIds.filter { sourceId ->
            AprendeOntology.wikiLabPatternId(sourceId)?.let { it in wikiLabPatternIds } != true
        }.toSet(),
        unknownJointIds = jointIds - wikiLabJointIds,
        invalidLegacyMappings = invalidLegacy,
        explicitlyRemovedLegacyIds = removedLegacy,
    )
}

private data class ConfigurationAuditContext(
    val family: ExerciseFamilyV2,
    val definition: ExerciseDefinitionV2,
    val configuration: ExerciseConfigurationV2,
) {
    fun hasDesynchronizedMirrors(catalogRevision: String): Boolean {
        val profile = configuration.profile
        val rich = profile.richMetadata ?: return true
        val identity = rich.identity
        val anatomy = rich.anatomy
        val biomechanics = rich.biomechanics
        val editorial = rich.editorial
        val coaching = rich.coaching
        val replacement = rich.replacement
        val display = rich.display
        val fatigue = rich.fatigue
        return identity.catalogRevision != catalogRevision ||
            identity.familyId != family.id ||
            identity.definitionId != definition.id ||
            identity.configurationId != configuration.id ||
            identity.canonicalName != definition.canonicalName ||
            identity.searchTerms != definition.searchTerms ||
            identity.kind != definition.kind ||
            identity.performanceProfileId != profile.performanceProfileId ||
            anatomy.primaryMuscles != profile.primaryMuscles ||
            anatomy.secondaryMuscles != profile.secondaryMuscles ||
            anatomy.stabilizerMuscles != profile.stabilizerMuscles ||
            anatomy.muscleNotes != profile.muscleNotes ||
            anatomy.jointInvolvement != profile.jointInvolvement ||
            biomechanics.movementPatternId != profile.movementPatternId ||
            biomechanics.bodyRegion != profile.bodyRegion ||
            biomechanics.kineticChain != profile.kineticChain ||
            biomechanics.laterality != profile.laterality ||
            biomechanics.equipmentId != profile.equipmentId ||
            biomechanics.loadMode != profile.loadMode ||
            biomechanics.resistanceProfile != profile.resistanceProfile ||
            biomechanics.relevantJoints != profile.jointInvolvement.map { it.jointId } ||
            coaching.setup != profile.setupCues ||
            coaching.execution != profile.executionCues ||
            coaching.commonMistakes != profile.commonMistakes ||
            replacement.replacementGroup != profile.replacementGroup ||
            replacement.replacementPriority != profile.replacementPriority ||
            display.displayName != definition.canonicalName ||
            display.displaySummary != configuration.displaySummary ||
            display.selectedOptions != configuration.selectedOptions ||
            editorial.description != profile.description ||
            editorial.benefits != profile.benefits ||
            editorial.technique != profile.techniqueSummary ||
            editorial.variantRationale != profile.variantRationale ||
            fatigue.efc != profile.efc ||
            fatigue.cnc != profile.cnc ||
            fatigue.ssc != profile.ssc ||
            fatigue.ttc != profile.ttc ||
            fatigue.axialLoadFactor != profile.axialLoadFactor ||
            fatigue.technicalDifficulty != profile.technicalDifficulty
    }
}

private const val MIN_DESCRIPTION_CHARS = 80
private const val MIN_BENEFIT_COUNT = 2
private const val MIN_BENEFIT_CHARS = 30
private const val MIN_TECHNIQUE_CHARS = 80
private const val MIN_VARIANT_RATIONALE_CHARS = 60

private fun duplicateValueCount(values: List<String>): Int = values
    .map { it.trim().lowercase(Locale.ROOT) }
    .groupingBy { it }
    .eachCount()
    .values
    .sumOf { count -> (count - 1).coerceAtLeast(0) }
