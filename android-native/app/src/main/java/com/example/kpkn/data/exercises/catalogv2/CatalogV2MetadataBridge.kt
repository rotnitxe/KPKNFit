package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseMetadataV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseSnapshotV2
import kotlinx.serialization.json.Json

private val catalogRichMetadataDecoder = Json {
    ignoreUnknownKeys = false
}

/** Decode typed v2 metadata without reconstructing it from a display name. */
fun ExerciseMuscleInfo.decodeCatalogRichMetadata(): ResolvedExerciseMetadataV2? =
    catalogRichMetadataJson?.let { encoded ->
        runCatching {
            catalogRichMetadataDecoder.decodeFromString(
                ResolvedExerciseMetadataV2.serializer(),
                encoded,
            )
        }.getOrNull()
    }

/**
 * Convert an approved metadata envelope to the runtime profile used by
 * snapshots. No value is inferred from a name or a legacy alias.
 */
fun ExerciseMuscleInfo.toResolvedExerciseProfileV2(): ResolvedExerciseProfileV2? {
    val metadata = decodeCatalogRichMetadata() ?: return null
    return ResolvedExerciseProfileV2(
        movementPatternId = metadata.biomechanics.movementPatternId,
        bodyRegion = metadata.biomechanics.bodyRegion,
        kineticChain = metadata.biomechanics.kineticChain,
        laterality = metadata.biomechanics.laterality,
        equipmentId = metadata.biomechanics.equipmentId,
        loadMode = metadata.biomechanics.loadMode,
        primaryMuscles = metadata.anatomy.primaryMuscles,
        secondaryMuscles = metadata.anatomy.secondaryMuscles,
        stabilizerMuscles = metadata.anatomy.stabilizerMuscles,
        efc = metadata.fatigue.efc,
        cnc = metadata.fatigue.cnc,
        ssc = metadata.fatigue.ssc,
        ttc = metadata.fatigue.ttc,
        axialLoadFactor = metadata.fatigue.axialLoadFactor,
        technicalDifficulty = metadata.fatigue.technicalDifficulty,
        resistanceProfile = metadata.biomechanics.resistanceProfile,
        setupCues = metadata.coaching.setup,
        executionCues = metadata.coaching.execution,
        commonMistakes = metadata.coaching.commonMistakes,
        performanceProfileId = metadata.identity.performanceProfileId,
        richMetadata = metadata,
        replacementGroup = metadata.replacement.replacementGroup,
        replacementPriority = metadata.replacement.replacementPriority,
        automationEligible = catalogReviewStatus == "APPROVED" &&
            metadata.evidenceConfidence != com.example.kpkn.domain.exercises.catalogv2.CatalogConfidenceV2.LOW,
    )
}

/** Build the immutable completed-history snapshot only from exact v2 identity. */
fun Exercise.toResolvedCatalogSnapshotJson(
    info: ExerciseMuscleInfo,
    capturedAtEpochMs: Long,
): String? {
    val identity = catalogSelectionOrNull() ?: return null
    val metadata = info.decodeCatalogRichMetadata() ?: return null
    if (
        metadata.identity.catalogRevision != identity.selection.catalogRevision ||
        metadata.identity.definitionId != identity.selection.definitionId ||
        metadata.identity.configurationId != identity.selection.configurationId ||
        metadata.identity.performanceProfileId != identity.performanceProfileId
    ) return null
    val profile = info.toResolvedExerciseProfileV2() ?: return null
    return encodeResolvedCatalogSnapshot(
        ResolvedExerciseSnapshotV2(
            selection = identity.selection,
            resolvedProfile = profile,
            catalogRevision = identity.selection.catalogRevision,
            capturedAtEpochMs = capturedAtEpochMs,
        ),
    )
}