package com.example.kpkn.domain.biomechanics.testing

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * Cámara estenopeica sobre el atleta (ejes del atleta: X adelante, Y arriba, Z izquierda). Está en
 * `(d·cos ψ, altura, d·sin ψ)` y mira horizontalmente al origen (el suelo bajo el atleta), con pitch y roll después.
 *
 * - ψ = 0: delante del atleta, que mira a la cámara; ψ = 90: a su izquierda; ψ = −90: a su derecha; ψ = 180: detrás.
 * - El «derecho» de la imagen es `(−sin ψ, 0, cos ψ)`: de lado (ψ = 90) la dirección hacia delante del atleta cae a
 *   la IZQUIERDA de la imagen; de frente, el lado izquierdo del atleta cae a la derecha de la imagen.
 * - Roll > 0 gira la imagen en sentido horario alrededor de su centro.
 */
internal class CameraModel(val cam: SynthCamera) {
    private val psi = Math.toRadians(cam.yawDeg)
    private val pitch = Math.toRadians(cam.pitchDeg)
    private val cosRoll = cos(Math.toRadians(cam.rollDeg))
    private val sinRoll = sin(Math.toRadians(cam.rollDeg))

    val widthPx = cam.widthPx.toDouble()
    val heightPx = cam.heightPx.toDouble()

    /** Focal en píxeles (cuadrados): HFOV a lo largo del ancho. */
    val focalPx: Double = (widthPx / 2.0) / tan(Math.toRadians(cam.hfovDeg) / 2.0)

    val position = V3(cam.distanceM * cos(psi), cam.heightM, cam.distanceM * sin(psi))
    val right = V3(-sin(psi), 0.0, cos(psi))

    private val forward0 = V3(-cos(psi), 0.0, -sin(psi))
    val forward = forward0 * cos(pitch) + V3(0.0, 1.0, 0.0) * sin(pitch)
    val up = V3(0.0, 1.0, 0.0) * cos(pitch) - forward0 * sin(pitch)

    /** Profundidad (m) del punto, a lo largo del eje óptico. */
    fun depth(p: V3): Double = (p - position).dot(forward)

    /**
     * Proyecta [p]: devuelve en [out] la x e y normalizadas (0..1 sobre el fotograma, ya con el roll) y la profundidad
     * en metros. Los puntos casi en el plano de la cámara se recortan a 5 cm para no dividir por cero.
     */
    fun project(p: V3, out: DoubleArray) {
        val rel = p - position
        val xc = rel.dot(right)
        val yc = rel.dot(up)
        val zc = max(rel.dot(forward), 0.05)
        val du = focalPx * xc / zc
        val dv = -focalPx * yc / zc
        // Roll: giro horario de la imagen alrededor de su centro (Y hacia abajo).
        val ru = du * cosRoll - dv * sinRoll
        val rv = du * sinRoll + dv * cosRoll
        out[0] = (widthPx / 2.0 + ru) / widthPx
        out[1] = (heightPx / 2.0 + rv) / heightPx
        out[2] = zc
    }

    /** Punto relativo a [origin] en ejes de cámara «world»: x a la derecha, y hacia abajo, z en profundidad (m), con el roll. */
    fun worldOf(p: V3, origin: V3, out: DoubleArray) {
        val rel = p - origin
        val x = rel.dot(right)
        val yDown = -rel.dot(up)
        val z = rel.dot(forward)
        out[0] = x * cosRoll - yDown * sinRoll
        out[1] = x * sinRoll + yDown * cosRoll
        out[2] = z
    }
}
