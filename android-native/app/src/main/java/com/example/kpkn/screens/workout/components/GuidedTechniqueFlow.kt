package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.domain.workout.SetTechniqueScope
import com.example.kpkn.domain.workout.techniqueScope
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
    if (techniqueScope() != SetTechniqueScope.STACKED_ON_SET) return null
    val drop = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
    val rest = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }
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
    if (technique?.params?.get("weightDropKg")?.toDoubleOrNull() != null) {
        return emptyList()
    }
    val raw = technique?.params?.get("weightPcts")
        ?: DropSetPlanDefaults.weightPctsFor(DropSetPlanDefaults.DefaultDrops)
    return raw.split(",")
        .mapNotNull { it.trim().toDoubleOrNull() }
        .ifEmpty { emptyList() }
}

/**
 * Suggests drop weight for ~3 reps from the main set: about 5 kg less
 * per drop so the load stays close to the working set.
 */
internal fun suggestedDropLoadsForMainSet(
    mainWeight: Double,
    mainReps: Int,
    count: Int = DropSetPlanDefaults.DefaultDrops,
): List<Double> {
    val clamped = count.coerceIn(DropSetPlanDefaults.MinDrops, DropSetPlanDefaults.MaxDrops)
    return (0 until clamped).map { index ->
        suggestDropWeightForThreeReps(mainWeight, mainReps, index, emptyList())
    }
}

internal fun shouldAutoCommitTechniqueRows(doneFlags: List<Boolean>): Boolean =
    doneFlags.isNotEmpty() && doneFlags.all { it }

internal fun suggestDropWeightForThreeReps(
    mainWeight: Double,
    mainReps: Int,
    dropIndex: Int,
    dropPcts: List<Double>,
): Double {
    if (mainWeight <= 0.0) return 0.0
    val fromKg = mainWeight - DropSetPlanDefaults.DropKg * (dropIndex + 1)
    val pct = dropPcts.getOrNull(dropIndex)
    val fromPct = if (pct != null && pct > -15.0) {
        mainWeight * (1.0 + pct / 100.0)
    } else {
        fromKg
    }
    val candidate = min(fromKg, fromPct)
        .coerceAtMost(mainWeight - 0.5)
        .coerceAtLeast(0.5)
    return LoadSuggestionEngine.roundLoad(candidate)
}
