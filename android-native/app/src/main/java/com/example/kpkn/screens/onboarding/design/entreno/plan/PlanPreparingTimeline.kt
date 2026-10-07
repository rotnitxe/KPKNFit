package com.example.kpkn.screens.onboarding.design.entreno.plan

import com.example.kpkn.screens.onboarding.design.seg

/*
 * El guion en el tiempo de la animación «estamos preparando tu programa», como funciones puras de `t` (segundos desde
 * que se abre el overlay). La pantalla solo pinta lo que dice este guion, así se puede probar sin Compose:
 *
 *  - la Torre de la marca se arma (los tres discos caen) en el primer segundo,
 *  - la guía luminosa recorre las cinco etapas, una cada [PER_STAGE] segundos,
 *  - la animación dura al menos [MIN_SECONDS] aunque el motor ya tenga el resultado,
 *  - mientras el resultado no esté listo la guía sigue recorriendo el riel en bucle (nunca parece terminada),
 *  - al estar todo listo hay un destello de [FLASH_SECONDS] y se avisa.
 */
internal object PreparingTimeline {
    /** Etapas de la fila (las dos variantes tienen cinco). */
    const val STAGE_COUNT = 5

    /** Segundos antes de que la guía salga de la primera etapa. */
    const val LEAD = 0.55f

    /** Segundos que tarda la guía en pasar de una etapa a la siguiente. */
    const val PER_STAGE = 0.42f

    /** Duración mínima de la animación, con el resultado listo o no. */
    const val MIN_SECONDS = 2.7f

    /** Duración del destello final y del desvanecido que lo sigue. */
    const val FLASH_SECONDS = 0.55f
    const val FADE_SECONDS = 0.2f

    /** Segundos que tarda la guía en cruzar el riel mientras se espera el resultado. */
    const val WAIT_LOOP = 1.6f

    /** Movimiento reducido: el instante del estado final (todo encendido, sin movimiento). */
    const val SETTLED = 60f

    /** Segundos de espera con el estado final a la vista antes de avisar, con movimiento reducido. */
    const val REDUCED_HOLD_MS = 600L

    /** Instante en que la guía llega a la etapa [i] (0 = la primera). */
    fun reach(i: Int): Float = LEAD + i * PER_STAGE

    /** Instante en que se enciende la última etapa. */
    val allLitAt: Float get() = reach(STAGE_COUNT - 1)

    /** Cuántas etapas están encendidas en [t] (0 a [STAGE_COUNT]). */
    fun litCount(t: Float, stages: Int = STAGE_COUNT): Int {
        var n = 0
        for (i in 0 until stages) if (t >= LEAD + i * PER_STAGE) n++
        return n
    }

    /** Posición de la guía del recorrido inicial, en unidades de etapa (0 = sobre la primera, `stages − 1` = sobre la última). */
    fun guide(t: Float, stages: Int = STAGE_COUNT): Float =
        ((t - LEAD) / PER_STAGE).coerceIn(0f, (stages - 1).toFloat())

    /**
     * Posición del destello que cruza el riel mientras se espera el resultado, en unidades de etapa, o `−1` si todavía
     * no toca (el recorrido inicial no ha acabado).
     */
    fun waitGuide(t: Float, stages: Int = STAGE_COUNT): Float {
        val start = LEAD + (stages - 1) * PER_STAGE + 0.35f
        if (t < start) return -1f
        val u = ((t - start) / WAIT_LOOP) % 1f
        return u * (stages - 1)
    }

    /** ¿Puede terminar ya? El motor tiene el resultado y la animación mínima se cumplió. */
    fun canFinish(t: Float, ready: Boolean): Boolean = ready && t >= MIN_SECONDS

    /** Avance del destello final (0 a 1) si empezó en [finishAt]; 0 mientras no haya empezado (`NaN`). */
    fun flash(t: Float, finishAt: Float): Float =
        if (finishAt.isNaN()) 0f else seg(t, finishAt, finishAt + FLASH_SECONDS)
}
