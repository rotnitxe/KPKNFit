package com.example.kpkn.screens.workout.components

import com.example.kpkn.screens.workout.WorkoutTechniqueDraftKind
import com.example.kpkn.screens.workout.WorkoutTechniqueMainCaptureDraft
import com.example.kpkn.screens.workout.WorkoutTechniqueProgressDraft

internal fun GuidedMainCapture.toDurableDraft(): WorkoutTechniqueMainCaptureDraft =
    WorkoutTechniqueMainCaptureDraft(
        loadMode = loadMode,
        unitMode = unitMode,
        weight = weight,
        value = value,
        intensity = intensity,
        amrapOverride = amrapOverride,
        bodyWeight = bodyWeight,
        side = side,
    )

/** Returns null for older or incomplete serialized payloads instead of trusting missing fields. */
internal fun WorkoutTechniqueMainCaptureDraft?.toGuidedMainCaptureOrNull(): GuidedMainCapture? {
    val saved = this ?: return null
    return GuidedMainCapture(
        loadMode = saved.loadMode ?: return null,
        unitMode = saved.unitMode ?: return null,
        weight = saved.weight ?: return null,
        value = saved.value ?: return null,
        intensity = saved.intensity,
        amrapOverride = saved.amrapOverride ?: return null,
        bodyWeight = saved.bodyWeight,
        side = saved.side,
    )
}

internal fun WorkoutTechniqueProgressDraft.toGuidedPhaseOrNull(): GuidedTechniquePhase? {
    return when (kind) {
        WorkoutTechniqueDraftKind.GUIDED_DROP -> {
            val total = phaseCount?.coerceAtLeast(1) ?: return null
            val index = (phaseIndex ?: 0).coerceIn(0, total - 1)
            GuidedTechniquePhase.DropSet(
                index = index,
                total = total,
                suggestedWeight = dropWeightText?.toDoubleOrNull()
                    ?: dropRows?.lastOrNull()?.weight
                    ?: 0.0,
            )
        }
        WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE -> {
            val total = phaseCount?.coerceAtLeast(1) ?: return null
            val index = (phaseIndex ?: 0).coerceIn(0, total - 1)
            val secondsLeft = restRemainingSeconds?.coerceAtLeast(0) ?: 0
            if (secondsLeft > 0) {
                GuidedTechniquePhase.RestPauseCountdown(index = index, total = total, secondsLeft = secondsLeft)
            } else {
                GuidedTechniquePhase.RestPauseReps(index = index, total = total)
            }
        }
        WorkoutTechniqueDraftKind.SCHEDULED_DROP,
        WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE,
        null -> null
    }
}
