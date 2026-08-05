package com.example.kpkn.services.workout

import android.os.SystemClock

/**
 * Decide el ritmo de reintentos del "fénix" (auto-recuperación de la voz).
 *
 * Lógica pura y determinista para poder testear el ciclo completo sin Android:
 * `lost → retry ×N (backoff) → giveUp` o `lost → retry → recovered`.
 */
class WorkoutVoiceRecoveryPolicy(
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
) {

    /** Backoff creciente por intento (0 = inmediato). */
    val backoffMs: LongArray = longArrayOf(0L, 1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

    val maxAttempts: Int get() = backoffMs.size

    private var attempt = 0
    private var lostAtMs = 0L

    val recovering: Boolean get() = attempt > 0

    /** Máximo de tiempo total razonable hasta rendirse (para el diagnóstico/logs). */
    val totalBudgetMs: Long
        get() = backoffMs.sum() + 2_000L * maxAttempts

    sealed interface Decision {
        data class Retry(
            val attempt: Int,
            val backoffMs: Long,
        ) : Decision

        data object GiveUp : Decision
    }

    /** Señal de pérdida de sesión; arranca el ciclo de reintentos. */
    fun onLost(): Decision {
        if (attempt == 0) lostAtMs = clockMs()
        attempt = 1
        return Decision.Retry(attempt = attempt, backoffMs = backoffMs.first())
    }

    /** Un intento de reconexión no terminó en éxito; continúa el ciclo o se rinde. */
    fun onAttemptFailed(): Decision {
        if (attempt <= 0) {
            // Fallo sin `onLost` previo: tratarlo como la primera pérdida.
            lostAtMs = clockMs()
            attempt = 1
        } else if (attempt >= maxAttempts) {
            return Decision.GiveUp
        } else {
            attempt += 1
        }
        return Decision.Retry(attempt = attempt, backoffMs = backoffMs[attempt - 1])
    }

    /** Reconexión exitosa: reinicia el ciclo. */
    fun onRecovered() {
        attempt = 0
        lostAtMs = 0L
    }

    /** El usuario apagó la voz (o se abandona): reinicia sin rendirse. */
    fun onAborted() {
        attempt = 0
        lostAtMs = 0L
    }

    fun currentAttempt(): Int = attempt

    fun reset() = onAborted()
}
