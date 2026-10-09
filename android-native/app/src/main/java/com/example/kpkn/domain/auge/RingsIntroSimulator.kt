package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.InitialRecoveryDayLoad
import com.example.kpkn.data.models.InitialRecoverySensations
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Canales de la demostración de RINGS, de abajo arriba como la torre (Columna, Músculos, Energía). Es un enum propio del
 * dominio: la UI tiene el suyo y este archivo no lo importa.
 */
enum class RingChannelId { COLUMNA, MUSCULOS, ENERGIA }

/**
 * Estado de la demostración del paso «Tus RINGS» (E3c). Vive en memoria: nunca entra en el borrador.
 *
 * - [heavySession]: la sesión pesada de referencia está simulada.
 * - [hoursAfter]: horas desde que terminó esa sesión (0–72).
 * - [manual]: ajuste de la persona por canal (0–100). [manualHours] guarda la hora de la línea de tiempo en que lo hizo;
 *   sin hora cuenta como 0 h.
 */
data class RingsIntroState(
    val heavySession: Boolean = false,
    val hoursAfter: Float = 0f,
    val manual: Map<RingChannelId, Int> = emptyMap(),
    val manualHours: Map<RingChannelId, Float> = emptyMap(),
)

/** Valores 0–100 de la torre en una hora dada. */
data class RingsIntroValues(val columna: Int, val musculos: Int, val energia: Int) {
    operator fun get(channel: RingChannelId): Int = when (channel) {
        RingChannelId.COLUMNA -> columna
        RingChannelId.MUSCULOS -> musculos
        RingChannelId.ENERGIA -> energia
    }
}

/**
 * Demostración de RINGS: cómo baja un entreno pesado, cómo se recupera cada anillo y qué pasa cuando la persona ajusta
 * un valor. Puro y determinista: sin reloj ni Android.
 *
 * La ley es la de la evidencia inicial v2 de E3a ([InitialRecoveryV2Model]), canal a canal:
 * - Sesión: `B(t) = 100·exp(−s·d(t)/C)`, con C = 90 (músculo), 75 (energía) y 70 (columna). Se llama a las baterías de E3a
 *   con un único día de carga y sin sensaciones; el músculo, a `pillarBattery` sobre un pilar de τ 48 h (la global).
 * - Ajuste hecho a la hora `h`: `B(t) = 100·(B₀/100)^d(t − h)` desde `h`, con la curva de cada canal de `anchored` (E3a).
 *   Antes de `h` vale la sesión: el valor salta al ajuste en `h` y la curva sigue desde ahí.
 *
 * El motor agrega 13 pilares; aquí cada canal usa su curva de canal, así que es una demostración, no una cifra de la torre.
 */
object RingsIntroSimulator {
    /** Última hora de la línea de tiempo. */
    const val MAX_HOURS = 72f

    /*
     * Cargas de la sesión pesada de demostración (sentadilla y peso muerto pesados), fijadas para que recién terminada dé
     * Músculos 45 (90·ln(100/45) ≈ 71,9), Columna 55 (70·ln(100/55) ≈ 41,8) y Energía 70 (75·ln(100/70) ≈ 26,8).
     * No salen de sumar sesiones de la tabla de E3a: un día de sentadilla y peso muerto muy intensos, con esa tabla, deja
     * la Columna en ≈ 84.
     */
    private const val HEAVY_MUSCULAR_LOAD = 71.9
    private const val HEAVY_SPINAL_LOAD = 41.8
    private const val HEAVY_ENERGY_LOAD = 26.8

    /** Pilar de referencia del músculo global: su τ (48 h) es la que usa el ajuste manual del motor. */
    private const val MUSCLE_REFERENCE_PILLAR = "Pectorales"

    private const val MS_PER_HOUR = 3_600_000.0

    private val heavyDay = InitialRecoveryDayLoad(
        dayStartMs = 0L,
        muscular = mapOf(MUSCLE_REFERENCE_PILLAR to HEAVY_MUSCULAR_LOAD),
        energy = HEAVY_ENERGY_LOAD,
        spinal = HEAVY_SPINAL_LOAD,
    )
    private val noSensations = InitialRecoverySensations()

