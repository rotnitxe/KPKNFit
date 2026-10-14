package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometría de una traza en unidades FH (§4): corrige SIEMPRE el aspecto del fotograma, de modo que las distancias y
 * los ángulos salen iguales en un vídeo vertical que en uno horizontal. (En v1, un tronco a 30° en un vídeo
 * vertical marcaba 46°: ese error no puede repetirse.)
 *
 * - **Unidades FH:** X = x·W/H, Y = y (Y hacia abajo), con W×H = `frameWidth×frameHeight` del fotograma girado.
 * - **Roll:** [rollDeg] > 0 significa que el contenido de la imagen aparece girado en sentido horario (la vertical
 *   real se ve inclinada hacia la derecha por arriba). Los puntos se giran −roll alrededor del centro del
 *   fotograma para volver a poner la vertical vertical. Solo importa en las métricas contra la vertical.
 * - **Confianza:** un punto es válido con presencia ≥ [minPresence] y visibilidad ≥ [minVisibility]. Con
 *   `weak = true` basta visibilidad ≥ 0,3 (solo para contar, o para la vista, donde el lado lejano es débil).
 * - Los puntos que faltan o no pasan la confianza devuelven `null`. Una traza con `aspectKnown = false` (v1) NO
 *   debe medirse con esta clase: el llamador decide.
 *
 * Es inmutable y barata de crear: solo guarda el aspecto y el seno y el coseno del roll.
 */
class FrameGeometry(
    val track: PoseTrackV2,
    val minVisibility: Float = 0.5f,
    val minPresence: Float = 0.5f,
    val rollDeg: Double = 0.0,
) {
    /** Ancho/alto del fotograma girado (W/H): pasa de x normalizada a unidades FH. 1 si las dimensiones no son válidas. */
    val aspect: Double =
        if (track.frameWidth > 0 && track.frameHeight > 0) track.frameWidth.toDouble() / track.frameHeight else 1.0

    /** Número de fotogramas de la traza (con o sin pose). */
    val frameCount: Int get() = frames.size

    private val frames: List<PoseFrameV2> = track.frames

    // Un punto con visibilidad por debajo de este umbral no cuenta ni siquiera como débil (nunca más estricto que el normal).
    private val weakVisibility: Float = min(WEAK_VISIBILITY, minVisibility)

    private val rolled: Boolean = abs(rollDeg) > 1e-9
    private val cosR: Double = cos(Math.toRadians(-rollDeg))
    private val sinR: Double = sin(Math.toRadians(-rollDeg))
    private val centerX: Double = aspect / 2.0

    /**
     * Punto en unidades FH (X = x·W/H, Y = y; Y hacia abajo), girado −roll alrededor del centro; `null` si falta o
     * no supera la confianza. Con [weak] = true basta visibilidad ≥ 0,3 (solo para contar).
     */
    fun point(i: Int, lm: PoseLandmark, weak: Boolean = false): Vec2? {
        if (i < 0 || i >= frames.size) return null
        val f = frames[i]
        if (!usable(f, lm, weak)) return null
        val x = f.x(lm)
        val y = f.y(lm)
        return Vec2(fhX(x, y), fhY(x, y))
    }

    /** Punto medio de [a] y [b] (en unidades FH); `null` si falta cualquiera de los dos. */
    fun mid(i: Int, a: PoseLandmark, b: PoseLandmark, weak: Boolean = false): Vec2? {
        if (i < 0 || i >= frames.size) return null
        val f = frames[i]
        if (!usable(f, a, weak) || !usable(f, b, weak)) return null
        val x = (f.x(a).toDouble() + f.x(b)) * 0.5
        val y = (f.y(a).toDouble() + f.y(b)) * 0.5
        return Vec2(fhX(x, y), fhY(x, y))
    }

    /**
     * Centro de la mano HC = ½·muñeca + ¼·(índice + meñique) del lado [side]; si falta un dedo, la muñeca.
     * `null` si falta la muñeca.
     */
    fun handCenter(i: Int, side: Side): Vec2? {
        if (i < 0 || i >= frames.size) return null
        val f = frames[i]
        val wrist = side.wrist()
        if (!usable(f, wrist, false)) return null
        val wx = f.x(wrist).toDouble()
        val wy = f.y(wrist).toDouble()
        val index = side.index()
        val pinky = side.pinky()
        if (!usable(f, index, false) || !usable(f, pinky, false)) return Vec2(fhX(wx, wy), fhY(wx, wy))
        // fhX y fhY son afines y los pesos suman 1: promediar antes de transformar es lo mismo y no crea puntos sueltos.
        val x = 0.5 * wx + 0.25 * (f.x(index).toDouble() + f.x(pinky))
        val y = 0.5 * wy + 0.25 * (f.y(index).toDouble() + f.y(pinky))
        return Vec2(fhX(x, y), fhY(x, y))
    }

    /** Medio pie MF = ½·(talón + punta) del lado [side]; `null` si falta alguno de los dos. */
    fun midFoot(i: Int, side: Side): Vec2? = mid(i, side.heel(), side.toe())

    /**
     * Distancia 2D en unidades FH entre [a] y [b] (con el aspecto corregido; el giro del roll no la cambia), o NaN si
     * falta alguno. Sin asignaciones: para los bucles que solo necesitan longitudes de segmentos.
     */
    fun distance(i: Int, a: PoseLandmark, b: PoseLandmark, weak: Boolean = false): Double {
        if (i < 0 || i >= frames.size) return Double.NaN
        val f = frames[i]
        if (!usable(f, a, weak) || !usable(f, b, weak)) return Double.NaN
        val dx = (f.x(a).toDouble() - f.x(b)) * aspect
        val dy = f.y(a).toDouble() - f.y(b)
        return sqrt(dx * dx + dy * dy)
    }

    /** `true` si en el fotograma [i] hay pose (al menos el modelo encontró al atleta). */
    fun hasPose(i: Int): Boolean = i >= 0 && i < frames.size && frames[i].hasPose

    private fun usable(f: PoseFrameV2, lm: PoseLandmark, weak: Boolean): Boolean {
        val vis = f.vis(lm) // NaN si falta el dato: toda comparación con NaN es falsa
        if (weak) return vis >= weakVisibility
        return vis >= minVisibility && f.pres(lm) >= minPresence
    }

    // x e y son normalizadas (0..1). Primero el aspecto y luego el giro −roll alrededor del centro (aspect/2, 0,5).
    private fun fhX(x: Double, y: Double): Double {
        val px = x * aspect
        if (!rolled) return px
        return centerX + (px - centerX) * cosR - (y - 0.5) * sinR
    }

    private fun fhY(x: Double, y: Double): Double {
        if (!rolled) return y
        return 0.5 + (x * aspect - centerX) * sinR + (y - 0.5) * cosR
    }

    private fun fhX(x: Float, y: Float): Double = fhX(x.toDouble(), y.toDouble())

    private fun fhY(x: Float, y: Float): Double = fhY(x.toDouble(), y.toDouble())

    companion object {
        /** Visibilidad mínima de un punto «débil» (solo para contar). */
        const val WEAK_VISIBILITY = 0.3f
    }
}
