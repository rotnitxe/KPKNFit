package com.example.kpkn.services.workout

/**
 * Process-wide ordering between start and stop requests of [WorkoutRestForegroundService].
 *
 * Android contract: every `Context.startForegroundService()` must be followed, within a few seconds, by
 * `Service.startForeground()` from `onStartCommand`. If the service is brought down with
 * `Context.stopService()` after the start was requested but before `onStartCommand` ran, the system logs
 * "Bringing down service while still waiting for start foreground" and kills the process with
 * `ForegroundServiceDidNotStartInTimeException`. A user can trigger it by cancelling right after a rest
 * timer starts.
 *
 * The gate therefore
 *  - counts the start intents that were dispatched but not yet delivered (pending),
 *  - defers any stop requested while at least one start is pending (no `stopService()` is issued), and
 *  - tells the service, once the last pending start went through `startForeground()`, that it must stop
 *    itself (`stopForeground` + `stopSelf(startId)`).
 *
 * A start requested after a deferred stop re-arms the service: the latest request wins.
 * Pending entries older than [pendingStartTtlMs] are ignored, so a start intent that is never delivered
 * can delay a stop by at most that long instead of blocking it forever.
 *
 * Pure Kotlin (no Android types): the ordering rules are covered by plain JVM tests. Thread-safe; the
 * Android calls handed to [dispatchStart] and [dispatchStop] run under the gate lock so a start and a stop
 * issued from different threads cannot interleave between the decision and the system call.
 */
internal class WorkoutRestForegroundStartGate(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val pendingStartTtlMs: Long = DEFAULT_PENDING_START_TTL_MS,
) {
    private val lock = Any()
    private val pendingDispatchTimesMs = ArrayList<Long>()
    private var stopDeferred = false

    /**
     * Runs [startCall] (the `startForegroundService()` / `startService()` call) and records the start as
     * pending. If [startCall] throws, the start is rolled back and the exception is rethrown unchanged,
     * restoring a previously deferred stop.
     */
    fun dispatchStart(startCall: () -> Unit) {
        synchronized(lock) {
            val nowMs = clockMs()
            pruneExpiredLocked(nowMs)
            val stopWasDeferred = stopDeferred
            pendingDispatchTimesMs.add(nowMs)
            stopDeferred = false
            try {
                startCall()
            } catch (error: Throwable) {
                pendingDispatchTimesMs.removeAt(pendingDispatchTimesMs.lastIndex)
                stopDeferred = stopWasDeferred
                throw error
            }
        }
    }

    /**
     * Runs [stopCall] (the `stopService()` call) immediately when no start is pending and returns true.
     * With a start pending, [stopCall] is NOT run: the stop is deferred to [onStartDelivered] and false is
     * returned.
     */
    fun dispatchStop(stopCall: () -> Unit): Boolean = synchronized(lock) {
        pruneExpiredLocked(clockMs())
        if (pendingDispatchTimesMs.isNotEmpty()) {
            stopDeferred = true
            false
        } else {
            stopDeferred = false
            stopCall()
            true
        }
    }

    /**
     * Called by the service from `onStartCommand`, after `startForeground()` returned, once per delivered
     * start intent. Returns true when a deferred stop is due: this was the last pending start and a stop
     * was requested meanwhile, so the service must now stop itself.
     */
    fun onStartDelivered(): Boolean = synchronized(lock) {
        if (pendingDispatchTimesMs.isNotEmpty()) {
            pendingDispatchTimesMs.removeAt(0)
        }
        pruneExpiredLocked(clockMs())
        if (pendingDispatchTimesMs.isEmpty() && stopDeferred) {
            stopDeferred = false
            true
        } else {
            false
        }
    }

    /** Number of start intents dispatched and not yet delivered (expired entries excluded). */
    fun pendingStartCount(): Int = synchronized(lock) {
        pruneExpiredLocked(clockMs())
        pendingDispatchTimesMs.size
    }

    /** True while a stop requested during a pending start is waiting for the service to run it. */
    fun isStopDeferred(): Boolean = synchronized(lock) { stopDeferred }

    /** Forgets all state; tests only (the gate is process-wide and survives between test cases). */
    fun resetForTests() {
        synchronized(lock) {
            pendingDispatchTimesMs.clear()
            stopDeferred = false
        }
    }

    private fun pruneExpiredLocked(nowMs: Long) {
        pendingDispatchTimesMs.removeAll { nowMs - it > pendingStartTtlMs }
    }

    companion object {
        /**
         * A startForegroundService() the system has not delivered after this long has already timed out in
         * the system (about 10 s), so it no longer needs protecting from a stop.
         */
        const val DEFAULT_PENDING_START_TTL_MS = 15_000L
    }
}
