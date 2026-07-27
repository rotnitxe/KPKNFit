package com.example.kpkn.services.workout

/**
 * Priority gate for session TTS: higher priority interrupts lower;
 * equal/lower while speaking is dropped (avoids stacking ambient cues).
 */
enum class WorkoutSpeechPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
}

/**
 * Coordinates overlapping speak requests on the shared workout TTS path.
 * Thread-safety: call from the voice controller's main/session scope only.
 */
class WorkoutSpeechBus {
    private var speakingPriority: WorkoutSpeechPriority? = null

    /**
     * @param onInterrupt invoked when a higher-priority request preempts the current one
     * @return true if [speak] should run now
     */
    fun tryAcquire(
        priority: WorkoutSpeechPriority,
        onInterrupt: () -> Unit,
    ): Boolean {
        val current = speakingPriority
        if (current != null && priority.ordinal < current.ordinal) {
            return false
        }
        if (current != null && priority.ordinal > current.ordinal) {
            onInterrupt()
        }
        speakingPriority = priority
        return true
    }

    fun release(priority: WorkoutSpeechPriority) {
        if (speakingPriority == priority) {
            speakingPriority = null
        }
    }

    fun clear() {
        speakingPriority = null
    }

    fun current(): WorkoutSpeechPriority? = speakingPriority
}
