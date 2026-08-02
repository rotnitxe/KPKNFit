package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseSnapshotV2
import kotlinx.serialization.json.Json

private val snapshotJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }

data class CatalogSelectionIdentityV2(
    val selection: ExerciseSelectionV2,
    val performanceProfileId: String,
    val occurrenceId: String,
)

fun Exercise.withCatalogSelection(
    selection: ExerciseSelectionV2,
    profile: ResolvedExerciseProfileV2,
    occurrenceId: String = id,
): Exercise = copy(
    catalogRevision = selection.catalogRevision,
    catalogDefinitionId = selection.definitionId,
    catalogConfigurationId = selection.configurationId,
    performanceProfileId = profile.performanceProfileId,
    occurrenceId = occurrenceId,
    // Keep the visible name parent-level; configuration is rendered from the
    // resolved profile/summary by the v2 picker, never concatenated ad hoc.
)

fun encodeResolvedCatalogSnapshot(snapshot: ResolvedExerciseSnapshotV2): String =
    snapshotJson.encodeToString(ResolvedExerciseSnapshotV2.serializer(), snapshot)

fun CompletedExercise.withResolvedCatalogSnapshot(
    selection: ExerciseSelectionV2,
    profile: ResolvedExerciseProfileV2,
    occurrenceId: String = exerciseId,
    capturedAtEpochMs: Long,
): CompletedExercise {
    val snapshot = ResolvedExerciseSnapshotV2(
        selection = selection,
        resolvedProfile = profile,
        catalogRevision = selection.catalogRevision,
        capturedAtEpochMs = capturedAtEpochMs,
    )
    return copy(
        catalogRevision = selection.catalogRevision,
        catalogDefinitionId = selection.definitionId,
        catalogConfigurationId = selection.configurationId,
        performanceProfileId = profile.performanceProfileId,
        occurrenceId = occurrenceId,
        resolvedProfileSnapshotJson = snapshotJson.encodeToString(
            ResolvedExerciseSnapshotV2.serializer(),
            snapshot,
        ),
    )
}

fun Exercise.catalogSelectionOrNull(): CatalogSelectionIdentityV2? {
    val revision = catalogRevision?.takeIf { it.isNotBlank() } ?: return null
    val definition = catalogDefinitionId?.takeIf { it.isNotBlank() } ?: return null
    val configuration = catalogConfigurationId?.takeIf { it.isNotBlank() } ?: return null
    val performance = performanceProfileId?.takeIf { it.isNotBlank() } ?: return null
    val occurrence = occurrenceId?.takeIf { it.isNotBlank() } ?: return null
    return CatalogSelectionIdentityV2(
        selection = ExerciseSelectionV2(definition, configuration, revision),
        performanceProfileId = performance,
        occurrenceId = occurrence,
    )
}

fun CompletedExercise.catalogSelectionOrNull(): CatalogSelectionIdentityV2? {
    val revision = catalogRevision?.takeIf { it.isNotBlank() } ?: return null
    val definition = catalogDefinitionId?.takeIf { it.isNotBlank() } ?: return null
    val configuration = catalogConfigurationId?.takeIf { it.isNotBlank() } ?: return null
    val performance = performanceProfileId?.takeIf { it.isNotBlank() } ?: return null
    val occurrence = occurrenceId?.takeIf { it.isNotBlank() } ?: return null
    return CatalogSelectionIdentityV2(
        selection = ExerciseSelectionV2(definition, configuration, revision),
        performanceProfileId = performance,
        occurrenceId = occurrence,
    )
}

fun CompletedExercise.decodeResolvedCatalogSnapshot(): ResolvedExerciseSnapshotV2? =
    resolvedProfileSnapshotJson?.let { encoded ->
        runCatching {
            snapshotJson.decodeFromString(ResolvedExerciseSnapshotV2.serializer(), encoded)
        }.getOrNull()
    }
