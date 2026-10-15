package com.example.kpkn.domain.relator

/**
 * Descanso (LR1g). En REST_MID dice cómo fue la serie que toca la última vez; en el resto de momentos del descanso,
 * qué toca después. El «siguiente» sale de [RelatorProgressSlice]: la serie que toca del mismo ejercicio, o el
 * ejercicio siguiente solo cuando ya no quedan series del actual.
 */
object RestObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        if (context.phase != RelatorSessionPhase.REST && !context.rest.active) return emptyList()
        val rest = context.rest
        val lines = if (context.moment == RelatorMoment.REST_MID) midLines(context) else prepLines(context)
        val actions = buildList {
            if (rest.remainingSeconds <= 0 && !rest.active) {
                add(
                    RelatorActionSpec(
                        kind = RelatorActionKind.START_REST,
                        label = "Arranca descanso",
                        span = "Arranca descanso",
                        restSeconds = rest.plannedSeconds.takeIf { it > 0 } ?: 90,
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
                lines = realVariants(lines),
                fingerprintBase = "REST:${context.progress.completedWorkingSets}",
                actions = actions,
                cooldownSets = 0,
            ),
        )
    }

    /** Qué viene después: «serie 2 de 4, 95 kg × 6», o «peso muerto rumano, 71 kg». Null si no queda nada. */
    private fun nextTarget(context: RelatorContext): String? {
        val progress = context.progress
        val setNumber = progress.nextSetNumber
        if (setNumber != null) {
            val total = progress.nextSetTotal
            val head = if (total != null) "serie $setNumber de $total" else "serie $setNumber"
            val kg = progress.nextSetWeightKg ?: progress.nextSuggestedKg
            val reps = progress.nextSetReps ?: 0
            val mark = when {
                kg != null && kg > 0.0 -> relatorSetMark(kg, reps)
                reps > 0 -> "$reps reps"
                else -> null
            }
            return listOfNotNull(head, mark).joinToString(", ")
        }
        val name = progress.nextExerciseName?.takeIf { it.isNotBlank() } ?: return null
        val kg = progress.nextSuggestedKg?.takeIf { it > 0.0 }
        val lowered = name.lowercase()
        return if (kg != null) "$lowered, ${relatorKg(kg)}" else lowered
    }

    private fun prepLines(context: RelatorContext): List<String> {
        val target = nextTarget(context)
        val rest = context.rest
        val lines = buildList {
            if (target != null) {
                add("Prepárate: $target.")
                add("Después del descanso, $target.")
            } else {
                add("Después de este descanso toca cerrar la sesión.")
            }
            val planned = rest.plannedSeconds.takeIf { it > 0 }
            val adaptive = rest.adaptiveSeconds.takeIf { it > 0 && it != rest.plannedSeconds }
            if (adaptive != null) {
                add("El descanso sugerido es ${relatorDoseTime(adaptive)}, por la serie de {ex}.")
            } else if (planned != null) {
                add("El plan marca ${relatorDoseTime(planned)} de descanso en {ex}.")
            }
        }
        return lines
    }

    /** Mitad del descanso: la serie que toca tal como fue la última vez. Sin historial, cuánto queda. */
    private fun midLines(context: RelatorContext): List<String> {
        val setNumber = context.progress.nextSetNumber ?: (context.setIndex + 1)
        val last = context.history.lastSessionSets.getOrNull(setNumber - 1)
        return buildList {
            if (last != null && last.weightKg > 0.0) {
                add("La última vez, en la serie $setNumber: ${relatorSetMark(last.weightKg, last.reps)}.")
                add("En la serie $setNumber de la última vez fueron ${relatorSetMark(last.weightKg, last.reps)}.")
            }
            add("Quedan ${relatorDoseTime(context.rest.remainingSeconds)} de descanso.")
        }
    }
}
