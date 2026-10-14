package com.example.kpkn.domain.biomechanics.vision

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Kalman de velocidad constante del centro del disco (§8 punto 4), con estado [cx, cy, vx, vy] en píxeles y segundos.
 *
 * Los dos ejes son independientes (misma Q y misma R), así que se llevan como dos filtros de 2 estados con su covarianza
 * (p_xx, p_xv, p_vv): sin matrices ni asignaciones.
 * - Predicción: F = [[1, Δt], [0, 1]] y Q por eje = σa²·[[Δt⁴/4, Δt³/2], [Δt³/2, Δt²]].
 * - Medida: z = posición del centro, con R = σm² (la misma en los dos ejes).
 * - Compuerta: la distancia de Mahalanobis al cuadrado de la innovación, con 2 grados de libertad (9,21 = 99 %).
 */
class ConstantVelocityKalman {
    var x: Double = 0.0
        private set
    var y: Double = 0.0
        private set
    var vx: Double = 0.0
        private set
    var vy: Double = 0.0
        private set

    private var pxx = 0.0
    private var pxv = 0.0
    private var pvx = 0.0
    private var pyy = 0.0
    private var pyv = 0.0
    private var pvy = 0.0

    /** Incertidumbre de la posición (desviación típica, px): la mayor de las de los dos ejes. */
    val positionSigma: Double get() = sqrt(max(pxx, pyy))

    /** Incertidumbre de la velocidad (px/s): la mayor de las de los dos ejes. */
    val velocitySigma: Double get() = sqrt(max(pvx, pvy))

    /** Fija el estado en ([cx], [cy]) con velocidad ([vx0], [vy0]) y las desviaciones iniciales dadas. */
    fun reset(cx: Double, cy: Double, vx0: Double, vy0: Double, sigmaPos: Double, sigmaVel: Double) {
        x = cx
        y = cy
        vx = vx0
        vy = vy0
        pxx = sigmaPos * sigmaPos
        pyy = pxx
        pvx = sigmaVel * sigmaVel
        pvy = pvx
        pxv = 0.0
        pyv = 0.0
    }

    /**
     * Avanza el estado [dt] segundos (puede ser negativo: se sigue hacia atrás) con ruido de aceleración [sigmaA] (px/s²).
     * Q depende de Δt²: con Δt negativo el término cruzado cambia de signo, como corresponde al tiempo invertido.
     */
    fun predict(dt: Double, sigmaA: Double) {
        if (dt == 0.0) return
        val dt2 = dt * dt
        val dt3 = dt2 * dt
        val dt4 = dt2 * dt2
        val s2 = sigmaA * sigmaA
        val qxx = s2 * dt4 / 4.0
        val qxv = s2 * dt3 / 2.0
        val qvv = s2 * dt2
        x += vx * dt
        y += vy * dt
        val nxx = pxx + 2.0 * dt * pxv + dt2 * pvx + qxx
        val nxv = pxv + dt * pvx + qxv
        pxx = nxx
        pxv = nxv
        pvx += qvv
        val nyy = pyy + 2.0 * dt * pyv + dt2 * pvy + qxx
        val nyv = pyv + dt * pvy + qxv
        pyy = nyy
        pyv = nyv
        pvy += qvv
    }

    /** Distancia de Mahalanobis al cuadrado de la medida ([zx], [zy]) con ruido [sigmaM] (px) respecto de la predicción. */
    fun mahalanobis2(zx: Double, zy: Double, sigmaM: Double): Double {
        val r = sigmaM * sigmaM
        val ix = zx - x
        val iy = zy - y
        return ix * ix / (pxx + r) + iy * iy / (pyy + r)
    }

    /** Incorpora la medida ([zx], [zy]) con ruido [sigmaM] (px). */
    fun update(zx: Double, zy: Double, sigmaM: Double) {
        val r = sigmaM * sigmaM
        run {
            val s = pxx + r
            val k0 = pxx / s
            val k1 = pxv / s
            val innovation = zx - x
            x += k0 * innovation
            vx += k1 * innovation
            val nxx = pxx - k0 * pxx
            val nxv = pxv - k0 * pxv
            val nvv = pvx - k1 * pxv
            pxx = nxx
            pxv = nxv
            pvx = nvv
        }
        run {
            val s = pyy + r
            val k0 = pyy / s
            val k1 = pyv / s
            val innovation = zy - y
            y += k0 * innovation
            vy += k1 * innovation
            val nyy = pyy - k0 * pyy
            val nyv = pyv - k0 * pyv
            val nvv = pvy - k1 * pyv
            pyy = nyy
            pyv = nyv
            pvy = nvv
        }
    }
}
