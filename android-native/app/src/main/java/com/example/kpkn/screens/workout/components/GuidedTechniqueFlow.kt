package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateWeightFrom1RM
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import kotlin.math.min

internal sealed class GuidedTechniquePhase {
    data class DropSet(
        val index: Int,
        val total: Int,
        val suggestedWeight: Double,
    ) : GuidedTechniquePhase()

    data class RestPauseCountdown(
        val index: Int,
        val total: Int,
        val secondsLeft: Int,
    ) : GuidedTechniquePhase()

    data class RestPauseReps(
        val index: Int,
        val total: Int,
    ) : GuidedTechniquePhase()
}

internal data class GuidedMainCapture(
    val loadMode: com.example.kpkn.data.models.LoadModeV2,
    val unitMode: com.example.kpkn.data.models.UnitModeV2,
    val weight: Double,
    val value: Double,
    val intensity: Double?,
    val amrapOverride: Boolean,
    val bodyWeight: Double?,
    val side: String?,
)

internal data class PlannedTechniqueGuide(
    val kind: TechniqueType,
    val count: Int,
    val dropPcts: List<Double>,
)

internal fun ExerciseSet.resolvePlannedTechniqueGuide(): PlannedTechniqueGuide? {
    val drop = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
    if (drop != null || isDropSet) {
        val count = (drop?.params?.get("count")?.toIntOrNull()
            ?: drop?.params?.get("weightPcts")?.split(",")?.size
            ?: DropSetPlanDefaults.DefaultDrops)
            .coerceIn(DropSetPlanDefaults.MinDrops, DropSetPlanDefaults.MaxDrops)
        val pcts = parseDropPcts(drop)
        return PlannedTechniqueGuide(
            kind = TechniqueType.DROP_SET,
            count = count,
            dropPcts = pcts,
        )
    }
    val rest = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }
    if (rest != null || isRestPause) {
        val count = (rest?.params?.get("count")?.toIntOrNull() ?: RestPausePlanDefaults.DefaultCount)
            .coerceIn(RestPausePlanDefaults.MinCount, RestPausePlanDefaults.MaxCount)
        return PlannedTechniqueGuide(
            kind = TechniqueType.REST_PAUSE,
            count = count,
            dropPcts = emptyList(),
        )
    }
    return null
}

private fun parseDropPcts(technique: PlannedTechnique?): List<Double> {
    val raw = technique?.params?.get("weightPcts")
        ?: DropSetPlanDefaults.weightPctsFor(DropSetPlanDefaults.DefaultDrops)
    return raw.split(",")
        .mapNotNull { it.trim().toDoubleOrNull() }
        .ifEmpty { listOf(-20.0) }
}

/**
 * Suggests drop weight for ~3 reps from the main set performance,
 * progressive with planned % reductions, never heavier than the main set.
 */
internal fun suggestDropWeightForThreeReps(
    mainWeight: Double,
    mainReps: Int,
    dropIndex: Int,
    dropPcts: List<Double>,
): Double {
    if (mainWeight <= 0.0) return 0.0
    val reps = mainReps.coerceAtLeast(1)
    val e1rm = calculateHybrid1RM(mainWeight, reps)
    val capacityFor3 = calculateWeightFrom1RM(e1rm, RestPausePlanDefaults.Reps)
    val pct = dropPcts.getOrNull(dropIndex) ?: dropPcts.lastOrNull() ?: -20.0
    val fromWorking = mainWeight * (1.0 + pct / 100.0)
    val candidate = min(capacityFor3.takeIf { it > 0.0 } ?: fromWorking, fromWorking)
        .coerceAtMost(mainWeight * 0.95)
        .coerceAtLeast(0.5)
    return LoadSuggestionEngine.roundLoad(candidate)
}
