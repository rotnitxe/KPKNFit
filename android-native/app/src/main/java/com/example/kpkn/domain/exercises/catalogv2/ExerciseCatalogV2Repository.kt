package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The only domain boundary for the v2 exercise catalog.
 *
 * A draft selection is deliberately separate from [ExerciseSelectionV2]. A
 * map of chips is never a valid exercise identity and cannot leak into
 * persistence, AUGE, split generation, or replacement logic.
 */
interface ExerciseCatalogRepositoryV2 {
    val state: StateFlow<ExerciseCatalogStateV2>

    suspend fun load()

    fun search(query: String, filters: ExerciseSearchFiltersV2 = ExerciseSearchFiltersV2()): List<ExerciseSearchHitV2>

    fun compatibility(
        definitionId: String,
        selectedOptions: Map<String, String> = emptyMap(),
    ): ExerciseConfigurationCompatibilityV2

    fun validate(draft: ExerciseSelectionDraftV2): ExerciseSelectionValidationV2

    fun resolve(selection: ExerciseSelectionV2): ExerciseCatalogResolveResultV2
}

data class ExerciseSelectionDraftV2(
    val definitionId: String,
    val selectedOptions: Map<String, String> = emptyMap(),
    val catalogRevision: String,
)

data class ExerciseChipOptionV2(
    val axis: String,
    val value: String,
    val compatibleConfigurationIds: Set<String>,
    val enabled: Boolean,
    val disabledReason: String? = null,
)

data class ExerciseChipAxisStateV2(
    val axis: String,
    val selectedValue: String?,
    val options: List<ExerciseChipOptionV2>,
)

data class ExerciseConfigurationCompatibilityV2(
    val matchingConfigurationIds: Set<String>,
    val axes: List<ExerciseChipAxisStateV2>,
    val exactConfigurationId: String? = null,
)

sealed interface ExerciseCatalogResolveResultV2 {
    data class Resolved(
        val selection: ExerciseSelectionV2,
        val profile: ResolvedExerciseProfileV2,
    ) : ExerciseCatalogResolveResultV2

    data class Invalid(val reason: String) : ExerciseCatalogResolveResultV2
    data class NotReady(val reason: String = "catalog_not_ready") : ExerciseCatalogResolveResultV2
}

/**
 * Pure implementation used by tests and by the Android asset repository.
 * It never synthesises a configuration: all results come from the explicit
 * configuration rows in the source catalog.
 */
