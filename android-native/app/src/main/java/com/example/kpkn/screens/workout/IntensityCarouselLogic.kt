package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.IntensityMode

internal const val INTENSITY_CAROUSEL_FAILURE_LABEL = "FALLO"

data class IntensityCarouselItem(
    val display: String,
    val isFailure: Boolean = false,
    val mode: IntensityMode? = null,
    val numericValue: Double? = null,
)

data class IntensityCarouselSelection(
    val reachedFailure: Boolean,
    val reportedIntensityMode: IntensityMode?,
    val intensityText: String,
)

internal fun buildIntensityCarouselItems(
    plannedIntensityMode: IntensityMode?,
    reportedIntensityMode: IntensityMode?,
    targetRir: Int?,
    targetRpe: Double?,
): List<IntensityCarouselItem> {
    if (plannedIntensityMode == IntensityMode.FAILURE) {
        return buildPlannedFailureCarouselItems(targetRir, targetRpe)
    }
    return when (reportedIntensityMode) {
        IntensityMode.RIR -> buildRirCarouselItems(targetRir)
        IntensityMode.RPE -> buildRpeCarouselItems()
        else -> buildRpeCarouselItems()
    }
}

private fun buildRirCarouselItems(targetRir: Int?): List<IntensityCarouselItem> {
    val maxRir = maxOf(10, (targetRir ?: 2) + 3)
    return (maxRir downTo 0).map { rir ->
        IntensityCarouselItem(
            display = rir.toString(),
            mode = IntensityMode.RIR,
            numericValue = rir.toDouble(),
        )
    } + IntensityCarouselItem(
        display = INTENSITY_CAROUSEL_FAILURE_LABEL,
        isFailure = true,
    )
}

private fun buildRpeCarouselItems(): List<IntensityCarouselItem> {
    val steps = mutableListOf<IntensityCarouselItem>()
    var value = 0.0
    while (value <= 10.0 + 0.001) {
        steps += IntensityCarouselItem(
            display = value.toTrimmedNumberString(),
            mode = IntensityMode.RPE,
            numericValue = value,
        )
        value += 0.5
    }
    return steps + IntensityCarouselItem(
        display = INTENSITY_CAROUSEL_FAILURE_LABEL,
        isFailure = true,
    )
}

private fun buildPlannedFailureCarouselItems(
    targetRir: Int?,
    targetRpe: Double?,
): List<IntensityCarouselItem> {
    val rpeStart = maxOf(6, ((targetRpe ?: 8.0) - 2.0).toInt()).coerceAtMost(10)
    val rpeItems = (rpeStart..10).map { rpe ->
        IntensityCarouselItem(
            display = rpe.toString(),
            mode = IntensityMode.RPE,
            numericValue = rpe.toDouble(),
        )
    }
    val failure = IntensityCarouselItem(
        display = INTENSITY_CAROUSEL_FAILURE_LABEL,
        isFailure = true,
    )
    val maxRir = maxOf(4, (targetRir ?: 2) + 2)
    val rirItems = (0..maxRir).map { rir ->
        IntensityCarouselItem(
            display = rir.toString(),
            mode = IntensityMode.RIR,
            numericValue = rir.toDouble(),
        )
    }
    return rpeItems + failure + rirItems
}

