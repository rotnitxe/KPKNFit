package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog

data class SystemicLoadReport(
    val insufficientData: Boolean,
    val acwr: Double?,
    val sessionsIn7: Int,
    val startTrend: List<Int>,
)

object SystemicLoadMonitor {
    const val MIN_SESSIONS = 4
    const val MIN_SPAN_DAYS = 14

    fun evaluate(
        history: List<WorkoutLog>,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        settings: Settings,
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
        nowMs: Long = AugeClock.nowMs(),
    ): SystemicLoadReport {
        val day = 24L * 3_600_000L
        val loads = history.mapNotNull { log ->
            val ms = AugeUtils.logDateMs(log)
            if (ms <= 0L) return@mapNotNull null
            val drain = AugeFatigueEngine.calculateCompletedSessionDrain(
                completedExercises = log.completedExercises,
                exerciseDb = exerciseDb,
                settings = settings,
                adaptiveCache = adaptiveCache,
            ).cns.toDouble()
            if (drain <= 0.0) return@mapNotNull null
            Triple(log, ms, drain)
        }
        val recent = loads.filter { it.second in (nowMs - 28L * day) until nowMs }
        val oldest = recent.minOfOrNull { it.second } ?: 0L
        val insufficient = recent.size < MIN_SESSIONS || nowMs - oldest < MIN_SPAN_DAYS * day
        val acute = recent.filter { it.second >= nowMs - 7L * day }.sumOf { it.third }
        val chronicWeekly = recent.sumOf { it.third } / 4.0
        val acwr = if (insufficient || chronicWeekly <= 0.0) null else AugeClassifiers.computeAcwr(acute, chronicWeekly)
        val sessions7 = recent.count { it.second >= nowMs - 7L * day }
        val trend = recent.sortedBy { it.second }.mapNotNull { it.first.ringStartSnapshot?.energy }.takeLast(8)
        return SystemicLoadReport(
            insufficientData = insufficient,
            acwr = acwr,
            sessionsIn7 = sessions7,
            startTrend = trend,
        )
    }
}
