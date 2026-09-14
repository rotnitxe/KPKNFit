package com.example.kpkn.screens.workout

import com.example.kpkn.domain.relator.RelatorActionSpec
import com.example.kpkn.domain.relator.RelatorCandidate
import com.example.kpkn.domain.relator.RelatorEngine
import com.example.kpkn.domain.relator.RelatorEngineResult
import com.example.kpkn.domain.relator.RelatorLongTermMemory
import com.example.kpkn.domain.relator.RelatorSelectorState
import com.example.kpkn.domain.relator.RelatorTopic

internal object RelatorReactionObserver {
    fun observe(snapshot: LiveRelatorSnapshot, previousText: String?): List<RelatorCandidate> {
        val resolution = WorkoutLiveRelator.resolve(snapshot, previousText)
        if (!snapshot.visible || snapshot.phase == RelatorPhase.HIDDEN) return emptyList()
        val bucket = snapshot.speechBucket()
        if (bucket.isAssist) return emptyList()
        val topic = when {
            resolution.holdPrevious || snapshot.lastChangedField.isReaction -> RelatorTopic.REACTION
            bucket == RelatorSpeechBucket.PR || bucket == RelatorSpeechBucket.PR_STAR -> RelatorTopic.MILESTONE
            else -> RelatorTopic.SITUATE
        }
        val text = resolution.text?.trim().orEmpty()
        if (text.isEmpty() && !resolution.holdPrevious) return emptyList()
        return listOf(
            RelatorCandidate(
                topic = topic,
                priority = when (topic) {
                    RelatorTopic.REACTION -> 96
                    RelatorTopic.MILESTONE -> 84
                    else -> 12
                },
                relevance = 0.8,
                lines = listOf(text.ifEmpty { previousText.orEmpty() }),
                fingerprintBase = resolution.fingerprint ?: bucket.name,
                actions = resolution.actions.map { it.toSpec() },
                isReaction = topic == RelatorTopic.REACTION,
                holdPrevious = resolution.holdPrevious,
                spokenConceptId = resolution.spokenConceptId,
                phaseKey = resolution.phaseKey,
            ),
        )
    }
}

internal object RelatorStructureAssistObserver {
    fun observe(snapshot: LiveRelatorSnapshot): List<RelatorCandidate> {
        val offer = snapshot.assistOffer ?: return emptyList()
        if (snapshot.assistAck != null && snapshot.lastChangedField == RelatorChangedField.NONE && snapshot.idleCycle == 0) {
            return emptyList()
        }
        val text = WorkoutLiveRelator.resolve(snapshot).text?.trim().orEmpty().ifEmpty { offer.text }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.STRUCTURE_ASSIST,
                priority = 90,
                relevance = 1.0,
                lines = listOf(text),
                fingerprintBase = offer.stickyKey,
                actions = offer.actions.map { it.toSpec() },
                isReaction = true,
                phaseKey = offer.kind.speechBucket.phaseKey(),
            ),
        )
    }
}

internal fun RelatorAssistAction.toSpec(): RelatorActionSpec = RelatorActionSpec(
    kind = kind.name,
    label = label,
    exerciseId = exerciseId,
    setIndex = setIndex,
    side = side,
    mobilityId = mobilityId,
    span = span,
    weightKg = weightKg,
    restSeconds = restSeconds,
    loadDeltaPercent = loadDeltaPercent,
)

internal fun RelatorActionSpec.toAssistAction(): RelatorAssistAction {
    val mapped = runCatching { RelatorAssistActionKind.valueOf(kind) }.getOrNull()
        ?: RelatorAssistActionKind.OPEN_HISTORY
    return RelatorAssistAction(
        kind = mapped,
        label = label,
        exerciseId = exerciseId,
        setIndex = setIndex,
        side = side,
        mobilityId = mobilityId,
        span = span,
        weightKg = weightKg,
        restSeconds = restSeconds,
        loadDeltaPercent = loadDeltaPercent,
    )
}

internal object WorkoutRelatorEngine {
    fun resolve(
        context: com.example.kpkn.domain.relator.RelatorContext,
        snapshot: LiveRelatorSnapshot,
        selectorState: RelatorSelectorState,
        longTermMemory: RelatorLongTermMemory,
        previousText: String?,
    ): RelatorEngineResult {
        val extra = RelatorReactionObserver.observe(snapshot, previousText) +
            RelatorStructureAssistObserver.observe(snapshot)
        return RelatorEngine.resolve(
            context = context,
            extraCandidates = extra,
            selectorState = selectorState,
            longTermMemory = longTermMemory,
            previousText = previousText,
        )
    }

    fun toUiResolution(result: RelatorEngineResult): RelatorResolution {
        val line = result.line
        return RelatorResolution(
            text = line.text,
            holdPrevious = line.holdPrevious,
            phaseKey = line.phaseKey,
            actions = line.actions.map { it.toAssistAction() },
            fingerprint = line.fingerprint,
            spokenConceptId = line.spokenConceptId,
            topic = line.topic?.name,
        )
    }
}
