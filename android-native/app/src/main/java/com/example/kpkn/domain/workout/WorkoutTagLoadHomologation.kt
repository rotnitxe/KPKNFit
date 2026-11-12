package com.example.kpkn.domain.workout

import kotlin.math.abs

data class WorkoutTagCapacityObservation(
    val sessionId: String,
    val observedAtMs: Long,
    val capacityKg: Double,
)

/** Conservative, read-only bridge between a tag's own history and a changed source tag. */
object WorkoutTagLoadHomologation {
    fun destinationCapacityKg(destination: List<WorkoutTagCapacityObservation>): Double? {
        val sessions = destination.validDistinctSessions()
        if (sessions.size < 2) return null
        return median(sessions.sortedByDescending { it.observedAtMs }.take(3).map { it.capacityKg })
    }

    fun adjustedDestinationCapacityKg(
        destinationTagId: String,
        sourceTagId: String,
        destination: List<WorkoutTagCapacityObservation>,
        source: List<WorkoutTagCapacityObservation>,
    ): Double? {
        if (destinationTagId.isBlank() || sourceTagId.isBlank() || destinationTagId == sourceTagId) return null
        val destinationSessions = destination.validDistinctSessions()
        val sourceSessions = source.validDistinctSessions()
        if (destinationSessions.size < 2) return null
        val lastDestinationUse = destinationSessions.maxOf { it.observedAtMs }

        // The closest two source sessions before the destination's last use establish its scale;
        // the latest two later source sessions estimate the change while that tag was in use.
        val baseline = sourceSessions.filter { it.observedAtMs <= lastDestinationUse }
            .sortedByDescending { it.observedAtMs }.take(2)
        val recent = sourceSessions.filter { it.observedAtMs > lastDestinationUse }
            .sortedByDescending { it.observedAtMs }.take(2)
        if (baseline.size < 2 || recent.size < 2) return null

        val baselineCapacity = median(baseline.map { it.capacityKg }) ?: return null
        val recentCapacity = median(recent.map { it.capacityKg }) ?: return null
        if (baselineCapacity <= 0.0 || recentCapacity <= 0.0) return null
        val ratio = (recentCapacity / baselineCapacity).coerceIn(0.90, 1.10)
        val destinationCapacity = destinationCapacityKg(destinationSessions) ?: return null
        return (destinationCapacity * ratio).takeIf { it.isFinite() && it > 0.0 }
    }

    private fun List<WorkoutTagCapacityObservation>.validDistinctSessions(): List<WorkoutTagCapacityObservation> =
        asSequence()
            .filter { it.sessionId.isNotBlank() && it.capacityKg.isFinite() && it.capacityKg > 0.0 }
            .groupBy { it.sessionId }
            .map { (_, sameSession) -> sameSession.maxBy { it.observedAtMs } }
            .sortedBy { it.observedAtMs }

    private fun median(values: List<Double>): Double? {
        val sorted = values.filter { it.isFinite() && it > 0.0 }.sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
