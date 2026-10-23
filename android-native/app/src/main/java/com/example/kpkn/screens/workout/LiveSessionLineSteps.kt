package com.example.kpkn.screens.workout

/** Presentation nodes retain the original pager indices, preparation callbacks, sides and rounds. */
internal data class LiveSessionLineStep(
    val key: String,
    val label: String,
    val description: String,
    val active: Boolean,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val volumeReplaced: Boolean = false,
    val pageIndex: Int? = null,
    val preparationSelect: (() -> Unit)? = null,
    val preparationProgress: Float? = null,
    val calibratedLoad: Double? = null,
)

internal fun liveSessionLineSteps(snapshot: LiveSetStepperSnapshot, activePageIndex: Int?): List<LiveSessionLineStep> = buildList {
    snapshot.elements.forEachIndexed { timelineIndex, element ->
        val selectedElement = timelineIndex == snapshot.activeElementIndex
        when (element) {
            is TimelineElement.RestPill -> add(LiveSessionLineStep(
                key = "rest-$timelineIndex-${element.pageIndex}", label = element.remainingLabel,
                description = "Descanso", active = activePageIndex?.let { it == element.pageIndex } ?: selectedElement,
                pageIndex = element.pageIndex, preparationProgress = element.progress,
            ))
            is TimelineElement.MobilityPill -> add(LiveSessionLineStep(
                key = "mobility-$timelineIndex", label = "MOV", description = "Movilidad",
                active = selectedElement || element.isCurrent, completed = element.isCompleted,
                preparationSelect = element.onSelect, preparationProgress = element.progress,
            ))
            is TimelineElement.WarmupPill -> add(LiveSessionLineStep(
                key = "warmup-$timelineIndex", label = "APROX", description = "Aproximación",
                active = selectedElement || element.isCurrent, completed = element.isCompleted,
                preparationSelect = element.onSelect, preparationProgress = element.progress,
            ))
            is TimelineElement.RoundBadge -> add(LiveSessionLineStep(
                key = "round-${element.roundIndex}", label = "R${element.roundIndex + 1}",
                description = "Ronda ${element.roundIndex + 1}", active = selectedElement,
                completed = element.isAllDone, pageIndex = element.firstPageIndex,
            ))
            is TimelineElement.BilateralSet -> add(LiveSessionLineStep(
                key = "bilateral-$timelineIndex-${element.pageIndex}", label = element.label,
                description = seriesRoundDescription(element.label, element.roundIndex),
                active = activePageIndex?.let { it == element.pageIndex } ?: selectedElement,
                completed = element.state == WorkoutSetCardVisualState.COMPLETED,
                skipped = element.state == WorkoutSetCardVisualState.SKIPPED,
                volumeReplaced = element.volumeReplaced, pageIndex = element.pageIndex,
            ))
            is TimelineElement.UnilateralSet -> {
                val label = element.setLabel ?: "S"
                // Both sides retain separate destinations, including when only one side exists.
                element.leftPageIndex?.let { page -> add(LiveSessionLineStep(
                    key = "unilateral-$timelineIndex-left-$page", label = "$label L",
                    description = seriesRoundDescription("$label, lado izquierdo", element.roundIndex),
                    active = activePageIndex?.let { it == page } ?:
                        (selectedElement && (element.leftState == WorkoutSetCardVisualState.ACTIVE || element.rightPageIndex == null)),
                    completed = element.leftState == WorkoutSetCardVisualState.COMPLETED,
                    skipped = element.leftState == WorkoutSetCardVisualState.SKIPPED,
                    volumeReplaced = element.volumeReplaced, pageIndex = page,
                )) }
                element.rightPageIndex?.let { page -> add(LiveSessionLineStep(
                    key = "unilateral-$timelineIndex-right-$page", label = "$label R",
                    description = seriesRoundDescription("$label, lado derecho", element.roundIndex),
                    active = activePageIndex?.let { it == page } ?:
                        (selectedElement && (element.rightState == WorkoutSetCardVisualState.ACTIVE || element.leftPageIndex == null)),
                    completed = element.rightState == WorkoutSetCardVisualState.COMPLETED,
                    skipped = element.rightState == WorkoutSetCardVisualState.SKIPPED,
                    volumeReplaced = element.volumeReplaced, pageIndex = page,
                )) }
            }
        }
    }
}

private fun seriesRoundDescription(label: String, round: Int?): String =
    if (round == null) label else "Ronda ${round + 1}, $label"
