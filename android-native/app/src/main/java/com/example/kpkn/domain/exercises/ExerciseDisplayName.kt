package com.example.kpkn.domain.exercises

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo

data class ExerciseDisplayParts(
    val parentName: String,
    val chips: List<String> = emptyList(),
) {
    val text: String
        get() = if (chips.isEmpty()) parentName else "$parentName · ${chips.joinToString(" · ")}"
}

/**
 * Builds the user-facing name from the canonical parent and the selected
 * non-default technical options. Defaults are intentionally omitted to keep
 * compact surfaces readable.
 */
fun exerciseDisplayParts(
    exercise: Exercise,
    catalogInfo: ExerciseMuscleInfo?,
): ExerciseDisplayParts {
    val v2Chips = catalogInfo?.catalogVariantChips.orEmpty()
    if (v2Chips.isNotEmpty()) {
        return ExerciseDisplayParts(
            parentName = exercise.name,
            chips = dedupeChips(v2Chips),
        )
    }
    val selected = exercise.selectedAspects.orEmpty()
    val options = catalogInfo?.catalogOptionAxes.orEmpty().flatMap { aspect ->
        val optionId = selected[aspect.id] ?: return@flatMap emptyList<AspectOption>()
        val defaultId = aspect.defaultOptionId ?: aspect.options.firstOrNull()?.id
        if (optionId == defaultId) return@flatMap emptyList()
        aspect.options.filter { it.id == optionId }
    }
    val legacyVariant = exercise.variantName
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { value -> options.any { it.name.equals(value, ignoreCase = true) } }
    return ExerciseDisplayParts(
        parentName = exercise.name,
        chips = dedupeChips(options.map { it.name } + listOfNotNull(legacyVariant)),
    )
}

/**
 * Removes redundant chips: exact duplicates and chips fully contained in a
 * more specific one. Example: ["Polea", "Polea Alta"] collapses to
 * ["Polea Alta"] because the height already implies the implement.
 */
private fun dedupeChips(chips: List<String>): List<String> {
    val unique = chips.distinctBy { it.trim().lowercase() }
    return unique.filter { chip ->
        unique.none { other ->
            other.length > chip.length && other.contains(chip, ignoreCase = true)
        }
    }
}

fun exerciseDisplayName(
    exercise: Exercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): String {
    val info = resolveCatalogInfoForDisplay(exercise, catalogLookup)
    return exerciseDisplayParts(exercise, info).text
}

fun completedExerciseDisplayName(
    exercise: CompletedExercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): String {
    val planned = Exercise(
        id = exercise.exerciseId,
        name = exercise.exerciseName,
        exerciseDbId = exercise.exerciseDbId,
        catalogRevision = exercise.catalogRevision,
        catalogDefinitionId = exercise.catalogDefinitionId,
        catalogConfigurationId = exercise.catalogConfigurationId,
        performanceProfileId = exercise.performanceProfileId,
        occurrenceId = exercise.occurrenceId,
        variantName = exercise.variantName,
        selectedAspects = exercise.selectedAspects,
        effectiveMuscles = exercise.effectiveMuscles,
    )
    return exerciseDisplayName(planned, catalogLookup)
}

internal fun resolveCatalogInfoForDisplay(
    exercise: Exercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
    index = catalogLookup,
    catalogConfigurationId = exercise.catalogConfigurationId,
    exerciseDbId = exercise.exerciseDbId,
    exerciseId = exercise.exerciseId,
    exerciseName = exercise.name,
)
