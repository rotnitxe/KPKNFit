package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round
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
    return round(e1rm * 10.0) / 10.0
}

/** Epley formula. Better for higher reps (11–20). */
fun calculateEpley1RM(weight: Double, reps: Int): Double {
    if (weight <= 0 || reps <= 0) return 0.0
    if (reps == 1) return weight
    val e1rm = weight * (1 + reps / 30.0)
    return round(e1rm * 10.0) / 10.0
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
    return round(weight * 10000.0 / denominator) / 100.0
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
    return round(adjusted * 10.0) / 10.0
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
    return maxOf(0.0, round(weight * 10.0) / 10.0)
}

/**
 * Weight suggestion from 1RM + set intensity (RPE/RIR/failure).
 * 1:1 port of calculateWeightFrom1RMAndIntensity.
 */
fun calculateWeightFrom1RMAndIntensity(reference1RM: Double, set: ExerciseSet): Double? {
    if (reference1RM <= 0) return null
    val reps = set.plannedRepAnchor() ?: return null
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

fun calculateGeneralizedCapacity(load: Double, metric: Double): Double {
    if (load <= 0 || metric <= 0) return 0.0
    if (metric <= 1.0) return load
    val normalizedMetric = metric.coerceAtMost(300.0)
    val capacity = when {
        normalizedMetric <= 10.0 -> load * (36.0 / (37.0 - normalizedMetric))
        normalizedMetric <= 20.0 -> load * (1.0 + normalizedMetric / 30.0)
        else -> load * (1 + 20.0 / 30.0) * (1 + (normalizedMetric - 20.0) / 80.0).pow(0.9)
    }
    return round(capacity * 10.0) / 10.0
}

fun calculateLoadFromGeneralizedCapacity(capacity: Double, metric: Double): Double {
    if (capacity <= 0 || metric <= 0) return 0.0
    if (metric <= 1.0) return capacity
    val normalizedMetric = metric.coerceAtMost(300.0)
    val load = when {
        normalizedMetric <= 10.0 -> capacity * ((37.0 - normalizedMetric) / 36.0)
        normalizedMetric <= 20.0 -> capacity / (1.0 + normalizedMetric / 30.0)
        else -> capacity / ((1 + 20.0 / 30.0) * (1 + (normalizedMetric - 20.0) / 80.0).pow(0.9))
    }
    return round(load * 10.0) / 10.0
}

fun estimateRepsFromPercent1RM(percent: Double): Int {
    val boundedPercent = percent.coerceIn(45.0, 100.0)
    return (1..30).minByOrNull { reps ->
        kotlin.math.abs(estimatePercent1RM(reps) - boundedPercent)
    } ?: 1
}

private fun roundSuggestedLoad(weight: Double): Double {
    // Saltos de 0.25 kg, nuncaando a ≤2 decimales.
    val stepped = (weight * 4.0).roundToInt() / 4.0
    return kotlin.math.round(stepped * 100.0) / 100.0
}

private fun assistanceFactor(metric: Double): Double {
    if (metric <= 0.0) return 0.0
    val normalizedMetric = metric.coerceAtMost(300.0)
    return when {
        normalizedMetric <= 1.0 -> 1.0
        normalizedMetric <= 10.0 -> 36.0 / (37.0 - normalizedMetric)
        normalizedMetric <= 20.0 -> 1.0 + normalizedMetric / 30.0
        else -> (1 + 20.0 / 30.0) * (1 + (normalizedMetric - 20.0) / 80.0).pow(0.9)
    }
}

private fun calculateAssistedLoadFromPr(
    prAssistance: Double,
    prMetric: Double,
    targetMetric: Double,
): Double {
    if (prAssistance <= 0.0 || prMetric <= 0.0 || targetMetric <= 0.0) return 0.0
    val prFactor = assistanceFactor(prMetric)
    if (prFactor <= 0.0) return 0.0
    val targetFactor = assistanceFactor(targetMetric)
    return prAssistance * (targetFactor / prFactor)
}

private fun plannedMetricForMode(set: ExerciseSet, trainingMode: TrainingMode): Double? = when (trainingMode) {
    TrainingMode.TIME -> set.targetDuration?.toDouble()
    TrainingMode.SOLO_RPE -> null
    else -> set.plannedRepAnchor()?.toDouble()
}

private fun effectiveMetricForSuggestion(set: ExerciseSet, trainingMode: TrainingMode): Double? {
    if (trainingMode == TrainingMode.RM) {
        val percent = set.targetPercentageRM ?: return null
        return estimateRepsFromPercent1RM(percent).toDouble()
    }
    val baseMetric = plannedMetricForMode(set, trainingMode) ?: return null
    return when (trainingMode) {
        TrainingMode.REPS -> when {
            set.intensityMode == IntensityMode.FAILURE ||
                set.intensityMode == IntensityMode.AMRAP ||
                set.intensityMode == IntensityMode.SOLO_RM -> baseMetric
            set.targetRIR != null -> baseMetric + set.targetRIR
            set.targetRPE != null -> baseMetric + (10 - set.targetRPE).roundToInt()
            else -> baseMetric + 2.0
        }
        TrainingMode.TIME,
        TrainingMode.DISTANCE,
        TrainingMode.CUSTOM,
        -> {
            val multiplier = when {
                set.intensityMode == IntensityMode.FAILURE ||
                    set.intensityMode == IntensityMode.AMRAP -> 1.0
                set.targetRIR != null -> 1.0 + (set.targetRIR * 0.05)
                set.targetRPE != null -> 1.0 + ((10.0 - set.targetRPE).coerceAtLeast(0.0) * 0.05)
                else -> 1.10
            }
            (baseMetric * multiplier).coerceAtLeast(1.0)
        }
        TrainingMode.SOLO_RPE -> null
        TrainingMode.RM -> null
        TrainingMode.AMRAP -> baseMetric
    }
}

fun resolveReferenceCapacity(exercise: Exercise): Double? {
    exercise.reference1RM?.takeIf { it > 0.0 }?.let { return it }
    val pr = exercise.prFor1RM ?: return null
    if (pr.weight <= 0 || pr.reps <= 0) return null
    return when (exercise.trainingMode) {
        TrainingMode.REPS,
        TrainingMode.RM,
        -> calculateHybrid1RM(pr.weight, pr.reps)
        TrainingMode.TIME,
        TrainingMode.DISTANCE,
        TrainingMode.CUSTOM,
        -> calculateGeneralizedCapacity(pr.weight, pr.reps.toDouble())
        TrainingMode.SOLO_RPE -> null
        TrainingMode.AMRAP -> calculateHybrid1RM(pr.weight, pr.reps)
    }
}

fun resolveReferenceCapacity(
    exercise: Exercise,
    history: List<WorkoutLog>,
): Double? {
    resolveReferenceCapacity(exercise)?.let { return it }

    val canonicalId = exercise.resolvedCanonicalExerciseId()
    return history.asSequence()
        .flatMap { it.completedExercises.asSequence() }
        .filter { completed -> completed.resolvedCanonicalExerciseId() == canonicalId }
        .flatMap { completed -> completed.sets.asSequence() }
        .filter { set -> !set.isWarmup && set.weight > 0.0 && set.reps > 0 }
        .map { set -> calculateHybrid1RM(set.weight, set.reps) }
        .maxOrNull()
}

fun calculateSuggestedLoad(exercise: Exercise, set: ExerciseSet): Double? {
    val loadMode = set.loadModeV2 ?: LoadModeV2.LOAD
    if (loadMode == LoadModeV2.BODYWEIGHT) return 0.0
    if (loadMode == LoadModeV2.ASSISTED) {
        val pr = exercise.prFor1RM ?: return null
        val targetMetric = effectiveMetricForSuggestion(set, exercise.trainingMode) ?: return null
        val suggestedAssistance = calculateAssistedLoadFromPr(
            prAssistance = pr.weight,
            prMetric = pr.reps.toDouble(),
            targetMetric = targetMetric,
        )
        return if (suggestedAssistance > 0.0) roundSuggestedLoad(suggestedAssistance) else null
    }

    val referenceCapacity = resolveReferenceCapacity(exercise) ?: return null
    val suggested = when (exercise.trainingMode) {
        TrainingMode.RM -> {
            val percent = (set.targetPercentageRM ?: estimatePercent1RM(set.plannedRepAnchor() ?: 1)).coerceIn(40.0, 100.0)
            referenceCapacity * percent / 100.0
        }
        TrainingMode.SOLO_RPE -> null
        TrainingMode.REPS -> {
            val effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode)?.roundToInt() ?: return null
            calculateWeightFrom1RM(referenceCapacity, effectiveMetric)
        }
        TrainingMode.TIME,
        TrainingMode.DISTANCE,
        TrainingMode.CUSTOM,
        -> {
            val effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode) ?: return null
            calculateLoadFromGeneralizedCapacity(referenceCapacity, effectiveMetric)
        }
        TrainingMode.AMRAP -> {
            val effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode)?.roundToInt() ?: return null
            calculateWeightFrom1RM(referenceCapacity, effectiveMetric)
        }
    } ?: return null
    return if (suggested > 0.0) roundSuggestedLoad(suggested) else null
}

