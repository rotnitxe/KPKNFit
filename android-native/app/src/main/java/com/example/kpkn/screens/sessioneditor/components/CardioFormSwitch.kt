package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalPattern
import com.example.kpkn.domain.cardio.CardioAuthoringShape
import com.example.kpkn.domain.cardio.CardioIntervalProgramBuilder
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioUniformRepeat

/** Las tres formas del cardio, en el orden del control segmentado: Continuo · Series · Intervalos. */
internal enum class CardioForm { CONTINUO, SERIES, INTERVALOS }

/** Forma que hoy elige el contenido de [details], con la misma lógica que el editor original. */
internal fun cardioFormOf(details: CardioDetails): CardioForm = when (CardioRepeatGrammar.shape(details)) {
    is CardioAuthoringShape.Steady -> CardioForm.CONTINUO
    is CardioAuthoringShape.Uniform -> CardioForm.SERIES
    is CardioAuthoringShape.Irregular -> CardioForm.INTERVALOS
}

/** Series por defecto al pasar de continuo a series: las mismas que «Añadir series» en el original. */
internal val DefaultCardioSeries = CardioUniformRepeat(
    warmupSeconds = 180,
    workSeconds = 30,
    restSeconds = 60,
    rounds = 8,
    cooldownSeconds = 180,
    targetRpe = 9.0,
)

/**
 * Pasa [details] a la forma [target] con las transiciones que ya tenía el original:
 * - a continuo: `applySteady`, con la duración que hay (o 20 min); conserva distancia y GPS;
 * - a series: las de «Añadir series»; conserva distancia, GPS y ritmo;
 * - a intervalos, desde otra forma: el patrón «Escalera» del editor de bloques, con el mismo constructor que usan sus tarjetas.
 * Devuelve null si ya está en esa forma, así no hay nada que cambiar.
 */
internal fun switchCardioForm(details: CardioDetails, target: CardioForm): CardioDetails? {
    if (cardioFormOf(details) == target) return null
    return when (target) {
        CardioForm.CONTINUO -> CardioRepeatGrammar.applySteady(
            details,
            durationSeconds = details.effectiveDurationSeconds().takeIf { it > 0 } ?: 20 * 60,
        )
        CardioForm.SERIES -> CardioRepeatGrammar.applyUniform(details, DefaultCardioSeries)
        CardioForm.INTERVALOS -> CardioIntervalProgramBuilder.buildDetails(
            pattern = CardioIntervalPattern.LADDER,
            totalSeconds = details.effectiveDurationSeconds().coerceAtLeast(20 * 60),
            type = details.type,
            baseLevel = details.resolvedIntensityLevel(),
            base = details.copy(hiit = null),
        )
    }
}
