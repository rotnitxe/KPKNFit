package com.example.kpkn.ui.tour

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Tiempo que espera un paso a que su ancla aparezca antes de saltarlo. */
const val TOUR_ANCHOR_TIMEOUT_MS = 1_500L

/**
 * Recorrido guiado: solo hay uno activo a la vez.
 *
 * - [start] arranca un recorrido que no se ha visto; [next] avanza; [skip] lo termina.
 * - Un paso «Pruébalo» ([TourStepKind.TRY]) avanza con [onAnchorTapped] sobre su ancla.
 * - Si el ancla de un paso no aparece en [anchorTimeoutMs], ese paso se salta solo.
 * - Terminar o saltar marca el recorrido como visto en [seen] y llama a [onFinished].
 */
class TourController(
    private val scope: CoroutineScope,
    private val seen: TourSeenStore,
    private val hasAnchor: (String) -> Boolean,
    private val onFinished: () -> Unit = {},
    private val anchorTimeoutMs: Long = TOUR_ANCHOR_TIMEOUT_MS,
) {
    private val _state = MutableStateFlow(TourUiState())
    val state: StateFlow<TourUiState> = _state.asStateFlow()

    private var waitJob: Job? = null

    /** Arranca [tourId] si tiene pasos, no hay otro activo y no se ha visto. Devuelve si arrancó. */
    fun start(tourId: String, steps: List<TourStep>): Boolean {
        if (steps.isEmpty() || _state.value.isActive || tourId in seen.seenIds()) return false
        _state.value = TourUiState(tourId = tourId, steps = steps, index = 0)
        awaitAnchor()
        return true
    }

    fun next() = advance()

    fun skip() = finish()

    /** Un toque sobre [anchorId] avanza solo si el paso actual es «Pruébalo» sobre ese mismo ancla. */
    fun onAnchorTapped(anchorId: String) {
        val step = _state.value.current ?: return
        if (step.kind == TourStepKind.TRY && step.anchorId == anchorId) advance()
    }

    private fun advance() {
        val current = _state.value
        if (!current.isActive) return
        val nextIndex = current.index + 1
        if (nextIndex >= current.steps.size) {
            finish()
        } else {
            _state.value = current.copy(index = nextIndex)
            awaitAnchor()
        }
    }

    private fun finish() {
        val tourId = _state.value.tourId ?: return
        waitJob?.cancel()
        waitJob = null
        _state.value = TourUiState()
        seen.markSeen(tourId)
        onFinished()
    }

    /** Plazo del paso actual: si su ancla sigue sin aparecer al cumplirse, el paso se salta. */
    private fun awaitAnchor() {
        waitJob?.cancel()
        val current = _state.value
        val step = current.current ?: return
        waitJob = scope.launch {
            delay(anchorTimeoutMs)
            val now = _state.value
            if (now.tourId == current.tourId && now.index == current.index && !hasAnchor(step.anchorId)) {
                advance()
            }
        }
    }
}
