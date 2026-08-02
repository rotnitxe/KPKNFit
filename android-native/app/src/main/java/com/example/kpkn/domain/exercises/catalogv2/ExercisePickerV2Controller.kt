package com.example.kpkn.domain.exercises.catalogv2

/**
 * Stateful picker logic kept outside Compose. Each selected parent owns an
 * independent draft, so multi-select cannot leak chips between exercises.
 */
data class ExercisePickerV2DraftState(
    val definitionId: String,
    val selectedOptions: Map<String, String> = emptyMap(),
)

sealed interface ExercisePickerV2ConfirmResult {
    data class Confirmed(val selection: ExerciseSelectionV2) : ExercisePickerV2ConfirmResult
    data class Blocked(val reason: String) : ExercisePickerV2ConfirmResult
}

class ExercisePickerV2Controller(
    private val repository: ExerciseCatalogRepositoryV2,
) {
    private val drafts = linkedMapOf<String, ExercisePickerV2DraftState>()

    fun draftFor(definitionId: String): ExercisePickerV2DraftState =
        drafts.getOrPut(definitionId) { ExercisePickerV2DraftState(definitionId) }

    fun updateOption(
        definitionId: String,
        axis: String,
        value: String,
    ): ExerciseConfigurationCompatibilityV2 {
        val current = draftFor(definitionId)
        val candidate = current.selectedOptions + (axis to value)
        val compatibility = repository.compatibility(definitionId, candidate)
        if (compatibility.matchingConfigurationIds.isNotEmpty()) {
            drafts[definitionId] = current.copy(selectedOptions = candidate)
        }
        return compatibility
    }

    fun clear(definitionId: String) {
        drafts.remove(definitionId)
    }

    fun confirm(definitionId: String): ExercisePickerV2ConfirmResult {
        val draft = draftFor(definitionId)
        return when (val result = repository.validate(
            ExerciseSelectionDraftV2(
                definitionId = draft.definitionId,
                selectedOptions = draft.selectedOptions,
                catalogRevision = readyRevision(),
            ),
        )) {
            is ExerciseSelectionValidationV2.Valid -> ExercisePickerV2ConfirmResult.Confirmed(result.selection)
            is ExerciseSelectionValidationV2.Invalid -> ExercisePickerV2ConfirmResult.Blocked(result.reason)
        }
    }

    private fun readyRevision(): String = when (val state = repository.state.value) {
        is ExerciseCatalogStateV2.Ready -> state.catalog.catalogRevision
        is ExerciseCatalogStateV2.Loading -> ""
        is ExerciseCatalogStateV2.Error -> ""
    }
}
