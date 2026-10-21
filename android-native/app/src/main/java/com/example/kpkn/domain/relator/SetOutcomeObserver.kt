package com.example.kpkn.domain.relator

/**
 * Lo que el relator dice al registrar una serie (LR1h): cómo fue frente a lo previsto, en una frase corta. Solo en el
 * momento SET_LOGGED, que es el inicio del descanso. Con marca personal lo dice [MilestoneObserver]. Sin reps registradas,
 * dice qué toca después.
 */
object SetOutcomeObserver : RelatorObserver {
    override fun observe(context: RelatorContext): List<RelatorCandidate> {
        if (context.moment != RelatorMoment.SET_LOGGED) return emptyList()
        if (context.milestone.prJustNow) return emptyList()
        val logged = context.loggedSet
        val number = logged.setNumber ?: return emptyList()
        val outcome = setOutcomeOf(logged)
        return listOf(
            RelatorCandidate(
                topic = RelatorTopic.REACTION,
                priority = 96,
                relevance = 1.0,
                lines = listOf(outcomeLine(outcome, logged)),
                fingerprintBase = "OUTCOME:$number:${outcome.name}",
                isReaction = true,
            ),
        )
    }
}

/** Cómo fue la serie registrada frente a lo previsto (LR1h). */
internal enum class SetOutcome { NO_DATA, FACT, BELOW, ABOVE, ON_PLAN }

/** Paso de carga que se sugiere cuando la serie fue por encima: «la próxima puede subir 2,5 kg». */
private const val OUTCOME_LOAD_STEP_KG = 2.5

/**
 * Compara la serie con lo previsto. Sin reps registradas, no hay nada que juzgar. Sin reps previstas, solo se dice la
 * serie. Por debajo de las reps previstas, primero (aunque el RIR fuera más alto). Por encima, si pasa el máximo del rango
 * o si el RIR fue más alto que el previsto.
 */
internal fun setOutcomeOf(logged: RelatorLoggedSetSlice): SetOutcome {
    val reps = logged.reps?.takeIf { it > 0 } ?: return SetOutcome.NO_DATA
    val planned = logged.plannedReps?.takeIf { it > 0 } ?: return SetOutcome.FACT
    if (reps < planned) return SetOutcome.BELOW
    val max = logged.plannedRepsMax?.takeIf { it >= planned } ?: planned
    val rir = logged.rir
    val plannedRir = logged.plannedRir
    val rirAbove = rir != null && plannedRir != null && rir > plannedRir
    return if (reps > max || rirAbove) SetOutcome.ABOVE else SetOutcome.ON_PLAN
}

private fun outcomeLine(outcome: SetOutcome, logged: RelatorLoggedSetSlice): String = when (outcome) {
    SetOutcome.ON_PLAN -> "${setMark(logged)}: lo previsto."
    SetOutcome.ABOVE -> if (logged.nextSetNumber != null) {
        "Te sobró margen: la próxima puede subir ${relatorKg(OUTCOME_LOAD_STEP_KG)}."
    } else {
        "Te sobró margen: la próxima vez puede subir ${relatorKg(OUTCOME_LOAD_STEP_KG)}."
    }
    SetOutcome.BELOW -> {
        val deficit = (logged.plannedReps ?: 0) - (logged.reps ?: 0)
        if (deficit <= 1) {
            "Una rep menos de lo previsto: mantén la carga."
        } else {
            "$deficit reps menos de lo previsto: mantén la carga."
        }
    }
    SetOutcome.FACT -> "Serie ${logged.setNumber ?: 1} hecha: ${setMark(logged)}."
    SetOutcome.NO_DATA -> noDataLine(logged)
}

/** Sin reps registradas: di qué toca después, como «Prepárate». Si la serie era la última del ejercicio, dilo. */
private fun noDataLine(logged: RelatorLoggedSetSlice): String {
    val number = logged.nextSetNumber ?: return "Esta era la última serie del ejercicio."
    val next = relatorNextSetText(
        number = number,
        total = logged.nextSetTotal,
        kg = logged.nextSetWeightKg,
        reps = logged.nextSetReps,
        mid = false,
    )
    return "Prepárate: $next."
}

/** La serie en una frase: «95 kg × 6 a RIR 2»; sin peso, «6 reps a RIR 2». El RIR solo si la persona lo registró. */
private fun setMark(logged: RelatorLoggedSetSlice): String {
    val kg = logged.weightKg?.takeIf { it > 0.0 }
    val reps = logged.reps?.takeIf { it > 0 } ?: 0
    val base = when {
        kg != null -> relatorSetMark(kg, reps)
        reps > 0 -> "$reps reps"
        else -> ""
    }
    val rir = logged.rir
    return if (rir != null && base.isNotEmpty()) "$base a RIR $rir" else base
}
