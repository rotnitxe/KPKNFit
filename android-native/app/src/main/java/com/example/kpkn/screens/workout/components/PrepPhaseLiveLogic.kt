package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.MobilitySeries

/** Reloj de la preparación en m:ss («5:00», «0:45»), como el de los tableros H5 y H6. */
internal fun formatPrepClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

/** Segundos de la dosis de un movimiento: 0 si se dosifica por repeticiones. */
internal fun mobilityDoseSeconds(mobility: MobilitySeries): Int =
    mobility.durationSeconds?.takeIf { it > 0 } ?: 0

/** Dosis del movimiento tal como la lee el usuario: «60 s», «10 reps» o «3 × 45 s». */
internal fun mobilityDoseLabel(mobility: MobilitySeries): String {
    val seconds = mobilityDoseSeconds(mobility)
    val dose = when {
        seconds > 0 -> "$seconds s"
        !mobility.reps.isNullOrBlank() -> "${mobility.reps} reps"
        else -> "—"
    }
    return if (mobility.sets > 1) "${mobility.sets} × $dose" else dose
}
