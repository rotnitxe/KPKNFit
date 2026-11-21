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

    fun onPrealert(expectedTimerId: String?) = Unit
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

    override fun onPrealert(expectedTimerId: String?) = manager.onPrealertInApp(expectedTimerId)
}

/**
 * Owns rest-timer mechanics (remaining/recovery flows, alert schedule, tick loop).
 * Finish-sheet / feedback / voice policy stay in [WorkoutViewModel] via callbacks.
 */
class RestTimerController private constructor(
    private val scope: CoroutineScope,
    private val alertSink: RestTimerAlertSink,
    private val capabilityProvider: (Boolean) -> WorkoutRestAlertManager.RestAlertCapabilityState,
    private val wallClock: () -> Long,
    private val monotonicClock: () -> Long,
    private val bootCountProvider: () -> Int?,
) {
    constructor(
        scope: CoroutineScope,
        restAlertManager: WorkoutRestAlertManager,
    ) : this(
        scope = scope,
        alertSink = WorkoutRestAlertSinkAdapter(restAlertManager),
        capabilityProvider = restAlertManager::capabilityState,
        wallClock = System::currentTimeMillis,
        monotonicClock = android.os.SystemClock::elapsedRealtime,
        bootCountProvider = { restAlertManager.bootCount },
    )

    internal constructor(
        scope: CoroutineScope,
        alertSink: RestTimerAlertSink,
        wallClock: () -> Long = System::currentTimeMillis,
        monotonicClock: () -> Long = wallClock,
    ) : this(
        scope = scope,
        alertSink = alertSink,
        capabilityProvider = { error("capability() is unavailable for a test alert sink") },
        wallClock = wallClock,
        monotonicClock = monotonicClock,
        bootCountProvider = { null },
    )

    private val _remaining = MutableStateFlow(0)
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    private val _recovery = MutableStateFlow<RestRecoveryStatus?>(null)
    val recovery: StateFlow<RestRecoveryStatus?> = _recovery.asStateFlow()

    private var timerJob: Job? = null
    private var timerGeneration: Long = 0L
    private var scheduledEndMs: Long = 0L
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

    fun stampDeadline(rest: WorkoutRestModalState, nowMs: Long): WorkoutRestModalState =
        rest.copy(
            elapsedRealtimeDeadlineMs = monotonicClock() + (rest.endsAtMs - nowMs),
            deadlineBootCount = bootCountProvider(),
        )

    fun projectWallDeadline(rest: WorkoutRestModalState, nowMs: Long): WorkoutRestModalState {
        val elapsedDeadline = rest.elapsedRealtimeDeadlineMs ?: return rest
        if (rest.deadlineBootCount == null || rest.deadlineBootCount != bootCountProvider()) return rest
        if (rest.pausedRemainingSeconds != null) return rest
        val projected = nowMs + (elapsedDeadline - monotonicClock())
        val correction = projected - rest.endsAtMs
        return rest.copy(
            endsAtMs = projected,
            startedAtMs = rest.startedAtMs.takeIf { it > 0L }?.plus(correction) ?: 0L,
        )
    }

    fun wallDeadline(nowMs: Long): Long? =
        scheduledEndMs.takeIf { it > 0L }?.let { nowMs + (it - monotonicClock()) }

    /** Cancels jobs/alarms without applying pending feedback/finish side-effects. */
    fun abortHard() {
        timerJob?.cancel()
        timerJob = null
        timerGeneration += 1
        alertSink.cancelRestAlerts()
        activeRestTimerId = null
        scheduledEndMs = 0L
        com.example.kpkn.services.workout.RestEndSignal.endsAtMs = 0L
        restReferenceSet = null
        restReferenceAdvanced = null
        restStartedAtMs = null
        _remaining.value = 0
        _recovery.value = null
    }

    /** Freezes the clock and the alert without losing the remaining time. Returns the frozen seconds. */
    fun pause(nowMs: Long = monotonicClock()): Int? {
        if (scheduledEndMs <= 0L) return null
        val remaining = remainingAt(nowMs)
        timerGeneration += 1
        timerJob?.cancel()
        timerJob = null
        alertSink.cancelRestAlerts()
        activeRestTimerId = null
        scheduledEndMs = 0L
        com.example.kpkn.services.workout.RestEndSignal.endsAtMs = 0L
        _remaining.value = remaining
        return remaining
    }

    /** Remaining whole seconds of the running rest, including the same rounding as the tick loop. */
    fun remainingAt(nowMs: Long = monotonicClock()): Int =
        if (scheduledEndMs <= 0L) {
            _remaining.value.coerceAtLeast(0)
        } else {
            ((scheduledEndMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(0)
        }

    /** Updates the frozen seconds after a suggestion is applied while the rest is paused. */
    fun setPausedRemaining(seconds: Int) {
        timerGeneration += 1
        timerJob?.cancel()
        timerJob = null
        alertSink.cancelRestAlerts()
        activeRestTimerId = null
        scheduledEndMs = 0L
        com.example.kpkn.services.workout.RestEndSignal.endsAtMs = 0L
        _remaining.value = seconds.coerceAtLeast(0)
    }

    /** Detach the screen while keeping the system alert for the ongoing workout. */
    fun detachScreen() = cancelJob()

    fun cancelJob() {
        timerGeneration += 1
        timerJob?.cancel()
        timerJob = null
    }

    fun cancelAlerts() {
        timerGeneration += 1
        com.example.kpkn.services.workout.RestEndSignal.endsAtMs = 0L
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
        scheduledEndMs = 0L
        _remaining.value = 0
        _recovery.value = null
    }

    fun fireNaturalFinishIfIdleAtZero() {
        if (_remaining.value > 0) return
        if (timerJob?.isActive == true) return
        val callback = lastOnNaturalFinish ?: return
        val id = activeRestTimerId
        val generation = timerGeneration
        scope.launch { if (ownsTimer(generation, id)) callback(id) }
    }

    private var lastOnNaturalFinish: (suspend (String?) -> Unit)? = null

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
        val wallNow = wallClock()
        val monotonicNow = monotonicClock()
        val elapsedEndMs = monotonicNow + (endMs - wallNow)
        val elapsedStartMs = monotonicNow + (restStartMs - wallNow)
        scheduledEndMs = elapsedEndMs
        com.example.kpkn.services.workout.RestEndSignal.endsAtMs = elapsedEndMs
        val initialElapsed = ((monotonicNow - elapsedStartMs) / 1000L).toInt().coerceAtLeast(0)
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
        lastOnNaturalFinish = onNaturalFinish
        timerJob = scope.launch {
            var lastElapsedSecond = -1
            var lastRemaining = _remaining.value
            val prealertAt = com.example.kpkn.services.workout.workoutRestPrealertTriggerAt(elapsedEndMs)
            var prealertDelivered = !com.example.kpkn.services.workout.shouldScheduleWorkoutRestPrealert(seconds)
            while (true) {
                delay(500L)
                if (!ownsTimer(generation, scheduledId)) return@launch
                val now = monotonicClock()
                val remainingNow = ((elapsedEndMs - now + 999L) / 1000L).toInt().coerceAtLeast(0)
                if (!prealertDelivered && now >= prealertAt && remainingNow > 0) {
                    prealertDelivered = true
                    alertSink.onPrealert(scheduledId)
                }
                if (remainingNow != lastRemaining) {
                    lastRemaining = remainingNow
                    _remaining.value = remainingNow
                }
                val elapsed = ((now - elapsedStartMs) / 1000L).toInt().coerceAtLeast(0)
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
            val finishedId = scheduledId
            activeRestTimerId = null
            scheduledEndMs = 0L
            timerJob = null
            onNaturalFinish(finishedId ?: scheduledId)
        }
    }

    fun capability(soundsEnabled: Boolean) =
        capabilityProvider(soundsEnabled)

    private fun ownsTimer(generation: Long, scheduledId: String?): Boolean =
        timerGeneration == generation && activeRestTimerId == scheduledId
}
