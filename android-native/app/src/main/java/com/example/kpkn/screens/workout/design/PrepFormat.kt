package com.example.kpkn.screens.workout.design

import com.example.kpkn.data.models.MobilitySeries
import kotlin.math.roundToInt

/** Reloj «00:45» de los temporizadores de calentamiento y movilidad (KPKN LS5b). */
internal fun prepClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

/** Duración corta de una dosis, «0:30» o «1:30», sin cero delante de los minutos (KPKN LS5b). */
internal fun prepDoseClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}

/**
 * Dosis de un elemento de movilidad: «0:30» si tiene tiempo, o «2 × 10» («10» con una sola serie) si tiene repeticiones. Null si el
 * elemento no dice ninguna: nunca se inventa una.
 */
internal fun prepMobilityDose(series: MobilitySeries): String? {
    val sets = series.sets.coerceAtLeast(1)
    val seconds = series.durationSeconds?.takeIf { it > 0 }
    val reps = series.reps?.trim()?.takeIf { it.isNotEmpty() }
    val amount = when {
        seconds != null -> prepDoseClock(seconds)
        reps != null -> reps
        else -> return null
    }
    return if (sets > 1) "$sets × $amount" else amount
}

/**
 * Porcentaje del peso de trabajo, «50%» (sin hueco, KPKN_UI_V2 §5). Acepta la fracción (0,5) o el porcentaje (50).
 */
internal fun prepPercentText(raw: Double): String {
    val pct = if (raw <= 1.0) raw * 100.0 else raw
    return "${pct.roundToInt()}%"
}
