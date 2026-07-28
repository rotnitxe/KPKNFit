package com.example.kpkn.services.workout

import kotlinx.coroutines.CompletableDeferred

/**
 * Pedido de TTS desde el engine (p. ej. antes del fallback).
 * El controller debe completar [signal] al terminar la utterance — no con un delay fijo.
 */
data class PromptSpeakRequest(
    val text: String,
    val signal: CompletableDeferred<Unit> = CompletableDeferred(),
) {
    fun complete() {
        signal.complete(Unit)
    }

    fun completeExceptionally(error: Throwable) {
        signal.completeExceptionally(error)
    }
}
