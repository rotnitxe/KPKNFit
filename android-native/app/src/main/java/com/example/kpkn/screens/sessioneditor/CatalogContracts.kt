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
enum class CatalogCommitAction {
    ADD,
    CREATE_SUPERSET,
}

@Serializable
data class CatalogSupersetConfig(
    val rounds: Int = 3,
    val restBetweenExercisesSeconds: Int = 60,
    val restAfterSupersetSeconds: Int = 120,
)

@Serializable
data class CatalogLaunchRequest(
    val requestId: String = UUID.randomUUID().toString(),
    /** Versioned so a restored pre-v2 payload cannot be interpreted as a new action. */
    val contractVersion: Int = CURRENT_CONTRACT_VERSION,
    val origin: CatalogLaunchOrigin = CatalogLaunchOrigin.SESSION_EDITOR,
    val selectionMode: CatalogSelectionMode = CatalogSelectionMode.MULTIPLE,
    val targetExerciseId: String? = null,
    val targetGroupName: String? = null,
    val selectedExerciseIds: List<String> = emptyList(),
    val initialQuery: String = "",
)

@Serializable
data class CatalogResult(
    val requestId: String,
    /** Old saved results deserialize as v1 and are accepted only as ADD results. */
    val contractVersion: Int = LEGACY_CONTRACT_VERSION,
    val selectedExerciseIds: List<String> = emptyList(),
    val selectedConfigurationIds: List<String> = emptyList(),
    val canceled: Boolean = false,
    val origin: CatalogLaunchOrigin = CatalogLaunchOrigin.SESSION_EDITOR,
    val selectionMode: CatalogSelectionMode = CatalogSelectionMode.MULTIPLE,
    val targetExerciseId: String? = null,
    val commitAction: CatalogCommitAction = CatalogCommitAction.ADD,
    val supersetConfig: CatalogSupersetConfig? = null,
) {
    companion object {
        fun success(
            request: CatalogLaunchRequest,
            selected: List<ExerciseMuscleInfo>,
            commitAction: CatalogCommitAction = CatalogCommitAction.ADD,
            supersetConfig: CatalogSupersetConfig? = null,
        ): CatalogResult = CatalogResult(
            requestId = request.requestId,
            contractVersion = request.contractVersion.coerceAtLeast(CURRENT_CONTRACT_VERSION),
            selectedExerciseIds = selected.map { it.id },
            selectedConfigurationIds = selected.map { it.catalogConfigurationId.orEmpty() },
            canceled = false,
            origin = request.origin,
            selectionMode = request.selectionMode,
            targetExerciseId = request.targetExerciseId,
            commitAction = commitAction,
            supersetConfig = supersetConfig,
        )

        fun cancel(request: CatalogLaunchRequest): CatalogResult = CatalogResult(
            requestId = request.requestId,
            contractVersion = request.contractVersion,
            canceled = true,
            origin = request.origin,
            selectionMode = request.selectionMode,
            targetExerciseId = request.targetExerciseId,
        )

        fun from(
            request: CatalogLaunchRequest,
            selected: List<ExerciseMuscleInfo>,
        ): CatalogResult = success(request, selected)
    }

    /**
     * Resolves the ID-only result against the caller's catalog index.
     * Configuration IDs are preferred because they preserve the exact chips
     * chosen in the page catalog; the definition/legacy ID remains a fallback
     * for old payloads and custom exercises.
     */
    fun unresolvedSelectionIds(index: Map<String, ExerciseMuscleInfo>): List<String> =
        selectedExerciseIds.mapIndexedNotNull { position, exerciseId ->
            val configurationId = selectedConfigurationIds.getOrNull(position).orEmpty()
            val resolved = index[configurationId.lowercase()] ?: index[exerciseId.lowercase()]
            if (resolved == null) exerciseId else null
        }

    /**
     * Resolves all IDs atomically. A partial result is never returned: callers
     * must either apply the complete selection in its original order or abort.
     */
    fun resolveSelectedInfos(index: Map<String, ExerciseMuscleInfo>): List<ExerciseMuscleInfo> {
        if (selectedExerciseIds.isEmpty()) return emptyList()
        if (unresolvedSelectionIds(index).isNotEmpty()) return emptyList()
        return selectedExerciseIds.mapIndexed { position, exerciseId ->
            val configurationId = selectedConfigurationIds.getOrNull(position).orEmpty()
            requireNotNull(index[configurationId.lowercase()] ?: index[exerciseId.lowercase()])
        }
    }

    fun matches(request: CatalogLaunchRequest): Boolean =
        requestId == request.requestId &&
            origin == request.origin &&
            selectionMode == request.selectionMode &&
            targetExerciseId == request.targetExerciseId

    /**
     * Full boundary validation. Legacy v1 results remain readable, while a
     * future/unknown version or an action that cannot be represented by the
     * request is rejected before any mutation is attempted.
     */
    fun isValidFor(request: CatalogLaunchRequest): Boolean {
        if (!matches(request)) return false
        if (contractVersion !in LEGACY_CONTRACT_VERSION..CURRENT_CONTRACT_VERSION) return false
        if (canceled) return true
        if (selectedExerciseIds.isEmpty()) return false
        if (selectedConfigurationIds.isNotEmpty() && selectedConfigurationIds.size != selectedExerciseIds.size) return false
        val isReplacement = request.origin == CatalogLaunchOrigin.REPLACEMENT ||
            request.selectionMode == CatalogSelectionMode.REPLACEMENT
        if (isReplacement) {
            // Replacements are deliberately single-item ADDs. A result carrying
            // a structural action must never be able to mutate the target slot.
            if (request.origin != CatalogLaunchOrigin.REPLACEMENT ||
                request.selectionMode != CatalogSelectionMode.REPLACEMENT ||
                targetExerciseId.isNullOrBlank() ||
                selectedExerciseIds.size != 1 ||
                commitAction != CatalogCommitAction.ADD ||
                supersetConfig != null
            ) return false
        }
        if (request.selectionMode == CatalogSelectionMode.SINGLE && selectedExerciseIds.size != 1) return false
        if (contractVersion == LEGACY_CONTRACT_VERSION && commitAction != CatalogCommitAction.ADD) return false
        when (commitAction) {
            CatalogCommitAction.ADD -> if (supersetConfig != null) return false
            CatalogCommitAction.CREATE_SUPERSET -> {
                if (selectedExerciseIds.size < 2) return false
                val canCreateSuperset = request.origin in setOf(
                    CatalogLaunchOrigin.SESSION_EDITOR,
                    CatalogLaunchOrigin.LIVE_SESSION,
                ) && request.selectionMode == CatalogSelectionMode.MULTIPLE
                if (!canCreateSuperset) return false
            }
        }
        supersetConfig?.let { config ->
            if (config.rounds < 1 || config.restBetweenExercisesSeconds < 0 || config.restAfterSupersetSeconds < 0) return false
        }
        return true
    }
}

private const val LEGACY_CONTRACT_VERSION = 1
private const val CURRENT_CONTRACT_VERSION = 2

/** Keys intentionally remain constant so a request survives recomposition and rotation. */
object CatalogSavedStateKeys {
    const val REQUEST = "kpkn.catalog.request"
    const val RESULT = "kpkn.catalog.result"

    fun request(requestId: String): String = "$REQUEST.$requestId"
}
