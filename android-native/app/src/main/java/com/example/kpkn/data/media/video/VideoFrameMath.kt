package com.example.kpkn.data.media.video

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Aritmética de la decodificación sin Android (se prueba en JVM): tamaños de salida, giros, luma y tiempos. */
object VideoFrameMath {
    /** Ancho y alto en píxeles. */
    data class Size(val width: Int, val height: Int)

    /** Pesos de la luma: suman 256, así que `(77 r + 150 g + 29 b) shr 8` da 0..255 sin dividir. */
    private const val LUMA_R = 77
    private const val LUMA_G = 150
    private const val LUMA_B = 29

    /** Giro horario llevado a 0, 90, 180 o 270 (un valor que no sea múltiplo de 90 va al múltiplo más cercano). */
    fun normalizeRotation(degrees: Int): Int {
        val wrapped = ((degrees % 360) + 360) % 360
        return ((wrapped + 45) / 90 * 90) % 360
    }

    /** Tamaño en pantalla (ya girado) de un vídeo codificado de [codedWidth]×[codedHeight] con ese giro. */
    fun displaySize(codedWidth: Int, codedHeight: Int, rotationDegrees: Int): Size =
        if (normalizeRotation(rotationDegrees) % 180 == 0) Size(codedWidth, codedHeight) else Size(codedHeight, codedWidth)

    /**
     * Tamaño del fotograma de salida: el lado largo de la pantalla a [targetLongEdge] (sin ampliar nunca) y el otro lado con la
     * proporción conservada y PAR (el par más cercano; a medias, el de arriba). 1080×1920 a 720 da 406×720.
     */
    fun outputSize(displayWidth: Int, displayHeight: Int, targetLongEdge: Int): Size {
        require(displayWidth > 0 && displayHeight > 0) { "Tamaño de pantalla no válido: ${displayWidth}x$displayHeight" }
        val longSide = max(displayWidth, displayHeight)
        val shortSide = min(displayWidth, displayHeight)
        val newLong = if (targetLongEdge <= 0 || targetLongEdge >= longSide) longSide else targetLongEdge
        val newShort = max(2, nearestEven(shortSide.toDouble() * newLong / longSide))
        return if (displayWidth >= displayHeight) Size(newLong, newShort) else Size(newShort, newLong)
    }

    /** El entero par más cercano a [value]; si está justo en medio de dos pares, el mayor. */
    private fun nearestEven(value: Double): Int = 2 * floor(value / 2.0 + 0.5).toInt()

    /**
     * Matriz 4×4 (columna-mayor, la de `glUniformMatrix4fv`) del vértice: gira el cuadrado de la imagen [rotationDegrees] grados
     * en sentido horario (coordenadas de dispositivo con la Y hacia arriba) y, con [flipY], invierte la Y para que `glReadPixels`
     * devuelva las filas de arriba abajo. El cuadrado va de −1 a 1 en los dos ejes, así que girarlo no cambia su forma.
     */
    fun vertexMatrix(rotationDegrees: Int, flipY: Boolean): FloatArray {
        // x' = a·x + c·y ; y' = b·x + d·y
        var a = 1f
        var b = 0f
        var c = 0f
        var d = 1f
        when (normalizeRotation(rotationDegrees)) {
            90 -> { a = 0f; b = -1f; c = 1f; d = 0f }
            180 -> { a = -1f; b = 0f; c = 0f; d = -1f }
            270 -> { a = 0f; b = 1f; c = -1f; d = 0f }
        }
        if (flipY) {
            b = -b
            d = -d
        }
        val m = FloatArray(16)
        m[0] = a
        m[1] = b
        m[4] = c
        m[5] = d
        m[10] = 1f
        m[15] = 1f
        return m
    }

