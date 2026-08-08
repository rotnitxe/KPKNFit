package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.services.workout.WorkoutPacingNotificationManager
import com.example.kpkn.services.workout.WorkoutTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Session pacing coach alerts, session countdown timer, and rest-time adjustment.
 */
class WorkoutPacingController(
    private val scope: CoroutineScope,
    private val pacingNotifications: WorkoutPacingNotificationManager,
    private val sessionTtsManager: WorkoutTtsManager,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val persistOngoingState: () -> Unit,
    private val visibleExercises: (WorkoutUiState) -> List<Exercise>,
    private val isVoiceActive: () -> Boolean = { false },
    private val speakViaVoice: (String) -> Unit = {},
) {
    private val _sessionTimeRemainingSeconds = MutableStateFlow<Int?>(null)
    val sessionTimeRemainingSeconds: StateFlow<Int?> = _sessionTimeRemainingSeconds.asStateFlow()

    private var sessionTimerJob: Job? = null
    private val announcedBudgetThresholds = mutableSetOf<String>()
    private var lastPaceNotifyAtMs: Long = 0L
    private var lastPaceNotifyKind: String? = null
    private val softPaceCooldownMs = 8 * 60 * 1000L

    private fun speakPacing(text: String) {
        if (isVoiceActive()) {
            speakViaVoice(text)
        } else {
            sessionTtsManager.speak(text, queueFlush = true)
        }
    }

    private fun alertMode(): PacingAlertMode = getState().pacingAlertMode

    private fun maybeNotify(kind: String, message: String, force: Boolean = false) {
        when (alertMode()) {
            PacingAlertMode.OFF -> return
            PacingAlertMode.FINAL -> if (kind != "final" && kind != "exhausted") return
            PacingAlertMode.SOFT -> Unit
        }
        val now = System.currentTimeMillis()
        val sameKind = lastPaceNotifyKind == kind
        val withinCooldown = now - lastPaceNotifyAtMs < softPaceCooldownMs
        if (!force && sameKind && withinCooldown) return
        if (!force && alertMode() == PacingAlertMode.SOFT && withinCooldown && kind == "slow") return
        lastPaceNotifyAtMs = now
        lastPaceNotifyKind = kind
        pacingNotifications.notify(message)
    }

    fun resetBudgetAnnouncements() {
        announcedBudgetThresholds.clear()
    }

    /**
     * Guía sonora de presupuesto local (ejercicio o grupo) al ~75 / 90 / 100%.
     * No corta el workout: solo avisa ritmo.
     */
    fun checkLocalBudgetGuide(
        scopeKey: String,
        scopeLabel: String,
        progress: Float,
        isExerciseScope: Boolean,
    ) {
        if (alertMode() == PacingAlertMode.OFF) return
        if (progress < 0.75f) return
        val threshold = when {
            progress >= 1f -> 100
            progress >= 0.9f -> 90
            else -> 75
        }
        // En modo FINAL solo avisar al 100%.
        if (alertMode() == PacingAlertMode.FINAL && threshold < 100) return
        val key = "$scopeKey@$threshold"
        if (!announcedBudgetThresholds.add(key)) return
        val kind = if (isExerciseScope) "ejercicio" else "grupo"
        val message = when (threshold) {
            75 -> "Vas al 75 por ciento del presupuesto de $kind $scopeLabel. Es solo guía de ritmo."
            90 -> "Vas al 90 por ciento del presupuesto de $kind $scopeLabel. Ajusta el ritmo si hace falta."
            else -> "Presupuesto de $kind $scopeLabel agotado. Continúa con calma; es una guía, no un corte."
        }
        if (alertMode() == PacingAlertMode.SOFT || threshold == 100) {
            speakPacing(message)
        }
        maybeNotify(
            kind = "budget_$threshold",
            message = when (threshold) {
                75 -> "75% presupuesto · $scopeLabel"
                90 -> "90% presupuesto · $scopeLabel"
                else -> "Presupuesto agotado · $scopeLabel"
            },
        )
    }

    fun startSessionTimer(totalSeconds: Int) {
        sessionTimerJob?.cancel()
        announcedBudgetThresholds.clear()
        lastPaceNotifyAtMs = 0L
        lastPaceNotifyKind = null
        _sessionTimeRemainingSeconds.value = totalSeconds
        sessionTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining >= -3600) {
                _sessionTimeRemainingSeconds.value = remaining
                val mode = alertMode()
                if (mode != PacingAlertMode.OFF) {
                    if (remaining == 15 * 60) {
                        speakPacing("Quedan 15 minutos para completar la sesión.")
                        maybeNotify("final", "Quedan 15 minutos", force = true)
                    }
                    if (remaining == 300) {
                        speakPacing("Quedan 5 minutos para completar la sesión.")
                        maybeNotify("final", "Quedan 5 minutos", force = true)
                    }
                    if (mode == PacingAlertMode.SOFT && remaining == 60) {
                        speakPacing("Queda un minuto para completar la sesión de entrenamiento.")
                    }
                    if (remaining == 0) {
                        speakPacing("Tiempo estimado de entrenamiento agotado.")
                        maybeNotify("exhausted", "Tiempo de sesión agotado", force = true)
                    }
                }
                evaluatePace(fromTimer = true)
                delay(1000L)
                remaining--
            }
        }
    }

    fun adjustSessionTimeLimit(minutesDelta: Int) {
        val currentLimit = getState().customTargetDurationMinutes
            ?: getState().targetDurationMinutes
            ?: getState().session?.targetDurationMinutes
            ?: 60
        setAbsoluteSessionTimeLimit((currentLimit + minutesDelta).coerceAtLeast(5))
    }

    fun setAbsoluteSessionTimeLimit(totalMinutes: Int) {
        val newLimit = totalMinutes.coerceAtLeast(5)
        val now = System.currentTimeMillis()
        val elapsedSeconds = ((now - getState().startTimeMs) / 1000L).coerceAtLeast(0)
        val newRemainingSeconds = ((newLimit * 60) - elapsedSeconds).toInt()
        updateState {
            it.copy(
                customTargetDurationMinutes = newLimit,
                targetDurationMinutes = newLimit,
                session = it.session?.copy(targetDurationMinutes = newLimit),
            )
        }
        _sessionTimeRemainingSeconds.value = newRemainingSeconds
        persistOngoingState()
        startSessionTimer(newRemainingSeconds)
    }

    fun setPacingAlertMode(mode: PacingAlertMode) {
        updateState { it.copy(pacingAlertMode = mode) }
        persistOngoingState()
    }

    fun cancelSessionTimer() {
        sessionTimerJob?.cancel()
        _sessionTimeRemainingSeconds.value = null
    }

    fun checkPaceCoachAlert() = evaluatePace(fromTimer = false)

    fun checkPacingStatus() = evaluatePace(fromTimer = true)

    private fun evaluatePace(fromTimer: Boolean) {
        val state = getState()
        val targetMin = state.customTargetDurationMinutes
            ?: state.targetDurationMinutes
            ?: state.session?.targetDurationMinutes
        if (targetMin == null || targetMin <= 0 || state.isComplete) {
            if (state.coachPaceAlert != null || state.pacingAlertMessage != null) {
                updateState { it.copy(coachPaceAlert = null, pacingAlertMessage = null) }
            }
            return
        }

        val remainingSeconds = _sessionTimeRemainingSeconds.value
            ?: ((targetMin * 60) - ((System.currentTimeMillis() - state.startTimeMs) / 1000L).toInt())
        val elapsedSeconds = (targetMin * 60) - remainingSeconds
        val remainingMin = remainingSeconds / 60
        val allExercises = visibleExercises(state)
        val totalSets = allExercises.sumOf { it.sets.size }
        if (totalSets == 0) return

        val uniqueCompletedSets = state.completedSets.keys.map { key ->
            val parts = key.split("_")
            if (parts.size >= 2) "${parts[0]}_${parts[1]}" else key
        }.distinct().size

        val progress = uniqueCompletedSets.toDouble() / totalSets.toDouble()
        if (progress >= 1.0) return

        val expectedProgress = elapsedSeconds.toDouble() / (targetMin * 60.0).coerceAtLeast(1.0)

        val newAlert = when {
            remainingMin <= 0 -> "excedido"
            progress < expectedProgress - 0.15 && elapsedSeconds > 5 * 60 -> "retrasado"
            progress < expectedProgress - 0.05 && elapsedSeconds > 5 * 60 -> "apurar"
            else -> null
        }

        val alertChanged = state.coachPaceAlert != newAlert
        if (alertChanged) {
            updateState { it.copy(coachPaceAlert = newAlert) }
        }

        when {
            remainingMin <= 0 -> {
                val message = "Tiempo de sesión agotado"
                if (alertChanged || !fromTimer) {
                    maybeNotify("exhausted", message, force = alertChanged)
                }
                updateState { it.copy(pacingAlertMessage = message) }
            }
            progress < expectedProgress - 0.15 && elapsedSeconds > 5 * 60 -> {
                val remainingSets = totalSets - uniqueCompletedSets
                val safeRemainingMin = remainingMin.coerceAtLeast(0)
                val message = "Ritmo lento · $remainingSets series · $safeRemainingMin min"
                // Stable kind so minute ticks do not re-spam notifications.
                if (alertMode() == PacingAlertMode.SOFT && (alertChanged || !fromTimer)) {
                    maybeNotify("slow", message)
                }
                updateState { it.copy(pacingAlertMessage = message) }
            }
            else -> {
                if (state.pacingAlertMessage != null) {
                    updateState { it.copy(pacingAlertMessage = null) }
                    if (alertMode() != PacingAlertMode.OFF) {
                        pacingNotifications.cancel()
                    }
                }
            }
        }
    }

    fun adjustRestTimeForPace(baseSeconds: Int): Int {
        val state = getState()
        val targetMin = state.customTargetDurationMinutes ?: state.session?.targetDurationMinutes ?: return baseSeconds
        if (targetMin <= 0) return baseSeconds

        val elapsedMin = ((System.currentTimeMillis() - state.startTimeMs) / 60000).toInt()
        val remainingMin = targetMin - elapsedMin
        val totalSets = visibleExercises(state).sumOf { it.sets.size }
        val dedupCompletedSets = state.completedSets.keys.map { key ->
            val parts = key.split("_")
            if (parts.size >= 2) "${parts[0]}_${parts[1]}" else key
        }.distinct().size
        val progress = if (totalSets > 0) dedupCompletedSets.toFloat() / totalSets else 0f

        val needsHurry = remainingMin <= 15 && progress < 0.50f
        if (needsHurry && baseSeconds > 60) {
            return 60.coerceAtLeast(baseSeconds - 30)
        }
        return baseSeconds
    }
}
