package com.example.kpkn.screens.workout

/**
 * Temporary in-memory bridge to pass readiness adjustments from
 * ReadinessGateScreen to WorkoutScreen during the navigation transition.
 */
object WorkoutReadinessBridge {
    data class ReadinessAdjustments(
        val neural: Int?,
        val muscular: Int?,
        val spinal: Int?,
        val perMuscle: Map<String, Int>,
    )

    private var pending: ReadinessAdjustments? = null

    fun store(adjustments: ReadinessAdjustments) {
        pending = adjustments
    }

    fun consume(): ReadinessAdjustments? {
        val result = pending
        pending = null
        return result
    }
}
