package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import java.time.Instant
import java.time.ZoneOffset
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
 * - [heavySession]: la sesión de referencia está simulada.
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
 * Demostración de RINGS: cómo baja un entreno pesado, cómo se recupera cada anillo y qué pasa cuando la persona ajusta un
 * valor. Puro y determinista: sin reloj ni Android.
 *
 * Todo sale del motor, sin números propios:
 * - Sesión: la sesión de referencia ([referenceEvidence]) se calcula con `fromInputsV2`, la función que usa la evidencia
 *   real de RINGS. Sus `dayLoads` alimentan las mismas baterías que `resolveV2`: músculo global (agregado de los 13 pilares),
 *   energía y columna (esta última, si la estructura es conocida).
 * - Ajuste hecho a la hora `h`: `B(t) = 100·(B₀/100)^d(t − h)`, con la curva de cada canal de `anchored` (E3a). Antes de `h`
 *   vale la sesión: el valor salta al ajuste en `h` y la curva sigue desde ahí.
 */
object RingsIntroSimulator {
    /** Última hora de la línea de tiempo. */
    const val MAX_HOURS = 72f

    private const val MS_PER_HOUR = 3_600_000.0

    /** Instante fijo de la sesión de referencia (12:00 UTC): la línea de tiempo cuenta las horas desde aquí. No es el reloj. */
    private val SESSION_MS: Long = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()

    /**
     * Sesión de referencia de la demostración: hoy, intensidad muy alta, sentadilla y peso muerto, 12 series de piernas y
     * 6 de espalda, sin sensaciones. Se calcula una vez con la misma función que usa la evidencia real.
     */
    private val referenceEvidence: InitialRecoveryEvidence by lazy {
        InitialRecoveryEvidenceFactory.fromInputsV2(
            capturedAtMs = SESSION_MS,
            zone = ZoneOffset.UTC,
            trainedDays = setOf(0),
            intensity = InitialRecoveryIntensity.VERY_HARD,
            lifts = setOf(RingsLiftPattern.SQUAT, RingsLiftPattern.DEADLIFT),
            groupSets = mapOf(RingsMuscleGroup.LEGS to 12, RingsMuscleGroup.BACK to 6),
        )
    }

    /** Valores de la torre en la hora de [state] (acotada a 0–72). Sin sesión ni ajustes, todo vale 100. */
    fun valuesAt(state: RingsIntroState): RingsIntroValues {
        val hours = state.hoursAfter.coerceIn(0f, MAX_HOURS).toDouble()
        return RingsIntroValues(
            columna = channelValue(RingChannelId.COLUMNA, state, hours),
            musculos = channelValue(RingChannelId.MUSCULOS, state, hours),
            energia = channelValue(RingChannelId.ENERGIA, state, hours),
        )
    }

    /** Simula la sesión de referencia o la quita. Quitarla devuelve la demostración a su estado inicial. */
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

    /** Batería de la sesión de referencia en [hours] (100 sin sesión), con las baterías de E3a sobre sus `dayLoads`. */
    private fun sessionBattery(channel: RingChannelId, heavy: Boolean, hours: Double): Double {
        if (!heavy) return 100.0
        val evidence = referenceEvidence
        val days = evidence.dayLoads
        val sensations = evidence.sensations
        val nowMs = SESSION_MS + (hours * MS_PER_HOUR).roundToLong()
        return when (channel) {
            RingChannelId.MUSCULOS -> InitialRecoveryV2Model.muscularBattery(
                InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS.map { pillar ->
                    InitialRecoveryV2Model.pillarBattery(pillar, days, sensations, evidence.capturedAtMs, nowMs)
                },
            )
            RingChannelId.ENERGIA -> InitialRecoveryV2Model.energyBattery(days, sensations, evidence.capturedAtMs, nowMs)
            RingChannelId.COLUMNA -> if (evidence.structureKnown) {
                InitialRecoveryV2Model.spinalBattery(days, sensations, evidence.capturedAtMs, nowMs)
            } else {
                100.0
            }
        }
    }

    /** Curva de cada canal tras un ajuste: las mismas que usa `anchored` en `resolveV2` de E3a. */
    private fun anchorDecay(channel: RingChannelId, hours: Double): Double = when (channel) {
        RingChannelId.COLUMNA -> InitialRecoveryV2Model.spinalDecay(hours)
        RingChannelId.MUSCULOS -> InitialRecoveryV2Model.muscleDecay(hours, InitialRecoveryV2Model.TAU_GLOBAL_MUSCLE_HOURS)
        RingChannelId.ENERGIA -> InitialRecoveryV2Model.energyDecay(hours)
    }
}
