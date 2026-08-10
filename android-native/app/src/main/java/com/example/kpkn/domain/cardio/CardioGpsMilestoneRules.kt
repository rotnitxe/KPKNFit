package com.example.kpkn.domain.cardio

import kotlin.math.floor

object CardioGpsMilestoneEngine {
    /** Returns each whole-kilometre milestone reached since the last emitted one. */
    fun reachedKilometres(
        distanceKm: Double,
        alreadyEmitted: Set<Int> = emptySet(),
        targetDistanceKm: Double? = null,
    ): List<Int> {
        if (!distanceKm.isFinite() || distanceKm < 1.0) return emptyList()
        val cap = targetDistanceKm?.takeIf { it > 0.0 } ?: distanceKm
        val lastReached = floor(distanceKm.coerceAtMost(cap)).toInt()
        return (1..lastReached).filter { it !in alreadyEmitted }
    }
}
