package com.example.kpkn.domain.exercises

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.protocols.displayName as techniqueDisplayName

/** Snapshot of user display nicknames. Updated from Settings; never mutate the catalog asset. */
object ExerciseNicknameResolver {
    @Volatile
    var nicknames: Map<String, String> = emptyMap()
}

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
 *
 * Recipe slots carry their technique in variantName/techniqueModifier/
 * relationshipNotes (never in Exercise.name); those surface here as chips so
 * the technique remains visible next to the verbatim catalog name.
 */
fun exerciseDisplayParts(
    exercise: Exercise,
    catalogInfo: ExerciseMuscleInfo?,
    nicknames: Map<String, String> = ExerciseNicknameResolver.nicknames,
): ExerciseDisplayParts {
    val parentName = overlayExerciseParentName(
        fallbackName = catalogInfo?.name?.takeIf { it.isNotBlank() }
            ?: exercise.name,
        nicknameKey = exercise.nicknameKey(),
        nicknames = nicknames,
    )
    val v2Chips = catalogInfo?.catalogVariantChips.orEmpty()
    if (v2Chips.isNotEmpty()) {
        return ExerciseDisplayParts(
            parentName = parentName,
            chips = dedupeChips(v2Chips).filterNot(::isDisplayNoiseChip),
        )
    }
    if (!exercise.catalogConfigurationId.isNullOrBlank()) {
        return ExerciseDisplayParts(parentName = parentName)
    }
    val technique = recipeTechniqueLabel(exercise)
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
        parentName = parentName,
        chips = dedupeChips(listOfNotNull(technique) + options.map { it.name } + listOfNotNull(legacyVariant))
            .filterNot(::isDisplayNoiseChip),
    )
}

/**
 * Recipe technique label for display chips. Only recipe fields are read:
 * catalog-backed sessions always carry variantName (mirroring
 * techniqueModifier), so a populated legacy variantName on a catalog
 * exercise must never invent display text.
 */
private fun recipeTechniqueLabel(exercise: Exercise): String? {
    val technique = exercise.techniqueModifier?.techniqueDisplayName()?.trim().orEmpty()
    if (technique.isNotBlank()) return technique
    if (exercise.catalogConfigurationId.isNullOrBlank()) {
        val legacy = exercise.variantName?.trim().orEmpty()
        if (legacy.isNotBlank()) return legacy
    }
    return null
}

private fun isDisplayNoiseChip(chip: String): Boolean = when (chip.trim().lowercase()) {
    "bilateral",
    "sentado",
    "de pie" -> true
    else -> false
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
