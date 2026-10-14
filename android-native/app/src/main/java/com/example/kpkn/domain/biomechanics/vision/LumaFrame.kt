package com.example.kpkn.domain.biomechanics.vision

/**
 * Luma de 8 bits sin signo, fila a fila (stride = [width]) (§3.3).
 *
 * Convención de coordenadas de todo el paquete `vision`: el píxel (i, j) cubre [i, i+1)×[j, j+1) y su centro está en
 * (i + 0,5; j + 0,5). Es la misma que usa la pose (x normalizada × ancho), así que un `BarSample` y un punto de la pose se
 * comparan sin desfase de medio píxel.
 *
 * La clase no copia [data]: quien la crea puede reutilizar el mismo buffer fotograma a fotograma (el rastreador no guarda
 * ninguna referencia entre llamadas).
 */
class LumaFrame(val width: Int, val height: Int, val data: ByteArray) {
    init {
        require(width > 0 && height > 0) { "dimensiones inválidas: ${width}x$height" }
        require(data.size >= width * height) { "faltan píxeles: ${data.size} < ${width * height}" }
    }

    /** Luma 0..255 del píxel de índices (x, y). No comprueba los límites. */
    fun at(x: Int, y: Int): Int = data[y * width + x].toInt() and 0xFF

    /**
     * Luma interpolada (bilinear) en el punto continuo (x, y), con el centro del píxel (i, j) en (i + 0,5; j + 0,5). Fuera del
     * fotograma vale lo del borde más cercano.
     */
    fun sample(x: Double, y: Double): Double {
        val fx = x - 0.5
        val fy = y - 0.5
        val ix = Math.floor(fx).toInt()
        val iy = Math.floor(fy).toInt()
        val wx = fx - ix
        val wy = fy - iy
        val xa = if (ix < 0) 0 else if (ix >= width) width - 1 else ix
        val xb = if (ix + 1 < 0) 0 else if (ix + 1 >= width) width - 1 else ix + 1
        val ya = if (iy < 0) 0 else if (iy >= height) height - 1 else iy
        val yb = if (iy + 1 < 0) 0 else if (iy + 1 >= height) height - 1 else iy + 1
        val rowA = ya * width
        val rowB = yb * width
        val top = (data[rowA + xa].toInt() and 0xFF) * (1.0 - wx) + (data[rowA + xb].toInt() and 0xFF) * wx
        val bottom = (data[rowB + xa].toInt() and 0xFF) * (1.0 - wx) + (data[rowB + xb].toInt() and 0xFF) * wx
        return top * (1.0 - wy) + bottom * wy
    }
}
