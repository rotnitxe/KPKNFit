package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.UnitModeV2
import java.text.NumberFormat
import java.util.Locale

/** Ejercicio y unidad de la serie mostrada; la unidad viene de la definición activa, no del log. */
internal data class RestCompletedSetSummary(
    val exerciseName: String,
    val set: CompletedSet,
    val customUnit: String?,
)

/** Cantidad ejecutada del descanso, respetando la unidad registrada y conservando las etiquetas históricas de REPS/TIME. */
internal fun restLastSetQuantityText(
    set: CompletedSet,
    customUnit: String?,
    timeSuffix: String = " s",
): String {
    val payload = set.recordedPayloadV3
    val homologated = set.homologatedResultV3
    val outcome = set.setOutcomeV2
    val unitMode = payload?.unitMode ?: homologated?.unitMode ?: outcome?.unitMode
        ?: if (set.timeSeconds != null) UnitModeV2.TIME else UnitModeV2.REPS

    val recordedResult = homologated?.takeIf { it.unitMode == unitMode }?.actualValue
        ?: outcome?.takeIf { it.unitMode == unitMode }?.actualValue
    val value = when (unitMode) {
        UnitModeV2.TIME -> set.timeSeconds?.toDouble()
            ?: payload?.takeIf { it.unitMode == unitMode }?.durationSeconds?.toDouble()
            ?: recordedResult
        UnitModeV2.REPS -> set.reps.toDouble()
        UnitModeV2.DISTANCE -> recordedResult
            ?: payload?.takeIf { it.unitMode == unitMode }?.completedReps?.toDouble()
            ?: set.reps.takeIf { it > 0 }?.toDouble()
        UnitModeV2.CUSTOM -> recordedResult
            ?: payload?.takeIf { it.unitMode == unitMode }?.completedReps?.toDouble()
            ?: set.reps.takeIf { it > 0 }?.toDouble()
    }
    val amount = value?.takeIf { it.isFinite() }?.let(::formatRestQuantityNumber)
    return when (unitMode) {
        UnitModeV2.TIME -> amount?.let { "$it$timeSuffix" } ?: "—$timeSuffix"
        UnitModeV2.REPS -> "${set.reps} reps"
        UnitModeV2.DISTANCE -> amount?.let { "$it m" } ?: "— m"
        UnitModeV2.CUSTOM -> {
            val unit = customUnit?.trim()?.takeIf(String::isNotBlank) ?: "u"
            amount?.let { "$it $unit" } ?: "— $unit"
        }
    }
}

/** Figura compartida por la tarjeta compacta y el descanso expandido. */
internal fun restLastSetFigureText(
    set: CompletedSet,
    customUnit: String?,
    timeSuffix: String = " s",
): String {
    val load = if (set.weight > 0.0) "${formatRestLoad(set.weight)} kg" else "Peso corporal"
    return "$load × ${restLastSetQuantityText(set, customUnit, timeSuffix)}"
}

private fun formatRestLoad(weight: Double): String = if (weight % 1.0 == 0.0) {
    weight.toInt().toString()
} else {
    String.format(Locale.forLanguageTag("es"), "%.1f", weight)
}

private fun formatRestQuantityNumber(value: Double): String = NumberFormat
    .getNumberInstance(Locale.forLanguageTag("es"))
    .apply {
        isGroupingUsed = false
        maximumFractionDigits = 3
        minimumFractionDigits = 0
    }
    .format(value)
