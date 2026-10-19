package com.example.kpkn.domain.biomechanics.kinematics

import kotlin.math.sqrt

/** Vector o punto 2D en unidades FH (alto de fotograma = 1; X hacia la derecha, Y hacia abajo). */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2): Vec2 = Vec2(x + o.x, y + o.y)

    operator fun minus(o: Vec2): Vec2 = Vec2(x - o.x, y - o.y)

    operator fun times(k: Double): Vec2 = Vec2(x * k, y * k)

    /** Longitud euclídea. */
    fun norm(): Double = sqrt(x * x + y * y)

    /** Producto escalar. */
    fun dot(o: Vec2): Double = x * o.x + y * o.y

    /** Componente z del producto vectorial 2D: x·o.y − y·o.x. */
    fun cross(o: Vec2): Double = x * o.y - y * o.x
}