    /** Valores de la torre en la hora de [state] (acotada a 0–72). Sin sesión ni ajustes, todo vale 100. */
    fun valuesAt(state: RingsIntroState): RingsIntroValues {
        val hours = state.hoursAfter.coerceIn(0f, MAX_HOURS).toDouble()
        return RingsIntroValues(
            columna = channelValue(RingChannelId.COLUMNA, state, hours),
            musculos = channelValue(RingChannelId.MUSCULOS, state, hours),
            energia = channelValue(RingChannelId.ENERGIA, state, hours),
        )
    }

    /** Simula la sesión pesada o la quita. Quitarla devuelve la demostración a su estado inicial. */
    fun toggleSession(state: RingsIntroState): RingsIntroState =
        if (state.heavySession) RingsIntroState() else state.copy(heavySession = true)

    /** Mueve la línea de tiempo a una hora entera entre 0 y 72. */
    fun withHours(state: RingsIntroState, hours: Float): RingsIntroState =
        state.copy(hoursAfter = hours.roundToInt().toFloat().coerceIn(0f, MAX_HOURS))

    /** Ajuste de la persona: fija [channel] en [value] a la hora actual de la línea de tiempo. */
    fun withAdjustment(state: RingsIntroState, channel: RingChannelId, value: Int): RingsIntroState = state.copy(
        manual = state.manual + (channel to value.coerceIn(0, 100)),
        manualHours = state.manualHours + (channel to state.hoursAfter.coerceIn(0f, MAX_HOURS)),
    )

    /** Valor de un canal: la sesión, o el ajuste desde su hora si ya ha ocurrido. */
    private fun channelValue(channel: RingChannelId, state: RingsIntroState, hours: Double): Int {
        val session = sessionBattery(channel, state.heavySession, hours)
        val adjusted = state.manual[channel] ?: return session.roundToInt().coerceIn(0, 100)
        val since = (state.manualHours[channel] ?: 0f).coerceIn(0f, MAX_HOURS).toDouble()
        if (hours < since) return session.roundToInt().coerceIn(0, 100)
        val decay = anchorDecay(channel, hours - since)
        return InitialRecoveryV2Model.anchorLaw(adjusted.coerceIn(0, 100), decay).roundToInt().coerceIn(0, 100)
    }

    /** Batería de la sesión pesada en [hours] (100 sin sesión), con las funciones de batería de E3a. */
    private fun sessionBattery(channel: RingChannelId, heavy: Boolean, hours: Double): Double {
        if (!heavy) return 100.0
        val nowMs = (hours * MS_PER_HOUR).roundToLong()
        val days = listOf(heavyDay)
        return when (channel) {
            RingChannelId.COLUMNA -> InitialRecoveryV2Model.spinalBattery(
                dayLoads = days, sensations = noSensations, capturedAtMs = 0L, nowMs = nowMs,
            )
            RingChannelId.MUSCULOS -> InitialRecoveryV2Model.pillarBattery(
                pillar = MUSCLE_REFERENCE_PILLAR, dayLoads = days, sensations = noSensations, capturedAtMs = 0L, nowMs = nowMs,
            )
            RingChannelId.ENERGIA -> InitialRecoveryV2Model.energyBattery(
                dayLoads = days, sensations = noSensations, capturedAtMs = 0L, nowMs = nowMs,
            )
        }
    }

    /** Curva de cada canal tras un ajuste: las mismas que usa `anchored` en `resolveV2` de E3a. */
    private fun anchorDecay(channel: RingChannelId, hours: Double): Double = when (channel) {
        RingChannelId.COLUMNA -> InitialRecoveryV2Model.spinalDecay(hours)
        RingChannelId.MUSCULOS -> InitialRecoveryV2Model.muscleDecay(hours, InitialRecoveryV2Model.TAU_GLOBAL_MUSCLE_HOURS)
        RingChannelId.ENERGIA -> InitialRecoveryV2Model.energyDecay(hours)
    }
}
