package com.example.kpkn.screens.workout

/**
 * El descanso se pasó de tiempo (LV6, F6). Al llegar a 0 de forma natural el descanso se cierra solo y la sesión avanza (eso NO
 * cambia: `handleNaturalFinish`, alarmas, servicio y voz siguen igual). Esto es solo la capa de interfaz que se queda encima: la
 * pantalla del descanso (completa o tarjeta, la que estuviera) pasa a «Descanso cumplido» con el tiempo extra contando hacia arriba
 * desde el instante en que acabó. No se guarda: si el proceso muere, no vuelve.
 */
data class RestOverrun(
    /** Cuándo acabó el descanso (su `endsAtMs`): de ahí cuenta el tiempo extra. */
    val endedAtMs: Long,
    /** Lo que duró el descanso entero («sobre 4:09»). */
    val totalSeconds: Int,
    /** Tipo del descanso que acabó, para que «Necesito +30 s» arranque uno igual. */
    val kind: RestTimerKind = RestTimerKind.STANDARD,
    /** Dónde estaba el cursor al acabar: si cambia (otro ejercicio, otra serie) la capa se va. */
    val exerciseIdx: Int = 0,
    val setIdx: Int = 0,
    val activeStepKey: String? = null,
    /** Última serie registrada y cuántas había: si cambia alguna, se registró algo y la capa se va. */
    val setJustLoggedKey: String? = null,
    val completedCount: Int = 0,
    /** Stable visual event identity, including sub-second clock precision. Zero in old state. */
    val startedAtMs: Long = 0L,
)

/**
 * Cuándo nace y cuándo muere el [RestOverrun]. Puro: lo decide el estado, no la pantalla, así la pantalla completa y la tarjeta
 * hablan de lo mismo.
 */
internal object RestOverrunPolicy {
    /** La capa se va sola a los 5 minutos. */
    const val EXPIRES_AFTER_MS = 5L * 60L * 1000L

    /** «Necesito +30 s»: segundos del descanso nuevo. */
    const val EXTEND_SECONDS = 30

    /**
     * La capa que toca al acabar de forma natural el descanso de [state] (que aún no se ha cerrado), o `null` si lo que sigue no es
     * una serie normal: calentamiento (va dentro de su tarjeta), saltar el resto del ejercicio, feedback del ejercicio, cierre de la
     * sesión o una sesión que ya terminó.
     */
    fun captureOrNull(state: WorkoutUiState, nowMs: Long): RestOverrun? {
        val rest = state.restModalState ?: return null
        if (rest.kind == RestTimerKind.WARMUP) return null
        if (rest.skipCurrentExerciseOnFinish) return null
        if (state.pendingPostExerciseIdx != -1 || state.showPostExerciseSheet || state.showFinishSheet) return null
        if (state.isComplete || state.isFinishingWorkout || state.wasCancelled || state.isCancellingWorkout) return null
        return RestOverrun(
            endedAtMs = rest.endsAtMs.takeIf { it > 0L } ?: nowMs,
            totalSeconds = rest.totalRestSeconds(),
            startedAtMs = rest.restEventStartedAtMs(fallbackEndAtMs = nowMs),
            kind = rest.kind,
            exerciseIdx = state.currentExerciseIdx,
            setIdx = state.currentSetIdx,
            activeStepKey = state.activeStepKey,
            setJustLoggedKey = state.setJustLoggedKey,
            completedCount = state.completedSets.size,
        )
    }

    /** Segundos de tiempo extra: lo que pasó desde que acabó el descanso. Nunca negativo. */
    fun extraSeconds(overrun: RestOverrun, nowMs: Long): Int =
        ((nowMs - overrun.endedAtMs) / 1000L).toInt().coerceAtLeast(0)

    /** Milisegundos que faltan para que la capa caduque (0 si ya caducó). */
    fun remainingLifeMs(overrun: RestOverrun, nowMs: Long): Long =
        (overrun.endedAtMs + EXPIRES_AFTER_MS - nowMs).coerceAtLeast(0L)

    /**
     * Si la capa debe irse: pasaron 5 minutos, arrancó un descanso nuevo, la persona registró o tocó otra serie, cambió de
     * ejercicio o la sesión se encaminó al cierre.
     */
    fun shouldDismiss(overrun: RestOverrun, state: WorkoutUiState, nowMs: Long): Boolean = when {
        nowMs - overrun.endedAtMs >= EXPIRES_AFTER_MS -> true
        state.isRestTimerRunning -> true
        state.currentExerciseIdx != overrun.exerciseIdx -> true
        state.currentSetIdx != overrun.setIdx -> true
        state.activeStepKey != overrun.activeStepKey -> true
        state.setJustLoggedKey != overrun.setJustLoggedKey -> true
        state.completedSets.size != overrun.completedCount -> true
        state.showPostExerciseSheet || state.showFinishSheet || state.isComplete -> true
        else -> false
    }

    /** Texto del tiempo extra: «+0:24». */
    fun extraLabel(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return "+${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
    }
}

/** Infer legacy identity once, before activeSeconds becomes remainingSeconds after an adjustment. */
internal fun WorkoutRestModalState.restEventStartedAtMs(fallbackEndAtMs: Long = 0L): Long {
    if (startedAtMs > 0L) return startedAtMs
    val end = endsAtMs.takeIf { it > 0L } ?: fallbackEndAtMs
    if (end <= 0L || activeSeconds <= 0) return 0L
    return (end - activeSeconds * 1000L).coerceAtLeast(0L)
}

internal fun WorkoutRestModalState.withRestEventIdentity(): WorkoutRestModalState =
    if (startedAtMs > 0L) this else copy(startedAtMs = restEventStartedAtMs())
