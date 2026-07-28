package com.example.kpkn.services.workout

class WorkoutVoiceFallbackPolicy(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val fallbackTimestampsMs = ArrayDeque<Long>()
    private var consecutiveNativeFailures = 0
    private var circuitOpenUntilMs = 0L
    private val seenUtteranceIds = mutableSetOf<String>()

    fun canAttempt(utteranceId: String): FallbackGateResult {
        val now = clockMs()
        prune(now)
        if (utteranceId in seenUtteranceIds) {
            return FallbackGateResult.Blocked("Ya hubo fallback para esta utterance")
        }
        if (now < circuitOpenUntilMs) {
            return FallbackGateResult.Blocked("Fallback pausado temporalmente")
        }
        val lastAttempt = fallbackTimestampsMs.lastOrNull()
        if (lastAttempt != null && now - lastAttempt < MIN_GAP_MS) {
            return FallbackGateResult.Blocked("Fallback limitado por ventana corta")
        }
        if (fallbackTimestampsMs.size >= MAX_ATTEMPTS_PER_WINDOW) {
            return FallbackGateResult.Blocked("Fallback pausado por límite de ventana")
        }
        return FallbackGateResult.Allowed
    }

    fun recordAttempt(utteranceId: String) {
        val now = clockMs()
        prune(now)
        seenUtteranceIds += utteranceId
        fallbackTimestampsMs.addLast(now)
    }

    fun recordSuccess() {
        consecutiveNativeFailures = 0
    }

    fun recordFailure() {
        consecutiveNativeFailures += 1
        if (consecutiveNativeFailures >= CONSECUTIVE_FAILURES_FOR_CIRCUIT) {
            circuitOpenUntilMs = clockMs() + CIRCUIT_OPEN_MS
            consecutiveNativeFailures = 0
        }
    }

    val isCircuitOpen: Boolean
        get() = clockMs() < circuitOpenUntilMs

    private fun prune(now: Long) {
        while (fallbackTimestampsMs.isNotEmpty() && now - fallbackTimestampsMs.first() > WINDOW_MS) {
            fallbackTimestampsMs.removeFirst()
        }
    }

    companion object {
        const val MIN_GAP_MS = 2 * 60 * 1000L
        const val WINDOW_MS = 10 * 60 * 1000L
        const val MAX_ATTEMPTS_PER_WINDOW = 2
        const val CONSECUTIVE_FAILURES_FOR_CIRCUIT = 2
        const val CIRCUIT_OPEN_MS = 5 * 60 * 1000L
    }
}

sealed interface FallbackGateResult {
    data object Allowed : FallbackGateResult
    data class Blocked(val reason: String) : FallbackGateResult
}
