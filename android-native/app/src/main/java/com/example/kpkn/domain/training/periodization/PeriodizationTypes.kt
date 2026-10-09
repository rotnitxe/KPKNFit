package com.example.kpkn.domain.training.periodization

import com.example.kpkn.data.models.Program
import java.time.LocalDate

/** Forma del programa que arma el periodizador: la semana simple del generador, varios bloques o periodizado. */
enum class ProgramVariant { SIMPLE, BLOCKS, PERIODIZED }

/** Fase de un periodo. [TEST] es la semana de prueba al final del periodizado. */
enum class PhaseKind { ACCUMULATION, INTENSIFICATION, REALIZATION, PEAK, DELOAD, TAPER, TEST }

/** Tramo de [weeks] semanas seguidas de la misma [kind]. */
data class PhaseSpan(val kind: PhaseKind, val weeks: Int) {
    init {
        require(weeks >= 1) { "Un tramo de fase dura al menos una semana." }
    }
}

/** Horizonte del programa: simple (una semana que se repite), semanas fijas o hasta una competición. */
sealed interface Horizon {
    /** Programa simple: una sola semana que se repite. */
    data object Simple : Horizon

    /** Sin fecha: número de semanas fijas. */
    data class Weeks(val weeks: Int) : Horizon

    /**
     * Con fecha: [anchor] es el día de inicio del plan, [meetDate] el de la competición y [weekStartDay] el día que empieza
     * cada semana (1 = lunes … 7 = domingo). La última semana del programa es la de la competición.
     */
    data class ToMeet(val anchor: LocalDate, val meetDate: LocalDate, val weekStartDay: Int) : Horizon
}

/**
 * Resultado del periodizador: el programa, sus tramos de fase en orden (ya con los ajustes del modo) y las notas honestas
 * que la pantalla debe mostrar.
 */
data class PeriodizedRoutine(
    val program: Program,
    val phases: List<PhaseSpan>,
    val notes: List<String>,
)
