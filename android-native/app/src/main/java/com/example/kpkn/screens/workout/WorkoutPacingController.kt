package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.services.workout.WorkoutPacingNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object SessionTimeCues {
    const val REMAINING_15 = "Quedan 15 min"
    const val REMAINING_5 = "Quedan 5 min"
    const val REMAINING_1 = "Queda 1 min"
    const val EXHAUSTED = "Tiempo agotado"

    val ALL = setOf(REMAINING_15, REMAINING_5, REMAINING_1, EXHAUSTED)

    const val THRESHOLD_15 = 15 * 60
    const val THRESHOLD_5 = 5 * 60
    const val THRESHOLD_1 = 60
    const val THRESHOLD_0 = 0

    fun crossed(previous: Int?, current: Int, threshold: Int): Boolean {
        val prev = previous ?: return false
        return prev > threshold && current <= threshold
    }
}

/** Custom `0` means the live session explicitly cleared the limit without touching the program. */
internal fun resolveEffectiveSessionTargetMinutes(
    customTargetDurationMinutes: Int?,
    targetDurationMinutes: Int?,
    sessionTargetDurationMinutes: Int?,
): Int? {
    val custom = customTargetDurationMinutes
    if (custom != null) return custom.takeIf { it > 0 }
    return targetDurationMinutes ?: sessionTargetDurationMinutes
}

/**
 * Session pacing coach alerts, session countdown timer, and rest-time adjustment.
 */
