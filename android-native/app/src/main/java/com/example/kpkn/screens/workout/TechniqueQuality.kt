package com.example.kpkn.screens.workout

import kotlin.math.roundToInt

/**
 * La escala de la calidad técnica de 1 a 10 del feedback tras el ejercicio (LV6, F2): la palabra que la describe, su color (rojo, ámbar
 * y verde) y la postura de la figura. Todo es puro: la pantalla solo dibuja lo que sale de aquí, y las pruebas lo comprueban sin pintar.
 */
internal object TechniqueQuality {
    const val MIN: Int = 1
    const val MAX: Int = 10

    /** Valor con el que arranca cada ejercicio (el de siempre). */
    const val DEFAULT: Int = 8

    /** Hasta esta nota la figura tambalea. */
    const val SHAKY_UP_TO: Int = 4

    /** Desde esta nota la figura destella. */
    const val CLEAN_FROM: Int = 8

    private val words = listOf(
        "muy irregular", "muy irregular", "irregular", "irregular", "aceptable",
        "aceptable", "buena", "limpia", "muy limpia", "perfecta",
    )

    private val red = intArrayOf(239, 111, 100)
    private val amber = intArrayOf(247, 207, 115)
    private val green = intArrayOf(67, 209, 140)

    fun clamp(value: Int): Int = value.coerceIn(MIN, MAX)

    /** La palabra de la nota: «limpia», «aceptable», «irregular»… */
    fun word(value: Int): String = words[clamp(value) - 1]

    /** Técnica limpia: la figura destella. */
    fun isClean(value: Int): Boolean = clamp(value) >= CLEAN_FROM

    fun pose(value: Int): TechniquePose = when {
        clamp(value) <= SHAKY_UP_TO -> TechniquePose.SHAKY
        clamp(value) < CLEAN_FROM -> TechniquePose.LEANING
        else -> TechniquePose.UPRIGHT
    }

    /** Fracción de la pista que llena la nota (0,1 a 1). */
    fun fraction(value: Int): Float = clamp(value) / MAX.toFloat()

    /** Color de la nota como 0xAARRGGBB: del rojo (1) al ámbar (a mitad) y al verde (10). */
    fun colorArgb(value: Int): Int {
        val t = (clamp(value) - MIN) / (MAX - MIN).toFloat()
        val channels = if (t < 0.5f) mix(red, amber, t * 2f) else mix(amber, green, (t - 0.5f) * 2f)
        return (0xFF shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
    }

    /** El rojo de la pista (primer color del degradado de la barra). */
    fun redArgb(): Int = (0xFF shl 24) or (red[0] shl 16) or (red[1] shl 8) or red[2]

    private fun mix(from: IntArray, to: IntArray, t: Float): IntArray =
        IntArray(3) { i -> (from[i] + (to[i] - from[i]) * t).roundToInt() }
}

/** Postura de la figura: tambaleante (nota baja), inclinada (media) o derecha (alta). */
internal enum class TechniquePose { SHAKY, LEANING, UPRIGHT }
