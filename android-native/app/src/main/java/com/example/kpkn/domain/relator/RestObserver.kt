package com.example.kpkn.domain.relator

object RestObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        if (context.phase != RelatorSessionPhase.REST && !context.rest.active) return emptyList()
        val rest = context.rest
        val next = context.progress.nextExerciseName ?: context.shortExerciseName()
        val nextKg = context.progress.nextSuggestedKg ?: context.suggestedWeightKg
        val kgBit = nextKg?.let { " a ${formatRelatorKg(it)} kg" }.orEmpty()
        val planned = rest.plannedSeconds.takeIf { it > 0 }
        val adaptive = rest.adaptiveSeconds.takeIf { it > 0 && it != rest.plannedSeconds }
        val lines = buildList {
            add("Descanso. Serie ${context.setIndex + 1}. Prepara $next$kgBit.")
            add("Siguiente: $next$kgBit. Aprovecha el timer.")
            add("Pausa activa. $next espera$kgBit.")
            if (adaptive != null && planned != null) {
                add("Descanso adaptativo ${adaptive}s (plan ${planned}s) por la serie de {ex}.")
                add("El timer se alargó a ${adaptive}s: {ex} pidió más recobro.")
            } else if (planned != null) {
                add("Recobro de ${planned}s como marca el plan de {ex}.")
            }
            add("Mismo recobro en overlay o tarjeta: prepara $next$kgBit.")
        }
        val actions = buildList {
            if (rest.remainingSeconds <= 0 && !rest.active) {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.START_REST,
                        label = "Arranca descanso",
                        span = "Arranca descanso",
                        restSeconds = planned ?: 90,
                    ),
                )
            } else {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.EXTEND_REST,
                        label = "+15 s",
                        span = "+15 s",
                        restSeconds = 15,
                    ),
                )
            }
            if (rest.justHitPr || context.milestone.prJustNow) {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.CAPTURE_MEDIA,
                        label = "Foto/vídeo PR",
                        span = "Foto/vídeo PR",
                        exerciseId = context.exerciseId,
                        setIndex = context.setIndex,
                    ),
                )
            }
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.REST,
                priority = 70,
                relevance = 0.9,
                lines = padVariants(lines),
                fingerprintBase = "REST:${context.progress.completedWorkingSets}",
                actions = actions,
                cooldownSets = 0,
                openers = listOf("Siguiente", "Ahora"),
            ),
        )
    }
}
