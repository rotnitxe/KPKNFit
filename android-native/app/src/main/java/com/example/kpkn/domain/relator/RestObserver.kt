package com.example.kpkn.domain.relator

/**
 * Descanso (LR1h). En REST_MID dice cómo fue esta serie la última vez; sin historial, cuánto queda y qué sigue. En REST_PREP
 * (y el resto de momentos del descanso), qué toca después. El «siguiente» sale de [RelatorProgressSlice]: la serie que toca
 * del mismo ejercicio, o el ejercicio siguiente solo cuando ya no quedan series del actual.
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

    /** Últimos 15 s: qué toca después y el descanso que marca el plan (LR1h: como antes). */
    private fun prepLines(context: RelatorContext): List<String> {
        val target = relatorNextTarget(context.progress, mid = false)
        val rest = context.rest
        val lines = buildList {
            if (target != null) {
                add("Prepárate: $target.")
                add("Después del descanso, $target.")
            } else {
                add("Esta es la última serie: después del descanso, cierras la sesión.")
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

    /**
     * Mitad del descanso (LR1h): cómo fue esta serie la última vez, «La última vez, en la serie 2: 92,5 kg × 6.». Sin
     * historial, cuánto queda y qué sigue: «Quedan 2:00. Después, serie 2 de 4: 95 kg × 6.».
     */
    private fun midLines(context: RelatorContext): List<String> {
        val setNumber = context.progress.nextSetNumber ?: (context.setIndex + 1)
        val last = context.history.lastSessionSets.getOrNull(setNumber - 1)
        if (last != null && last.weightKg > 0.0) {
            return listOf("La última vez, en la serie $setNumber: ${relatorSetMark(last.weightKg, last.reps)}.")
        }
        val remaining = context.rest.remainingSeconds
        val left = if (remaining > 0) "Quedan ${relatorDoseTime(remaining)}. " else ""
        val target = relatorNextTarget(context.progress, mid = true)
        return listOf(if (target != null) "${left}Después, $target." else "${left}Después cierras la sesión.")
    }
}

/**
 * Qué viene después (LR1h): «serie 2 de 4, 95 kg × 6» en la frase de prepararse; con [mid], «serie 2 de 4: 95 kg × 6» en
 * la de la mitad. Si ya no quedan series del ejercicio, el ejercicio siguiente: «peso muerto rumano, 71 kg». Null si no
 * queda nada.
 */
internal fun relatorNextTarget(progress: RelatorProgressSlice, mid: Boolean): String? {
    val setNumber = progress.nextSetNumber
    if (setNumber != null) {
        return relatorNextSetText(
            number = setNumber,
            total = progress.nextSetTotal,
            kg = progress.nextSetWeightKg ?: progress.nextSuggestedKg,
            reps = progress.nextSetReps,
            mid = mid,
        )
    }
    val name = progress.nextExerciseName?.takeIf { it.isNotBlank() } ?: return null
    val kg = progress.nextSuggestedKg?.takeIf { it > 0.0 }
    val lowered = name.lowercase()
    return if (kg != null) "$lowered, ${relatorKg(kg)}" else lowered
}

/** Una serie que viene: «serie 2 de 4, 95 kg × 6»; con [mid], «serie 2 de 4: 95 kg × 6». Sin carga ni reps, solo la serie. */
internal fun relatorNextSetText(number: Int, total: Int?, kg: Double?, reps: Int?, mid: Boolean): String {
    val head = if (total != null) "serie $number de $total" else "serie $number"
    val safeKg = kg?.takeIf { it > 0.0 }
    val safeReps = reps?.takeIf { it > 0 } ?: 0
    val mark = when {
        safeKg != null -> relatorSetMark(safeKg, safeReps)
        safeReps > 0 -> "$safeReps reps"
        else -> null
    }
    return when {
        mark == null -> head
        mid -> "$head: $mark"
        else -> "$head, $mark"
    }
}
