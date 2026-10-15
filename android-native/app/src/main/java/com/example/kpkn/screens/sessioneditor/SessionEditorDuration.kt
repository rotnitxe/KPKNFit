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
        totalSetCount = exercises.sumOf { if (it.cardioDetails != null) 0 else it.sets.size },
    )
}

/** Minutos de la sesión con el estimador común, para el resumen de la Auge del editor. 0 si el estimador no puede medirla. */
internal fun sessionEditorDurationMinutes(session: Session): Int =
    runCatching { SessionDurationEstimator.estimate(session).totalMinutes }.getOrDefault(0)

/**
 * Minutos que muestra el FAB de tiempo del editor (SE9). Sin ejercicios no hay tiempo que decir: el estimador da 1 min y eso no es
 * un dato. Null: el FAB de tiempo no se muestra.
 */
internal fun sessionTimeFabMinutes(hasExercises: Boolean, breakdownMinutes: Int?, fallbackMinutes: Int): Int? =
    (breakdownMinutes ?: fallbackMinutes).takeIf { hasExercises }
