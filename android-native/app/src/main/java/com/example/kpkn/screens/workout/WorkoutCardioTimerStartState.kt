package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState

/** A paused free-duration timer keeps its elapsed time; another set starts its own timer. */
internal fun cardioTimerForStart(
    exerciseId: String,
    setId: String?,
    previous: CardioTimerState?,
    totalSeconds: Int,
    freeDuration: Boolean,
    nowMs: Long,
): CardioTimerState {
    val current = previous?.takeIf { it.exerciseId == exerciseId && it.setId == setId }
    val base = when {
        current == null || current.status == CardioExecutionStatus.RECORDED ->
            CardioTimerState(exerciseId, totalSeconds, totalSeconds, setId = setId, executionStartedAtMs = nowMs)
        !freeDuration && current.remainingSeconds <= 0 -> current.copy(
            totalSeconds = totalSeconds, remainingSeconds = totalSeconds, elapsedSeconds = 0,
            status = CardioExecutionStatus.READY,
        )
        else -> current.copy(totalSeconds = totalSeconds)
    }
    return base.copy(setId = setId, executionStartedAtMs = base.executionStartedAtMs.takeIf { it > 0L } ?: nowMs)
}
