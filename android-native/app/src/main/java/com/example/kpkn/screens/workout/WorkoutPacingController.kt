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

    private fun speakPacing(text: String) {
        if (isVoiceActive()) {
            speakViaVoice(text)
        } else {
            sessionTtsManager.speak(text, queueFlush = true)
        }
    }

    fun startSessionTimer(totalSeconds: Int) {
        sessionTimerJob?.cancel()
        _sessionTimeRemainingSeconds.value = totalSeconds
        sessionTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining >= -3600) {
                _sessionTimeRemainingSeconds.value = remaining
                if (remaining == 300) {
                    speakPacing("Quedan 5 minutos para completar la sesión.")
                }
                if (remaining == 60) {
                    speakPacing("Queda un minuto para completar la sesión de entrenamiento.")
                }
                if (remaining == 0) {
                    speakPacing("Tiempo estimado de entrenamiento agotado.")
                }
                checkPacingStatus()
                delay(1000L)
                remaining--
            }
        }
    }

    fun adjustSessionTimeLimit(minutes: Int) {
        val currentLimit = getState().customTargetDurationMinutes
            ?: getState().targetDurationMinutes
            ?: getState().session?.targetDurationMinutes
            ?: 60
        val newLimit = (currentLimit + minutes).coerceAtLeast(5)
        val now = System.currentTimeMillis()
        val elapsedSeconds = ((now - getState().startTimeMs) / 1000L).coerceAtLeast(0)
        val newRemainingSeconds = ((newLimit * 60) - elapsedSeconds).toInt()
        updateState {
            it.copy(
                customTargetDurationMinutes = newLimit,
                targetDurationMinutes = newLimit,
            )
        }
        _sessionTimeRemainingSeconds.value = newRemainingSeconds
        persistOngoingState()
        startSessionTimer(newRemainingSeconds)
    }

    fun cancelSessionTimer() {
        sessionTimerJob?.cancel()
        _sessionTimeRemainingSeconds.value = null
    }

    fun checkPaceCoachAlert() {
        val state = getState()
        val targetMin = state.customTargetDurationMinutes ?: state.session?.targetDurationMinutes
        if (targetMin == null || targetMin <= 0 || state.isComplete) {
            if (state.coachPaceAlert != null || state.pacingAlertMessage != null) {
                updateState { it.copy(coachPaceAlert = null, pacingAlertMessage = null) }
            }
            return
        }

        val elapsedSeconds = ((System.currentTimeMillis() - state.startTimeMs) / 1000L).coerceAtLeast(0)
        val remainingSeconds = ((targetMin * 60) - elapsedSeconds).toInt()
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

        val expectedProgress = elapsedSeconds.toDouble() / (targetMin * 60.0)

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

        if (progress < expectedProgress - 0.15 && elapsedSeconds > 5 * 60) {
            val remainingSets = totalSets - uniqueCompletedSets
            val safeRemainingMin = remainingMin.coerceAtLeast(0)
            val message = "Ritmo lento · $remainingSets series · $safeRemainingMin min"
            if (message != state.pacingAlertMessage) {
                pacingNotifications.notify(message)
            }
            updateState { it.copy(pacingAlertMessage = message) }
        } else if (remainingMin <= 0) {
            val message = "Tiempo de sesión agotado"
            if (alertChanged) {
                pacingNotifications.notify(message)
            }
            updateState { it.copy(pacingAlertMessage = message) }
        } else {
            updateState { it.copy(pacingAlertMessage = null) }
            pacingNotifications.cancel()
        }
    }

    fun checkPacingStatus() {
        val state = getState()
        val totalMinutes = state.customTargetDurationMinutes ?: state.targetDurationMinutes ?: return
        val remainingSeconds = _sessionTimeRemainingSeconds.value ?: return

        val allExercises = visibleExercises(state)
        val totalSets = allExercises.sumOf { it.sets.size }
        if (totalSets == 0) return

        val uniqueCompletedSets = state.completedSets.keys.map { key ->
            val parts = key.split("_")
            if (parts.size >= 2) "${parts[0]}_${parts[1]}" else key
        }.distinct().size

        val progress = uniqueCompletedSets.toDouble() / totalSets.toDouble()
        if (progress >= 1.0) return

        val totalSeconds = totalMinutes * 60
        val elapsedSeconds = totalSeconds - remainingSeconds
        if (elapsedSeconds < 5 * 60) return

        val expectedProgress = elapsedSeconds.toDouble() / totalSeconds.toDouble()

        if (progress < expectedProgress - 0.15) {
            val remainingSets = totalSets - uniqueCompletedSets
            val remainingMinutes = if (remainingSeconds > 0) remainingSeconds / 60 else 0
            val message = "Ritmo lento · $remainingSets series · $remainingMinutes min"
            if (message != state.pacingAlertMessage) {
                pacingNotifications.notify(message)
            }
            updateState { it.copy(pacingAlertMessage = message) }
        } else if (state.pacingAlertMessage != null) {
            pacingNotifications.cancel()
            updateState { it.copy(pacingAlertMessage = null) }
        }
    }

    fun adjustRestTimeForPace(baseSeconds: Int): Int {
        val state = getState()
        val targetMin = state.customTargetDurationMinutes ?: state.session?.targetDurationMinutes ?: return baseSeconds
        if (targetMin <= 0) return baseSeconds

        val elapsedMin = ((System.currentTimeMillis() - state.startTimeMs) / 60000).toInt()
        val remainingMin = targetMin - elapsedMin
        val totalSets = visibleExercises(state).sumOf { it.sets.size }
        val completedSets = state.completedSets.size
        val progress = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f

        val needsHurry = remainingMin <= 15 && progress < 0.50f
        if (needsHurry && baseSeconds > 60) {
            return 60.coerceAtLeast(baseSeconds - 30)
        }
        return baseSeconds
    }
}
