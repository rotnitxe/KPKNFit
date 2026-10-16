package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.HiitWorkTarget
import com.example.kpkn.domain.cardio.CardioAuthoringShape
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import com.example.kpkn.ui.design.KpknSetFormat
import kotlin.math.roundToInt

/**
 * Línea de dosis de un cardio: la que va en grande en el editor y en su fila de la lista. Ejemplos: «20 min · Z2»,
 * «8 × 30 s / 30 s · RPE 9», «5 km · 5:30/km» o «5 bloques · 11 min 30 s». Función pura, sin Android. Solo dice lo que el cardio
 * tiene programado: sin objetivo de tiempo ni de distancia, dice «Libre».
 */
internal fun cardioDoseLine(details: CardioDetails): String = when (val shape = CardioRepeatGrammar.shape(details)) {
    is CardioAuthoringShape.Steady -> steadyDose(details)
    is CardioAuthoringShape.Uniform -> uniformDose(shape.repeat, details.resolvedRpe())
    is CardioAuthoringShape.Irregular -> irregularDose(details, shape.blocks.size)
}

/** Zona de FC sobre el máximo, en cinco bandas (cortes en 60, 70, 80 y 90 %), como en el rediseño archivado. */
internal fun cardioZoneOf(hrPercent: Int): Int = when {
    hrPercent < 60 -> 1
    hrPercent < 70 -> 2
    hrPercent < 80 -> 3
    hrPercent < 90 -> 4
    else -> 5
}

private fun steadyDose(details: CardioDetails): String {
    val distance = details.targetDistanceKm?.takeIf { it > 0.0 }
    val duration = details.targetDurationSeconds?.takeIf { it > 0 }
    val pace = details.targetPaceSecondsPerKm?.takeIf { it > 0 }
    // Con distancia, la distancia es el objetivo: el tiempo por defecto no se lee.
    val target = when {
        distance != null -> kmText(distance)
        duration != null -> CardioPrescriptionFormatter.formatDuration(duration)
        else -> "Libre"
    }
    // El ritmo, si lo hay, dice la intensidad; si no, la zona de FC o el RPE.
    val second = pace?.let { CardioPrescriptionFormatter.formatPace(it) }
        ?: details.targetHrPercent?.let { "Z${cardioZoneOf(it)}" }
        ?: "RPE ${details.resolvedRpe().roundToInt()}"
    return "$target · $second"
}

private fun uniformDose(repeat: CardioUniformRepeat, rpe: Double): String {
    val target = repeat.workTargetValue?.takeIf { it > 0.0 }
    val work = when {
        target != null && repeat.workTargetType == HiitWorkTarget.DISTANCE -> "${target.toInt()} m"
        target != null && repeat.workTargetType == HiitWorkTarget.KCAL -> "${target.toInt()} kcal"
        else -> KpknSetFormat.doseTime(repeat.workSeconds)
    }
    val rest = if (repeat.restSeconds > 0) " / ${KpknSetFormat.doseTime(repeat.restSeconds)}" else ""
    val sets = if (repeat.sets > 1) " · ${repeat.sets} series" else ""
    return "${repeat.rounds} × $work$rest$sets · RPE ${rpe.roundToInt().coerceIn(1, 10)}"
}

private fun irregularDose(details: CardioDetails, blockCount: Int): String {
    val rounds = details.intervalRounds.coerceIn(1, 99)
    val blocks = if (blockCount == 1) "1 bloque" else "$blockCount bloques"
    val repeat = if (rounds > 1) " × $rounds" else ""
    val total = details.effectiveDurationSeconds()
    return if (total > 0) "$blocks$repeat · ${CardioPrescriptionFormatter.formatDuration(total)}" else "$blocks$repeat"
}

/** «5 km», o «2,5 km» con coma decimal, como el resto de la app en español. */
private fun kmText(km: Double): String {
    val tenths = (km * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10} km" else "${tenths / 10},${tenths % 10} km"
}
