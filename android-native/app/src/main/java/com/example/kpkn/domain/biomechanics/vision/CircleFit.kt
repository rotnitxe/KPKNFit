package com.example.kpkn.domain.biomechanics.vision

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ajuste de círculo y de elipse alineada a ejes sobre puntos de borde (§8 puntos 2 y 6).
 *
 * - [kasa]: círculo por mínimos cuadrados algebraicos (Kåsa): minimiza Σ(x² + y² + D·x + E·y + F)².
 * - [ellipseFixedCenter]: con el centro conocido, minimiza Σ(u·dx² + w·dy² − 1)², con a = 1/√u (semieje horizontal) y
 *   b = 1/√w (vertical). Da una escala horizontal y otra vertical.
 * - [ellipseFit]: alterna ese ajuste con un paso de Gauss–Newton sobre el centro. Con el contorno entero da lo mismo que el
 *   centro de Kåsa; con arcos sueltos (un oclusor) evita el sesgo del círculo ajustado a un arco de elipse.
 *
 * Todo trabaja sobre arreglos propios del llamador y no asigna nada. Los puntos con `alive[i] == false` se ignoran
 * (`alive == null`: valen todos los primeros [n]).
 */
object CircleFit {
    /**
     * Círculo de Kåsa de los puntos vivos: `out` = [cx, cy, r]. `false` si hay menos de 3 puntos o están alineados.
     * Se centra en la media antes de resolver para no perder precisión con coordenadas grandes.
     */
    fun kasa(x: DoubleArray, y: DoubleArray, alive: BooleanArray?, n: Int, out: DoubleArray): Boolean {
        var m = 0
        var sx = 0.0
        var sy = 0.0
        for (i in 0 until n) {
            if (alive != null && !alive[i]) continue
            sx += x[i]
            sy += y[i]
            m++
        }
        if (m < 3) return false
        val mx = sx / m
        val my = sy / m
        var suu = 0.0
        var svv = 0.0
        var suv = 0.0
        var suuu = 0.0
        var svvv = 0.0
        var suvv = 0.0
        var svuu = 0.0
        for (i in 0 until n) {
            if (alive != null && !alive[i]) continue
            val u = x[i] - mx
            val v = y[i] - my
            val uu = u * u
            val vv = v * v
            suu += uu
            svv += vv
            suv += u * v
            suuu += uu * u
            svvv += vv * v
            suvv += u * vv
            svuu += v * uu
        }
        val det = suu * svv - suv * suv
        val scale = suu + svv
        if (!(scale > 0.0) || abs(det) < 1e-9 * scale * scale) return false
        val a1 = 0.5 * (suuu + suvv)
        val a2 = 0.5 * (svvv + svuu)
        val uc = (a1 * svv - a2 * suv) / det
        val vc = (suu * a2 - suv * a1) / det
        val r2 = uc * uc + vc * vc + scale / m
        if (!(r2 > 0.0)) return false
        out[0] = mx + uc
        out[1] = my + vc
        out[2] = sqrt(r2)
        return true
    }

    /**
     * Elipse alineada a ejes con el centro ([cx], [cy]) fijo: `out` = [a, b], los semiejes horizontal y vertical.
     * `false` si el sistema es singular o sale un coeficiente no positivo.
     */
    fun ellipseFixedCenter(
        x: DoubleArray,
        y: DoubleArray,
        alive: BooleanArray?,
        n: Int,
        cx: Double,
        cy: Double,
        out: DoubleArray,
    ): Boolean {
        var s20 = 0.0
        var s02 = 0.0
        var s40 = 0.0
        var s22 = 0.0
        var s04 = 0.0
        var m = 0
        for (i in 0 until n) {
            if (alive != null && !alive[i]) continue
            val dx = x[i] - cx
            val dy = y[i] - cy
            val xx = dx * dx
            val yy = dy * dy
            s20 += xx
            s02 += yy
            s40 += xx * xx
            s22 += xx * yy
            s04 += yy * yy
            m++
        }
        if (m < 3) return false
        val det = s40 * s04 - s22 * s22
        if (!(det > 1e-12 * s40 * s04)) return false
        val u = (s20 * s04 - s02 * s22) / det
        val w = (s40 * s02 - s22 * s20) / det
        if (!(u > 0.0) || !(w > 0.0)) return false
        out[0] = 1.0 / sqrt(u)
        out[1] = 1.0 / sqrt(w)
        return true
    }

    /**
     * Elipse alineada a ejes con el centro libre: parte de ([cx0], [cy0]), ajusta los semiejes con el centro fijo y mueve el
     * centro con un paso de Gauss–Newton, [centerIterations] veces. `out` = [cx, cy, a, b].
     */
    fun ellipseFit(
        x: DoubleArray,
        y: DoubleArray,
        alive: BooleanArray?,
        n: Int,
        cx0: Double,
        cy0: Double,
        centerIterations: Int,
        out: DoubleArray,
    ): Boolean {
        var cx = cx0
        var cy = cy0
        if (!ellipseFixedCenter(x, y, alive, n, cx, cy, out)) return false
        var a = out[0]
        var b = out[1]
        for (iteration in 0 until centerIterations) {
            val u = 1.0 / (a * a)
            val w = 1.0 / (b * b)
            var s11 = 0.0
            var s12 = 0.0
            var s22 = 0.0
            var t1 = 0.0
            var t2 = 0.0
            for (i in 0 until n) {
                if (alive != null && !alive[i]) continue
                val dx = x[i] - cx
                val dy = y[i] - cy
                val r = u * dx * dx + w * dy * dy - 1.0
                val jx = -2.0 * u * dx
                val jy = -2.0 * w * dy
                s11 += jx * jx
                s12 += jx * jy
                s22 += jy * jy
                t1 += jx * r
                t2 += jy * r
            }
            val det = s11 * s22 - s12 * s12
            val scale = s11 + s22
            if (!(scale > 0.0) || !(det > 1e-9 * scale * scale)) break
            val stepX = -(t1 * s22 - t2 * s12) / det
            val stepY = -(s11 * t2 - s12 * t1) / det
            // Un paso grande es señal de datos pobres (pocos arcos): no se sigue.
            val limit = 0.25 * (if (a < b) a else b)
            if (abs(stepX) > limit || abs(stepY) > limit) break
            cx += stepX
            cy += stepY
            if (!ellipseFixedCenter(x, y, alive, n, cx, cy, out)) return false
            a = out[0]
            b = out[1]
            if (abs(stepX) < 1e-4 && abs(stepY) < 1e-4) break
        }
        out[0] = cx
        out[1] = cy
        out[2] = a
        out[3] = b
        return true
    }

    /**
     * Distancia con signo (aproximada, de primer orden) del punto ([px], [py]) al borde de la elipse alineada a ejes de centro
     * ([cx], [cy]) y semiejes [a] y [b]: positiva fuera, negativa dentro. Con a = b es exactamente |p − c| − r.
     */
    fun ellipseDistance(px: Double, py: Double, cx: Double, cy: Double, a: Double, b: Double): Double {
        val dx = px - cx
        val dy = py - cy
        val a2 = a * a
        val b2 = b * b
        val rho = sqrt(dx * dx / a2 + dy * dy / b2)
        val gx = dx / a2
        val gy = dy / b2
        val gn = sqrt(gx * gx + gy * gy)
        if (gn < 1e-12) return -sqrt(a * b)
        return (rho - 1.0) * rho / gn
    }
}
