package com.example.kpkn.services.workout

/**
 * Ensures utterance-complete callbacks (and their watchdogs) fire [onComplete] at most once.
 */
internal object WorkoutVoiceUtteranceGuard {
    const val TIMEOUT_MS: Long = 8_000L

    fun createCompletionGate(onComplete: () -> Unit): () -> Unit {
        var completed = false
        return {
            if (!completed) {
                completed = true
                onComplete()
            }
        }
    }
}