internal fun intensityCarouselIndexFromState(
    items: List<IntensityCarouselItem>,
    reachedFailure: Boolean,
    intensityText: String,
    reportedIntensityMode: IntensityMode?,
    plannedRir: Int? = null,
    plannedRpe: Double? = null,
): Int {
    if (items.isEmpty()) return 0
    if (reachedFailure) {
        return items.indexOfFirst { it.isFailure }.takeIf { it >= 0 } ?: items.lastIndex
    }
    val numeric = intensityText.toDoubleOrNull()
        ?: when (reportedIntensityMode) {
            IntensityMode.RIR -> plannedRir?.toDouble()
            IntensityMode.RPE -> plannedRpe
            else -> plannedRpe ?: plannedRir?.toDouble()
        }
    if (numeric != null) {
        val modeHint = reportedIntensityMode?.takeIf { it != IntensityMode.FAILURE }
        items.indexOfFirst { item ->
            !item.isFailure &&
                item.numericValue != null &&
                kotlin.math.abs(item.numericValue - numeric) < 0.001 &&
                (modeHint == null || item.mode == modeHint)
        }.takeIf { it >= 0 }?.let { return it }
        items.indexOfFirst { item ->
            !item.isFailure &&
                item.numericValue != null &&
                kotlin.math.abs(item.numericValue - numeric) < 0.001
        }.takeIf { it >= 0 }?.let { return it }
    }
    if (reportedIntensityMode != null && reportedIntensityMode != IntensityMode.FAILURE) {
        items.indexOfFirst { !it.isFailure && it.mode == reportedIntensityMode }
            .takeIf { it >= 0 }
            ?.let { return it }
    }
    return items.indexOfFirst { !it.isFailure }.takeIf { it >= 0 } ?: 0
}

internal fun intensitySelectionFromCarouselItem(item: IntensityCarouselItem): IntensityCarouselSelection {
    if (item.isFailure) {
        return IntensityCarouselSelection(
            reachedFailure = true,
            reportedIntensityMode = null,
            intensityText = "",
        )
    }
    val text = when (item.mode) {
        IntensityMode.RIR -> item.numericValue?.toInt()?.toString().orEmpty()
        IntensityMode.RPE -> item.numericValue?.toTrimmedNumberString().orEmpty()
        else -> item.numericValue?.toTrimmedNumberString().orEmpty()
    }
    return IntensityCarouselSelection(
        reachedFailure = false,
        reportedIntensityMode = item.mode,
        intensityText = text,
    )
}

internal fun buildRepsCarouselMax(currentValue: Int, ghostValue: Int?, minimum: Int = 50): Int {
    return maxOf(minimum, currentValue + 10, ghostValue ?: 0)
}

internal fun repsCarouselIndexFromValue(valueText: String, maxReps: Int): Int {
    return (valueText.toIntOrNull() ?: 0).coerceIn(0, maxReps)
}

/**
 * The reps wheel always shows a concrete number (ghost/plan if the field is
 * still empty). Record/AUGE must use that visible number, not a blank/zero
 * leftover from the old typed field.
 */
internal fun effectiveCarouselRepsText(valueText: String, displayedGhost: String): String {
    val parsed = valueText.toIntOrNull()
    if (parsed != null && parsed > 0) return valueText.trim()
    val ghost = displayedGhost.toIntOrNull()
    if (ghost != null && ghost > 0) return displayedGhost.trim()
    return valueText.trim()
}

/**
 * Weight carousel shows ghost/suggested load when the field is empty.
 * Record must accept that visible number, not a blank leftover.
 */
internal fun effectiveCarouselWeightText(valueText: String, displayedGhost: String): String {
    val parsed = valueText.replace(',', '.').toDoubleOrNull()
    if (parsed != null && parsed > 0.0) return valueText.trim()
    val ghost = displayedGhost.replace(',', '.').toDoubleOrNull()
    if (ghost != null && ghost > 0.0) return displayedGhost.trim()
    return valueText.trim()
}

/** Prefer the wheel center while a fling/snap is still in flight. */
internal fun effectiveCarouselSelectedIndex(selectedIndex: Int, centeredIndex: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    val clampedSelected = selectedIndex.coerceIn(0, itemCount - 1)
    if (centeredIndex !in 0 until itemCount) return clampedSelected
    return centeredIndex
}

internal fun effectiveCarouselIntensityValue(
    intensityText: String,
    reachedFailure: Boolean,
    items: List<IntensityCarouselItem>,
    selectedIndex: Int,
): Double? {
    if (reachedFailure) return 10.0
    intensityText.toDoubleOrNull()?.let { return it }
    val item = items.getOrNull(selectedIndex) ?: return null
    if (item.isFailure) return 10.0
    return item.numericValue
}
