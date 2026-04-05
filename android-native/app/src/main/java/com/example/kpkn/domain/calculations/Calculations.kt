package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

// ─── 1RM Formulas ────────────────────────────────────────────────────────────
// 1:1 port from utils/calculations.ts

/**
 * Brzycki formula. Best for ≤10 reps.
 * Partial reps NOT included — they don't contribute to 1RM.
 */
fun calculateBrzycki1RM(weight: Double, reps: Int, isAmrap: Boolean = false): Double {
    if (weight <= 0 || reps <= 0) return 0.0
    if (reps == 1) return weight
    val effectiveReps = reps.coerceAtMost(30)
    var e1rm = weight * (36.0 / (37 - effectiveReps))
    if (isAmrap && reps > 3) e1rm *= 1.025
    return (e1rm * 10).toLong() / 10.0
}

/** Epley formula. Better for higher reps (11–20). */
fun calculateEpley1RM(weight: Double, reps: Int): Double {
    if (weight <= 0 || reps <= 0) return 0.0
    if (reps == 1) return weight
    val e1rm = weight * (1 + reps / 30.0)
    return (e1rm * 10).toLong() / 10.0
}

/**
 * Lander formula.
 * e1RM = (100 * weight) / (101.3 - 2.67123 * reps)
 */
fun calculateLander1RM(weight: Double, reps: Int): Double {
    if (weight <= 0 || reps <= 0) return 0.0
    if (reps == 1) return weight
    val denominator = 101.3 - 2.67123 * reps
    if (denominator <= 0) return 0.0
    return (weight * 10000 / denominator).toLong() / 100.0
}

/**
 * Hybrid: Brzycki ≤10, Epley 11–20, smooth extrapolation >20.
 * 1:1 port of calculateHybrid1RM from calculations.ts.
 */
fun calculateHybrid1RM(weight: Double, reps: Int, isAmrap: Boolean = false): Double {
    if (weight <= 0 || reps <= 0) return 0.0
    if (reps == 1) return weight
    val r = reps.coerceAtMost(50)
    val e1rm = when {
        r <= 10 -> weight * (36.0 / (37 - r))
        r <= 20 -> weight * (1 + r / 30.0)
        else    -> weight * (1 + 20.0 / 30) * (1 + (r - 20) / 80.0).pow(0.9)
    }
    val adjusted = if (isAmrap && reps > 3) e1rm * 1.025 else e1rm
    return (adjusted * 10).toLong() / 10.0
}

/** Inverse of Hybrid1RM: weight from 1RM and target reps. */
fun calculateWeightFrom1RM(e1rm: Double, reps: Int): Double {
    if (reps <= 0 || e1rm <= 0) return 0.0
    if (reps == 1) return e1rm
    val r = reps.coerceAtMost(50)
    val weight = when {
        r <= 10 -> e1rm * ((37 - r) / 36.0)
        r <= 20 -> e1rm / (1 + r / 30.0)
        else    -> e1rm / ((1 + 20.0 / 30) * (1 + (r - 20) / 80.0).pow(0.9))
    }
    return maxOf(0.0, (weight * 10).toLong() / 10.0)
}

/**
 * Weight suggestion from 1RM + set intensity (RPE/RIR/failure).
 * 1:1 port of calculateWeightFrom1RMAndIntensity.
 */
fun calculateWeightFrom1RMAndIntensity(reference1RM: Double, set: ExerciseSet): Double? {
    if (reference1RM <= 0) return null
    val reps = set.targetReps ?: return null
    if (reps <= 0) return null

    val effectiveReps = when {
        set.intensityMode == IntensityMode.FAILURE ||
        set.intensityMode == IntensityMode.AMRAP ||
        set.intensityMode == IntensityMode.SOLO_RM -> reps
        set.targetRIR != null -> reps + set.targetRIR
        set.targetRPE != null -> reps + (10 - set.targetRPE).toInt()
        else -> reps + 2   // default RPE 8
    }
    if (effectiveReps <= 0) return null
    val weight = calculateWeightFrom1RM(reference1RM, effectiveReps)
    return if (weight > 0) weight else null
}

// ─── RPE / RIR conversions ───────────────────────────────────────────────────

fun rpeToRir(rpe: Double): Int = maxOf(0, (10 - maxOf(0.0, rpe)).toInt())
fun rirToRpe(rir: Int): Double = maxOf(0.0, 10 - maxOf(0, rir).toDouble())

// ─── FFMI ────────────────────────────────────────────────────────────────────
// 1:1 port of calculateFFMI from calculations.ts

data class FfmiResult(
    val ffmi: Double,
    val normalizedFfmi: Double,
    val interpretation: String,
    val leanBodyMass: Double,
)