    /**
     * Giro horario que YA lleva incorporado la matriz de textura de un `SurfaceTexture` (0, 90, 180 o 270), por si un códec
     * ignorase el `KEY_ROTATION = 0` que se le pide. La matriz de Android es «volteo vertical · recorte · giro»: sin giro, la X
     * conserva el signo y la Y lo invierte; con 90° los ejes se cruzan con los dos signos negativos; con 270°, positivos.
     */
    fun bakedRotationOf(texMatrix: FloatArray): Int {
        val diagonal = abs(texMatrix[0]) + abs(texMatrix[5])
        val crossed = abs(texMatrix[1]) + abs(texMatrix[4])
        return when {
            crossed > diagonal && texMatrix[1] < 0f && texMatrix[4] < 0f -> 90
            crossed > diagonal && texMatrix[1] > 0f && texMatrix[4] > 0f -> 270
            crossed <= diagonal && texMatrix[0] < 0f && texMatrix[5] > 0f -> 180
            else -> 0
        }
    }

    /**
     * Lleva el punto continuo ([x], [y]) de un fotograma codificado de [codedWidth]×[codedHeight] (origen arriba a la izquierda,
     * Y hacia abajo) al mismo punto del fotograma girado [rotationDegrees] grados en sentido horario. Resultado en [out] (x, y).
     */
    fun rotatePoint(x: Double, y: Double, codedWidth: Int, codedHeight: Int, rotationDegrees: Int, out: DoubleArray) {
        when (normalizeRotation(rotationDegrees)) {
            90 -> { out[0] = codedHeight - y; out[1] = x }
            180 -> { out[0] = codedWidth - x; out[1] = codedHeight - y }
            270 -> { out[0] = y; out[1] = codedWidth - x }
            else -> { out[0] = x; out[1] = y }
        }
    }

    /** Luma 0..255 de un color RGB (cada canal 0..255). */
    fun luma(r: Int, g: Int, b: Int): Int = (LUMA_R * r + LUMA_G * g + LUMA_B * b) shr 8

    /** Luma de [pixelCount] píxeles RGBA (4 bytes cada uno, sin signo) de [rgba] a [out]. No asigna nada. */
    fun lumaFromRgba(rgba: ByteArray, pixelCount: Int, out: ByteArray) {
        var source = 0
        for (pixel in 0 until pixelCount) {
            val r = rgba[source].toInt() and 0xFF
            val g = rgba[source + 1].toInt() and 0xFF
            val b = rgba[source + 2].toInt() and 0xFF
            out[pixel] = ((LUMA_R * r + LUMA_G * g + LUMA_B * b) shr 8).toByte()
            source += 4
        }
    }

    /** PTS creciente: si [ptsUs] no supera al anterior ([previousUs], `Long.MIN_VALUE` si no hay), vale el anterior + 1. */
    fun repairedPts(ptsUs: Long, previousUs: Long): Long =
        if (previousUs == Long.MIN_VALUE || ptsUs > previousUs) ptsUs else previousUs + 1

    /** fps nominal = 1 / mediana de los saltos entre los primeros [count] PTS (en cualquier orden); `null` si no se puede. */
    fun nominalFpsFromPts(ptsUs: LongArray, count: Int): Float? {
        val n = min(count, ptsUs.size)
        if (n < 2) return null
        val sorted = ptsUs.copyOf(n)
        sorted.sort()
        val gaps = LongArray(n - 1)
        var m = 0
        for (i in 0 until n - 1) {
            val gap = sorted[i + 1] - sorted[i]
            if (gap > 0L) gaps[m++] = gap
        }
        if (m == 0) return null
        gaps.sort(0, m)
        val median = if (m % 2 == 1) gaps[m / 2].toDouble() else (gaps[m / 2 - 1] + gaps[m / 2]) / 2.0
        return (1_000_000.0 / median).toFloat()
    }

    /** PTS que el respaldo asigna al fotograma [index]: sale del índice y del fps nominal. */
    fun indexedPtsUs(index: Int, fps: Float): Long = (index * 1_000_000.0 / fps).roundToLong()

    /** Primer índice cuyo PTS (según [indexedPtsUs]) es ≥ [fromUs]. */
    fun firstIndexAtOrAfter(fromUs: Long, fps: Float): Int {
        if (fromUs <= 0L) return 0
        var index = ceil(fromUs.toDouble() * fps / 1_000_000.0).toInt().coerceAtLeast(0)
        while (index > 0 && indexedPtsUs(index - 1, fps) >= fromUs) index--
        while (indexedPtsUs(index, fps) < fromUs) index++
        return index
    }
}
