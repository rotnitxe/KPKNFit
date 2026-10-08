package com.example.kpkn.services.workout

/** Wall time is only a reboot fallback. Clock changes cannot extend a rest on the current boot. */
data class MonotonicRestDeadline(
    val startedWallMs: Long,
    val startedElapsedMs: Long,
    val durationMs: Long,
    val bootCount: Int,
) {
    fun remainingMs(wallNowMs: Long, elapsedNowMs: Long, currentBootCount: Int): Long {
        if (bootCount == currentBootCount && elapsedNowMs >= startedElapsedMs) {
            return (durationMs - (elapsedNowMs - startedElapsedMs)).coerceAtLeast(0)
        }
        // A clock rollback across reboot cannot prolong a timer indefinitely.
        if (wallNowMs < startedWallMs) return 0
        return (durationMs - (wallNowMs - startedWallMs)).coerceIn(0, durationMs)
    }
    val endElapsedMs: Long get() = startedElapsedMs + durationMs
}
