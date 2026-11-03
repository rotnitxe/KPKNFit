package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.ui.design.KpknSetFormat
import java.util.Locale

/*
 * Cifras y unidades de las celdas del cardio (la celda en sí es `CeCell`, de `CardioEditorPrimitives.kt`).
 */

/** Cifra y unidad de una duración: «20» «min»; «1:30» sin unidad; «30» «s»; nulo es «Libre». */
internal fun durationCellParts(seconds: Int?): Pair<String, String?> = when {
    seconds == null -> "Libre" to null
    seconds >= 60 && seconds % 60 == 0 -> "${seconds / 60}" to "min"
    seconds >= 60 -> KpknSetFormat.rest(seconds) to null
    else -> "${seconds.coerceAtLeast(0)}" to "s"
}

/** Cifra y unidad de una distancia: «5» «km», «2,5» «km»; nulo es «—». */
internal fun distanceCellParts(km: Double?): Pair<String, String?> = when (km) {
    null -> "—" to null
    else -> cellNumber(km) to "km"
}

/** Cifra y unidad de un ritmo: «5:30» «/km»; nulo es «—». */
internal fun paceCellParts(secondsPerKm: Int?): Pair<String, String?> = when (secondsPerKm) {
    null -> "—" to null
    else -> KpknSetFormat.rest(secondsPerKm) to "/km"
}

/** Número con coma decimal y sin decimales si es entero: «5», «2,5». */
internal fun cellNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.forLanguageTag("es"), "%.1f", value)
