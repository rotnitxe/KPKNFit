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

/** Narrow alert dependency so timer ownership can be tested without Android services. */
internal interface RestTimerAlertSink {
    fun scheduleRestEnd(
        durationSeconds: Int,
        sessionName: String,
        exerciseName: String,
        endAtOverrideMs: Long,
        isAdjustment: Boolean,
    ): String

    fun onTimerFinishedInApp(expectedTimerId: String?)

    fun cancelRestAlerts()
}

private class WorkoutRestAlertSinkAdapter(
    private val manager: WorkoutRestAlertManager,
) : RestTimerAlertSink {
    override fun scheduleRestEnd(
        durationSeconds: Int,
        sessionName: String,
        exerciseName: String,
        endAtOverrideMs: Long,
        isAdjustment: Boolean,
    ): String = manager.scheduleRestEnd(
        durationSeconds = durationSeconds,
        sessionName = sessionName,
        exerciseName = exerciseName,
        endAtOverrideMs = endAtOverrideMs,
        isAdjustment = isAdjustment,
    )

    override fun onTimerFinishedInApp(expectedTimerId: String?) {
        manager.onTimerFinishedInApp(expectedTimerId)
    }

    override fun cancelRestAlerts() {
        manager.cancelRestAlerts()
    }
}

/**
 * Owns rest-timer mechanics (remaining/recovery flows, alert schedule, tick loop).
 * Finish-sheet / feedback / voice policy stay in [WorkoutViewModel] via callbacks.
 */
class RestTimerController private constructor(
    private val scope: CoroutineScope,
    private val alertSink: RestTimerAlertSink,
    private val capabilityProvider: (Boolean) -> WorkoutRestAlertManager.RestAlertCapabilityState,
) {
    constructor(
        scope: CoroutineScope,
        restAlertManager: WorkoutRestAlertManager,
    ) : this(
        scope = scope,
        alertSink = WorkoutRestAlertSinkAdapter(restAlertManager),
        capabilityProvider = restAlertManager::capabilityState,
    )

    internal constructor(
        scope: CoroutineScope,
        alertSink: RestTimerAlertSink,
    ) : this(
        scope = scope,
        alertSink = alertSink,
        capabilityProvider = { error("capability() is unavailable for a test alert sink") },
    )

    private val _remaining = MutableStateFlow(0)
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    private val _recovery = MutableStateFlow<RestRecoveryStatus?>(null)
    val recovery: StateFlow<RestRecoveryStatus?> = _recovery.asStateFlow()

    private var timerJob: Job? = null
    private var timerGeneration: Long = 0L
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
        timerGeneration += 1
        alertSink.cancelRestAlerts()
        activeRestTimerId = null
        restReferenceSet = null
        restReferenceAdvanced = null
        restStartedAtMs = null
        _remaining.value = 0
        _recovery.value = null
    }

    fun cancelJob() {
        timerGeneration += 1
        timerJob?.cancel()
        timerJob = null
    }

    fun cancelAlerts() {
        timerGeneration += 1
        alertSink.cancelRestAlerts()
    }

    fun clearActiveTimerId() {
        timerGeneration += 1
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
        val generation = ++timerGeneration
        val initialElapsed = ((System.currentTimeMillis() - restStartMs) / 1000L).toInt().coerceAtLeast(0)
        _recovery.value = WorkoutRestRecoveryModel.fromLastSet(
            elapsedSeconds = initialElapsed,
            completedSet = restReferenceSet,
            advanced = restReferenceAdvanced,
        )
        activeRestTimerId = alertSink.scheduleRestEnd(
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
                if (!ownsTimer(generation, scheduledId)) return@launch
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
            if (!ownsTimer(generation, scheduledId)) return@launch
            alertSink.onTimerFinishedInApp(scheduledId)
            if (!ownsTimer(generation, scheduledId)) return@launch
            alertSink.cancelRestAlerts()
            if (!ownsTimer(generation, scheduledId)) return@launch
            val finishedId = scheduledId
            activeRestTimerId = null
            timerJob = null
            onNaturalFinish(finishedId ?: scheduledId)
        }
    }

    fun capability(soundsEnabled: Boolean) =
        capabilityProvider(soundsEnabled)

    private fun ownsTimer(generation: Long, scheduledId: String?): Boolean =
        timerGeneration == generation && activeRestTimerId == scheduledId
}