class WorkoutPacingController(
    private val scope: CoroutineScope,
    private val pacingNotifications: WorkoutPacingNotificationManager? = null,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val persistOngoingState: () -> Unit,
    private val visibleExercises: (WorkoutUiState) -> List<Exercise>,
    private val isVoiceActive: () -> Boolean = { false },
    private val speakViaVoice: (text: String, essential: Boolean) -> Unit = { _, _ -> },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val isAppInForeground: () -> Boolean = { true },
) {
    private val _sessionTimeRemainingSeconds = MutableStateFlow<Int?>(null)
    val sessionTimeRemainingSeconds: StateFlow<Int?> = _sessionTimeRemainingSeconds.asStateFlow()

    private var sessionTimerJob: Job? = null
    private var cueClearJob: Job? = null
    private val announcedBudgetThresholds = mutableSetOf<String>()
    private val announcedTimeThresholds = mutableSetOf<Int>()
    private var lastPaceNotifyAtMs: Long = 0L
    private var lastPaceNotifyKind: String? = null
    private val softPaceCooldownMs = 8 * 60 * 1000L
    private val countdownCueTexts = SessionTimeCues.ALL

    private fun speakPacing(text: String, essential: Boolean) {
        if (!isVoiceActive()) return
        speakViaVoice(text, essential)
    }

    private fun alertMode(): PacingAlertMode = getState().pacingAlertMode

    private fun resolveTargetMinutes(state: WorkoutUiState = getState()): Int? {
        return resolveEffectiveSessionTargetMinutes(
            customTargetDurationMinutes = state.customTargetDurationMinutes,
            targetDurationMinutes = state.targetDurationMinutes,
            sessionTargetDurationMinutes = state.session?.targetDurationMinutes,
        )
    }

    private fun computeRemainingSeconds(state: WorkoutUiState = getState()): Int? {
        val targetMin = resolveTargetMinutes(state) ?: return null
        if (targetMin <= 0) return null
        val elapsed = ((nowMs() - state.startTimeMs) / 1000L).toInt()
        return targetMin * 60 - elapsed
    }

    private fun maybeNotify(kind: String, message: String, force: Boolean = false) {
        when (alertMode()) {
            PacingAlertMode.OFF -> return
            PacingAlertMode.FINAL -> if (kind != "final" && kind != "exhausted") return
            PacingAlertMode.SOFT,
            PacingAlertMode.STRICT,
            -> Unit
        }
        val now = nowMs()
        val sameKind = lastPaceNotifyKind == kind
        val withinCooldown = now - lastPaceNotifyAtMs < softPaceCooldownMs
        if (!force && sameKind && withinCooldown) return
        if (!force && alertMode() == PacingAlertMode.SOFT && withinCooldown && kind == "slow") return
        lastPaceNotifyAtMs = now
        lastPaceNotifyKind = kind
        if (!isAppInForeground()) {
            pacingNotifications?.notify(message)
        }
    }

    fun resetBudgetAnnouncements() {
        announcedBudgetThresholds.clear()
    }

    /**
     * Guía sonora de presupuesto local (ejercicio o grupo) al ~75 / 90 / 100%.
     * Solo en STRICT (FINAL no avisa locales). No corta el workout.
     */
    fun checkLocalBudgetGuide(
        scopeKey: String,
        scopeLabel: String,
        progress: Float,
        isExerciseScope: Boolean,
    ) {
        if (alertMode() != PacingAlertMode.STRICT) return
        if (progress < 0.75f) return
        val threshold = when {
            progress >= 1f -> 100
            progress >= 0.9f -> 90
            else -> 75
        }
        val key = "$scopeKey@$threshold"
        if (!announcedBudgetThresholds.add(key)) return
        val label = scopeLabel.ifBlank { if (isExerciseScope) "ejercicio" else "grupo" }
        val spoken = when (threshold) {
            75 -> "75 por ciento de $label"
            90 -> "90 por ciento de $label"
            else -> "$label: tiempo agotado"
        }
        speakPacing(spoken, essential = threshold == 100)
        maybeNotify(
            kind = "budget_$threshold",
            message = when (threshold) {
                75 -> "75% · $label"
                90 -> "90% · $label"
                else -> "Tiempo agotado · $label"
            },
        )
    }

    fun startSessionTimer(totalSeconds: Int) {
        sessionTimerJob?.cancel()
        announcedBudgetThresholds.clear()
        announcedTimeThresholds.clear()
        lastPaceNotifyAtMs = 0L
        lastPaceNotifyKind = null
        val initial = computeRemainingSeconds() ?: totalSeconds
        _sessionTimeRemainingSeconds.value = initial
        sessionTimerJob = scope.launch {
            var previous: Int? = null
            while (true) {
                val remaining = computeRemainingSeconds()
                if (remaining == null) {
                    _sessionTimeRemainingSeconds.value = null
                    break
                }
                if (remaining < -3600) break
                _sessionTimeRemainingSeconds.value = remaining
                fireTimeThresholds(previous, remaining)
                evaluatePace(fromTimer = true)
                previous = remaining
                delay(1000L)
            }
        }
    }

    private fun fireTimeThresholds(previous: Int?, remaining: Int) {
        val mode = alertMode()
        if (mode == PacingAlertMode.OFF) return
        fun fire(threshold: Int, cue: String, kind: String, includeOneMinute: Boolean = false) {
            if (!SessionTimeCues.crossed(previous, remaining, threshold)) return
            if (!announcedTimeThresholds.add(threshold)) return
            if (threshold == SessionTimeCues.THRESHOLD_1 && !includeOneMinute) return
            speakPacing(cue, essential = true)
            setCue(cue, sticky = threshold == SessionTimeCues.THRESHOLD_0)
            if (kind.isNotEmpty()) {
                maybeNotify(kind, cue, force = true)
            }
        }
        val oneMinute = mode == PacingAlertMode.SOFT || mode == PacingAlertMode.STRICT
        fire(SessionTimeCues.THRESHOLD_15, SessionTimeCues.REMAINING_15, "final")
        fire(SessionTimeCues.THRESHOLD_5, SessionTimeCues.REMAINING_5, "final")
        fire(SessionTimeCues.THRESHOLD_1, SessionTimeCues.REMAINING_1, "", includeOneMinute = oneMinute)
        fire(SessionTimeCues.THRESHOLD_0, SessionTimeCues.EXHAUSTED, "exhausted")
    }

    private fun setCue(message: String, sticky: Boolean) {
        updateState { it.copy(pacingAlertMessage = message) }
        cueClearJob?.cancel()
        if (sticky) return
        cueClearJob = scope.launch {
            delay(4_000L)
            if (getState().pacingAlertMessage == message) {
                updateState { it.copy(pacingAlertMessage = null) }
            }
        }
    }

    fun adjustSessionTimeLimit(minutesDelta: Int) {
        val currentLimit = resolveTargetMinutes() ?: 60
        setAbsoluteSessionTimeLimit((currentLimit + minutesDelta).coerceAtLeast(5))
    }

    fun setAbsoluteSessionTimeLimit(totalMinutes: Int) {
        val newLimit = totalMinutes.coerceAtLeast(5)
        updateState {
            it.copy(
                customTargetDurationMinutes = newLimit,
                targetDurationMinutes = newLimit,
            )
        }
        persistOngoingState()
        startSessionTimer(0)
    }

    fun clearSessionTimeLimit(persistToSession: Boolean) {
        updateState {
            it.copy(
                customTargetDurationMinutes = if (persistToSession) null else 0,
                targetDurationMinutes = null,
                session = if (persistToSession) {
                    it.session?.copy(targetDurationMinutes = null)
                } else {
                    it.session
                },
                coachPaceAlert = null,
                pacingAlertMessage = it.pacingAlertMessage.takeIf { msg -> msg in countdownCueTexts },
            )
        }
        persistOngoingState()
        cancelSessionTimer()
    }

    fun setPacingAlertMode(mode: PacingAlertMode) {
        updateState { it.copy(pacingAlertMode = mode) }
        persistOngoingState()
    }

    fun cancelSessionTimer() {
        sessionTimerJob?.cancel()
        cueClearJob?.cancel()
        _sessionTimeRemainingSeconds.value = null
    }

    fun checkPaceCoachAlert() = evaluatePace(fromTimer = false)

    fun checkPacingStatus() = evaluatePace(fromTimer = true)

    private fun evaluatePace(fromTimer: Boolean) {
        val state = getState()
        val targetMin = resolveTargetMinutes(state)
        if (targetMin == null || targetMin <= 0 || state.isComplete) {
            if (state.coachPaceAlert != null ||
                (state.pacingAlertMessage != null && state.pacingAlertMessage !in countdownCueTexts)
            ) {
                updateState {
                    it.copy(
                        coachPaceAlert = null,
                        pacingAlertMessage = it.pacingAlertMessage.takeIf { msg -> msg in countdownCueTexts },
                    )
                }
            }
            return
        }

        val remainingSeconds = computeRemainingSeconds(state)
            ?: _sessionTimeRemainingSeconds.value
            ?: return
        val elapsedSeconds = (targetMin * 60) - remainingSeconds
        val allExercises = visibleExercises(state)
        val totalSets = allExercises.sumOf { it.sets.size }
        if (totalSets == 0) return

        val uniqueCompletedSets = state.completedSets.keys.mapNotNull { key ->
            parseCompletedSetKey(key)?.let { "${it.exerciseId}_${it.setIdx}" }
        }.distinct().size

        val progress = uniqueCompletedSets.toDouble() / totalSets.toDouble()
        if (progress >= 1.0) return

        val expectedProgress = elapsedSeconds.toDouble() / (targetMin * 60.0).coerceAtLeast(1.0)

        val newAlert = when {
            remainingSeconds <= 0 -> "excedido"
            progress < expectedProgress - 0.15 && elapsedSeconds > 5 * 60 -> "retrasado"
            progress < expectedProgress - 0.05 && elapsedSeconds > 5 * 60 -> "apurar"
            progress > expectedProgress + 0.15 && elapsedSeconds > 5 * 60 -> "adelantado"
            else -> null
        }

        val alertChanged = state.coachPaceAlert != newAlert
        if (alertChanged) {
            updateState { it.copy(coachPaceAlert = newAlert) }
        }

        when {
            remainingSeconds <= 0 -> {
                val message = SessionTimeCues.EXHAUSTED
                if (alertChanged || !fromTimer) {
                    maybeNotify("exhausted", message, force = alertChanged)
                }
                if (state.pacingAlertMessage != message) {
                    setCue(message, sticky = true)
                }
            }
            progress < expectedProgress - 0.15 && elapsedSeconds > 5 * 60 -> {
                if ((alertMode() == PacingAlertMode.SOFT || alertMode() == PacingAlertMode.STRICT) &&
                    (alertChanged || !fromTimer)
                ) {
                    maybeNotify("slow", "Ritmo lento")
                    speakPacing("Ritmo lento", essential = false)
                }
                if (state.pacingAlertMessage != null && state.pacingAlertMessage !in countdownCueTexts) {
                    updateState { it.copy(pacingAlertMessage = null) }
                }
            }
            else -> {
                if (state.pacingAlertMessage != null && state.pacingAlertMessage !in countdownCueTexts) {
                    updateState { it.copy(pacingAlertMessage = null) }
                    if (alertMode() != PacingAlertMode.OFF) {
                        pacingNotifications?.cancel()
                    }
                }
            }
        }
    }

    fun adjustRestTimeForPace(baseSeconds: Int): Int {
        val state = getState()
        val targetMin = resolveTargetMinutes(state) ?: return baseSeconds
        if (targetMin <= 0) return baseSeconds

        val elapsedMin = ((nowMs() - state.startTimeMs) / 60000).toInt()
        val remainingMin = targetMin - elapsedMin
        val totalSets = visibleExercises(state).sumOf { it.sets.size }
        val dedupCompletedSets = state.completedSets.keys.mapNotNull { key ->
            parseCompletedSetKey(key)?.let { "${it.exerciseId}_${it.setIdx}" }
        }.distinct().size
        val progress = if (totalSets > 0) dedupCompletedSets.toFloat() / totalSets else 0f

        val needsHurry = remainingMin <= 15 && progress < 0.50f
        if (needsHurry && baseSeconds > 60) {
            return 60.coerceAtLeast(baseSeconds - 30)
        }
        return baseSeconds
    }
}
