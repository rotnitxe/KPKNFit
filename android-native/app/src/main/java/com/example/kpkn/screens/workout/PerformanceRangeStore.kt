package com.example.kpkn.screens.workout

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.PerformanceRangeData
import com.example.kpkn.data.db.PerformanceSnapshotData
import com.example.kpkn.data.db.toEntity as rangeToEntity
import com.example.kpkn.data.db.toEntity as snapshotToEntity
import com.example.kpkn.data.db.toPerformanceRangeData
import com.example.kpkn.data.db.toPerformanceSnapshotData
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.performance.PerformanceRangeCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Encapsulates performance-range / snapshot DAO access and in-memory cache.
 */
class PerformanceRangeStore(appContext: Context) {
    private val performanceRangeDao = KpknDatabase.getInstance(appContext).performanceRangeDao()
    private val performanceSnapshotDao = KpknDatabase.getInstance(appContext).performanceSnapshotDao()
    private val cache = mutableMapOf<String, PerformanceRangeData>()
    private val prefetchInFlight = mutableSetOf<String>()

    fun getCached(contextKey: String): PerformanceRangeData? = cache[contextKey]

    fun putCache(contextKey: String, data: PerformanceRangeData) {
        cache[contextKey] = data
    }

    suspend fun loadFromDb(contextKey: String): PerformanceRangeData? =
        performanceRangeDao.getByContextKey(contextKey)?.toPerformanceRangeData()

    fun prefetchIfMissing(contextKey: String, scope: CoroutineScope) {
        if (contextKey.isBlank() || cache.containsKey(contextKey)) return
        if (!prefetchInFlight.add(contextKey)) return
        scope.launch(Dispatchers.IO) {
            runCatching { loadFromDb(contextKey) }.getOrNull()?.let { cache[contextKey] = it }
            prefetchInFlight.remove(contextKey)
        }
    }

    suspend fun persistFinishedSessionPerformance(
        completedExercises: List<CompletedExercise>,
        sessionId: String,
        postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    ) {
        for (completedEx in completedExercises) {
            val canonicalId = completedEx.canonicalExerciseId ?: completedEx.exerciseDbId ?: continue
            if (canonicalId.isBlank()) continue
            val contextKey = canonicalId

            val postFeedback = postExerciseFeedbackByExerciseId.values
                .firstOrNull { it.exerciseId == completedEx.exerciseId || it.canonicalExerciseId == canonicalId }
            val techQuality = postFeedback?.technicalQuality ?: 10
            val isTechnicalInvalid = techQuality <= 2

            val rawSessionErm = completedEx.sets.mapNotNull { set ->
                if (set.weight > 0 && set.reps > 0) {
                    calculateHybrid1RM(set.weight, set.reps)
                } else {
                    null
                }
            }.maxOrNull() ?: continue

            val sessionErm = when {
                isTechnicalInvalid -> rawSessionErm
                techQuality == 3 -> rawSessionErm * 0.90
                else -> rawSessionErm
            }

            val avgRpe = completedEx.sets.mapNotNull { it.rpe }.average().takeIf { !it.isNaN() }
            val reachedFailure = completedEx.sets.any { it.isFailure }
            val snapshot = PerformanceSnapshotData(
                contextKey = contextKey,
                sessionId = sessionId,
                erm = sessionErm,
                setCount = completedEx.sets.size,
                avgRpe = avgRpe,
                reachedFailure = reachedFailure,
                isTechnicalInvalid = isTechnicalInvalid,
            )

            if (!isTechnicalInvalid) {
                performanceSnapshotDao.upsert(snapshot.snapshotToEntity())
            }

            val allSnapshots = performanceSnapshotDao.getByContextKey(contextKey)
                .map { it.toPerformanceSnapshotData() }
                .filter { !it.isTechnicalInvalid }
            val validErms = allSnapshots.map { it.erm }

            val existingRangeData = performanceRangeDao.getByContextKey(contextKey)?.toPerformanceRangeData()
            val previousEwma = existingRangeData?.ermRms ?: 0.0

            val range = PerformanceRangeCalculator.computeRange(
                snapshots = validErms,
                currentErm = sessionErm,
                previousEwma = previousEwma,
            )
            val currentData = existingRangeData?.let {
                PerformanceRangeData(
                    contextKey = contextKey,
                    ermMin = range.ermMin,
                    ermMax = range.ermMax,
                    ermRms = range.ewmaErm,
                    sampleCount = validErms.size,
                    lastUpdatedMs = System.currentTimeMillis(),
                    consecutiveAbove = if (range.isCurrentInRange) 0 else it.consecutiveAbove + 1,
                    consecutiveBelow = if (range.isCurrentInRange || sessionErm >= it.ermRms) 0 else it.consecutiveBelow + 1,
                )
            } ?: PerformanceRangeData(
                contextKey = contextKey,
                ermMin = range.ermMin,
                ermMax = range.ermMax,
                ermRms = range.ewmaErm,
                sampleCount = validErms.size,
                lastUpdatedMs = System.currentTimeMillis(),
            )
            performanceRangeDao.upsert(currentData.rangeToEntity())
            cache[contextKey] = currentData
        }
    }
}
