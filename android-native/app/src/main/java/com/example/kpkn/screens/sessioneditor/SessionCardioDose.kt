package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.domain.cardio.CardioAuthoringShape
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import kotlin.math.roundToInt

/**
 * Un tramo de la mini línea de tiempo del cardio: [work] = esfuerzo; si no, pausa, calentamiento o vuelta a la calma
 * (SESSION_EDITOR_V1 §2, punto 7). La duración decide el ancho de la barra.
 */
internal data class CardioTimelineSegment(val work: Boolean, val seconds: Int)

/**
 * Dosis corta de un bloque de cardio para su fila plegada: «30 min · zona 2», «5 km · 5:30 /km» o «8 × 0:30 / 1:30». Función pura, sin
 * Android. Sin dato no hay dosis (null): nunca se inventa un objetivo que el bloque no tiene.
 */
internal object SessionCardioDose {

    /** Tope de tramos en la mini línea: a partir de aquí no se lee como línea de tiempo. */
    const val MAX_TIMELINE_SEGMENTS = 120

    /** Dosis del bloque, o null si no hay ningún objetivo que decir. */
    fun of(details: CardioDetails): String? = when (val shape = CardioRepeatGrammar.shape(details)) {
        is CardioAuthoringShape.Steady -> steady(details)
        is CardioAuthoringShape.Uniform -> uniform(shape.repeat)
        is CardioAuthoringShape.Irregular -> irregular(details, shape.blocks.size)
    }

    /** Tramos de la mini línea. Solo intervalos y HIIT la tienen; el cardio continuo no tiene tramos. */
    fun timelineOf(details: CardioDetails): List<CardioTimelineSegment> {
        val segments = when (val shape = CardioRepeatGrammar.shape(details)) {
            is CardioAuthoringShape.Steady -> emptyList()
            is CardioAuthoringShape.Uniform -> uniformTimeline(shape.repeat)
            is CardioAuthoringShape.Irregular -> irregularTimeline(details)
        }
        return segments.take(MAX_TIMELINE_SEGMENTS)
    }

    /** Zona de FC sobre el máximo, con las bandas del modelo de 5 zonas (cortes en 60, 70, 80 y 90 %). */
    fun zoneOf(hrPercent: Int): Int = when {
        hrPercent < 60 -> 1
        hrPercent < 70 -> 2
        hrPercent < 80 -> 3
        hrPercent < 90 -> 4
        else -> 5
    }

    /** «30 min», «1 h 5 min», «1 min 30 s» o «45 s». */
    fun minutesText(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return when {
            hours > 0 && minutes == 0 && secs == 0 -> "$hours h"
            hours > 0 && secs == 0 -> "$hours h $minutes min"
            hours > 0 -> "$hours h $minutes min $secs s"
            minutes > 0 && secs == 0 -> "$minutes min"
            minutes > 0 -> "$minutes min $secs s"
            else -> "$secs s"
        }
    }

    /** «5 km» o «2,5 km»: coma decimal, como en el resto de la app en español. */
    fun kmText(km: Double): String {
        val tenths = (km * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10} km" else "${tenths / 10},${tenths % 10} km"
    }

    /** «0:30» o «1:30»: minutos sin cero a la izquierda y segundos con dos cifras. */
    fun clock(seconds: Int): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

    /** «5:30 /km». */
    fun paceText(secondsPerKm: Int): String = "%d:%02d /km".format(secondsPerKm.coerceAtLeast(0) / 60, secondsPerKm.coerceAtLeast(0) % 60)

    private fun steady(details: CardioDetails): String? {
        val distance = details.targetDistanceKm?.takeIf { it > 0.0 }
        val duration = details.targetDurationSeconds?.takeIf { it > 0 }
        val pace = details.targetPaceSecondsPerKm?.takeIf { it > 0 }
        val zone = details.targetHrPercent?.let { zoneOf(it) }
        // Con distancia, la distancia es el objetivo; el tiempo por defecto no se lee.
        val target = when {
            distance != null -> kmText(distance)
            duration != null -> minutesText(duration)
            else -> null
        }
        return listOfNotNull(target, pace?.let { paceText(it) }, zone?.let { "zona $it" })
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
    }

    private fun uniform(repeat: CardioUniformRepeat): String? {
        if (repeat.rounds < 1 || repeat.workSeconds <= 0) return null
        val rest = if (repeat.restSeconds > 0) " / ${clock(repeat.restSeconds)}" else ""
        return "${repeat.rounds} × ${clock(repeat.workSeconds)}$rest"
    }

    private fun irregular(details: CardioDetails, blockCount: Int): String {
        val total = details.effectiveDurationSeconds()
        return if (total > 0) "$blockCount bloques · ${minutesText(total)}" else "$blockCount bloques"
    }

    private fun uniformTimeline(repeat: CardioUniformRepeat): List<CardioTimelineSegment> {
        val out = mutableListOf<CardioTimelineSegment>()
        if (!repeat.warmupOpen && repeat.warmupSeconds > 0) {
            out += CardioTimelineSegment(work = false, seconds = repeat.warmupSeconds)
        }
        val sets = repeat.sets.coerceAtLeast(1)
        val rounds = repeat.rounds.coerceAtLeast(0)
        for (set in 1..sets) {
            for (round in 1..rounds) {
                if (repeat.workSeconds > 0) out += CardioTimelineSegment(work = true, seconds = repeat.workSeconds)
                if (round < rounds && repeat.restSeconds > 0) out += CardioTimelineSegment(work = false, seconds = repeat.restSeconds)
            }
            if (set < sets && repeat.restBetweenSetsSeconds > 0) {
                out += CardioTimelineSegment(work = false, seconds = repeat.restBetweenSetsSeconds)
            }
        }
        if (!repeat.cooldownOpen && repeat.cooldownSeconds > 0) {
            out += CardioTimelineSegment(work = false, seconds = repeat.cooldownSeconds)
        }
        return out
    }

    private fun irregularTimeline(details: CardioDetails): List<CardioTimelineSegment> {
        val rounds = details.intervalRounds.coerceIn(1, 99)
        return buildList {
            repeat(rounds) {
                details.intervalBlocks.forEach { block ->
                    if (block.durationSeconds > 0) {
                        add(CardioTimelineSegment(work = block.type == CardioBlockType.WORK, seconds = block.durationSeconds))
                    }
                }
            }
        }
    }
}
