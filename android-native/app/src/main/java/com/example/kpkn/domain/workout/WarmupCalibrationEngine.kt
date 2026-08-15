package com.example.kpkn.domain.workout

/** A deliberately small vocabulary suitable for both UI and voice input. */
enum class WarmupEffort {
    LIGHT,
    NORMAL,
    HEAVY,
}

data class WarmupEffortReport(
    val warmupIndex: Int,
    val effort: WarmupEffort,
)

/**
 * Pure input for calibrating the remaining warm-up work.
 *
 * Percentages are accepted as either fractions (0.5) or legacy whole percentages
 * (50.0), so an old session cannot silently produce a 50x load.
 */
data class WarmupCalibrationInput(
    val programmedPercentages: List<Double>,
    val reference1RMKg: Double? = null,
    val currentWorkingLoadKg: Double? = null,
    val reports: List<WarmupEffortReport> = emptyList(),
)

data class WarmupCalibrationResult(
    val adjustmentFactor: Double,
    val remainingWarmupLoadsKg: List<Double?>,
    val firstEffectiveLoadKg: Double?,
    val note: String?,
)

/**
 * Converts warm-up feedback into a conservative adjustment.
 *
 * The engine is intentionally independent of Android and persistence. A single
 * report moves the plan by 2.5%; mixed reports average out, and the total
 * correction is capped at 5% so one subjective report cannot overreact.
 */
object WarmupCalibrationEngine {
    private const val REPORT_STEP = 0.025
    private const val MAX_CORRECTION = 0.05

    fun calibrate(input: WarmupCalibrationInput): WarmupCalibrationResult {
        val reference = input.reference1RMKg?.takeIf { it > 0.0 }
        val lastReportedIndex = input.reports.maxOfOrNull { it.warmupIndex } ?: -1
        val rawCorrection = input.reports
            .map { report ->
                when (report.effort) {
                    WarmupEffort.LIGHT -> REPORT_STEP
                    WarmupEffort.NORMAL -> 0.0
                    WarmupEffort.HEAVY -> -REPORT_STEP
                }
            }
            .averageOrNull() ?: 0.0
        val factor = (1.0 + rawCorrection).coerceIn(1.0 - MAX_CORRECTION, 1.0 + MAX_CORRECTION)
        val remainingLoads = input.programmedPercentages.mapIndexed { index, rawPercentage ->
            val percentage = normalizePercentage(rawPercentage)
            if (reference == null || index <= lastReportedIndex) null else reference * percentage * factor
        }
        val firstEffective = (input.currentWorkingLoadKg ?: reference)
            ?.takeIf { it > 0.0 }
            ?.times(factor)
        val note = when {
            reference == null -> "Sin referencia de carga: registra el peso usado para calibrar las siguientes series."
            input.reports.isEmpty() -> null
            factor > 1.0 -> "Ajuste conservador: +${((factor - 1.0) * 100).toTrimmedPercent()}% para la primera serie efectiva."
            factor < 1.0 -> "Ajuste conservador: ${((factor - 1.0) * 100).toTrimmedPercent()}% para la primera serie efectiva."
            else -> "Sin ajuste: los reportes de aproximación fueron normales."
        }
        return WarmupCalibrationResult(
            adjustmentFactor = factor,
            remainingWarmupLoadsKg = remainingLoads,
            firstEffectiveLoadKg = firstEffective,
            note = note,
        )
    }

    /**
     * Calibrates the session's warm-up percentages against its working-load
     * anchor. The editor stores these percentages as a fraction of the first
     * effective load, while [calibrate] also remains available for callers
     * that work directly from a true 1RM reference.
     */
    fun calibrateWorkingLoad(
        programmedPercentages: List<Double>,
        workingLoadKg: Double?,
        reports: List<WarmupEffortReport> = emptyList(),
    ): WarmupCalibrationResult = calibrate(
        WarmupCalibrationInput(
            programmedPercentages = programmedPercentages,
            reference1RMKg = workingLoadKg,
            currentWorkingLoadKg = workingLoadKg,
            reports = reports,
        ),
    )

    fun normalizePercentage(rawPercentage: Double): Double =
        if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage

    /** Applies a small first-working-set correction from the athlete's warm-up RPE. */
    fun adjustWorkingLoad(baseKg: Double, warmupRpe: Double?): Double {
        if (baseKg <= 0.0 || warmupRpe == null) return baseKg
        val factor = when {
            warmupRpe >= 9.0 -> 0.975
            warmupRpe <= 5.0 -> 1.025
            else -> 1.0
        }
        return baseKg * factor
    }

    fun explanation(warmupRpe: Double?): String? = when {
        warmupRpe == null -> null
        warmupRpe >= 9.0 -> "Ajuste de aproximación: -2,5% por esfuerzo alto"
        warmupRpe <= 5.0 -> "Ajuste de aproximación: +2,5% por esfuerzo bajo"
        else -> "Ajuste de aproximación: sin cambio"
    }

    fun generateVoiceFeedback(
        weightKg: Double?,
        effort: WarmupEffort?,
        result: WarmupCalibrationResult,
        nextWarmupIndex: Int?,
    ): String {
        val effortLabel = when (effort) {
            WarmupEffort.LIGHT -> "liviana"
            WarmupEffort.NORMAL -> "a ritmo normal"
            WarmupEffort.HEAVY -> "pesada"
            null -> "completada"
        }
        val weightPart = weightKg?.let { " con ${it.toTrimmedLoad()} kilos" }.orEmpty()
        val nextLoad = nextWarmupIndex?.let { result.remainingWarmupLoadsKg.getOrNull(it) }
        val effectiveLoad = result.firstEffectiveLoadKg

        return when (effort) {
            WarmupEffort.HEAVY -> {
                val nextPart = if (nextLoad != null && nextLoad > 0) {
                    " Siguiente aproximación calibrada a ${nextLoad.toTrimmedLoad()} kilos."
                } else if (effectiveLoad != null && effectiveLoad > 0) {
                    " Primera serie efectiva ajustada a ${effectiveLoad.toTrimmedLoad()} kilos para cuidar tu fatiga."
                } else ""
                "Anotado$weightPart, $effortLabel.$nextPart"
            }
            WarmupEffort.LIGHT -> {
                val nextPart = if (nextLoad != null && nextLoad > 0) {
                    " Siguiente aproximación sugerida: ${nextLoad.toTrimmedLoad()} kilos."
                } else if (effectiveLoad != null && effectiveLoad > 0) {
                    " Buena velocidad neural. Serie efectiva proyectada en ${effectiveLoad.toTrimmedLoad()} kilos."
                } else ""
                "Anotado$weightPart, $effortLabel.$nextPart"
            }
            WarmupEffort.NORMAL, null -> {
                val nextPart = if (nextLoad != null && nextLoad > 0) {
                    " Siguiente aproximación: ${nextLoad.toTrimmedLoad()} kilos."
                } else if (effectiveLoad != null && effectiveLoad > 0) {
                    " Primera serie efectiva en ${effectiveLoad.toTrimmedLoad()} kilos."
                } else ""
                "Anotado$weightPart, $effortLabel.$nextPart"
            }
        }
    }

    private fun Double.toTrimmedLoad(): String {
        val rounded = kotlin.math.round(this * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    private fun List<Double>.averageOrNull(): Double? = takeIf { isNotEmpty() }?.average()

    private fun Double.toTrimmedPercent(): String {
        val rounded = kotlin.math.round(this * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
