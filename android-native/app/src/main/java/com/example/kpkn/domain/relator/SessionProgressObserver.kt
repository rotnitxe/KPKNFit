package com.example.kpkn.domain.relator

object SessionProgressObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        val progress = context.progress
        if (progress.totalWorkingSets <= 0) return emptyList()
        val done = progress.completedWorkingSets
        val total = progress.totalWorkingSets
        val remaining = (total - done).coerceAtLeast(0)
        val lines = buildList {
            add("Llevas $done de $total series. Quedan $remaining.")
            add("Progreso: $done/$total. {ex} es la pieza actual.")
            add("Sesión al ${((done.toDouble() / total) * 100).toInt()} %. Sigue {ex}.")
            progress.minutesAhead?.let { delta ->
                when {
                    delta > 1 -> add("Vas $delta min por delante del tiempo objetivo.")
                    delta < -1 -> add("Vas ${-delta} min por detrás; aprieta descansos sin perder {ex}.")
                    else -> add("Ritmo calzado al tiempo de la sesión.")
                }
            }
            progress.remainingSeconds?.let { sec ->
                if (sec in 1..900) {
                    val min = (sec / 60).coerceAtLeast(1)
                    add("Quedan ~$min min de reloj. Estima $remaining series.")
                }
            }
            progress.nextExerciseName?.let { next ->
                val kg = progress.nextSuggestedKg?.let { " a ${formatRelatorKg(it)} kg" }.orEmpty()
                add("Después de {ex} viene $next$kg.")
            }
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.SESSION_PROGRESS,
                priority = 36,
                relevance = 0.55,
                lines = padVariants(lines),
                fingerprintBase = "PROGRESS:${done}",
                cooldownSets = 2,
                openers = listOf("Ahora", "Siguiente"),
            ),
        )
    }
}
