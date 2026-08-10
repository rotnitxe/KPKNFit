package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Stable hand-off contract between an editor/live session and the full-screen catalog.
 * Only IDs cross the navigation boundary; the caller resolves them against its own state.
 */
@Serializable
enum class CatalogLaunchOrigin {
    SESSION_EDITOR,
    LIVE_SESSION,
    REPLACEMENT,
    SUPERSET,
    MOBILITY,
}

@Serializable
enum class CatalogSelectionMode {
    SINGLE,
    MULTIPLE,
    REPLACEMENT,
    SUPERSET,
    MOBILITY,
}

@Serializable
data class CatalogLaunchRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val origin: CatalogLaunchOrigin = CatalogLaunchOrigin.SESSION_EDITOR,
    val selectionMode: CatalogSelectionMode = CatalogSelectionMode.MULTIPLE,
    val targetExerciseId: String? = null,
    val selectedExerciseIds: List<String> = emptyList(),
    val initialQuery: String = "",
)

@Serializable
data class CatalogResult(
    val requestId: String,
    val selectedExerciseIds: List<String> = emptyList(),
    val selectedConfigurationIds: List<String> = emptyList(),
    val canceled: Boolean = false,
) {
    companion object {
        fun from(request: CatalogLaunchRequest, selected: List<ExerciseMuscleInfo>): CatalogResult =
            CatalogResult(
                requestId = request.requestId,
                selectedExerciseIds = selected.map { it.id },
                // Keep positional alignment with selectedExerciseIds. An item
                // without a v2 configuration still occupies a slot so a later
                // configuration cannot be applied to the wrong exercise.
                selectedConfigurationIds = selected.map { it.catalogConfigurationId.orEmpty() },
            )
    }

    /**
     * Resolves the ID-only result against the caller's catalog index.
     * Configuration IDs are preferred because they preserve the exact chips
     * chosen in the page catalog; the definition/legacy ID remains a fallback
     * for old payloads and custom exercises.
     */
    fun resolveSelectedInfos(index: Map<String, ExerciseMuscleInfo>): List<ExerciseMuscleInfo> =
        selectedExerciseIds.mapIndexedNotNull { position, exerciseId ->
            val configurationId = selectedConfigurationIds.getOrNull(position).orEmpty()
            index[configurationId.lowercase()] ?: index[exerciseId.lowercase()]
        }
}

/** Keys intentionally remain constant so a request survives recomposition and rotation. */
object CatalogSavedStateKeys {
    const val REQUEST = "kpkn.catalog.request"
    const val RESULT = "kpkn.catalog.result"

    fun request(requestId: String): String = "$REQUEST.$requestId"
}
