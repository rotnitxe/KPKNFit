package com.example.kpkn.services.workout

/**
 * Keeps continuous listening passive: one unresolved Vosk utterance is ignored.
 * A native fallback is considered only after a second unresolved utterance in a
 * short window; any understood command resets the sequence.
 */
class WorkoutVoiceFallbackTriggerPolicy(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private var firstUnresolvedAtMs: Long? = null

    fun shouldRequestFallback(): Boolean {
        val now = clockMs()
        val first = firstUnresolvedAtMs
        if (first == null || now - first > SECOND_ATTEMPT_WINDOW_MS) {
            firstUnresolvedAtMs = now
            return false
        }
        firstUnresolvedAtMs = null
        return true
    }

    fun recordResolved() {
        firstUnresolvedAtMs = null
    }

    companion object {
        const val SECOND_ATTEMPT_WINDOW_MS = 15_000L
    }
}
