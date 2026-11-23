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

/** Pausa breve para mostrar la micro-celebración «✓ ¡Listo!» tras completar un paso TRY. */
const val TOUR_TRY_SUCCESS_DELAY_MS = 600L

/**
 * Recorrido guiado: solo hay uno activo a la vez.
 *
 * - [start] arranca un recorrido que no se ha visto; [next] avanza; [previous] retrocede; [skip] lo termina.
 * - Un paso «Pruébalo» ([TourStepKind.TRY]) avanza con [onAnchorTapped] sobre `anchorId` o `tryAnchorId`
 *   (tras mostrar `trySuccessPulse`), o con [next] como salida segura.
 * - Si el ancla tiene un scroller registrado, [revealAnchor] lo desplaza al viewport antes de esperar [anchorTimeoutMs].
 * - Si el ancla de un paso no aparece en [anchorTimeoutMs], ese paso se salta solo.
 * - Terminar llama a [onCompleted] y [onFinished]; saltar solo llama a [onFinished]. Ambos marcan visto en [seen].
 */
class TourController(
    private val scope: CoroutineScope,
    private val seen: TourSeenStore,
    private val hasAnchor: (String) -> Boolean,
    private val onFinished: () -> Unit = {},
    private val onCompleted: (String) -> Unit = {},
    private val hasScroller: (String) -> Boolean = { false },
    private val revealAnchor: (suspend (String) -> Unit)? = null,
    private val anchorTimeoutMs: Long = TOUR_ANCHOR_TIMEOUT_MS,
    private val trySuccessDelayMs: Long = TOUR_TRY_SUCCESS_DELAY_MS,
) {
    private val _state = MutableStateFlow(TourUiState())
    val state: StateFlow<TourUiState> = _state.asStateFlow()

    var reducedMotion: Boolean = false

    fun seenIds(): Set<String> = seen.seenIds()

    fun markSeen(tourId: String) = seen.markSeen(tourId)

    private var waitJob: Job? = null
    private var tryPulseJob: Job? = null

    /** Arranca [tourId] si tiene pasos, no hay otro activo y no se ha visto. Devuelve si arrancó. */
    fun start(tourId: String, steps: List<TourStep>): Boolean {
        if (steps.isEmpty() || _state.value.isActive || tourId in seen.seenIds()) return false
        _state.value = TourUiState(tourId = tourId, steps = steps, index = 0)
        prepareCurrentStep()
        return true
    }

    fun next() {
        tryPulseJob?.cancel()
        tryPulseJob = null
        advance()
    }

    fun previous() {
        val current = _state.value
        if (!current.isActive || current.index <= 0) return
        tryPulseJob?.cancel()
        tryPulseJob = null
        _state.value = current.copy(
            index = current.index - 1,
            isScrollingToAnchor = false,
            trySuccessPulse = false,
        )
        prepareCurrentStep()
    }

    fun skip() = finish(completed = false)

    /** Un toque sobre [anchorId] o `tryAnchorId` avanza cuando el paso actual es «Pruébalo». */
    fun onAnchorTapped(anchorId: String) {
        val current = _state.value
        val step = current.current ?: return
        if (step.kind != TourStepKind.TRY || current.trySuccessPulse) return
        val matches = anchorId == step.anchorId || (step.tryAnchorId != null && anchorId == step.tryAnchorId)
        if (!matches) return
        val pulseMs = if (reducedMotion) 0L else trySuccessDelayMs
        if (pulseMs <= 0L) {
            advance()
        } else {
            _state.value = current.copy(trySuccessPulse = true)
            tryPulseJob?.cancel()
            tryPulseJob = scope.launch {
                delay(pulseMs)
                advance()
            }
        }
    }

    private fun advance() {
        val current = _state.value
        if (!current.isActive) return
        val nextIndex = current.index + 1
        if (nextIndex >= current.steps.size) {
            finish(completed = true)
        } else {
            _state.value = current.copy(
                index = nextIndex,
                isScrollingToAnchor = false,
                trySuccessPulse = false,
            )
            prepareCurrentStep()
        }
    }

    private fun finish(completed: Boolean) {
        val tourId = _state.value.tourId ?: return
        waitJob?.cancel()
        waitJob = null
        tryPulseJob?.cancel()
        tryPulseJob = null
        _state.value = TourUiState()
        seen.markSeen(tourId)
        onFinished()
        if (completed) onCompleted(tourId)
    }

    /** Pre-scroll si hace falta y plazo acotado del paso actual: si el ancla sigue ausente, se salta. */
    private fun prepareCurrentStep() {
        waitJob?.cancel()
        val current = _state.value
        val step = current.current ?: return
        val needsReveal = revealAnchor != null && hasScroller(step.anchorId)
        if (needsReveal) {
            _state.value = current.copy(isScrollingToAnchor = true)
        }
        waitJob = scope.launch {
            if (needsReveal) {
                try {
                    revealAnchor?.invoke(step.anchorId)
                } finally {
                    val now = _state.value
                    if (now.tourId == current.tourId && now.index == current.index) {
                        _state.value = now.copy(isScrollingToAnchor = false)
                    }
                }
            }
            delay(anchorTimeoutMs)
            val now = _state.value
            if (now.tourId == current.tourId && now.index == current.index && !hasAnchor(step.anchorId)) {
                advance()
            }
        }
    }
}
