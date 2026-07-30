package com.example.kpkn.services.workout

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ensures utterance-complete callbacks (and their watchdogs) fire [onComplete] at most once.
 */
internal object WorkoutVoiceUtteranceGuard {
    const val TIMEOUT_MS: Long = 8_000L

    fun createCompletionGate(onComplete: () -> Unit): () -> Unit {
        val completed = AtomicBoolean(false)
        return {
            if (completed.compareAndSet(false, true)) {
                onComplete()
            }
        }
    }
}
