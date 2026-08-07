package com.example.kpkn.data.exercises.catalogv2

import android.content.Context
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogResolveResultV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationCompatibilityV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSearchHitV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSearchFiltersV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionDraftV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionValidationV2
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Android asset boundary for the approved v2 catalog. The old asset is not a
 * fallback here: an absent/corrupt/unapproved v2 asset is an explicit Error.
 */
class ApprovedAssetExerciseCatalogRepositoryV2(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExerciseCatalogRepositoryV2 {
    private var delegate: InMemoryExerciseCatalogRepositoryV2? = null
    private val errorState = kotlinx.coroutines.flow.MutableStateFlow<ExerciseCatalogStateV2>(ExerciseCatalogStateV2.Loading)
    override val state: StateFlow<ExerciseCatalogStateV2> = errorState

    override suspend fun load() {
        CatalogV2ProcessCache.peek()?.let { entry ->
            delegate = entry.repository
            errorState.value = ExerciseCatalogStateV2.Ready(entry.catalog)
            return
        }
        errorState.value = ExerciseCatalogStateV2.Loading
        val result = withContext(ioDispatcher) {
            runCatching { CatalogV2ProcessCache.getOrLoad(context) }
        }
        result.fold(
            onSuccess = { entry ->
                delegate = entry.repository
                errorState.value = ExerciseCatalogStateV2.Ready(entry.catalog)
            },
            onFailure = { failure ->
                delegate = null
                errorState.value = ExerciseCatalogStateV2.Error(
                    failure.message ?: "exercise_catalog_v2_unavailable",
                )
            },
        )
    }

    override fun search(query: String, filters: ExerciseSearchFiltersV2): List<ExerciseSearchHitV2> =
        delegate?.search(query, filters).orEmpty()

    override fun compatibility(
        definitionId: String,
        selectedOptions: Map<String, String>,
    ): ExerciseConfigurationCompatibilityV2 =
        delegate?.compatibility(definitionId, selectedOptions)
            ?: ExerciseConfigurationCompatibilityV2(emptySet(), emptyList())

    override fun validate(draft: ExerciseSelectionDraftV2): ExerciseSelectionValidationV2 =
        delegate?.validate(draft)
            ?: ExerciseSelectionValidationV2.Invalid("catalog_not_ready")

    override fun resolve(selection: ExerciseSelectionV2): ExerciseCatalogResolveResultV2 =
        delegate?.resolve(selection) ?: ExerciseCatalogResolveResultV2.NotReady()
}
