package com.example.kpkn.screens.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.Session
import java.util.Locale

internal fun deduplicateCanonicalMuscles(muscleIds: List<String>): List<String> {
    val result = muscleIds.toMutableList()
    val toRemove = mutableSetOf<String>()
    for (id in result) {
        if (result.any { other -> other != id && other.startsWith("$id ") }) {
            toRemove.add(id)
        }
    }
    result.removeAll(toRemove)
    return result
}

internal val LOWER_SESSION_MUSCLE_KEYS = setOf(
    "cuadriceps",
    "isquiosurales",
    "gluteos",
    "aductores",
    "pantorrillas",
)

internal fun isUpperOnlyWorkoutSession(
    session: Session,
    exercises: List<Exercise>,
): Boolean {
    var upperCount = 0
    var lowerCount = 0
    var fullCount = 0

    exercises.forEach { ex ->
        when (resolveCatalogExerciseInfo(
            catalogConfigurationId = ex.catalogConfigurationId,
            exerciseDbId = ex.exerciseDbId,
            exerciseId = ex.exerciseId,
            exerciseName = ex.name,
        )?.bodyPart?.lowercase(Locale.ROOT)) {
            "upper" -> upperCount += 1
            "lower" -> lowerCount += 1
            "full" -> fullCount += 1
        }
    }

    if (upperCount > 0 && lowerCount == 0 && fullCount == 0) return true

    val normalizedLabel = normalizeWorkoutMuscleKey("${session.name} ${session.focus.orEmpty()}")
    val looksUpper = normalizedLabel.contains("tren superior") ||
        normalizedLabel.contains("upper") ||
        normalizedLabel.contains("torso")
    val looksLower = normalizedLabel.contains("tren inferior") ||
        normalizedLabel.contains("lower") ||
        normalizedLabel.contains("pierna")

    return upperCount == 0 && lowerCount == 0 && fullCount == 0 && looksUpper && !looksLower
}

internal fun buildWorkoutAchievementMessage(
    homologated: HomologatedPerformanceResult?,
): String? {
    homologated ?: return null
    return when {
        homologated.estimatedRm != null && homologated.trm != null && homologated.estimatedRm >= homologated.trm -> {
            "Meta RM superada · ${homologated.estimatedRm.toTrimmedNumberString()} kg"
        }
        else -> null
    }
}

internal class RecordActionHolder {
    private class Binding(
        val scopeOwner: Any,
        val instanceOwner: Any,
        val stepKey: String,
        val pageKey: String,
        var action: () -> Unit,
    )

    private var binding by mutableStateOf<Binding?>(null)

    /** Kept for callers that only need to invoke the currently bound action. */
    val action: (() -> Unit)? get() = binding?.action
    val isArmed: Boolean get() = binding != null

    /**
     * Bind an action to one page instance and one workout step. Updating the
     * callback for the same owner mutates the binding without publishing new
     * Compose state, so callbacks stay current without a recomposition loop.
     */
    fun bind(
        scopeOwner: Any,
        instanceOwner: Any,
        stepKey: String,
        pageKey: String,
        action: () -> Unit,
    ) {
        val current = binding
        if (
            current != null &&
            current.scopeOwner === scopeOwner &&
            current.instanceOwner === instanceOwner &&
            current.stepKey == stepKey &&
            current.pageKey == pageKey
        ) {
            current.action = action
        } else {
            binding = Binding(scopeOwner, instanceOwner, stepKey, pageKey, action)
        }
    }

    fun actionForPage(pageKey: String?): (() -> Unit)? {
        if (pageKey.isNullOrBlank()) return null
        return binding?.takeIf { it.pageKey == pageKey }?.action
    }

    /** Clears only the card instance or containing workout scope that owns it. */
    fun clearIfOwner(owner: Any): Boolean {
        val current = binding ?: return false
        if (current.instanceOwner !== owner && current.scopeOwner !== owner) return false
        binding = null
        return true
    }
}

/** FAB visibility driven from the live pager's settled page. */
internal class RecordFabHolder {
    var visible by mutableStateOf(false)
    var isUpdateMode by mutableStateOf(false)
    var activePageKey by mutableStateOf<String?>(null)
}

/** Gate for invoking the floating action from the currently settled page. */
internal fun canInvokeWorkoutRecordFab(
    hasActivePageAction: Boolean,
    isRecording: Boolean,
    isFinishing: Boolean,
    isCancelling: Boolean,
    startPersistenceError: String?,
    isComplete: Boolean,
    finishSheetOpen: Boolean,
): Boolean = hasActivePageAction &&
    !isRecording &&
    !isFinishing &&
    !isCancelling &&
    startPersistenceError == null &&
    !isComplete &&
    !finishSheetOpen

/** Stable identity shared by the settled pager page and its record action. */
internal fun workoutRecordPageKey(
    page: WorkoutSetSwipePage,
    fallbackExerciseId: String,
    supersetGroupId: String?,
): String? {
    val exerciseId = page.exerciseId ?: fallbackExerciseId
    return when (page.type) {
        LivePageType.NORMAL -> "$exerciseId:${page.setIndex}:${page.side ?: "B"}"
        LivePageType.WARMUP -> "${supersetGroupId ?: fallbackExerciseId}:warmup:phase"
        LivePageType.MOBILITY -> "${supersetGroupId ?: fallbackExerciseId}:mobility:phase"
        LivePageType.CARDIO,
        LivePageType.REST,
        -> null
    }
}

/** Gate for the live-session record FAB (working, warmup and mobility pages). */
internal fun shouldShowWorkoutRecordFab(
    pageType: LivePageType?,
    showingPostExerciseCard: Boolean,
    workingRestActive: Boolean,
    isCardio: Boolean,
): Boolean = (
    pageType == LivePageType.NORMAL ||
        pageType == LivePageType.WARMUP ||
        pageType == LivePageType.MOBILITY
    ) &&
    !showingPostExerciseCard &&
    !workingRestActive &&
    !isCardio

/** Opens readiness Adapt sheet from header into the active set card. */
internal class AdaptActionHolder {
    var open: (() -> Unit)? = null
}

/** Publishes live set-stepper args from WorkoutV2Body into WorkoutRoadmapBar. */
internal data class LiveSetStepperSnapshot(
    val elements: List<TimelineElement>,
    val activeElementIndex: Int,
    val completedCount: Int,
    val totalCount: Int,
    val sessionAccentColor: Color?,
    val canAddSet: Boolean,
)

internal class LiveSetStepperHolder {
    var snapshot by mutableStateOf<LiveSetStepperSnapshot?>(null)
    var onSelectPage: (Int) -> Unit = {}
    var onAddSet: (() -> Unit)? = null
    var onLongPressPage: ((Int) -> Unit)? = null
    var onNavigateAdjacentExercise: ((forward: Boolean) -> Unit)? = null
}

/** Pure gate for PR e1RM session milestones (no first-set-without-baseline). */
internal fun shouldRecordPrE1rmMilestone(
    e1rm: Double,
    historyBest: Double,
    sessionBestPrevious: Double,
): Boolean {
    val bestBaseline = maxOf(historyBest, sessionBestPrevious)
    if (bestBaseline <= 0.0) return false
    val minDelta = maxOf(0.5, bestBaseline * 0.01)
    return e1rm >= bestBaseline + minDelta
}
