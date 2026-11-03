package com.example.kpkn.domain.workout

import kotlin.math.max
import kotlin.math.min

/*
 * Reloj libre de la hoja «Tiempo» (Pulido v1, punto 16): cronómetro y temporizador independientes de la sesión y del descanso.
 * Todo es puro (sin Android): el tiempo se calcula SIEMPRE con la hora de inicio / fin que se le pasa (`nowMs`), nunca con un contador,
 * así sobrevive a la pantalla apagada, al giro y a cerrar la hoja. Contrato en `maps/vivo-resto.md` (escenas `lx-t-*`).
 */

/** Cronómetro: `startedAtMs` solo vale mientras corre; `accumulatedMs` es lo ya contado; `lapTotalsMs` son los totales de cada vuelta. */
data class StopwatchState(
    val running: Boolean = false,
    val startedAtMs: Long = 0L,
    val accumulatedMs: Long = 0L,
    val lapTotalsMs: List<Long> = emptyList(),
) {
    /** Sin nada que reiniciar: ni corre ni ha contado. */
    val idle: Boolean get() = !running && accumulatedMs == 0L
}

enum class FreeTimerPhase { CONFIG, RUNNING, PAUSED, FINISHED }

/**
 * Temporizador. En `CONFIG` manda `digits` (teclado de 4 cifras mm:ss); en `RUNNING` manda `endAtMs`; en `PAUSED` manda `remainingMs`;
 * en `FINISHED` el anillo está completo y el total se guarda para «Repetir».
 */
data class FreeTimerState(
    val phase: FreeTimerPhase = FreeTimerPhase.CONFIG,
    val digits: String = "",
    val totalMs: Long = 0L,
    val endAtMs: Long = 0L,
    val remainingMs: Long = 0L,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
)

enum class LapMark { NONE, FASTEST, SLOWEST }

/** Una fila de la tabla de vueltas. `live` es la vuelta en curso (la primera de la lista). */
data class LapRow(val number: Int, val partialMs: Long, val totalMs: Long, val mark: LapMark, val live: Boolean)

object FreeClock {
    // ------------------------------------------------------------------ cronómetro

    fun stopwatchElapsedMs(s: StopwatchState, nowMs: Long): Long =
        s.accumulatedMs + if (s.running) max(0L, nowMs - s.startedAtMs) else 0L

    /** Iniciar / Pausar / Seguir. */
    fun stopwatchToggle(s: StopwatchState, nowMs: Long): StopwatchState =
        if (s.running) s.copy(running = false, startedAtMs = 0L, accumulatedMs = stopwatchElapsedMs(s, nowMs))
        else s.copy(running = true, startedAtMs = nowMs)

    /** Corriendo: marca una vuelta. Parado: «Reiniciar» (a cero, sin vueltas; sin confirmar porque es desechable). */
    fun stopwatchLapOrReset(s: StopwatchState, nowMs: Long): StopwatchState =
        if (s.running) {
            val total = stopwatchElapsedMs(s, nowMs)
            if (total <= (s.lapTotalsMs.lastOrNull() ?: 0L)) s else s.copy(lapTotalsMs = s.lapTotalsMs + total)
        } else StopwatchState()

    /**
     * Filas de la tabla, la vuelta en curso primero y después las cerradas de la última a la primera. La más rápida y la más lenta
     * solo se marcan con dos o más vueltas cerradas (y si no empatan).
     */
    fun lapRows(s: StopwatchState, nowMs: Long): List<LapRow> {
        val totals = s.lapTotalsMs
        val partials = totals.mapIndexed { i, t -> t - (if (i == 0) 0L else totals[i - 1]) }
        val best = if (partials.size > 1) partials.min() else -1L
        val worst = if (partials.size > 1) partials.max() else -1L
        val closed = partials.mapIndexed { i, p ->
            LapRow(i + 1, p, totals[i], when {
                best == worst -> LapMark.NONE
                p == best -> LapMark.FASTEST
                p == worst -> LapMark.SLOWEST
                else -> LapMark.NONE
            }, live = false)
        }.reversed()
        val elapsed = stopwatchElapsedMs(s, nowMs)
        val live = if (s.running || s.accumulatedMs > 0L) {
            listOf(LapRow(totals.size + 1, elapsed - (totals.lastOrNull() ?: 0L), elapsed, LapMark.NONE, live = true))
        } else emptyList()
        return live + closed
    }

    // ------------------------------------------------------------------ temporizador

    /** Teclado: añade una cifra (o «00») o borra la última («del»). Máximo 4 cifras significativas, sin ceros a la izquierda. */
    fun timerKey(digits: String, key: String): String {
        if (key == "del") return digits.dropLast(1)
        val next = (digits + key).trimStart('0')
        return if (next.length <= 4) next else digits
    }

    fun timerDigitsSeconds(digits: String): Int {
        val d = digits.padStart(4, '0')
        return d.substring(0, 2).toInt() * 60 + d.substring(2).toInt()
    }

    /** `mm:ss` del teclado, siempre con cuatro cifras («01:30»). */
    fun timerDigitsDisplay(digits: String): String {
        val d = digits.padStart(4, '0')
        return d.substring(0, 2) + ":" + d.substring(2)
    }

