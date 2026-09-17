package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.UnitModeV2

/**
 * Defaults shared by the editor, live execution and the pure resolver.  Keep
 * these values in one place so a scheduled technique cannot silently diverge
 * between the editor and the recorder.
 */
object ScheduledTechniqueDefaults {
    const val DROP_KG = 5.0
    const val REST_PAUSE_SECONDS = 15
    const val FOLLOW_UP_REPS = 3
}

enum class ScheduledTechniqueKind {
    DROP_SET,
    REST_PAUSE,
}

/** Normalized parameters for an editor-programmed (volume-replaced) chain. */
data class ScheduledTechniquePlan(
    val kind: ScheduledTechniqueKind,
    val followUpCount: Int,
    val pauseSeconds: Int,
    val followUpReps: Int,
    val dropKg: Double,
) {
    val phaseCount: Int get() = followUpCount + 1
}

/**
 * Result of resolving one phase of a scheduled technique.  [phaseIndex] is
 * zero based (the normal/main phase is 0), and [phaseCount] includes that
 * main phase.  A null [nextLoad] means this is the final phase or the load
 * mode does not support a numeric prescription.
 */
data class ScheduledTechniqueExecution(
    val kind: ScheduledTechniqueKind,
    val scope: SetTechniqueScope,
    val phaseIndex: Int,
    val phaseCount: Int,
    val restAfterSeconds: Int,
    val currentLoad: Double?,
    val nextLoad: Double?,
    val targetReps: Int?,
    val unitMode: UnitModeV2,
    val plannedIntensityMode: IntensityMode?,
    val plannedIntensityValue: Double?,
    val actualIntensityMode: IntensityMode? = null,
    val actualIntensityValue: Double? = null,
) {
    val hasFollowUp: Boolean get() = phaseIndex < phaseCount - 1
}

/** True only for the explicit editor/marked contract, never for manual UI techniques. */
fun ExerciseSet.isEditorScheduledTechnique(): Boolean =
    techniqueScope() == SetTechniqueScope.VOLUME_REPLACED &&
        plannedIntensityTechniques.any {
            it.type == TechniqueType.DROP_SET || it.type == TechniqueType.REST_PAUSE
        }

/**
 * Inline editor chips carry `weightPcts` (drops) or an unassigned
 * `restAfterSeconds` (rest-pause).  Marked chains produced by UltraFast use
 * `weightDropKg`/a concrete rest and already represent one phase per set, so
 * they must not be expanded into another in-card mini-series.
 */
fun ExerciseSet.isInlineEditorScheduledTechnique(): Boolean {
    if (!isEditorScheduledTechnique()) return false
    val technique = plannedIntensityTechniques.firstOrNull {
        it.type == TechniqueType.DROP_SET || it.type == TechniqueType.REST_PAUSE
    } ?: return false
    return when (technique.type) {
        TechniqueType.DROP_SET -> technique.params.containsKey("weightPcts")
        TechniqueType.REST_PAUSE -> restAfterSeconds == null
        else -> false
    }
}

/**
 * Deferred compatibility pass for sessions saved before `betweenMarked` was
 * introduced.  It is intentionally called only at an editor boundary; live
 * workouts already in progress are not rewritten.
 */
fun ExerciseSet.normalizeEditorScheduledTechnique(): ExerciseSet {
    val drop = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
    val rest = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }
    val hasDrop = drop != null || isDropSet
    val hasRest = rest != null || isRestPause
    if (!hasDrop && !hasRest) return this
    val normalized = buildList {
        plannedIntensityTechniques
            .filterNot { it.type == TechniqueType.DROP_SET || it.type == TechniqueType.REST_PAUSE }
            .forEach(::add)
        if (hasDrop) {
            val source = drop ?: PlannedTechnique(
                id = "scheduled-drop-${id}",
                type = TechniqueType.DROP_SET,
                params = mapOf("count" to dropSets.size.coerceAtLeast(1).toString()),
            )
            add(source.copy(params = source.params + ("betweenMarked" to "true")))
        } else if (hasRest) {
            val source = rest ?: PlannedTechnique(
                id = "scheduled-rest-${id}",
                type = TechniqueType.REST_PAUSE,
                params = mapOf("count" to restPauses.size.coerceAtLeast(1).toString()),
            )
            add(source.copy(params = source.params + ("betweenMarked" to "true")))
        }
    }
    return copy(
        plannedIntensityTechniques = normalized,
        isDropSet = hasDrop,
        isRestPause = hasRest,
    )
}

private fun List<com.example.kpkn.data.models.Exercise>.normalizeEditorScheduledTechniques() =
    map { exercise ->
        exercise.copy(sets = exercise.sets.map(ExerciseSet::normalizeEditorScheduledTechnique))
    }

/** Apply the deferred editor compatibility pass to every session variant. */
fun Session.normalizeEditorScheduledTechniques(): Session = copy(
    exercises = exercises.normalizeEditorScheduledTechniques(),
    parts = parts.map { part -> part.copy(exercises = part.exercises.normalizeEditorScheduledTechniques()) },
    sessionB = sessionB?.normalizeEditorScheduledTechniques(),
    sessionC = sessionC?.normalizeEditorScheduledTechniques(),
    sessionD = sessionD?.normalizeEditorScheduledTechniques(),
)

