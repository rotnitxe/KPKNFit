package com.example.kpkn.domain.biomechanics.metrics

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Tempo de la serie «E-P-C-P» (§6): mediana de las repeticiones FULL de la excéntrica, la pausa tras ella, la concéntrica y
 * la pausa tras ella, en ms. Un valor `null` es una fase que no se pudo medir. Las pausas de menos de 0,25 s valen 0.
 */
@Serializable
data class TempoSummary(
    val eccentricMs: Int?,
    val pauseAfterEccentricMs: Int?,
    val concentricMs: Int?,
    val pauseAfterConcentricMs: Int?,
) {
    /**
     * Etiqueta compacta, en segundos enteros: «3-1-1-0». La concéntrica de 0,5 s o menos se escribe «X» (explosiva)
     * y una fase sin medir, «?».
     */
    fun label(): String = listOf(
        seconds(eccentricMs),
        seconds(pauseAfterEccentricMs),
        if (concentricMs != null && concentricMs <= FAST_CONCENTRIC_MS) "X" else seconds(concentricMs),
        seconds(pauseAfterConcentricMs),
    ).joinToString("-")

    /** Etiqueta con detalle, a 0,1 s y con coma decimal: «2,5-1,0-0,8-0,0». Una fase sin medir se escribe «?». */
    fun detailLabel(): String = listOf(
        eccentricMs, pauseAfterEccentricMs, concentricMs, pauseAfterConcentricMs,
    ).joinToString("-") { ms -> if (ms == null) "?" else tenths(ms) }

    private fun seconds(ms: Int?): String = if (ms == null) "?" else (ms / 1000.0).roundToInt().toString()

    private fun tenths(ms: Int): String {
        val t = (ms / 100.0).roundToInt()
        return "${t / 10},${t % 10}"
    }
}

/** Con una concéntrica de esta duración o menos, el tempo compacto la marca «X». */
private const val FAST_CONCENTRIC_MS = 500