fun calculateSuggestedLoad(
    exercise: Exercise,
    set: ExerciseSet,
    history: List<WorkoutLog>,
): Double? {
    val loadMode = set.loadModeV2 ?: LoadModeV2.LOAD
    if (loadMode == LoadModeV2.BODYWEIGHT) return 0.0
    if (loadMode == LoadModeV2.ASSISTED) {
        val pr = exercise.prFor1RM ?: return null
        val targetMetric = effectiveMetricForSuggestion(set, exercise.trainingMode) ?: return null
        val suggestedAssistance = calculateAssistedLoadFromPr(
            prAssistance = pr.weight,
            prMetric = pr.reps.toDouble(),
            targetMetric = targetMetric,
        )
        return if (suggestedAssistance > 0.0) roundSuggestedLoad(suggestedAssistance) else null
    }

    val referenceCapacity = resolveReferenceCapacity(exercise, history) ?: return null
    val suggested = when (exercise.trainingMode) {
        TrainingMode.RM -> {
            val percent = (set.targetPercentageRM ?: estimatePercent1RM(set.plannedRepAnchor() ?: 1)).coerceIn(40.0, 100.0)
            referenceCapacity * percent / 100.0
        }
        TrainingMode.SOLO_RPE -> null
        TrainingMode.REPS -> {
            val effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode)?.roundToInt() ?: return null
            calculateWeightFrom1RM(referenceCapacity, effectiveMetric)
        }
        TrainingMode.TIME,
        TrainingMode.DISTANCE,
        TrainingMode.CUSTOM,
        -> {
            val effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode) ?: return null
            calculateLoadFromGeneralizedCapacity(referenceCapacity, effectiveMetric)
        }
        TrainingMode.AMRAP -> {
            val effectiveMetric = effectiveMetricForSuggestion(set, exercise.trainingMode)?.roundToInt() ?: return null
            calculateWeightFrom1RM(referenceCapacity, effectiveMetric)
        }
    } ?: return null
    return if (suggested > 0.0) roundSuggestedLoad(suggested) else null
}

