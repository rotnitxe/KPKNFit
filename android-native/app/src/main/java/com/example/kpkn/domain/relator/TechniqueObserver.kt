package com.example.kpkn.domain.relator

object TechniqueObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        val cues = context.executionCues.map { it.trim() }.filter { it.isNotEmpty() }
        val conceptLines = context.conceptLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (cues.isEmpty() && conceptLines.isEmpty()) return emptyList()
        val lines = (cues.take(4) + conceptLines.take(4)).distinct()
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.TECHNIQUE,
                priority = 32,
                relevance = 0.5,
                lines = padVariants(lines.map { line ->
                    if (line.contains("{ex}")) line else "$line {ex}.".trim()
                }),
                fingerprintBase = "TECHNIQUE:${context.progress.completedWorkingSets}",
                actions = listOf(
                    RelatorActionSpec(
                        kind = RelatorActionKind.OPEN_TECHNIQUE,
                        label = "Ver técnica",
                        span = "Ver técnica",
                        exerciseId = context.exerciseId,
                    ),
                ),
                cooldownSets = 3,
                spokenConceptId = context.conceptId,
                openers = listOf("Recuerda", "Ojo"),
            ),
        )
    }
}
