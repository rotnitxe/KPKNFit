package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns rest-timer mechanics (remaining/recovery flows, alert schedule, tick loop).
 * Finish-sheet / feedback / voice policy stay in [WorkoutViewModel] via callbacks.
 */
class RestTimerController(
    private val scope: CoroutineScope,
    private val restAlertManager: WorkoutRestAlertManager,
) {
    private val _remaining = MutableStateFlow(0)
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    private val _recovery = MutableStateFlow<RestRecoveryStatus?>(null)
    val recovery: StateFlow<RestRecoveryStatus?> = _recovery.asStateFlow()

    private var timerJob: Job? = null
    var activeRestTimerId: String? = null
        private set
    var restStartedAtMs: Long? = null
        private set
    var restReferenceSet: CompletedSet? = null
        private set
    var restReferenceAdvanced: SetAdvancedFeedback? = null
        private set

    fun setReferences(lastSet: CompletedSet?, advanced: SetAdvancedFeedback?) {
        restReferenceSet = lastSet
        restReferenceAdvanced = advanced
    }

    fun markRestStart(nowMs: Long = System.currentTimeMillis()): Long {
        restStartedAtMs = nowMs
        return nowMs
    }

    fun keepRestStartOrNow(
        preserveElapsed: Boolean,
        previousEndsAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Long {
        val timerExpired = preserveElapsed && previousEndsAtMs != null && previousEndsAtMs <= nowMs
        return if (preserveElapsed && restStartedAtMs != null && !timerExpired) {
            restStartedAtMs!!
        } else {
            markRestStart(nowMs)
        }
    }

    /** Cancels jobs/alarms without applying pending feedback/finish side-effects. */
    fun abortHard() {
        timerJob?.cancel()
        timerJob = null
        restAlertManager.cancelRestAlerts()
        activeRestTimerId = null
        restReferenceSet = null
        restReferenceAdvanced = null
        restStartedAtMs = null
        _remaining.value = 0
        _recovery.value = null
    }

    fun cancelJob() {
        timerJob?.cancel()
        timerJob = null
    }

    fun cancelAlerts() {
        restAlertManager.cancelRestAlerts()
    }

    fun clearActiveTimerId() {
        activeRestTimerId = null
    }

    fun clearReferences() {
        restReferenceSet = null
        restReferenceAdvanced = null
        restStartedAtMs = null
    }

    fun zeroRemaining() {
        _remaining.value = 0
        _recovery.value = null
    }

    fun scheduleAndTick(
        seconds: Int,
        endMs: Long,
        restStartMs: Long,
        sessionName: String,
        exerciseName: String,
        preserveElapsed: Boolean,
        onNaturalFinish: suspend (finishedTimerId: String?) -> Unit,
    ) {
        timerJob?.cancel()
        val initialElapsed = ((System.currentTimeMillis() - restStartMs) / 1000L).toInt().coerceAtLeast(0)
        _recovery.value = WorkoutRestRecoveryModel.fromLastSet(
            elapsedSeconds = initialElapsed,
            completedSet = restReferenceSet,
            advanced = restReferenceAdvanced,
        )
        activeRestTimerId = restAlertManager.scheduleRestEnd(
            durationSeconds = seconds,
            sessionName = sessionName,
            exerciseName = exerciseName,
            endAtOverrideMs = endMs,
            isAdjustment = preserveElapsed,
        )
        _remaining.value = seconds

        val scheduledId = activeRestTimerId
        timerJob = scope.launch {
            var lastElapsedSecond = -1
            var lastRemaining = _remaining.value
            while (true) {
                delay(500L)
                val remainingNow = ((endMs - System.currentTimeMillis() + 500) / 1000L).toInt().coerceAtLeast(0)
                if (remainingNow != lastRemaining) {
                    lastRemaining = remainingNow
                    _remaining.value = remainingNow
                }
                val elapsed = ((System.currentTimeMillis() - restStartMs) / 1000L).toInt().coerceAtLeast(0)
                if (elapsed != lastElapsedSecond) {
                    lastElapsedSecond = elapsed
                    _recovery.value = WorkoutRestRecoveryModel.fromLastSet(
                        elapsedSeconds = elapsed,
                        completedSet = restReferenceSet,
                        advanced = restReferenceAdvanced,
                    )
                }
                if (remainingNow <= 0) break
            }
            restAlertManager.onTimerFinishedInApp(activeRestTimerId)
            restAlertManager.cancelRestAlerts()
            val finishedId = activeRestTimerId
            activeRestTimerId = null
            onNaturalFinish(finishedId ?: scheduledId)
        }
    }

    fun capability(soundsEnabled: Boolean) =
        restAlertManager.capabilityState(soundsEnabled = soundsEnabled)
}
