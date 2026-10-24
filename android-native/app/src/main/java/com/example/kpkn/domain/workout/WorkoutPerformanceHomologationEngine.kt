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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object WorkoutPerformanceHomologationEngine {

    private const val EWMA_ALPHA = 0.35
    /** Capacity references reuse the contextual history smoothing, never its volume/score cache. */
    fun capacityHistoryEwma(capacities: List<Double>): Double? = capacities
        .filter { it.isFinite() && it > 0.0 }.takeIf { it.isNotEmpty() }
        ?.reduce { previous, capacity -> EWMA_ALPHA * capacity + (1 - EWMA_ALPHA) * previous }
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

        val suggestion = Suggestion(isFailure = entry.failedSet || entry.reachedFailure)

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
        // ASSISTED/BODYWEIGHT use a non-barbell scoring metric — never expose as eRM kg.
        val estimatedRm = when {
            entry.unitMode == UnitModeV2.REPS && entry.completedReps == 0 -> null
            entry.unitMode == UnitModeV2.TIME -> null
            entry.loadMode == LoadModeV2.ASSISTED || entry.loadMode == LoadModeV2.BODYWEIGHT -> null
            else -> metric
        }

        val nextState = prior.copy(
            ewma = contextStats.ewma,
            mean = contextStats.mean,
            variance = contextStats.variance,
            bestScore = contextStats.best,
            sampleCount = contextStats.count,
            recentScores = contextStats.recent,
            consecutiveGreenSessions = when {
                historyColor == HistoryColorV2.RED -> 0
                entry.isFirstEvaluationInSession -> prior.consecutiveGreenSessions + 1
                else -> prior.consecutiveGreenSessions
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
            estimatorVersion = com.example.kpkn.domain.calculations.CAPACITY_ESTIMATOR_VERSION,
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
            isPr = priorCount >= 1 && metric > priorBest,
        )
    }

    private fun computeComparableScore(
        localScore: Double,
        entry: SetEntryV2,
        isContextPr: Boolean,
        difficultySignal: DifficultySignalV2,
    ): Double {
        var adjusted = localScore
        if (isPrematureFailure(entry)) {
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



    fun isPrematureFailure(entry: SetEntryV2): Boolean {
        // Debt is evaluated against the semantic minimum for a range. The
        // planned target remains the upper/load anchor, so it must not turn a
        // valid 5/4-6 set into a premature-failure signal.
        return entry.reachedFailure && entry.debt > 0.0
    }

    fun computeNormalizedLoad(entry: SetEntryV2): Double {
        val userKg = entry.bodyWeight?.takeIf { it > 0.0 } ?: 0.0
        val logged = entry.loggedLoad ?: 0.0
        return when (entry.loadMode) {
            LoadModeV2.LOAD -> logged
            LoadModeV2.BODYWEIGHT -> userKg
            LoadModeV2.LASTRE -> userKg + logged
            LoadModeV2.ASSISTED -> (userKg - logged).coerceAtLeast(0.0)
        }
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
                val load = if (entry.loadMode == LoadModeV2.BODYWEIGHT) {
                    entry.bodyWeight ?: normalizeLoad(entry)
                } else {
                    normalizeLoad(entry)
                }
                val distance = entry.actualValue.coerceAtLeast(1.0)
                calculateGeneralizedCapacity(load, distance).coerceAtLeast(1.0)
            }

            UnitModeV2.REPS,
            UnitModeV2.CUSTOM,
            -> {
                val load = normalizeLoad(entry)
                val reps = entry.completedReps?.coerceAtLeast(0) ?: entry.actualValue.toInt().coerceAtLeast(1)
                if (entry.loadMode == LoadModeV2.ASSISTED) {
                    val assistance = entry.loggedLoad ?: 0.0
                    (200.0 - assistance) * (1.0 + reps * 0.03)
                } else if (entry.loadMode == LoadModeV2.BODYWEIGHT) {
                    reps.toDouble()
                } else {
                    if (reps == 0) 0.0 else calculateHybrid1RM(load, reps).coerceAtLeast(1.0)
                }
            }
        }
    }

    private fun normalizeLoad(entry: SetEntryV2): Double = computeNormalizedLoad(entry)

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
            if (isPrematureFailure(entry)) {
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

    private fun roundToHalf(value: Double): Double = round(value * 2.0) / 2.0
}
