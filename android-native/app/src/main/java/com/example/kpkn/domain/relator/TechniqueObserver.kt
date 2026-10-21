package com.example.kpkn.domain.relator

/**
 * Técnica (LR1h): la frase va tal cual. El nombre del ejercicio ya está en la cabecera del relator, así que no se pega
 * al final como frase suelta.
 */
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
                lines = realVariants(lines),
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
            ),
        )
    }
}
