package com.example.kpkn.domain.relator

object WarmupObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        if (context.phase != RelatorSessionPhase.WARMUP && context.warmup.remainingCount <= 0) {
            return emptyList()
        }
        val w = context.warmup
        val kg = w.suggestedKg
        val kgBit = kg?.let { "${formatRelatorKg(it)} kg" } ?: "la aproximación"
        val remaining = w.remainingCount.coerceAtLeast(1)
        val lines = buildList {
            add("Aprox $kgBit. Quedan $remaining de calentamiento en {ex}.")
            add("Suma $kgBit en {ex} y enciende el patrón.")
            add("Calentamiento: $kgBit. No es la serie de verdad.")
            if (w.isLastIncomplete) add("Última aprox de {ex} a $kgBit. Luego efectivas.")
            add("Rampas de {ex}: $kgBit, gesto idéntico al de trabajo.")
            add("Si $kgBit se siente pesado, recorta el salto.")
        }
        val highReadiness = (context.readiness.dailyScore ?: context.readiness.exerciseScore ?: 70) >= 80
        val timeCrunch = (context.progress.remainingSeconds ?: Int.MAX_VALUE) in 1..480
        val actions = buildList {
            kg?.let {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.APPLY_SUGGESTED_LOAD,
                        label = "Usa ${formatRelatorKg(it)} kg",
                        span = "Usa ${formatRelatorKg(it)} kg",
                        exerciseId = context.exerciseId,
                        setIndex = context.setIndex,
                        weightKg = it,
                    ),
                )
            }
            if (highReadiness || timeCrunch) {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.SKIP_REMAINING_WARMUPS,
                        label = "Salta aprox",
                        span = "Salta aprox",
                        exerciseId = context.exerciseId,
                    ),
                )
            }
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.WARMUP,
                priority = 48,
                relevance = 0.75,
                lines = padVariants(lines),
                fingerprintBase = "WARMUP:${context.progress.completedWorkingSets}",
                actions = actions,
                cooldownSets = 1,
                openers = listOf("Ahora", "Carga"),
            ),
        )
    }
}