fun calculateFFMI(heightCm: Double, weightKg: Double, bodyFatPercent: Double): FfmiResult? {
    if (heightCm <= 0 || weightKg <= 0 || bodyFatPercent < 0) return null
    val heightM = heightCm / 100.0
    val leanBodyMass = weightKg * (1 - bodyFatPercent / 100.0)
    val ffmi = leanBodyMass / (heightM * heightM)
    val normalizedFfmi = ffmi + 6.1 * (1.8 - heightM)
    val interpretation = when {
        normalizedFfmi >= 26 -> "Superior/Elite"
        normalizedFfmi >= 22 -> "Excelente"
        normalizedFfmi >= 20 -> "Promedio"
        else                 -> "Novato"
    }
    return FfmiResult(
        ffmi = (ffmi * 10).toLong() / 10.0,
        normalizedFfmi = (normalizedFfmi * 10).toLong() / 10.0,
        interpretation = interpretation,
        leanBodyMass = (leanBodyMass * 10).toLong() / 10.0,
    )
}

// ─── IPF GL Points ───────────────────────────────────────────────────────────
// 1:1 port of calculateIPFGLPoints from calculations.ts

enum class IpfEquipment { CLASSIC, EQUIPPED }
enum class IpfLift { TOTAL, BENCH, SQUAT, DEADLIFT }

fun calculateIPFGLPoints(
    totalLifted: Double,
    bodyWeight: Double,
    gender: String,
    equipment: IpfEquipment,
    weightUnit: String = "kg",
): Double {
    if (totalLifted <= 0 || bodyWeight <= 0) return 0.0
    val isFemale = gender == "female" || gender == "transfemale"
    var bwKg = if (weightUnit == "lbs") bodyWeight * 0.45359237 else bodyWeight
    val totalKg = if (weightUnit == "lbs") totalLifted * 0.45359237 else totalLifted

    // Minimum bodyweight guard (same as TS)
    bwKg = if (isFemale) bwKg.coerceAtLeast(35.0) else bwKg.coerceAtLeast(40.0)

    // Coefficients: A, B, C per gender+equipment (classic-total)
    data class Coeffs(val a: Double, val b: Double, val c: Double)
    val coeffs = when {
        !isFemale && equipment == IpfEquipment.EQUIPPED -> Coeffs(1236.25115, 1449.21864, 0.01644)
        !isFemale && equipment == IpfEquipment.CLASSIC  -> Coeffs(1199.72839, 1025.18162, 0.00921)
        isFemale && equipment == IpfEquipment.EQUIPPED  -> Coeffs(758.63878,  949.31382,  0.02435)
        else                                             -> Coeffs(610.32796,  1045.59282, 0.03048)
    }
    val denominator = coeffs.a - coeffs.b * exp(-coeffs.c * bwKg)
    if (denominator == 0.0) return 0.0
    val coefficient = 100.0 / denominator
    return (coefficient * totalKg * 100).toLong() / 100.0
}

// ─── Weight rounding ─────────────────────────────────────────────────────────

fun roundWeight(weight: Double, unit: String = "kg"): Double {
    val increment = if (unit == "lbs") 2.5 else 1.25
    return (weight / increment).toLong() * increment
}

fun estimatePercent1RM(repsToFailure: Int): Double {
    if (repsToFailure <= 0) return 100.0
    val percent = when {
        repsToFailure <= 1 -> 100.0
        repsToFailure <= 3 -> 100.0 - ((repsToFailure - 1) * 4.0)
        repsToFailure <= 6 -> 92.0 - ((repsToFailure - 3) * 3.0)
        repsToFailure <= 10 -> 83.0 - ((repsToFailure - 6) * 2.0)
        else -> maxOf(45.0, 75.0 - ((repsToFailure - 10) * 1.3))
    }
    return (percent * 10).roundToInt() / 10.0
}

fun getEffectiveRepsForRM(set: ExerciseSet): Int? {
    val reps = set.targetReps ?: return null
    return when (set.intensityMode) {
        IntensityMode.FAILURE, IntensityMode.AMRAP, IntensityMode.SOLO_RM -> reps
        IntensityMode.RIR -> reps + (set.targetRIR ?: 0)
        IntensityMode.RPE -> reps + (10 - (set.targetRPE ?: 8.0)).roundToInt()
        else -> reps + 2
    }.coerceAtLeast(1)
}

fun suggestRestSeconds(
    setCount: Int,
    averageRpe: Double = 8.0,
    averagePercent1RM: Double? = null,
): Int {
    val base = when {
        averagePercent1RM != null && averagePercent1RM >= 88 -> 240
        averagePercent1RM != null && averagePercent1RM >= 80 -> 180
        averageRpe >= 9.5 -> 210
        averageRpe >= 8.5 -> 150
        averageRpe >= 7.5 -> 120
        else -> 90
    }
    val densityPenalty = when {
        setCount >= 8 -> 30
        setCount >= 5 -> 15
        else -> 0
    }
    return (base + densityPenalty).coerceIn(45, 300)
}

fun estimateSessionDurationMinutes(totalSets: Int, averageRestSeconds: Int): Int {
    val workSeconds = totalSets * 55
    val restSeconds = totalSets * averageRestSeconds
    val transitions = (totalSets / 3) * 20
    return ((workSeconds + restSeconds + transitions) / 60.0).roundToInt().coerceAtLeast(5)
}