/**
 * Reads the existing PlannedTechnique params without creating a second
 * persistence shape. Legacy flags/lists still resolve as volume-replaced when
 * they already carry concrete phases; unmarked empty techniques remain manual.
 */
fun ExerciseSet.scheduledTechniquePlan(): ScheduledTechniquePlan? {
    if (techniqueScope() != SetTechniqueScope.VOLUME_REPLACED) return null
    val drop = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
    val rest = plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }
    val kind = when {
        drop != null || isDropSet -> ScheduledTechniqueKind.DROP_SET
        rest != null || isRestPause -> ScheduledTechniqueKind.REST_PAUSE
        else -> return null
    }
    val params = drop?.params ?: rest?.params.orEmpty()
    val concreteCount = when (kind) {
        ScheduledTechniqueKind.DROP_SET -> dropSets.size
        ScheduledTechniqueKind.REST_PAUSE -> restPauses.size
    }
    val count = (params["count"]?.toIntOrNull() ?: concreteCount)
        .coerceIn(1, 5)
    val pause = (params["pauseSeconds"]?.toIntOrNull()
        ?: restPauses.firstOrNull()?.restTime
        ?: ScheduledTechniqueDefaults.REST_PAUSE_SECONDS)
        .coerceAtLeast(0)
    val reps = (params["reps"]?.toIntOrNull()
        ?: when (kind) {
            ScheduledTechniqueKind.DROP_SET -> params["dropReps"]?.toIntOrNull()
            ScheduledTechniqueKind.REST_PAUSE -> restPauses.firstOrNull()?.reps
        }
        ?: ScheduledTechniqueDefaults.FOLLOW_UP_REPS)
        .coerceAtLeast(1)
    return ScheduledTechniquePlan(
        kind = kind,
        followUpCount = count,
        pauseSeconds = pause,
        followUpReps = reps,
        dropKg = (params["dropKg"]?.toDoubleOrNull()
            ?: params["weightDropKg"]?.toDoubleOrNull()
            ?: ScheduledTechniqueDefaults.DROP_KG).coerceAtLeast(0.0),
    )
}

/**
 * Resolve the transition/rest/load policy for a phase.  The final phase uses
 * the session's ordinary rest; a technique never leaks an extra technical
 * pause after its chain is complete.
 */
fun ExerciseSet.resolveScheduledTechniqueExecution(
    phaseIndex: Int = 0,
    currentLoad: Double? = weight,
    normalRestSeconds: Int,
    unitMode: UnitModeV2 = unitModeV2 ?: UnitModeV2.REPS,
    phaseCountOverride: Int? = null,
    actualIntensityMode: IntensityMode? = null,
    actualIntensityValue: Double? = null,
): ScheduledTechniqueExecution? {
    val plan = scheduledTechniquePlan() ?: return null
    val resolvedPhaseCount = (phaseCountOverride ?: plan.phaseCount).coerceAtLeast(1)
    val safeIndex = phaseIndex.coerceIn(0, resolvedPhaseCount - 1)
    val hasFollowUp = safeIndex < resolvedPhaseCount - 1
    val nextLoad = if (!hasFollowUp || currentLoad == null) {
        null
    } else {
        when (plan.kind) {
            ScheduledTechniqueKind.DROP_SET ->
                (currentLoad - plan.dropKg).coerceAtLeast(0.0)
            ScheduledTechniqueKind.REST_PAUSE -> currentLoad
        }
    }
    val plannedMode = when {
        isAmrap || intensityMode == IntensityMode.AMRAP -> IntensityMode.AMRAP
        targetRIR != null -> IntensityMode.RIR
        targetRPE != null -> IntensityMode.RPE
        isFailure || intensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
        targetPercentageRM != null -> IntensityMode.SOLO_RM
        else -> intensityMode
    }
    val plannedValue = when (plannedMode) {
        IntensityMode.RIR -> targetRIR?.toDouble()
        IntensityMode.RPE -> targetRPE
        IntensityMode.SOLO_RM -> targetPercentageRM
        else -> null
    }
    return ScheduledTechniqueExecution(
        kind = plan.kind,
        scope = SetTechniqueScope.VOLUME_REPLACED,
        phaseIndex = safeIndex,
        phaseCount = resolvedPhaseCount,
        restAfterSeconds = if (hasFollowUp) {
            when (plan.kind) {
                ScheduledTechniqueKind.DROP_SET -> 0
                ScheduledTechniqueKind.REST_PAUSE -> plan.pauseSeconds
            }
        } else {
            normalRestSeconds.coerceAtLeast(0)
        },
        currentLoad = currentLoad,
        nextLoad = nextLoad,
        targetReps = if (safeIndex == 0) targetReps ?: targetRepsRange?.max else plan.followUpReps,
        unitMode = unitMode,
        plannedIntensityMode = plannedMode,
        plannedIntensityValue = plannedValue,
        actualIntensityMode = actualIntensityMode,
        actualIntensityValue = actualIntensityValue,
    )
}
