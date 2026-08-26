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
    var action: (() -> Unit)? = null
    val isArmed: Boolean get() = action != null
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