    /** Un atajo (segundos) rellena el teclado como si se hubiera tecleado: 90 s → «130». */
    fun timerDigitsForSeconds(seconds: Int): String = (seconds / 60 * 100 + seconds % 60).toString().trimStart('0')

    fun timerRemainingMs(s: FreeTimerState, nowMs: Long): Long = when (s.phase) {
        FreeTimerPhase.RUNNING -> max(0L, s.endAtMs - nowMs)
        FreeTimerPhase.PAUSED -> s.remainingMs
        FreeTimerPhase.FINISHED -> 0L
        FreeTimerPhase.CONFIG -> 0L
    }

    fun timerStart(s: FreeTimerState, nowMs: Long, totalSeconds: Int = timerDigitsSeconds(s.digits)): FreeTimerState {
        if (totalSeconds <= 0) return s
        val total = totalSeconds * 1000L
        return s.copy(phase = FreeTimerPhase.RUNNING, totalMs = total, endAtMs = nowMs + total, remainingMs = total)
    }

    fun timerPauseOrResume(s: FreeTimerState, nowMs: Long): FreeTimerState = when (s.phase) {
        FreeTimerPhase.RUNNING -> s.copy(phase = FreeTimerPhase.PAUSED, remainingMs = timerRemainingMs(s, nowMs), endAtMs = 0L)
        FreeTimerPhase.PAUSED -> s.copy(phase = FreeTimerPhase.RUNNING, endAtMs = nowMs + s.remainingMs)
        else -> s
    }

    /** +15 s / +30 s / +1 min: mueve el final y también el total (el anillo conserva la proporción). */
    fun timerAdd(s: FreeTimerState, seconds: Int): FreeTimerState {
        val extra = seconds * 1000L
        return when (s.phase) {
            FreeTimerPhase.RUNNING -> s.copy(endAtMs = s.endAtMs + extra, totalMs = s.totalMs + extra)
            FreeTimerPhase.PAUSED -> s.copy(remainingMs = s.remainingMs + extra, totalMs = s.totalMs + extra)
            else -> s
        }
    }

    /** Cancelar / Cerrar: vuelve a configurar; el teclado conserva lo último tecleado. */
    fun timerBackToConfig(s: FreeTimerState): FreeTimerState =
        s.copy(phase = FreeTimerPhase.CONFIG, endAtMs = 0L, remainingMs = 0L, totalMs = 0L)

    /** Pasa a `FINISHED` si el tiempo se ha cumplido. Idempotente: una segunda llamada devuelve el mismo estado. */
    fun timerFinishIfDue(s: FreeTimerState, nowMs: Long): FreeTimerState =
        if (s.phase == FreeTimerPhase.RUNNING && s.endAtMs <= nowMs) s.copy(phase = FreeTimerPhase.FINISHED, remainingMs = 0L) else s

    /** Fracción del anillo que queda (1 = lleno al empezar). */
    fun timerFraction(s: FreeTimerState, nowMs: Long): Float = when (s.phase) {
        FreeTimerPhase.FINISHED -> 1f
        FreeTimerPhase.CONFIG -> 0f
        else -> (timerRemainingMs(s, nowMs).toFloat() / max(1L, s.totalMs).toFloat()).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------------ formato y esfera

    /** `m:ss` o `h:mm:ss` (sin ceros a la izquierda en el primer grupo). */
    fun formatClock(seconds: Long): String {
        val s = max(0L, seconds)
        val h = s / 3600
        val m = s % 3600 / 60
        return if (h > 0) "$h:${pad(m)}:${pad(s % 60)}" else "$m:${pad(s % 60)}"
    }

    private fun pad(n: Long) = n.toString().padStart(2, '0')

    private data class Parts(val minutes: Long, val seconds: Long, val tenths: Long)

    private fun parts(ms: Long): Parts {
        val t = max(0L, ms) / 100
        val s = t / 10
        return Parts(s / 60, s % 60, t % 10)
    }

    /** Cifra grande del cronómetro, sin las décimas: «02:17». */
    fun formatStopwatchMain(ms: Long): String = parts(ms).let { pad(it.minutes) + ":" + pad(it.seconds) }

    /** Décimas del cronómetro con su coma: «,9». */
    fun formatStopwatchTenths(ms: Long): String = "," + parts(ms).tenths

    /** Tiempo de vuelta o pastilla: «0:44,6». */
    fun formatLap(ms: Long): String = parts(ms).let { "${it.minutes}:${pad(it.seconds)},${it.tenths}" }

    /** Ángulo (grados desde las 12) del segundero: una vuelta por minuto. */
    fun secondHandDegrees(ms: Long): Float = (max(0L, ms) % 60_000L) / 60_000f * 360f

    /** Ángulo de la subesfera: una vuelta cada `subMaxMinutes` minutos. */
    fun subHandDegrees(ms: Long, subMaxMinutes: Int): Float {
        val m = max(0L, ms) / 60_000.0 % subMaxMinutes
        return (m / subMaxMinutes * 360.0).toFloat()
    }

    /** Arco exterior de la esfera: con objetivo, el avance hacia él; sin objetivo, la vuelta del minuto. */
    fun dialArcFraction(ms: Long, goalMinutes: Int): Float =
        if (goalMinutes > 0) min(1f, max(0L, ms).toFloat() / (goalMinutes * 60_000f)) else (max(0L, ms) % 60_000L) / 60_000f
}
