package com.example.kpkn.domain.relator

object CoachObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        val coach = context.coach
        val body = coach.body?.trim().orEmpty()
        val title = coach.title?.trim().orEmpty()
        if (body.isEmpty() && title.isEmpty()) return emptyList()
        val lines = buildList {
            if (title.isNotEmpty()) add(title)
            if (body.isNotEmpty()) add(body)
            if (title.isNotEmpty() && body.isNotEmpty()) add("$title. $body")
            coach.action?.let { action ->
                add(
                    when (action) {
                        "REDUCE_INTENSITY" -> "El coach pide bajar un punto {ex}."
                        "SKIP_EXERCISE" -> "El coach sugiere saltar o sustituir {ex}."
                        "EXTEND_REST" -> "El coach alargaría el descanso tras {ex}."
                        else -> "Mantén el rumbo en {ex}."
                    },
                )
            }
        }
        val actions = when (coach.action) {
            "REDUCE_INTENSITY" -> listOf(
                RelatorActionSpec(
                    kind = RelatorActionKind.ADJUST_LOAD,
                    label = "Ajusta carga",
                    span = "Ajusta carga",
                    exerciseId = context.exerciseId,
                    setIndex = context.setIndex,
                    loadDeltaPercent = -5.0,
                ),
            )
            "SKIP_EXERCISE" -> listOf(
                RelatorActionSpec(
                    kind = RelatorActionKind.OPEN_REPLACE,
                    label = "Sustituye",
                    span = "Sustituye",
                    exerciseId = context.exerciseId,
                ),
            )
            "EXTEND_REST" -> listOf(
                RelatorActionSpec(
                    kind = RelatorActionKind.EXTEND_REST,
                    label = "+15 s",
                    span = "+15 s",
                    restSeconds = 15,
                ),
            )
            else -> emptyList()
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.COACH,
                priority = 28,
                relevance = 0.45,
                lines = padVariants(lines),
                fingerprintBase = "COACH:${coach.key.orEmpty()}:${context.progress.completedWorkingSets}",
                actions = actions,
                cooldownSets = 3,
                openers = listOf("Ojo", "Ahora"),
            ),
        )
    }
}
