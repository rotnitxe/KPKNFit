package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.estimateRepsFromPercent1RM
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import java.math.BigDecimal
import java.math.RoundingMode

/** Presentation only. AMRAP keeps its recorder and exposes the result slot for CAL3. */
internal enum class LiveSetCardMode(val label: String) {
    REPS("Reps"), RM("RM"), SOLO_RPE("Solo RPE"), TIME("Tiempo"),
    DISTANCE("Distancia"), CUSTOM("Personalizado"), AMRAP("AMRAP"),
}

internal fun liveSetUnitMode(exercise: Exercise, set: ExerciseSet): UnitModeV2 =
    set.resolveUnitModeV2(exercise.trainingMode)

internal fun liveSetCardMode(exercise: Exercise, set: ExerciseSet, unit: UnitModeV2): LiveSetCardMode = when (unit) {
    UnitModeV2.TIME -> LiveSetCardMode.TIME
    UnitModeV2.DISTANCE -> LiveSetCardMode.DISTANCE
    UnitModeV2.CUSTOM -> LiveSetCardMode.CUSTOM
    UnitModeV2.REPS -> when {
        set.purpose.isAmrap -> LiveSetCardMode.AMRAP
        exercise.trainingMode == TrainingMode.SOLO_RPE -> LiveSetCardMode.SOLO_RPE
        exercise.trainingMode == TrainingMode.RM || (set.targetPercentageRM != null && set.intensityMode in listOf(IntensityMode.LOAD, IntensityMode.SOLO_RM)) -> LiveSetCardMode.RM
        else -> LiveSetCardMode.REPS
    }
}

/** Accepts legacy seconds and min:s. An invalid duration never turns into a different valid number. */
internal fun parseLiveDurationSeconds(text: String): Int? {
    val value = text.trim()
    if (value.matches(Regex("[0-9]+"))) return value.toIntOrNull()
    val parts = Regex("([0-9]+):([0-9]{1,2})").matchEntire(value)?.groupValues ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    if (minutes > (Int.MAX_VALUE - seconds) / 60L) return null
    return (minutes * 60 + seconds).toInt()
}

internal fun formatLiveDuration(seconds: Int): String =
    "${seconds.coerceAtLeast(0) / 60}:${(seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"

/** Live inputs must round-trip fractional loads and quantities without the legacy one-decimal truncation. */
internal fun Double.toLiveNumberString(): String =
    if (isFinite()) BigDecimal.valueOf(this).stripTrailingZeros().toPlainString().replace('.', ',') else "—"

/** A derived ratio is rounded only for display; its full value stays in the draft. */
internal fun Double.toLivePercentString(): String =
    if (isFinite()) BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',') else "—"

internal fun liveQuantityNumber(text: String, unit: UnitModeV2): Double? = when (unit) {
    UnitModeV2.TIME -> parseLiveDurationSeconds(text)?.toDouble()
    else -> text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** The legacy reps field truncates non-rep quantities; the evaluated result retains the real value. */
internal fun completedLiveQuantity(set: CompletedSet, unit: UnitModeV2): Double? = when (unit) {
    UnitModeV2.TIME -> (set.timeSeconds ?: set.recordedPayloadV3?.durationSeconds)?.toDouble()
    UnitModeV2.DISTANCE, UnitModeV2.CUSTOM -> set.homologatedResultV3?.actualValue
        ?: set.setOutcomeV2?.actualValue
        ?: set.distanceKm?.takeIf { unit == UnitModeV2.DISTANCE }?.times(1000.0)
        ?: set.reps.toDouble()
    UnitModeV2.REPS -> set.reps.toDouble()
}

internal fun liveRmReference(exercise: Exercise, ghost: CompletedSet?): Double? =
    resolveReferenceCapacity(exercise.copy(trainingMode = TrainingMode.RM))?.takeIf { it.isFinite() && it > 0 }
        ?: ghost?.takeIf { it.weight > 0 && it.reps > 0 }
            ?.let { calculateHybrid1RM(it.weight, it.reps) }?.takeIf { it.isFinite() && it > 0 }

internal data class LiveRmValues(
    val percent: Double,
    val weight: Double?,
    val estimatedReps: Int?,
    val oneRm: Double?,
    val trainingMax: Double?,
    val usesTrainingMax: Boolean,
) {
    val loadReference: Double? get() = if (usesTrainingMax) trainingMax else oneRm
}

/** Uses the same rounding, TM contract and rep estimate as Calculations.kt. */
internal fun liveRmValues(exercise: Exercise, set: ExerciseSet, percent: Double, oneRm: Double?, actualWeight: Double? = null): LiveRmValues {
    val tm = exercise.referenceTrainingMax?.takeIf { it.isFinite() && it > 0 }
    val usesTm = set.loadBasis == LoadBasis.PERCENT_TM
    val actual = actualWeight?.takeIf { it.isFinite() && it > 0 }
    val bounded = if (usesTm || actual != null) percent.coerceAtLeast(0.01) else percent.coerceIn(40.0, 100.0)
    val reference = oneRm?.takeIf { it.isFinite() && it > 0 }
    val weight = actual ?: calculateSuggestedLoad(
        exercise.copy(trainingMode = TrainingMode.RM, reference1RM = reference, prFor1RM = null),
        set.copy(targetPercentageRM = bounded),
    )?.takeIf { it.isFinite() && it > 0 }
    val estimate = if (reference != null && weight != null) estimateRepsFromPercent1RM(weight / reference * 100) else null
    return LiveRmValues(bounded, weight, estimate, reference, tm, usesTm)
}

/** Monotonic stopwatch. Pausing keeps subsecond time; a completed goal still requires the line's ✓. */
internal data class LiveSetClockState(val startedAtMs: Long? = null, val accumulatedMs: Long = 0L) {
    val running: Boolean get() = startedAtMs != null
    private fun elapsedMs(nowMs: Long): Long = accumulatedMs + (startedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)
    fun seconds(nowMs: Long, target: Int?): Int = (elapsedMs(nowMs) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        .let { if (target != null && target > 0) it.coerceAtMost(target) else it }
    fun complete(nowMs: Long, target: Int?): Boolean = target != null && target > 0 && seconds(nowMs, target) >= target
    fun start(nowMs: Long, restart: Boolean = false): LiveSetClockState =
        if (running) this else copy(startedAtMs = nowMs, accumulatedMs = if (restart) 0L else accumulatedMs)
    fun stop(nowMs: Long, target: Int?): LiveSetClockState = copy(startedAtMs = null,
        accumulatedMs = elapsedMs(nowMs).let { if (target != null && target > 0) it.coerceAtMost(target * 1000L) else it })
}