class InMemoryExerciseCatalogRepositoryV2(
    private val catalog: ExerciseCatalogV2,
) : ExerciseCatalogRepositoryV2 {
    private val _state = MutableStateFlow<ExerciseCatalogStateV2>(ExerciseCatalogStateV2.Loading)
    override val state: StateFlow<ExerciseCatalogStateV2> = _state.asStateFlow()
    private val resolver = ExerciseCatalogV2Resolver(catalog)

    override suspend fun load() {
        _state.value = ExerciseCatalogStateV2.Ready(catalog)
    }

    override fun search(query: String, filters: ExerciseSearchFiltersV2): List<ExerciseSearchHitV2> =
        if (_state.value is ExerciseCatalogStateV2.Ready) resolver.search(query, filters) else emptyList()

    override fun compatibility(
        definitionId: String,
        selectedOptions: Map<String, String>,
    ): ExerciseConfigurationCompatibilityV2 {
        val definition = catalog.families
            .asSequence()
            .flatMap { it.definitions.asSequence() }
            .firstOrNull { it.id == definitionId }
            ?: return ExerciseConfigurationCompatibilityV2(emptySet(), emptyList())

        val matching = definition.configurations.filter { configuration ->
            selectedOptions.all { (axis, value) -> configuration.selectedOptions[axis] == value }
        }
        val matchingIds = matching.mapTo(linkedSetOf()) { it.id }
        // optionAxes is an editorial hierarchy, not a flat checklist.  Keep
        // already selected levels visible so the user can change them, then
        // expose only the next unresolved level whose compatible candidates
        // actually differ.  Fixed downstream values are inferred by the
        // resolver and never become meaningless singleton chips.
        val nextUnselectedAxis = definition.optionAxes.firstOrNull { axis ->
            if (axis in selectedOptions) return@firstOrNull false
            matching.mapNotNull { it.selectedOptions[axis] }.distinct().size > 1
        }
        val visibleAxes = definition.optionAxes.filter { axis ->
            axis in selectedOptions || axis == nextUnselectedAxis
        }
        val axes = visibleAxes.map { axis ->
            // For a selected axis, remove that axis from the filter while
            // calculating choices.  This keeps an already selected chip
            // switchable instead of making every other value appear disabled.
            val base = definition.configurations.filter { configuration ->
                selectedOptions
                    .filterKeys { selectedAxis -> selectedAxis != axis }
                    .all { (selectedAxis, value) -> configuration.selectedOptions[selectedAxis] == value }
            }
            val candidates = if (base.isNotEmpty()) base else definition.configurations
            // Keep the editorial value set complete so callers can explain a
            // disabled choice; compatibleConfigurationIds still comes from
            // the contextual candidates above and is the source of truth for
            // whether the chip can be used.
            val values = definition.configurations
                .asSequence()
                .mapNotNull { it.selectedOptions[axis] }
                .distinct()
                .map { value ->
                    val compatibleIds = candidates
                        .filter { it.selectedOptions[axis] == value }
                        .mapTo(linkedSetOf()) { it.id }
                    ExerciseChipOptionV2(
                        axis = axis,
                        value = value,
                        compatibleConfigurationIds = compatibleIds,
                        enabled = compatibleIds.isNotEmpty(),
                        disabledReason = if (compatibleIds.isEmpty()) {
                            "incompatible_with_current_selection"
                        } else {
                            null
                        },
                    )
                }
                .toList()
            ExerciseChipAxisStateV2(
                axis = axis,
                selectedValue = selectedOptions[axis],
                options = values,
            )
        }
        return ExerciseConfigurationCompatibilityV2(
            matchingConfigurationIds = matchingIds,
            axes = axes,
            exactConfigurationId = matching.singleOrNull()?.id,
        )
    }

    override fun validate(draft: ExerciseSelectionDraftV2): ExerciseSelectionValidationV2 {
        if (draft.catalogRevision != catalog.catalogRevision) {
            return ExerciseSelectionValidationV2.Invalid(
                "catalog_revision_mismatch:${draft.catalogRevision}:${catalog.catalogRevision}",
            )
        }
        val definition = catalog.families
            .asSequence()
            .flatMap { it.definitions.asSequence() }
            .firstOrNull { it.id == draft.definitionId }
            ?: return ExerciseSelectionValidationV2.Invalid("unknown_definition:${draft.definitionId}")
        val compatibility = compatibility(definition.id, draft.selectedOptions)
        val exact = compatibility.exactConfigurationId
            ?: return ExerciseSelectionValidationV2.Invalid(
                if (compatibility.matchingConfigurationIds.isEmpty()) {
                    "no_compatible_configuration:${definition.id}"
                } else {
                    "selection_is_ambiguous:${definition.id}"
                },
            )
        return ExerciseSelectionValidationV2.Valid(
            ExerciseSelectionV2(
                definitionId = definition.id,
                configurationId = exact,
                catalogRevision = catalog.catalogRevision,
            ),
        )
    }

    override fun resolve(selection: ExerciseSelectionV2): ExerciseCatalogResolveResultV2 {
        if (_state.value !is ExerciseCatalogStateV2.Ready) {
            return ExerciseCatalogResolveResultV2.NotReady()
        }
        return when (val validation = resolver.validate(selection)) {
            is ExerciseSelectionValidationV2.Invalid ->
                ExerciseCatalogResolveResultV2.Invalid(validation.reason)
            is ExerciseSelectionValidationV2.Valid ->
                resolver.resolve(selection)?.let { profile ->
                    val family = catalog.families.firstOrNull { family ->
                        family.definitions.any { definition -> definition.id == selection.definitionId }
                    } ?: return ExerciseCatalogResolveResultV2.Invalid("definition_family_missing")
                    val definition = family.definitions.firstOrNull { it.id == selection.definitionId }
                        ?: return ExerciseCatalogResolveResultV2.Invalid("definition_missing")
                    val configuration = definition.configurations
                        .firstOrNull { it.id == selection.configurationId }
                        ?: return ExerciseCatalogResolveResultV2.Invalid("configuration_missing")
                    if (profile.richMetadata == null) {
                        return ExerciseCatalogResolveResultV2.Invalid("rich_metadata_missing:${configuration.id}")
                    }
                    ExerciseCatalogResolveResultV2.Resolved(selection, profile)
                } ?: ExerciseCatalogResolveResultV2.Invalid("configuration_profile_missing")
        }
    }
}