fun calculateEstimatedMetric(exercise: Exercise, set: ExerciseSet): Double? = when (exercise.trainingMode) {
    TrainingMode.RM -> {
        val percent = set.targetPercentageRM ?: return null
        estimateRepsFromPercent1RM(percent).toDouble()
    }
    TrainingMode.TIME -> set.targetDuration?.toDouble()
    TrainingMode.DISTANCE,
    TrainingMode.CUSTOM,
    TrainingMode.REPS,
    -> set.plannedRepAnchor()?.toDouble()
    TrainingMode.SOLO_RPE -> null
    TrainingMode.AMRAP -> set.plannedRepAnchor()?.toDouble()
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
        ffmi = round(ffmi * 10.0) / 10.0,
        normalizedFfmi = round(normalizedFfmi * 10.0) / 10.0,
        interpretation = interpretation,
        leanBodyMass = round(leanBodyMass * 10.0) / 10.0,
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
    return round(coefficient * totalKg * 100.0) / 100.0
}

// ─── Weight rounding ─────────────────────────────────────────────────────────

fun roundWeight(weight: Double, unit: String = "kg"): Double {
    val increment = if (unit == "lbs") 2.5 else 1.25
    val result = round(weight / increment) * increment
    return if (result < 0.0) 0.0 else result
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
    val reps = set.plannedRepAnchor() ?: return null
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

// ─── Session Time Breakdown ───────────────────────────────────────────────────

data class SessionTimeBreakdown(
    /** Tiempo de setup por ejercicio (ajuste de máquina, cambio de peso, etc.) */
    val setupSeconds: Int,
    /** Tiempo de ejecución de todas las series (trabajo real) */
    val executionSeconds: Int,
    /** Sumatoria total de todos los descansos (incluyendo supersets) */
    val restSeconds: Int,
    /** Tiempo de calentamiento/movilidad global + series de aproximación + movilidad por ejercicio */
    val warmupSeconds: Int,
    /** setupSeconds + executionSeconds + restSeconds + warmupSeconds */
    val totalSeconds: Int,
    val exerciseCount: Int,
    val totalSetCount: Int,
) {
    val totalMinutes: Int get() = (totalSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val setupMinutes: Int get() = (setupSeconds / 60.0).roundToInt()
    val executionMinutes: Int get() = (executionSeconds / 60.0).roundToInt()
    val restMinutes: Int get() = (restSeconds / 60.0).roundToInt()
    val warmupMinutes: Int get() = (warmupSeconds / 60.0).roundToInt()
}

/**
 * Calcula un desglose detallado del tiempo estimado de sesión.
 *
 * - Ejercicios TIME: usa [ExerciseSet.targetDuration] para el tiempo de ejecución.
 * - Dropsets programados: cada mini-drop añade ~6 s al execution (3 reps × 2 s).
 * - Rest-pause programados: cada mini-serie añade sus reps + tiempo de pausa al total.
 * - Supersets: el descanso entre ejercicios del mismo superset se cuenta 1 sola vez
 *   (no se duplica por cada miembro del superset).
 * - Series de movilidad ([com.example.kpkn.data.models.WarmupExercise]): se suman al bloque
 *   de calentamiento global, usando su [WarmupExercise.duration] si está disponible, o
 *   estimando a partir de sus series y repeticiones.
 * - Series de aproximación por ejercicio ([com.example.kpkn.data.models.WarmupSetDefinition]):
 *   ~30 s de ejecución + descanso propio de cada serie de aproximación.
 * - Series de movilidad por ejercicio ([com.example.kpkn.data.models.MobilitySeries]):
 *   usa [MobilitySeries.durationSeconds] o las repeticiones si están disponibles, e incluye
 *   el descanso programado entre series.
 */
fun calculateSessionTimeBreakdown(
    exercises: List<Exercise>,
    supersetGroups: List<com.example.kpkn.data.models.SupersetGroup>,
    sessionWarmup: List<com.example.kpkn.data.models.WarmupExercise> = emptyList(),
    globalMobilitySeries: List<MobilitySeries> = emptyList(),
    averageSetupSeconds: Int = 60,
    averageWorkSeconds: Int = 45,
    restTimerDefaultSeconds: Int = 90,
): SessionTimeBreakdown {
    var setupSec = 0
    var executionSec = 0
    var restSec = 0
    var warmupSec = 0

    // ── Bloque de calentamiento/movilidad global (session.warmup) ─────────────
    // Cada WarmupExercise aporta su duración explícita, o una estimación basada en
    // sus series y repeticiones (4 s/rep como referencia), más 15 s de transición.
    sessionWarmup.forEach { warmupExercise ->
        val exerciseDuration = when {
            warmupExercise.duration != null && warmupExercise.duration > 0 ->
                warmupExercise.duration
            else -> {
                val sets = warmupExercise.sets?.coerceAtLeast(1) ?: 1
                val repsStr = warmupExercise.reps
                val repsEstimate = repsStr?.filter { it.isDigit() }?.toIntOrNull() ?: 10
                sets * repsEstimate * 4  // ~4 s/rep
            }
        }
        warmupSec += exerciseDuration + 15  // +15 s de transición entre ejercicios de movilidad
    }

    // ── Grupos globales de movilidad (SessionPart.isMobilityGroup) ───────────
    globalMobilitySeries.forEach { mobility ->
        val setCount = mobility.sets.coerceAtLeast(1)
        val repsEstimate = mobility.reps?.filter { it.isDigit() }?.toIntOrNull()?.coerceAtLeast(1)
        val secondsPerSet = mobility.durationSeconds?.takeIf { it > 0 }
            ?: repsEstimate?.times(4)
            ?: 30
        warmupSec += secondsPerSet * setCount
    }

    // IDs de supersets ya procesados (para no duplicar descanso intra-superset)
    val supersetGroupsProcessed = mutableSetOf<String>()

    exercises.forEach { exercise ->
        setupSec += averageSetupSeconds

        // ── Series de aproximación (exercise.warmupSets) ──────────────────────
        // Cada WarmupSetDefinition = ~30 s de ejecución + descanso propio (restBetween ?: 45 s)
        exercise.warmupSets.forEach { approxSet ->
            warmupSec += 30  // ejecución de la serie de aproximación
            warmupSec += (approxSet.restBetween ?: 45)  // descanso post-serie
        }

        // ── Series de movilidad por ejercicio (exercise.mobilitySeries) ───────
        // Usa durationSeconds/repeticiones si están definidos y suma descansos entre series.
        exercise.mobilitySeries.forEach { mobility ->
            val setCount = mobility.sets.coerceAtLeast(1)
            val repsEstimate = mobility.reps?.filter { it.isDigit() }?.toIntOrNull()?.coerceAtLeast(1)
            val mobilityDuration = when {
                mobility.durationSeconds != null && mobility.durationSeconds > 0 ->
                    mobility.durationSeconds * setCount
                repsEstimate != null -> repsEstimate * 4 * setCount
                else -> 30 * setCount
            }
            warmupSec += mobilityDuration
        }

        // Cardio: intervals expand to total circuit time; legacy single-block uses targetDuration.
        exercise.cardioDetails?.let { cardio ->
            executionSec += cardio.effectiveDurationSeconds()
            return@forEach
        }

        val sets = exercise.sets.ifEmpty {
            List(3) { com.example.kpkn.data.models.ExerciseSet(id = "placeholder_$it") }
        }

        sets.forEach { set ->
            // ── Ejecución de la serie principal ──────────────────────────────
            val setExecSec = when (exercise.trainingMode) {
                TrainingMode.TIME -> set.targetDuration ?: averageWorkSeconds
                else -> averageWorkSeconds
            }
            executionSec += setExecSec

            // ── Drop-sets programados (cada drop ~3 reps × 2 s = 6 s) ───────
            val drops = set.plannedIntensityTechniques.filter {
                it.type == com.example.kpkn.data.models.TechniqueType.DROP_SET
            }
            val dropCount = drops.sumOf { technique ->
                technique.params["weightPcts"]?.split(",")?.size ?: 3
            }.coerceAtLeast(if (set.isDropSet && set.dropSets.isEmpty()) 3 else 0)
            executionSec += dropCount * 6

            // ── Rest-pause programados ────────────────────────────────────────
            val rpTechniques = set.plannedIntensityTechniques.filter {
                it.type == com.example.kpkn.data.models.TechniqueType.REST_PAUSE
            }
            rpTechniques.forEach { technique ->
                val miniSetCount = technique.params["count"]?.toIntOrNull() ?: 3
                val pauseSec    = technique.params["pauseSeconds"]?.toIntOrNull() ?: 10
                val miniReps    = technique.params["reps"]?.toIntOrNull() ?: 3
                executionSec += miniSetCount * miniReps * 2   // ~2 s por rep
                restSec      += miniSetCount * pauseSec
            }
            // Fallback: rest-pauses directos en el set (sin technique planeada)
            if (set.isRestPause && rpTechniques.isEmpty()) {
                val rpCount = set.restPauses.size.coerceAtLeast(3)
                executionSec += rpCount * 3 * 2
                restSec      += rpCount * 10
            }
        }

        // ── Descanso del ejercicio ─────────────────────────────────────────────
        val exerciseRestSec = exercise.restTime ?: restTimerDefaultSeconds
        // Entre lados unilateral (BETWEEN_SIDES) por set — preview ahora lo cuenta
        val betweenSidesSec = exercise.restBetweenSidesSeconds?.takeIf { it > 0 } ?: 0
        if (betweenSidesSec > 0) {
            // Paired L+R ≈ 1 set con 1 descanso entre lados; single-side no tiene betweenSides
            // Aproximación: 1 betweenSides por set si ejercicio es unilateral
            restSec += betweenSidesSec * sets.size
        }
        val supersetRef = exercise.supersetGroupRefOrLegacyId()

        if (supersetRef != null) {
            val group = supersetGroups.firstOrNull { it.id == supersetRef }
            if (group != null && supersetRef !in supersetGroupsProcessed) {
                // Descanso intra-superset y post-superset se cuentan 1 sola vez por grupo
                val rounds = sets.size.coerceAtLeast(1)
                // F2 D2: respetar descansos por ronda si existen
                var intraRest = 0
                var roundRest = 0
                for (r in 0 until rounds) {
                    intraRest += group.roundRestBetweenExercises?.get(r) ?: group.restBetweenExercises
                    roundRest += group.roundRestAfterSuperset?.get(r) ?: group.restAfterSuperset
                }
                restSec += intraRest * (group.exerciseOrder.size - 1)
                restSec += roundRest
                supersetGroupsProcessed.add(supersetRef)
            }
            // Para miembros adicionales del superset no se añade descanso individual
        } else {
            restSec += exerciseRestSec * sets.size
        }
    }

    val total = setupSec + executionSec + restSec + warmupSec
    return SessionTimeBreakdown(
        setupSeconds     = setupSec,
        executionSeconds = executionSec,
        restSeconds      = restSec,
        warmupSeconds    = warmupSec,
        totalSeconds     = total,
        exerciseCount    = exercises.size,
        // Cardio is a continuous block, never a strength-series count.  Older
        // sessions may still carry synthetic ExerciseSet rows, so do not let
        // those legacy rows leak into editor/time summaries.
        totalSetCount    = exercises.sumOf { exercise ->
            if (exercise.cardioDetails != null) 0 else exercise.sets.size.coerceAtLeast(1)
        },
    )
}

