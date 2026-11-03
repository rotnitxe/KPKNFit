package com.example.kpkn.domain.workout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado completo del reloj libre: cronómetro, temporizador y el aviso «Listo» que espera a que se toque. */
data class FreeClockState(
    val stopwatch: StopwatchState = StopwatchState(),
    val timer: FreeTimerState = FreeTimerState(),
    /** El temporizador terminó y nadie ha tocado «Listo» (la franja de la cabecera es persistente). */
    val bannerPending: Boolean = false,
)

/** Lo único de Android que necesita el reloj libre: guardar, programar la alarma del fin y avisar. Lo implementa `services/workout`. */
interface FreeClockPlatform {
    fun load(): FreeClockState
    fun save(state: FreeClockState)

    /** Alarma exacta (despierta el teléfono) para el fin del temporizador; al sonar llama a [FreeClockController.onAlarm]. */
    fun scheduleTimerEnd(endAtMs: Long)
    fun cancelTimerEnd()

    /** Sonido y/o vibración del fin; si el descanso termina a la vez, la implementación deja sonar primero al descanso. */
    fun playEndCue(sound: Boolean, vibrate: Boolean)
}

/**
 * Dueño del reloj libre (punto 16): independiente de la sesión y del descanso. No tiene contador propio: todo se calcula con la hora
 * de inicio / fin que guarda [FreeClock], así que da igual que la pantalla se apague, que se gire o que se cierre la hoja. El fin del
 * temporizador lo dispara una alarma del sistema ([onAlarm]) o, si la interfaz lo ve antes, [finishIfDue]; ambas son idempotentes.
 */
class FreeClockController(
    private val platform: FreeClockPlatform,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(platform.load())
    val state: StateFlow<FreeClockState> = _state.asStateFlow()

    init {
        // Tras matar el proceso: si el fin ya pasó, queda «terminado» con el aviso pendiente; solo suena si fue hace nada.
        val now = clock()
        val t = _state.value.timer
        if (t.phase == FreeTimerPhase.RUNNING) {
            if (t.endAtMs <= now) finishIfDue(playCue = now - t.endAtMs < LATE_CUE_WINDOW_MS) else platform.scheduleTimerEnd(t.endAtMs)
        }
    }

    private fun update(change: (FreeClockState) -> FreeClockState) {
        _state.value = change(_state.value)
        platform.save(_state.value)
    }

    // ------------------------------------------------------------------ cronómetro

    fun stopwatchToggle() = update { it.copy(stopwatch = FreeClock.stopwatchToggle(it.stopwatch, clock())) }

    fun stopwatchLapOrReset() = update { it.copy(stopwatch = FreeClock.stopwatchLapOrReset(it.stopwatch, clock())) }

    // ------------------------------------------------------------------ temporizador

    fun timerKey(key: String) = update { it.copy(timer = it.timer.copy(digits = FreeClock.timerKey(it.timer.digits, key))) }

    fun timerPreset(seconds: Int) = update { it.copy(timer = it.timer.copy(digits = FreeClock.timerDigitsForSeconds(seconds))) }

    fun timerSetSound(on: Boolean) = update { it.copy(timer = it.timer.copy(sound = on)) }

    fun timerSetVibrate(on: Boolean) = update { it.copy(timer = it.timer.copy(vibrate = on)) }

    fun timerStart() {
        val started = FreeClock.timerStart(_state.value.timer, clock())
        if (started.phase != FreeTimerPhase.RUNNING) return
        update { it.copy(timer = started, bannerPending = false) }
        platform.scheduleTimerEnd(started.endAtMs)
    }

    fun timerPauseOrResume() {
        update { it.copy(timer = FreeClock.timerPauseOrResume(it.timer, clock())) }
        val t = _state.value.timer
        if (t.phase == FreeTimerPhase.RUNNING) platform.scheduleTimerEnd(t.endAtMs) else platform.cancelTimerEnd()
    }

    fun timerAdd(seconds: Int) {
        update { it.copy(timer = FreeClock.timerAdd(it.timer, seconds)) }
        val t = _state.value.timer
        if (t.phase == FreeTimerPhase.RUNNING) platform.scheduleTimerEnd(t.endAtMs)
    }

    /** Cancelar (corriendo o en pausa) y Cerrar (terminado): vuelve al teclado. */
    fun timerBackToConfig() {
        platform.cancelTimerEnd()
        update { it.copy(timer = FreeClock.timerBackToConfig(it.timer), bannerPending = false) }
    }

    /** «Repetir 1:30» tras el fin. */
    fun timerRepeat() {
        val t = _state.value.timer
        val started = FreeClock.timerStart(t, clock(), (t.totalMs / 1000).toInt())
        if (started.phase != FreeTimerPhase.RUNNING) return
        update { it.copy(timer = started, bannerPending = false) }
        platform.scheduleTimerEnd(started.endAtMs)
    }

    /** «Listo» de la franja de la cabecera: se da por enterado; la hoja sigue mostrando «Se acabó el tiempo» hasta Cerrar o Repetir. */
    fun dismissBanner() = update { it.copy(bannerPending = false) }

    /** Llamada de la alarma del sistema (receptor) cuando llega el fin. */
    fun onAlarm() { finishIfDue() }

    /** Pasa a terminado si toca; devuelve si lo hizo en esta llamada. Solo la primera llamada avisa. */
    fun finishIfDue(playCue: Boolean = true): Boolean {
        while (true) {
            val cur = _state.value
            val next = FreeClock.timerFinishIfDue(cur.timer, clock())
            if (next == cur.timer) return false
            val updated = cur.copy(timer = next, bannerPending = true)
            if (_state.compareAndSet(cur, updated)) {
                platform.save(updated)
                if (playCue) platform.playEndCue(next.sound, next.vibrate)
                return true
            }
        }
    }

    companion object {
        /** Si la app despierta mucho después del fin, el aviso sonoro ya no tiene sentido. */
        const val LATE_CUE_WINDOW_MS = 5_000L
    }
}
