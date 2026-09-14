package com.example.kpkn.domain.relator

object RelatorEngine {
    fun defaultObservers(): List<RelatorObserver> = listOf(
        PlanObserver,
        HistoryObserver,
        SessionProgressObserver,
        RestObserver,
        ReadinessObserver,
        TechniqueObserver,
        MilestoneObserver,
        WarmupObserver,
        CoachObserver,
        MediaObserver,
    )

    fun resolve(
        context: RelatorContext,
        extraCandidates: List<RelatorCandidate> = emptyList(),
        selectorState: RelatorSelectorState = RelatorSelectorState(),
        longTermMemory: RelatorLongTermMemory = RelatorLongTermMemory(),
        previousText: String? = null,
        observers: List<RelatorObserver> = defaultObservers(),
        nowEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
    ): RelatorEngineResult {
        if (!context.visible || context.phase == RelatorSessionPhase.HIDDEN) {
            return RelatorEngineResult(
                line = RelatorLine(text = null, holdPrevious = false, phaseKey = "hidden"),
                selectorState = selectorState,
                longTermMemory = longTermMemory,
            )
        }
        val observed = observers.flatMap { observer -> observer.observe(context) }
        val candidates = extraCandidates + observed
        val selected = RelatorSelector.select(
            candidates = candidates,
            state = selectorState,
            context = context,
            longTerm = longTermMemory,
            nowEpochDay = nowEpochDay,
        )
        if (selected == null) {
            return RelatorEngineResult(
                line = RelatorLine(text = null, holdPrevious = false, phaseKey = context.phase.name.lowercase()),
                selectorState = selectorState,
                longTermMemory = longTermMemory,
            )
        }
        val (line, nextMemory) = RelatorComposer.compose(
            candidate = selected,
            context = context,
            state = selectorState,
            longTerm = longTermMemory,
            previousText = previousText,
        )
        val committedMemory = line.spokenConceptId
            ?.let { nextMemory.recordConcept(it, nowEpochDay).bumpExercise(context.exerciseId) }
            ?: nextMemory.bumpExercise(context.exerciseId)
        val nextState = if (line.holdPrevious || line.fingerprint.isNullOrBlank()) {
            selectorState
        } else {
            selectorState.record(
                fingerprint = line.fingerprint,
                topic = selected.topic,
                setKey = context.setKey,
                conceptId = line.spokenConceptId,
                exerciseId = context.exerciseId,
            )
        }
        return RelatorEngineResult(
            line = line,
            selectorState = nextState,
            longTermMemory = committedMemory,
        )
    }
}
