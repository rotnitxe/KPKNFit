package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import com.example.kpkn.domain.training.SessionDurationBreakdown
import com.example.kpkn.domain.training.SessionDurationEstimator

/**
 * Desglose del estimador común (SE12) con la forma que pintan la cabecera del editor y las reglas ([SessionTimeBreakdown]). Así la misma
 * sesión dice los mismos minutos que en Inicio y en el programa.
 *
 * El total va en minutos enteros hacia arriba, como `SessionDurationBreakdown.totalMinutes`: los segundos del redondeo se suman a la
 * ejecución para que las partes sumen el total. Sin ejercicios, todo va a cero.
 */
internal fun SessionDurationBreakdown.toSessionTimeBreakdown(session: Session): SessionTimeBreakdown {
    val exercises = session.allExercises()
    val total = if (totalSeconds > 0) totalMinutes * 60 else 0
    return SessionTimeBreakdown(
        setupSeconds = setupSeconds,
        executionSeconds = (total - setupSeconds - resistanceRestSeconds - warmupSeconds).coerceAtLeast(0),
        restSeconds = resistanceRestSeconds,
        warmupSeconds = warmupSeconds,
        totalSeconds = total,
        exerciseCount = exercises.size,
        totalSetCount = session.workingSetCount(),
    )
}

internal fun Session.workingSetCount(): Int = allExercises().sumOf { exercise ->
    if (exercise.cardioDetails != null) 0 else exercise.sets.count { !it.isEmptySlot }
}

/**
 * La sesión sin un ejercicio: lo que quedará cuando se confirme un borrado pendiente (Pulido v1 · EDITOR · A2). Los minutos y las fichas del
 * resumen bajan al borrar y suben si se deshace, aunque el ejercicio siga en la sesión durante los 5 s del aviso.
 */
internal fun Session.withoutExercise(exerciseId: String): Session = copy(
    exercises = exercises.filterNot { it.id == exerciseId },
    parts = parts.map { part -> part.copy(exercises = part.exercises.filterNot { it.id == exerciseId }) },
)

/** Minutos de la sesión con el estimador común, para el resumen de la Auge del editor. 0 si el estimador no puede medirla. */
internal fun sessionEditorDurationMinutes(session: Session): Int =
    runCatching { SessionDurationEstimator.estimate(session).totalMinutes }.getOrDefault(0)

/**
 * Minutos que muestra el FAB de tiempo del editor (SE9). Sin ejercicios no hay tiempo que decir: el estimador da 1 min y eso no es
 * un dato. Null: el FAB de tiempo no se muestra.
 */
internal fun sessionTimeFabMinutes(hasExercises: Boolean, breakdownMinutes: Int?, fallbackMinutes: Int): Int? =
    (breakdownMinutes ?: fallbackMinutes).takeIf { hasExercises }

/** Minutos con su unidad, como «7 min»: espacio inseparable antes de «min» (SESSION_EDITOR_R1 §1, formato de [KpknSetFormat]). */
internal fun sessionMinutesLabel(minutes: Int): String = "${minutes.coerceAtLeast(0)} min"

/**
 * El desglose de la sesión en texto (ER2b): el mismo para el asistente y para Reglas › Tiempo. La ejecución es lo que resta del total,
 * así las partes suman el total que se muestra, y el calentamiento sale siempre que lo haya.
 */
internal fun sessionTimeBreakdownCaption(breakdown: SessionTimeBreakdown): String {
    val total = breakdown.totalMinutes
    val setup = breakdown.setupMinutes.coerceAtLeast(0)
    val rest = breakdown.restMinutes.coerceAtLeast(0)
    val warmup = breakdown.warmupMinutes.coerceAtLeast(0)
    val execution = (total - setup - rest - warmup).coerceAtLeast(0)
    val parts = buildList {
        add("Preparación" to setup)
        add("Ejecución" to execution)
        add("Descansos" to rest)
        if (warmup > 0) add("Calentamiento" to warmup)
    }
    return parts.joinToString(" · ") { (label, minutes) -> "$label ${sessionMinutesLabel(minutes)}" }
}
