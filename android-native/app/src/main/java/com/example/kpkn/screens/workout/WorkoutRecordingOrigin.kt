package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet

/** Correcting actual values does not move an observation into today's prescription or setup. */
internal fun preserveRecordingOrigin(previous: CompletedSet?, next: CompletedSet): CompletedSet {
    if (previous == null) return next
    val old = previous.recordedPayloadV3
    val context = previous.setOutcomeV2?.contextKey ?: previous.homologatedResultV3?.contextKey
    return next.copy(
        sourceRef = previous.sourceRef ?: next.sourceRef,
        recordedAtMs = previous.recordedAtMs,
        contextProfileId = previous.contextProfileId,
        contextKey = previous.contextKey,
        tagId = previous.tagId, tagName = previous.tagName, subTagIds = previous.subTagIds,
        setupProfileId = previous.setupProfileId, machineBrand = previous.machineBrand,
        recordedPayloadV3 = next.recordedPayloadV3?.let { payload ->
            payload.copy(contextProfileId = old?.contextProfileId,
                exerciseDbId = old?.exerciseDbId ?: payload.exerciseDbId,
                bodyWeightSnapshot = old?.bodyWeightSnapshot ?: payload.bodyWeightSnapshot,
                plannedRepRange = old?.plannedRepRange,
                plannedTargetSnapshot = old?.plannedTargetSnapshot ?: previous.setOutcomeV2?.plannedTarget)
        },
        setOutcomeV2 = next.setOutcomeV2?.let { it.copy(contextKey = context ?: it.contextKey) },
        homologatedResultV3 = next.homologatedResultV3?.let { it.copy(contextKey = context ?: it.contextKey) },
    )
}
