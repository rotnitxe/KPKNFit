package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.ContextPerformanceStateV2
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.GlobalPerformanceStateV3
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.SetEntryV2
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.models.TimeProgressionStrategyV3
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.resolvedCanonicalExerciseId
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import kotlin.math.round
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object WorkoutPerformanceHomologationEngine {

    private const val EWMA_ALPHA = 0.35
    private const val DEFAULT_STD = 6.0
    private const val MIN_SCORE = 0.0
    private const val MAX_SCORE = 100.0

    data class EvaluationResult(
        val outcome: SetOutcomeV2,
        val homologated: HomologatedPerformanceResult,
        val nextState: ContextPerformanceStateV2,
        val nextGlobalState: GlobalPerformanceStateV3,
    )

    fun evaluate(
        entry: SetEntryV2,
        previous: ContextPerformanceStateV2?,
        previousGlobal: GlobalPerformanceStateV3? = null,
    ): EvaluationResult {
        val prior = previous ?: ContextPerformanceStateV2(contextKey = entry.contextKey)
        val globalKey = buildGlobalKey(entry)
        val priorGlobal = previousGlobal ?: GlobalPerformanceStateV3(globalKey = globalKey)

        val metric = computeMetric(entry)
        val difficultySignal = computeDifficultySignal(entry)

        val contextStats = updateStats(
            metric = metric,
            priorMean = prior.mean,
            priorVariance = prior.variance,
            priorCount = prior.sampleCount,
            priorBest = prior.bestScore,
            priorRecent = prior.recentScores,
            priorEwma = prior.ewma,
        )

        val comparableScore = computeComparableScore(
            localScore = contextStats.score,
            entry = entry,
            isContextPr = contextStats.isPr,
            difficultySignal = difficultySignal,
        )

        val globalStats = updateStats(
            metric = comparableScore,
            priorMean = priorGlobal.mean,
            priorVariance = priorGlobal.variance,
            priorCount = priorGlobal.sampleCount,
            priorBest = priorGlobal.bestScore,
            priorRecent = priorGlobal.recentScores,
            priorEwma = priorGlobal.ewma,
        )

        val historyColor = when {
            entry.failedSet || entry.debt > 0.0 -> HistoryColorV2.RED
            contextStats.isPr || globalStats.isPr -> HistoryColorV2.YELLOW
            comparableScore < (priorGlobal.ewma.takeIf { priorGlobal.sampleCount > 0 } ?: comparableScore) - 10.0 -> HistoryColorV2.RED
            else -> HistoryColorV2.NEUTRAL
        }

        val suggestion = suggestNextLoad(entry, prior, historyColor, contextStats.score)

        val normalizedLoad = normalizeLoad(entry)
        val normalizedReps = when (entry.unitMode) {
            UnitModeV2.TIME -> max(1, (entry.actualValue / 5.0).toInt())
            UnitModeV2.DISTANCE -> max(1, entry.actualValue.toInt())
            else -> max(1, entry.actualValue.toInt())
        }

        val metricType = when (entry.unitMode) {
            UnitModeV2.TIME -> "TRM"
            UnitModeV2.DISTANCE -> "DRM"
            UnitModeV2.CUSTOM -> "CRM"
            UnitModeV2.REPS -> "ERM"
        }
        val trm = if (entry.unitMode == UnitModeV2.TIME) metric else null
        val estimatedRm = if (entry.unitMode != UnitModeV2.TIME) metric else null

        val nextState = prior.copy(
            ewma = contextStats.ewma,
            mean = contextStats.mean,
            variance = contextStats.variance,
            bestScore = contextStats.best,
            sampleCount = contextStats.count,
            recentScores = contextStats.recent,
            consecutiveGreenSessions = when {
                historyColor == HistoryColorV2.RED -> 0
                else -> prior.consecutiveGreenSessions + 1
            },
            lastSuggestedLoad = suggestion.suggestedLoad,
            lastUpdatedAtIso = java.time.Instant.now().toString(),
        )

        val nextGlobalState = priorGlobal.copy(
            ewma = globalStats.ewma,
            mean = globalStats.mean,
            variance = globalStats.variance,
            bestScore = globalStats.best,
            sampleCount = globalStats.count,
            recentScores = globalStats.recent,
            lastUpdatedAtIso = java.time.Instant.now().toString(),
        )

        val homologated = HomologatedPerformanceResult(
            contextKey = entry.contextKey,
            globalKey = globalKey,
            loadMode = entry.loadMode,
            unitMode = entry.unitMode,
            plannedTarget = entry.plannedTarget,
            actualValue = entry.actualValue,
            actualIntensity = entry.actualIntensity,
            debt = entry.debt,
            failedSet = entry.failedSet,
            reachedFailure = entry.reachedFailure,
            amrapOverride = entry.amrapOverride,
            techniques = entry.techniques,
            metricType = metricType,
            metricValue = metric,
            estimatedRm = estimatedRm,
            trm = trm,
            localPerformanceIndex = contextStats.score,
            globalPerformanceIndex = globalStats.score,
            contextPercentile = contextStats.percentile,
            globalPercentile = globalStats.percentile,
            contextEwma = contextStats.ewma,
            contextStdDev = contextStats.stdDev,
            globalEwma = globalStats.ewma,
            globalStdDev = globalStats.stdDev,
            isContextPr = contextStats.isPr,
            isGlobalPr = globalStats.isPr,
            historyColor = historyColor,
            difficultySignal = difficultySignal,
            suggestedNextLoad = suggestion.suggestedLoad,
            suggestedTargetSeconds = suggestion.suggestedTimeSeconds,
            suggestionReason = suggestion.reason,
            augeEquivalentLoad = normalizedLoad,
            augeEquivalentReps = normalizedReps,
            suggestedLoadMode = suggestion.suggestedLoadMode,
        )

        val outcome = SetOutcomeV2(
            contextKey = entry.contextKey,
            loadMode = entry.loadMode,
            unitMode = entry.unitMode,
            plannedTarget = entry.plannedTarget,
            actualValue = entry.actualValue,
            actualIntensity = entry.actualIntensity,
            debt = entry.debt,
            failedSet = entry.failedSet,
            reachedFailure = entry.reachedFailure,
            amrapOverride = entry.amrapOverride,
            techniques = entry.techniques,
            metricType = metricType,
            metricValue = metric,
            estimatedRm = estimatedRm,
            trm = trm,
            globalPerformanceIndex = globalStats.score,
            contextPercentile = contextStats.percentile,
            globalPercentile = globalStats.percentile,
            contextEwma = contextStats.ewma,
            contextStdDev = contextStats.stdDev,
            globalEwma = globalStats.ewma,
            globalStdDev = globalStats.stdDev,
            isContextPr = contextStats.isPr,
            isGlobalPr = globalStats.isPr,
            historyColor = historyColor,
            difficultySignal = difficultySignal,
            suggestedNextLoad = suggestion.suggestedLoad,
            suggestedTargetSeconds = suggestion.suggestedTimeSeconds,
            suggestionReason = suggestion.reason,
            augeEquivalentLoad = normalizedLoad,
            augeEquivalentReps = normalizedReps,
            suggestedLoadMode = suggestion.suggestedLoadMode,
        )

        return EvaluationResult(
            outcome = outcome,
            homologated = homologated,
            nextState = nextState,
            nextGlobalState = nextGlobalState,
        )
    }

    private data class StatsResult(
        val mean: Double,
        val variance: Double,
        val stdDev: Double,
        val ewma: Double,
        val score: Double,
        val percentile: Double,
        val best: Double,
        val count: Int,
        val recent: List<Double>,
        val isPr: Boolean,
    )

    private data class Suggestion(
        val suggestedLoad: Double? = null,
        val suggestedTimeSeconds: Int? = null,
        val reason: String? = null,
        val suggestedLoadMode: LoadModeV2? = null,
        val isFailure: Boolean = false,
    )

    private fun buildGlobalKey(entry: SetEntryV2): String =
        entry.resolvedCanonicalExerciseId()

    private fun updateStats(
        metric: Double,
        priorMean: Double,
        priorVariance: Double,
        priorCount: Int,
        priorBest: Double,
        priorRecent: List<Double>,
        priorEwma: Double,
    ): StatsResult {
        val ewma = if (priorCount == 0) metric else EWMA_ALPHA * metric + (1 - EWMA_ALPHA) * priorEwma
        val newCount = priorCount + 1
        val delta = metric - priorMean
        val mean = priorMean + delta / newCount
        val delta2 = metric - mean
        val m2 = if (priorCount <= 1) {
            priorVariance * max(priorCount - 1, 0) + delta * delta2
        } else {
            priorVariance * (priorCount - 1) + delta * delta2
        }
        val variance = if (newCount > 1) (m2 / (newCount - 1)).coerceAtLeast(0.0) else 0.0
        val stdDev = sqrt(variance).takeIf { it > 0 } ?: DEFAULT_STD
        val score = normalizeScore(metric = metric, mean = mean, stdDev = stdDev)
        val percentile = computePercentile(metric, priorRecent)
        return StatsResult(
            mean = mean,
            variance = variance,
            stdDev = stdDev,
            ewma = ewma,
            score = score,
            percentile = percentile,
            best = max(priorBest, metric),
            count = newCount,
            recent = (listOf(metric) + priorRecent).take(30),
            isPr = metric > priorBest || priorCount == 0,
        )
    }

    private fun computeComparableScore(
        localScore: Double,
        entry: SetEntryV2,
        isContextPr: Boolean,
        difficultySignal: DifficultySignalV2,
    ): Double {
        var adjusted = localScore
        if (entry.reachedFailure && entry.plannedTarget != null && entry.actualValue < entry.plannedTarget) {
            adjusted -= 12.0
        }
        if (entry.plannedTarget != null && entry.actualValue >= entry.plannedTarget) adjusted += 4.0
        if (entry.debt > 0.0) adjusted -= (entry.debt * 4.0).coerceAtMost(18.0)
        if (entry.failedSet) adjusted -= 16.0
        if (isContextPr) adjusted += 6.0
        if (!entry.reachedFailure) {
            adjusted += when (difficultySignal) {
                DifficultySignalV2.EASIER -> 4.0
                DifficultySignalV2.HARDER -> -4.0
                DifficultySignalV2.MATCHED -> 0.0
            }
        }
        return adjusted.coerceIn(MIN_SCORE, MAX_SCORE)
    }

    private fun suggestNextLoad(
        entry: SetEntryV2,
        prior: ContextPerformanceStateV2,
        historyColor: HistoryColorV2,
        score: Double,
    ): Suggestion {
        val debtOrFail = entry.debt > 0.0 || entry.failedSet
        val currentLoad = entry.loggedLoad
        if (debtOrFail) {
            return Suggestion(
                suggestedLoad = currentLoad,
                reason = "Mantener por deuda/fallida",
                isFailure = entry.reachedFailure,
            )
        }

        if (entry.reachedFailure && entry.plannedTarget != null && entry.actualValue < entry.plannedTarget) {
            return Suggestion(
                suggestedLoad = currentLoad,
                reason = "Mantener/reducir por fallo prematuro",
                isFailure = true,
            )
        }

        if (entry.rom != null && entry.rom < 85) {
            return Suggestion(
                suggestedLoad = currentLoad,
                reason = "Priorizar mejorar ROM (${entry.rom}% registrado, objetivo >=85%)",
                isFailure = entry.reachedFailure,
            )
        }

        if (entry.assistedReps != null && entry.assistedReps > 0) {
            return Suggestion(
                suggestedLoad = currentLoad,
                reason = "Consolidar carga sin ayuda (se reportaron ${entry.assistedReps} reps asistidas)",
                isFailure = entry.reachedFailure,
            )
        }

        return when (entry.loadMode) {
            LoadModeV2.LOAD -> {
                val base = currentLoad ?: return Suggestion(reason = "Sin carga registrada", isFailure = entry.reachedFailure, suggestedLoadMode = LoadModeV2.LOAD)
                val factor = when {
                    historyColor == HistoryColorV2.YELLOW || score >= 72 -> 1.025
                    score >= 58 -> 1.015
                    else -> 1.0
                }
                Suggestion(
                    suggestedLoad = roundToHalf(base * factor),
                    reason = if (factor > 1.0) "Subir carga por rendimiento" else "Mantener carga",
                    suggestedLoadMode = LoadModeV2.LOAD,
                    isFailure = entry.reachedFailure,
                )
            }

            LoadModeV2.BODYWEIGHT -> {
                if (entry.unitMode == UnitModeV2.TIME && entry.timeProgressionStrategy == TimeProgressionStrategyV3.LOAD_THEN_TIME) {
                    return Suggestion(
                        suggestedLoad = currentLoad,
                        suggestedTimeSeconds = entry.actualValue.toInt() + 5,
                        reason = "Subir tiempo objetivo +5s",
                        suggestedLoadMode = LoadModeV2.BODYWEIGHT,
                        isFailure = entry.reachedFailure,
                    )
                }
                val bodyW = entry.bodyWeight
                if (bodyW == null || bodyW <= 0.0) {
                    return Suggestion(
                        suggestedLoad = 0.0,
                        reason = "Registra tu peso en Nutrición para guía de lastre",
                        suggestedLoadMode = LoadModeV2.BODYWEIGHT,
                        isFailure = entry.reachedFailure,
                    )
                }
                val reps = round(entry.actualValue).toInt().coerceAtLeast(1)
                val safeReps = reps.coerceAtMost(36)
                // Brzycki: 1RM = w * 36 / (37 - r)
                // Can do 4+ reps with +5kg? Derivation: r >= 37 - 33 * bw / (bw + 5)
                val minRepsFor5kg = 37.0 - 33.0 * bodyW / (bodyW + 5.0)
                val ready = safeReps.toDouble() >= minRepsFor5kg && score >= 55.0
                if (ready || prior.consecutiveGreenSessions >= 2 || historyColor == HistoryColorV2.YELLOW) {
                    return Suggestion(
                        suggestedLoad = 2.5,
                        reason = "Iniciar con lastre (+2.5kg)",
                        suggestedLoadMode = LoadModeV2.LASTRE,
                        isFailure = entry.reachedFailure,
                    )
                }
                val targetMinReps = round(minRepsFor5kg).toInt().coerceAtLeast(safeReps + 1)
                Suggestion(
                    suggestedLoad = 0.0,
                    reason = if (targetMinReps > safeReps) "Progresar reps (meta ~${targetMinReps} para lastre)" else "Solo peso corporal",
                    suggestedLoadMode = LoadModeV2.BODYWEIGHT,
                    isFailure = entry.reachedFailure,
                )
            }

            LoadModeV2.LASTRE -> {
                val external = currentLoad ?: 0.0
                if (external > 0.0 && (historyColor == HistoryColorV2.YELLOW || score >= 65)) {
                    return Suggestion(
                        suggestedLoad = roundToHalf(external + 2.5),
                        reason = "Agregar lastre",
                        suggestedLoadMode = LoadModeV2.LASTRE,
                        isFailure = entry.reachedFailure,
                    )
                }
                if (entry.unitMode == UnitModeV2.TIME && entry.timeProgressionStrategy == TimeProgressionStrategyV3.LOAD_THEN_TIME) {
                    return Suggestion(
                        suggestedLoad = currentLoad,
                        suggestedTimeSeconds = entry.actualValue.toInt() + 5,
                        reason = "Subir tiempo objetivo +5s",
                        suggestedLoadMode = LoadModeV2.LASTRE,
                        isFailure = entry.reachedFailure,
                    )
                }
                if (prior.consecutiveGreenSessions >= 2 || historyColor == HistoryColorV2.YELLOW) {
                    return Suggestion(
                        suggestedLoad = roundToHalf(external + 2.5),
                        reason = "Transición a lastre",
                        suggestedLoadMode = LoadModeV2.LASTRE,
                        isFailure = entry.reachedFailure,
                    )
                }
                if (external == 0.0 && score >= 65) {
                    return Suggestion(
                        suggestedLoad = roundToHalf(2.5),
                        reason = "Iniciar con lastre",
                        suggestedLoadMode = LoadModeV2.LASTRE,
                        isFailure = entry.reachedFailure,
                    )
                }
                Suggestion(
                    suggestedLoad = currentLoad,
                    reason = "Progresar por reps/intensidad",
                    suggestedLoadMode = LoadModeV2.LASTRE,
                    isFailure = entry.reachedFailure,
                )
            }

            LoadModeV2.ASSISTED -> {
                val assistance = currentLoad ?: return Suggestion(reason = "Sin asistencia registrada", isFailure = entry.reachedFailure, suggestedLoadMode = LoadModeV2.ASSISTED)
                val beatPlannedTarget = entry.plannedTarget?.let { target ->
                    entry.actualValue >= target + 1.0
                } ?: false
                val clearlyReadyForLessAssistance =
                    prior.consecutiveGreenSessions >= 2 ||
                        (beatPlannedTarget && (historyColor == HistoryColorV2.YELLOW || score >= 72))
                if (historyColor != HistoryColorV2.RED && clearlyReadyForLessAssistance) {
                    val nextAssistance = max(0.0, assistance - 2.5)
                    if (nextAssistance <= 0.0) {
                        return Suggestion(
                            suggestedLoad = 0.0,
                            reason = "Consolidar peso corporal",
                            suggestedLoadMode = LoadModeV2.BODYWEIGHT,
                            isFailure = entry.reachedFailure,
                        )
                    }
                    return Suggestion(
                        suggestedLoad = roundToHalf(nextAssistance),
                        reason = "Reducir asistencia",
                        suggestedLoadMode = LoadModeV2.ASSISTED,
                        isFailure = entry.reachedFailure,
                    )
                }
                Suggestion(
                    suggestedLoad = roundToHalf(assistance),
                    reason = "Mantener asistencia",
                    suggestedLoadMode = LoadModeV2.ASSISTED,
                    isFailure = entry.reachedFailure,
                )
            }
        }
    }

    fun isPrematureFailure(entry: SetEntryV2): Boolean {
        return entry.reachedFailure && entry.plannedTarget != null && entry.actualValue < entry.plannedTarget
    }

    fun computeNormalizedLoad(entry: SetEntryV2): Double = when (entry.loadMode) {
        LoadModeV2.LOAD -> entry.loggedLoad ?: 0.0
        LoadModeV2.BODYWEIGHT -> entry.loggedLoad ?: 0.0
        LoadModeV2.LASTRE -> entry.loggedLoad ?: 0.0
        LoadModeV2.ASSISTED -> -(entry.loggedLoad ?: 0.0)
    }

    private fun computeMetric(entry: SetEntryV2): Double {
        return when (entry.unitMode) {
            UnitModeV2.TIME -> {
                val load = normalizeLoad(entry)
                val seconds = entry.actualValue.coerceAtLeast(1.0)
                if (entry.loadMode == LoadModeV2.ASSISTED) {
                    val assistance = entry.loggedLoad ?: 0.0
                    ((200.0 - assistance) * seconds.pow(0.35)).coerceAtLeast(1.0)
                } else if (entry.loadMode == LoadModeV2.BODYWEIGHT) {
                    seconds.pow(0.35).coerceAtLeast(1.0)
                } else {
                    (load * seconds.pow(0.35)).coerceAtLeast(1.0)
                }
            }

            UnitModeV2.DISTANCE -> {
                val load = normalizeLoad(entry)
                val distance = entry.actualValue.coerceAtLeast(1.0)
                calculateGeneralizedCapacity(load, distance).coerceAtLeast(1.0)
            }

            UnitModeV2.REPS,
            UnitModeV2.CUSTOM,
            -> {
                val load = normalizeLoad(entry)
                val reps = entry.actualValue.toInt().coerceAtLeast(1)
                if (entry.loadMode == LoadModeV2.ASSISTED) {
                    val assistance = entry.loggedLoad ?: 0.0
                    (200.0 - assistance) * (1.0 + reps * 0.03)
                } else if (entry.loadMode == LoadModeV2.BODYWEIGHT) {
                    reps.toDouble()
                } else {
                    calculateHybrid1RM(load, reps, isAmrap = entry.amrapOverride).coerceAtLeast(1.0)
                }
            }
        }
    }

    private fun normalizeLoad(entry: SetEntryV2): Double = when (entry.loadMode) {
        LoadModeV2.LOAD -> entry.loggedLoad ?: 0.0
        LoadModeV2.BODYWEIGHT -> 0.0
        LoadModeV2.LASTRE -> entry.loggedLoad ?: 0.0
        LoadModeV2.ASSISTED -> entry.loggedLoad ?: 0.0
    }

    private fun normalizeScore(metric: Double, mean: Double, stdDev: Double): Double {
        val z = if (stdDev > 0.0) (metric - mean) / stdDev else 0.0
        val mapped = 50.0 + z * 15.0
        return mapped.coerceIn(MIN_SCORE, MAX_SCORE)
    }

    private fun computePercentile(metric: Double, recent: List<Double>): Double {
        if (recent.isEmpty()) return 100.0
        val lower = recent.count { it <= metric }
        return ((lower.toDouble() / recent.size.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun computeDifficultySignal(entry: SetEntryV2): DifficultySignalV2 {
        if (entry.reachedFailure) {
            if (entry.plannedTarget != null && entry.actualValue < entry.plannedTarget) {
                return DifficultySignalV2.HARDER
            }
            return DifficultySignalV2.MATCHED
        }
        val expected = entry.plannedIntensity ?: return DifficultySignalV2.MATCHED
        val actual = entry.actualIntensity ?: return DifficultySignalV2.MATCHED
        return when {
            actual <= expected - 0.5 -> DifficultySignalV2.EASIER
            actual >= expected + 0.5 -> DifficultySignalV2.HARDER
            else -> DifficultySignalV2.MATCHED
        }
    }

    private fun roundToHalf(value: Double): Double = (value * 2.0).toInt() / 2.0
}
