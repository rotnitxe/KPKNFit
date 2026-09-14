package com.example.kpkn.domain.relator

object HistoryObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        val history = context.history
        if (history.lastSessionSets.isEmpty() && history.bestEstimatedRmKg <= 0.0) return emptyList()
        val lines = buildList {
            if (history.lastSessionSets.isNotEmpty()) {
                val summary = history.lastSessionSets.take(4).joinToString(" · ") { set ->
                    "${formatRelatorKg(set.weightKg)}×${set.reps}"
                }
                add("Última vez {ex} (serie ${context.setIndex + 1}): $summary.")
                add("La sesión previa de {ex} cerró $summary.")
                val first = history.lastSessionSets.first()
                add("Arrancaste {ex} a ${formatRelatorKg(first.weightKg)}×${first.reps} la última vez.")
            }
            if (history.trendThreeSessionsKg.size >= 2) {
                val trend = history.trendThreeSessionsKg.joinToString(" → ") { formatRelatorKg(it) }
                add("Tendencia de {ex} (3 sesiones): $trend kg.")
                val delta = history.trendThreeSessionsKg.last() - history.trendThreeSessionsKg.first()
                add(
                    if (delta >= 0) {
                        "{ex} lleva ${formatRelatorKg(delta)} kg de subida en 3 sesiones."
                    } else {
                        "{ex} bajó ${formatRelatorKg(-delta)} kg en 3 sesiones; hoy prioriza calidad."
                    },
                )
            }
            history.distanceToBestKg?.let { gap ->
                val best = formatRelatorKg(history.bestEstimatedRmKg)
                add(
                    if (gap <= 0.0) {
                        "Estás en tu mejor marca de {ex} (~$best kg e1RM)."
                    } else {
                        "A ${formatRelatorKg(gap)} kg de tu mejor marca de {ex} (~$best kg)."
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
                openers = listOf("Dato", "Hoy"),
            ),
        )
    }
}
