package com.example.kpkn.domain.auge

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RingStartSnapshot
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.auge.AugeFatigueEngine.isSetEffective
import kotlin.math.roundToInt

data class AxialLoadReport(
    val insufficientData: Boolean,
    val acwr: Double?,
    val axialDaysIn7: Int,
    val axialDaysIn4: Int,
    val axialDaysIn6: Int,
    val consecutiveAxialDays: Int,
    val heavySessions7: Int,
    val startTrend: List<Int>,
)

object AxialLoadMonitor {
    const val AXIAL_MIN = 0.5
    const val MIN_AXIAL_SESSIONS = 4
    const val MIN_SPAN_DAYS = 14

    fun evaluate(
        history: List<WorkoutLog>,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        nowMs: Long = AugeClock.nowMs(),
    ): AxialLoadReport {
        val day = 24L * 3_600_000L
        val axialLogs = history.mapNotNull { log ->
            val ms = AugeUtils.logDateMs(log)
            if (ms <= 0L) return@mapNotNull null
            val units = axialUnits(log, exerciseDb)
            if (units <= 0.0) return@mapNotNull null
            Triple(log, ms, units)
        }
        val recent = axialLogs.filter { it.second in (nowMs - 28L * day) until nowMs }
        val oldest = recent.minOfOrNull { it.second } ?: 0L
        val insufficient = recent.size < MIN_AXIAL_SESSIONS || nowMs - oldest < MIN_SPAN_DAYS * day
        val acute = recent.filter { it.second >= nowMs - 7L * day }.sumOf { it.third }
        val chronicWeekly = recent.sumOf { it.third } / 4.0
        val acwr = if (insufficient || chronicWeekly <= 0.0) {
            null
        } else {
            AugeClassifiers.computeAcwr(acute, chronicWeekly)
        }
        val days7 = distinctDays(recent, nowMs, 7)
        val days4 = distinctDays(recent, nowMs, 4)
        val days6 = distinctDays(recent, nowMs, 6)
        val consecutive = consecutiveAxialDays(recent.map { it.second }, nowMs)
        val p75 = percentile(recent.map { it.third }, 0.75)
        val heavy = recent.count { it.second >= nowMs - 7L * day && it.third >= p75 }
        val trend = recent
            .sortedBy { it.second }
            .mapNotNull { it.first.ringStartSnapshot?.takeUnless { snapshot -> snapshot.isInitialEstimate }?.structure }
            .takeLast(8)
        return AxialLoadReport(
            insufficientData = insufficient,
            acwr = acwr,
            axialDaysIn7 = days7,
            axialDaysIn4 = days4,
            axialDaysIn6 = days6,
            consecutiveAxialDays = consecutive,
            heavySessions7 = heavy,
            startTrend = trend,
        )
    }

    fun axialUnits(log: WorkoutLog, exerciseDb: Map<String, ExerciseMuscleInfo>): Double {
        var total = 0.0
        log.completedExercises.forEach { ex ->
            if (ex.cardioDetails != null) return@forEach
            val info = resolveCatalogExerciseInfoInIndex(
                index = exerciseDb,
                catalogConfigurationId = ex.catalogConfigurationId,
                exerciseDbId = ex.exerciseDbId,
                exerciseId = ex.exerciseId,
                exerciseName = ex.exerciseName,
            )
            val axial = info?.axialLoadFactor ?: return@forEach
            if (axial < AXIAL_MIN) return@forEach
            ex.sets.forEach { set ->
                if (!isSetEffective(set)) return@forEach
                val side = if (set.side != null) 0.5 else 1.0
                total += side * axial * AugeFatigueEngine.relativeLoadFactorForAxial(set)
            }
        }
        return total
    }

    fun personalBaseline(
        snapshots: List<RingStartSnapshot>,
        channel: RecoveryChannelId,
    ): IntRange? {
        val values = snapshots.map { snap ->
            when (channel) {
                RecoveryChannelId.MUSCULAR -> snap.muscular
                RecoveryChannelId.SYSTEM -> snap.energy
                RecoveryChannelId.STRUCTURE -> snap.structure
            }
        }.sorted()
        if (values.size < 4) return null
        val p25 = percentileInt(values, 0.25)
        val p75 = percentileInt(values, 0.75)
        return p25..p75
    }

    fun sparkline(
        snapshots: List<RingStartSnapshot>,
        channel: RecoveryChannelId,
        limit: Int = 14,
    ): List<Int> = snapshots.takeLast(limit).map { snap ->
        when (channel) {
            RecoveryChannelId.MUSCULAR -> snap.muscular
            RecoveryChannelId.SYSTEM -> snap.energy
            RecoveryChannelId.STRUCTURE -> snap.structure
        }
    }

    private fun distinctDays(recent: List<Triple<WorkoutLog, Long, Double>>, nowMs: Long, days: Int): Int {
        val cutoff = nowMs - days * 24L * 3_600_000L
        return recent.filter { it.second >= cutoff }.map { dayKey(it.second) }.distinct().size
    }

    private fun consecutiveAxialDays(timestamps: List<Long>, nowMs: Long): Int {
        if (timestamps.isEmpty()) return 0
        val keys = timestamps.map { dayKey(it) }.toSet()
        val today = dayKey(nowMs)
        val start = keys.filter { it <= today }.maxOrNull() ?: return 0
        var count = 0
        var cursor = start
        while (cursor in keys) {
            count++
            cursor -= 1
        }
        return count
    }

    private fun dayKey(ms: Long): Long = ms / (24L * 3_600_000L)

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val idx = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    private fun percentileInt(values: List<Int>, p: Double): Int {
        val idx = ((values.size - 1) * p).roundToInt().coerceIn(0, values.lastIndex)
        return values[idx]
    }
}
