package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session

/**
 * Save-time firewall for the catalog-v2 cutover.
 *
 * A normal exercise is valid only when its complete identity is persisted
 * together. A partial identity is more dangerous than a missing identity: it
 * can make an old alias or a parent name look like a valid configuration.
 * Manual exercises are allowed only in the explicit `custom:` namespace and
 * are intentionally outside automatic AUGE/split/replacement decisions.
 */
data class CatalogV2SelectionGateIssue(
    val exerciseId: String,
    val code: String,
    val detail: String? = null,
)

fun Session.catalogV2SelectionIssues(): List<CatalogV2SelectionGateIssue> {
    val exercises = allExercises()
    val catalogExercises = exercises.filterNot(::isManualCustomExercise)
    val issues = mutableListOf<CatalogV2SelectionGateIssue>()

    catalogExercises.forEach { exercise ->
        val missing = buildList {
            if (exercise.catalogRevision.isNullOrBlank()) add("catalogRevision")
            if (exercise.catalogDefinitionId.isNullOrBlank()) add("catalogDefinitionId")
            if (exercise.catalogConfigurationId.isNullOrBlank()) add("catalogConfigurationId")
            if (exercise.performanceProfileId.isNullOrBlank()) add("performanceProfileId")
            if (exercise.occurrenceId.isNullOrBlank()) add("occurrenceId")
        }
        if (missing.isNotEmpty()) {
            issues += CatalogV2SelectionGateIssue(
                exerciseId = exercise.id,
                code = "incomplete_v2_identity",
                detail = missing.joinToString(","),
            )
        }
        if (exercise.selectedAspects != null) {
            issues += CatalogV2SelectionGateIssue(
                exerciseId = exercise.id,
                code = "legacy_chip_state_present",
                detail = "selectedAspects",
            )
        }
    }

    val revisions = catalogExercises.mapNotNull { it.catalogRevision?.takeIf(String::isNotBlank) }.distinct()
    if (revisions.size > 1) {
        issues += CatalogV2SelectionGateIssue(
            exerciseId = "session:${id}",
            code = "mixed_catalog_revisions",
            detail = revisions.joinToString(","),
        )
    }

    catalogExercises
        .mapNotNull { it.occurrenceId?.takeIf(String::isNotBlank) }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
        .sorted()
        .forEach { occurrenceId ->
            issues += CatalogV2SelectionGateIssue(
                exerciseId = "session:${id}",
                code = "duplicate_occurrence_id",
                detail = occurrenceId,
            )
        }

    return issues.distinctBy { Triple(it.exerciseId, it.code, it.detail) }
}

private fun isManualCustomExercise(exercise: Exercise): Boolean {
    val identityCandidates = listOf(
        exercise.exerciseDbId,
        exercise.exerciseId,
        exercise.canonicalExerciseId,
        exercise.exerciseFamilyId,
    )
    return identityCandidates.any { it?.startsWith("custom:", ignoreCase = true) == true }
}
