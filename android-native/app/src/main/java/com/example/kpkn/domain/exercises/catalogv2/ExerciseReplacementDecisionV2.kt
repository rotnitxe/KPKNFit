package com.example.kpkn.domain.exercises.catalogv2

/** Replacement is expressed with immutable selections, never just a parent id. */
sealed interface ReplacementSourceMatchV2 {
    data class Occurrence(val occurrenceId: String) : ReplacementSourceMatchV2
    data class ExactSelection(val selection: ExerciseSelectionV2) : ReplacementSourceMatchV2
    data class Definition(val definitionId: String) : ReplacementSourceMatchV2
}

enum class ReplacementScopeV2 {
    OCCURRENCE,
    SESSION,
    PROGRAM,
}

data class ReplacementDecisionV2(
    val sourceMatch: ReplacementSourceMatchV2,
    val fromSelection: ExerciseSelectionV2,
    val toSelection: ExerciseSelectionV2,
    val scope: ReplacementScopeV2,
) {
    init {
        require(fromSelection.catalogRevision.isNotBlank()) { "fromSelection requires catalog revision" }
        require(toSelection.catalogRevision.isNotBlank()) { "toSelection requires catalog revision" }
        require(fromSelection != toSelection) { "replacement must change the selection" }
    }
}

fun ReplacementDecisionV2.matches(
    selection: ExerciseSelectionV2,
    occurrenceId: String? = null,
): Boolean = when (val source = sourceMatch) {
    is ReplacementSourceMatchV2.Occurrence -> source.occurrenceId == occurrenceId
    is ReplacementSourceMatchV2.ExactSelection -> source.selection == selection
    is ReplacementSourceMatchV2.Definition -> source.definitionId == selection.definitionId
}
