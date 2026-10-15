package com.example.kpkn.domain.relator

object ReadinessObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        val r = context.readiness
        val score = r.dailyScore ?: r.exerciseScore ?: return emptyList()
        if (score >= 75 && r.preWorkoutDiscomforts.isEmpty() && (r.doms ?: 1) < 4) return emptyList()
        val done = context.progress.completedWorkingSets
        val lines = buildList {
            r.dailyLabel?.let { add("Disponibilidad de hoy: $it ($score). {ex} necesita ese margen.") }
            add("Disponibilidad $score/100. Ajusta {ex} si el gesto se ensucia.")
            r.limitingFactor?.let { add("El punto débil de hoy es $it. Cuida {ex}.") }
            r.muscleDrainLabel?.let { add("Fatiga acumulada: $it. No aprietes {ex} de más.") }
            if ((r.doms ?: 0) >= 4) add("Agujetas altas. {ex} pide recorte o rango limpio.")
            if ((r.sleepQuality ?: 3) <= 2) add("Sueño flojo (${r.sleepQuality}/5). Baja un punto {ex}.")
            if ((r.stressLevel ?: 3) >= 4) add("Estrés alto. Autoregula {ex} a la baja.")
            r.preWorkoutDiscomforts.firstOrNull()?.let { add("Molestia previa: $it. Mira la disponibilidad o sustituye {ex}.") }
        }
        val actions = buildList {
            if (score < 60) {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.ADJUST_LOAD,
                        label = "Ajusta carga",
                        span = "Ajusta carga",
                        exerciseId = context.exerciseId,
                        setIndex = context.setIndex,
                        loadDeltaPercent = if (score < 45) -10.0 else -5.0,
                    ),
                )
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.OPEN_REPLACE,
                        label = "Sustituye",
                        span = "Sustituye",
                        exerciseId = context.exerciseId,
                    ),
                )
            }
            add(
                RelatorActionSpec(
                    kind = RelatorActionKind.OPEN_READINESS,
                    label = "Ver disponibilidad",
                    span = "Ver disponibilidad",
                    exerciseId = context.exerciseId,
                ),
            )
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.READINESS,
                priority = 64,
                relevance = if (score < 55) 0.95 else 0.6,
                lines = padVariants(lines),
                fingerprintBase = "READINESS:${score}:$done",
                actions = actions,
                cooldownSets = 2,
            ),
        )
    }
}
