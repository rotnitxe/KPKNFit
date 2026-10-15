package com.example.kpkn.domain.relator

object HistoryObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        val history = context.history
        if (history.lastSessionSets.isEmpty() && history.bestEstimatedRmKg <= 0.0) return emptyList()
        val lines = buildList {
            if (history.lastSessionSets.isNotEmpty()) {
                val summary = history.lastSessionSets.take(4).joinToString(" · ") { set ->
                    relatorSetMark(set.weightKg, set.reps)
                }
                add("La última vez de {ex}, en la serie ${context.setIndex + 1}: $summary.")
                add("La sesión previa de {ex} cerró con $summary.")
                val first = history.lastSessionSets.first()
                add("Arrancaste {ex} con ${relatorSetMark(first.weightKg, first.reps)} la última vez.")
            }
            if (history.trendThreeSessionsKg.size >= 2) {
                val trend = history.trendThreeSessionsKg.joinToString(" → ") { formatRelatorKg(it) }
                add("Tendencia de {ex} en 3 sesiones: $trend kg.")
                val delta = history.trendThreeSessionsKg.last() - history.trendThreeSessionsKg.first()
                add(
                    if (delta >= 0) {
                        "{ex} sube ${relatorKg(delta)} en 3 sesiones."
                    } else {
                        "{ex} baja ${relatorKg(-delta)} en 3 sesiones; hoy prioriza la calidad."
                    },
                )
            }
            history.distanceToBestKg?.let { gap ->
                val best = formatRelatorKg(history.bestEstimatedRmKg)
                add(
                    if (gap <= 0.0) {
                        "Estás en tu mejor marca de {ex} (~$best kg e1RM)."
                    } else {
                        "A ${relatorKg(gap)} de tu mejor marca de {ex} (~$best kg)."
                    },
                )
            }
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.HISTORY,
                priority = 40,
                relevance = 0.7,
                lines = padVariants(lines),
                fingerprintBase = "HISTORY:${context.progress.completedWorkingSets}",
                actions = listOf(
                    RelatorActionSpec(
                        kind = RelatorActionKind.OPEN_HISTORY,
                        label = "Ver historial",
                        span = "Ver historial",
                        exerciseId = context.exerciseId,
                    ),
                ),
                cooldownSets = 2,
            ),
        )
    }
}
