package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter

internal fun formatMmSs(totalSeconds: Int): String {
    val capped = totalSeconds.coerceIn(0, 99 * 60 + 59)
    val m = capped / 60
    val s = capped % 60
    return "%02d:%02d".format(m, s)
}

private fun formatSeconds(totalSeconds: Int): String =
    if (totalSeconds <= 0) "0 min" else CardioPrescriptionFormatter.formatDuration(totalSeconds)

private fun rpeAnchor(value: Double): String = when (value.coerceIn(1.0, 10.0)) {
    in 1.0..2.0 -> "Muy suave"
    in 2.5..4.0 -> "Suave"
    in 4.5..6.0 -> "Algo duro"
    in 6.5..8.0 -> "Duro"
    in 8.5..9.0 -> "Muy duro"
    else -> "Máximo"
}
