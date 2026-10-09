package com.example.kpkn.domain.training.periodization

import com.example.kpkn.domain.training.ProgramCalendarEngine
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fases de un programa por bloques o periodizado (puro). Sin fecha, las semanas son fijas; con fecha, se cuentan hacia atrás
 * desde la semana de la competición, que siempre es la última. Las fases no dependen de las marcas ni del modo: solo de la
 * variante, del horizonte y de si la disciplina es pesada (la puesta a punto de pesadas dura dos semanas en periodizado largo).
 */
object PeriodLayout {

    /** Semanas máximas de acumulación seguidas; si se pasa, una descarga la parte en dos. */
    private const val MAX_ACCUMULATION_WEEKS = 6

    /** Tramos de fase de [variant] para [horizon]. El simple no tiene fases: devuelve lista vacía. */
    fun phases(variant: ProgramVariant, horizon: Horizon, heavy: Boolean): List<PhaseSpan> {
        if (variant == ProgramVariant.SIMPLE) return emptyList()
        return when (horizon) {
            Horizon.Simple -> throw IllegalArgumentException("Un programa por bloques o periodizado necesita semanas o una fecha.")
            is Horizon.Weeks -> withoutDate(variant, horizon.weeks)
            is Horizon.ToMeet -> withDate(variant, weeksUntil(horizon), heavy)
        }
    }

    /** Sin fecha: bloques de 4 a 8 semanas; periodizado de 10, 12 o 16 semanas. */
    private fun withoutDate(variant: ProgramVariant, weeks: Int): List<PhaseSpan> = when (variant) {
        ProgramVariant.SIMPLE -> emptyList()
        ProgramVariant.BLOCKS -> {
            require(weeks in 4..8) { "Un programa en bloques sin fecha va de 4 a 8 semanas." }
            val accumulation = ceil((weeks - 1) / 2.0).toInt()
            val intensification = floor((weeks - 1) / 2.0).toInt()
            splitAccumulation(accumulation) + listOf(
                PhaseSpan(PhaseKind.INTENSIFICATION, intensification),
                PhaseSpan(PhaseKind.DELOAD, 1),
            )
        }
        ProgramVariant.PERIODIZED -> when (weeks) {
            10 -> splitAccumulation(4) + listOf(
                PhaseSpan(PhaseKind.INTENSIFICATION, 2),
                PhaseSpan(PhaseKind.REALIZATION, 2),
                PhaseSpan(PhaseKind.DELOAD, 1),
                PhaseSpan(PhaseKind.TEST, 1),
            )
            12 -> splitAccumulation(5) + listOf(
                PhaseSpan(PhaseKind.INTENSIFICATION, 3),
                PhaseSpan(PhaseKind.REALIZATION, 2),
                PhaseSpan(PhaseKind.DELOAD, 1),
                PhaseSpan(PhaseKind.TEST, 1),
            )
            16 -> splitAccumulation(7) + listOf(
                PhaseSpan(PhaseKind.INTENSIFICATION, 4),
                PhaseSpan(PhaseKind.REALIZATION, 3),
                PhaseSpan(PhaseKind.DELOAD, 1),
                PhaseSpan(PhaseKind.TEST, 1),
            )
            else -> throw IllegalArgumentException("Un programa periodizado sin fecha va de 10, 12 o 16 semanas.")
        }
    }

    /**
     * Con fecha: cuenta hacia atrás desde la competición. Dos y tres semanas tienen forma fija; de 8 semanas en adelante el
     * periodizado reserva dos de pico y una o dos de puesta a punto; el resto (bloques, o periodizado de menos de 8 semanas)
     * termina en pico de una semana y puesta a punto de una.
     */
    private fun withDate(variant: ProgramVariant, weeks: Int, heavy: Boolean): List<PhaseSpan> = when {
        weeks <= 1 -> emptyList()
        weeks == 2 -> listOf(PhaseSpan(PhaseKind.PEAK, 1), PhaseSpan(PhaseKind.TAPER, 1))
        weeks == 3 -> listOf(
            PhaseSpan(PhaseKind.INTENSIFICATION, 1),
            PhaseSpan(PhaseKind.PEAK, 1),
            PhaseSpan(PhaseKind.TAPER, 1),
        )
        variant == ProgramVariant.PERIODIZED && weeks >= 8 -> {
            val taper = if (heavy && weeks >= 10) 2 else 1
            val head = weeks - 2 - taper
            val intensification = max(2, (head / 3.0).roundToInt())
            splitAccumulation(head - intensification) + listOf(
                PhaseSpan(PhaseKind.INTENSIFICATION, intensification),
                PhaseSpan(PhaseKind.PEAK, 2),
                PhaseSpan(PhaseKind.TAPER, taper),
            )
        }
        else -> {
            val accumulation = ceil((weeks - 1) / 2.0).toInt()
            val intensification = floor((weeks - 1) / 2.0).toInt()
            // La última semana de intensificación es el pico: por eso la intensificación queda en una menos.
            val middle = if (intensification > 1) listOf(PhaseSpan(PhaseKind.INTENSIFICATION, intensification - 1)) else emptyList()
            splitAccumulation(accumulation) + middle + listOf(PhaseSpan(PhaseKind.PEAK, 1), PhaseSpan(PhaseKind.TAPER, 1))
        }
    }

    /**
     * Acumulación de [weeks] semanas. Si pasa de [MAX_ACCUMULATION_WEEKS], se mete una descarga en medio; esa semana sale de la
     * propia acumulación, así que el total de semanas del programa no cambia.
     */
    private fun splitAccumulation(weeks: Int): List<PhaseSpan> {
        if (weeks <= MAX_ACCUMULATION_WEEKS) return listOf(PhaseSpan(PhaseKind.ACCUMULATION, weeks))
        val body = weeks - 1
        val first = ceil(body / 2.0).toInt()
        return splitAccumulation(first) + PhaseSpan(PhaseKind.DELOAD, 1) + splitAccumulation(body - first)
    }

    /**
     * Semanas desde la semana del ancla hasta la semana de la competición, ambas incluidas. Las semanas empiezan en
     * [weekStartDay] (se alinean con [ProgramCalendarEngine.alignToWeekStart]).
     */
    private fun weeksUntil(horizon: Horizon.ToMeet): Int {
        val first = ProgramCalendarEngine.alignToWeekStart(horizon.anchor, horizon.weekStartDay)
        val last = ProgramCalendarEngine.alignToWeekStart(horizon.meetDate, horizon.weekStartDay)
        return (ChronoUnit.DAYS.between(first, last) / 7).toInt() + 1
    }
}
