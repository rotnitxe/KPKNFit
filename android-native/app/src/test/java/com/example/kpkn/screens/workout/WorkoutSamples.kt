package com.example.kpkn.screens.workout

import com.example.kpkn.screens.workout.design.WorkoutSetDot
import com.example.kpkn.screens.workout.design.WorkoutSetDotState

/**
 * Datos de ejemplo de la sesión en vivo para las hojas de render: el marco (LS2a) y las piezas de la serie (LS1). Son textos y
 * cifras fijas, sin VM: todo se pinta con las piezas sin estado de `screens/workout/design/`.
 */
internal object WorkoutSamples {
    const val EXERCISE_NAME = "Press inclinado con mancuernas en banco"
    const val EXERCISE_NAME_SHORT = "Press inclinado"
    const val EYEBROW_SUPERSET = "Superserie A · ronda 2 de 3"
    const val EYEBROW_PLAIN = "Ejercicio 3 de 6 · Torso A"
    const val DETAIL = "Mancuernas · Agarre neutro"
    const val TARGET = "Objetivo 4 × 6–8 · RIR 2"
    const val LAST = "Última vez 80 kg × 8"
    const val PROTOCOL = "Drop-set"
    const val PACE_AHEAD = "Vas por delante"
    const val CLOCK = "24:31"
    val DONE = listOf(true, true, false, false, false, false)

    /** Dos hechas, la actual y dos pendientes. */
    val cincoSeries: List<WorkoutSetDot> = listOf(
        WorkoutSetDot("s1", "1", WorkoutSetDotState.DONE, "80 × 8"),
        WorkoutSetDot("s2", "2", WorkoutSetDotState.DONE, "80 × 8"),
        WorkoutSetDot("s3", "3", WorkoutSetDotState.CURRENT, null),
        WorkoutSetDot("s4", "4", WorkoutSetDotState.FUTURE, null),
        WorkoutSetDot("s5", "5", WorkoutSetDotState.FUTURE, null),
    )

    /** Diez series, las seis primeras hechas y la séptima en curso: la tira tiene que dejarla a la vista. */
    val diezSeries: List<WorkoutSetDot> = (1..10).map { n ->
        WorkoutSetDot(
            id = "s$n",
            label = "$n",
            state = when {
                n < 7 -> WorkoutSetDotState.DONE
                n == 7 -> WorkoutSetDotState.CURRENT
                else -> WorkoutSetDotState.FUTURE
            },
            caption = if (n < 7) "80 × 8" else null,
        )
    }

    /** Cuatro series con la segunda en curso: la pantalla de «Serie 2 de 4». */
    val cuatroSeries: List<WorkoutSetDot> = listOf(
        WorkoutSetDot("s1", "1", WorkoutSetDotState.DONE, "80 × 8"),
        WorkoutSetDot("s2", "2", WorkoutSetDotState.CURRENT, null),
        WorkoutSetDot("s3", "3", WorkoutSetDotState.FUTURE, null),
        WorkoutSetDot("s4", "4", WorkoutSetDotState.FUTURE, null),
    )
}
