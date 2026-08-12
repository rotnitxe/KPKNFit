package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2

/** Exact v2 editorial payload for one persisted exercise occurrence. */
internal data class ResolvedCatalogExerciseV2(
    val definition: ExerciseDefinitionV2,
    val configuration: ExerciseConfigurationV2,
    val legacyInfo: ExerciseMuscleInfo,
)

/**
 * Resolves the same definition/configuration pair used by the v2 picker.
 *
 * The persisted ids are authoritative. A legacy catalog row is accepted only
 * as an identity bridge when the Exercise predates the v2 fields; prose and
 * involvement still come from the current v2 catalog whenever that bridge is
 * exact.
 */
internal fun resolveCatalogExerciseV2(
    exercise: Exercise,
    catalog: ExerciseCatalogV2?,
    legacyInfo: ExerciseMuscleInfo?,
): ResolvedCatalogExerciseV2? {
    catalog ?: return null

    val revision = exercise.catalogRevision
        ?.takeIf { it.isNotBlank() }
        ?: legacyInfo?.catalogRevision?.takeIf { it.isNotBlank() }
    if (revision != null && revision != catalog.catalogRevision) return null

    val definitionId = exercise.catalogDefinitionId
        ?.takeIf { it.isNotBlank() }
        ?: legacyInfo?.catalogDefinitionId?.takeIf { it.isNotBlank() }
        ?: return null
    val configurationId = exercise.catalogConfigurationId
        ?.takeIf { it.isNotBlank() }
        ?: legacyInfo?.catalogConfigurationId?.takeIf { it.isNotBlank() }
        ?: return null

    val definition = catalog.families
        .asSequence()
        .flatMap { it.definitions.asSequence() }
        .firstOrNull { it.id == definitionId }
        ?: return null
    val configuration = definition.configurations.firstOrNull { it.id == configurationId }
        ?: return null
    val exactInfo = catalog.toLegacySelection(
        ExerciseSelectionV2(
            definitionId = definition.id,
            configurationId = configuration.id,
            catalogRevision = catalog.catalogRevision,
        ),
    ) ?: legacyInfo ?: return null

    return ResolvedCatalogExerciseV2(
        definition = definition,
        configuration = configuration,
        legacyInfo = exactInfo,
    )
}
