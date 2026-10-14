package com.example.kpkn.screens.workout

import com.example.kpkn.screens.workout.design.WorkoutSetDot
import com.example.kpkn.screens.workout.design.WorkoutSetDotState

/** Datos de ejemplo de las piezas de la serie en vivo (LS1), para la hoja de render. */
internal object WorkoutSamples {

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
