package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.NutritionCalibrationProfile
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

data class CalibrationWeightPoint(val date: LocalDate, val weightKg: Double)

data class NutritionCalibrationInput(
    val baselineKcal: Int?,
    val currentKcal: Int?,
    val weightPoints: List<CalibrationWeightPoint>,
    val completeIntakeDays: Set<LocalDate>,
    val nowEpochMs: Long,
    val planChangedAtEpochMs: Long? = null,
)

data class NutritionCalibrationResult(
    val profile: NutritionCalibrationProfile,
    val weeklyWeightChangeKg: Double? = null,
)

/**
 * Conservative energy calibration. It never produces an adjustment until the
 * minimum sample is present, and it caps each check-in at ±150 kcal.
 */
object NutritionCalibrationEngine {
    const val MINIMUM_DAYS = 14
    const val TARGET_DAYS = 21
    const val MIN_WEIGHT_READINGS = 7
    const val MIN_COMPLETE_DAYS = 10
    const val EWMA_HALF_LIFE_DAYS = 7.0
    private const val KCAL_PER_KG = 7700.0

    fun evaluate(input: NutritionCalibrationInput): NutritionCalibrationResult {
        val points = input.weightPoints
            .filter { it.weightKg.isFinite() && it.weightKg > 0.0 }
            .groupBy { it.date }
            .map { (date, sameDay) ->
                val values = sameDay.map { it.weightKg }.sorted()
                val middle = values.size / 2
                val median = if (values.size % 2 == 0) {
                    (values[middle - 1] + values[middle]) / 2.0
                } else values[middle]
                CalibrationWeightPoint(date, median)
            }
            .sortedBy { it.date }
        val smoothed = ewma(points)
        val observedDays = input.completeIntakeDays.size.coerceAtLeast(
            points.map { it.date }.toSet().size,
        )
        val enough = observedDays >= MINIMUM_DAYS &&
            points.size >= MIN_WEIGHT_READINGS &&
            input.completeIntakeDays.size >= MIN_COMPLETE_DAYS
        val planChangeTooRecent = input.planChangedAtEpochMs?.let {
            input.nowEpochMs - it < MINIMUM_DAYS * 86_400_000L
        } == true
        val weeklyChange = if (smoothed.size >= 2) {
            // Use the final EWMA interval rather than a noisy first/last
            // difference. The interval is calendar-time aware, so irregular
            // weigh-ins do not pretend to be equally spaced samples.
            val previous = smoothed[smoothed.lastIndex - 1]
            val latest = smoothed.last()
            val spanDays = (latest.date.toEpochDay() - previous.date.toEpochDay()).coerceAtLeast(1)
            (latest.weightKg - previous.weightKg) / spanDays * 7.0
        } else null
        val adjustment = if (enough && !planChangeTooRecent && weeklyChange != null) {
            // Losing weight requires adding calories; gaining requires removing them.
            (-weeklyChange * KCAL_PER_KG / 7.0).roundToInt().coerceIn(-150, 150)
        } else null
        val profile = NutritionCalibrationProfile(
            baselineKcal = input.baselineKcal,
            currentKcal = input.currentKcal,
            recommendedAdjustmentKcal = adjustment,
            startWeightKg = points.firstOrNull()?.weightKg,
            latestWeightKg = points.lastOrNull()?.weightKg,
            weightReadings = points.size,
            completeDays = input.completeIntakeDays.size,
            observedDays = observedDays,
            ewmaHalfLifeDays = EWMA_HALF_LIFE_DAYS,
            minimumDays = MINIMUM_DAYS,
            targetDays = TARGET_DAYS,
            status = when {
                planChangeTooRecent -> "waiting_after_plan_change"
                enough -> "ready"
                observedDays >= MINIMUM_DAYS -> "needs_more_weights_or_complete_days"
                else -> "incomplete"
            },
            updatedAtEpochMs = input.nowEpochMs,
        )
        return NutritionCalibrationResult(profile, weeklyChange)
    }

    private fun ewma(points: List<CalibrationWeightPoint>): List<CalibrationWeightPoint> {
        if (points.isEmpty()) return emptyList()
        var previousValue = points.first().weightKg
        var previousDate = points.first().date
        return points.mapIndexed { index, point ->
            if (index == 0) {
                previousValue = point.weightKg
            } else {
                val deltaDays = (point.date.toEpochDay() - previousDate.toEpochDay()).coerceAtLeast(0)
                val alpha = 1.0 - exp(-ln(2.0) / EWMA_HALF_LIFE_DAYS * deltaDays)
                previousValue = alpha * point.weightKg + (1.0 - alpha) * previousValue
            }
            previousDate = point.date
            point.copy(weightKg = previousValue)
        }
    }
}
