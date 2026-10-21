package com.example.kpkn.domain.biomechanics.kinematics

import kotlin.math.abs
import kotlin.math.atan2

/** Ángulos 2D sobre puntos ya en unidades FH (con el aspecto corregido): ver [FrameGeometry]. */
object Angles {
    /** Longitud por debajo de la cual un segmento se considera degenerado (dos puntos casi idénticos). */
    private const val MIN_SEGMENT_FH = 1e-9

    /**
     * Ángulo incluido en B: θ(A,B,C) = atan2(|BA×BC|, BA·BC), en grados, de 0 a 180.
     * NaN si A o C coinciden con B (no hay ángulo que medir).
     */
    fun included(a: Vec2, b: Vec2, c: Vec2): Double {
        val ba = a - b
        val bc = c - b
        if (ba.norm() < MIN_SEGMENT_FH || bc.norm() < MIN_SEGMENT_FH) return Double.NaN
        return Math.toDegrees(atan2(abs(ba.cross(bc)), ba.dot(bc)))
    }

    /**
     * Inclinación del tronco: τ = atan2(f·(X_S − X_H), Y_H − Y_S), en grados. 0 = vertical, + = hacia delante
     * (en el sentido [facing] del atleta, +1 o −1), 90 = tumbado con la cabeza hacia delante.
     * Y crece hacia abajo, así que con el atleta de pie Y_H − Y_S > 0.
     */
    fun trunkLean(shoulder: Vec2, hip: Vec2, facing: Int): Double {
        val f = if (facing >= 0) 1.0 else -1.0
        return Math.toDegrees(atan2(f * (shoulder.x - hip.x), hip.y - shoulder.y))
    }
}
