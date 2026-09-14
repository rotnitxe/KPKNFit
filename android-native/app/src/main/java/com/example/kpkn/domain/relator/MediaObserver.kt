package com.example.kpkn.domain.relator

object MediaObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        val count = context.media.previousCount
        val pr = context.milestone.prJustNow || context.rest.justHitPr
        if (count <= 0 && !pr) return emptyList()
        val name = context.media.previousExerciseName ?: context.shortExerciseName()
        val lines = buildList {
            if (count > 0) {
                val noun = if (count == 1) "foto" else "fotos"
                add("Hay $count $noun de $name de la última vez.")
                add("Álbum: $count de $name. Abre si quieres comparar {ex}.")
                add("La vez pasada grabaste $count de $name. Úsalo de referencia.")
            }
            if (pr) {
                add("PR de {ex}: captura ahora para el álbum.")
                add("Marca nueva. Una foto o vídeo deja constancia de {ex}.")
                add("No dejes el PR de {ex} solo en el log.")
            }
        }
        val actions = buildList {
            if (count > 0) {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.OPEN_ALBUM,
                        label = "Abrir álbum",
                        span = "Abrir álbum",
                        exerciseId = context.exerciseId,
                    ),
                )
            }
            if (pr) {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.CAPTURE_MEDIA,
                        label = "Capturar",
                        span = "Capturar",
                        exerciseId = context.exerciseId,
                        setIndex = context.setIndex,
                    ),
                )
            }
        }
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.MEDIA,
                priority = 22,
                relevance = if (pr) 0.8 else 0.4,
                lines = padVariants(lines),
                fingerprintBase = "MEDIA:${context.progress.completedWorkingSets}",
                actions = actions,
                cooldownSets = 3,
                openers = listOf("Dato", "Hoy"),
            ),
        )
    }
}
