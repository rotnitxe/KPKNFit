package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.kpkn.domain.onboarding.TrainingPlace
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Trazos de la semana: el sol del día de más energía, los glifos de línea de los tres lugares, la marca de «hecho» que se
 * dibuja y el chevrón del selector. Mismo lenguaje que el resto de símbolos de Entreno: línea fina de extremos redondos,
 * tinta cálida sobre negro y un solo acento por símbolo. Todo se pinta directo en el `DrawScope` (sin `Path` ni
 * asignaciones por cuadro).
 */

private const val RAD_PER_DEG = (PI / 180.0).toFloat()

/**
 * Sol de ocho rayos (los pares largos, los impares cortos) centrado en [center], dentro de un cuadrado de [size] px.
 * [rotationDeg] lo gira (el sol vivo da una vuelta lenta) y [pulse] (0..1) alarga un poco los rayos largos.
 */
internal fun DrawScope.drawWeekSun(
    center: Offset,
    size: Float,
    rotationDeg: Float,
    pulse: Float,
    color: Color,
    strokePx: Float,
) {
    if (size <= 0.5f) return
    drawCircle(color, size * 0.2f, center)
    val inner = size * 0.33f
    val longOuter = size * (0.47f + 0.05f * pulse)
    val shortOuter = size * 0.4f
    for (i in 0 until 8) {
        val angle = (rotationDeg + i * 45f) * RAD_PER_DEG
        val dx = cos(angle)
        val dy = sin(angle)
        val outer = if (i % 2 == 0) longOuter else shortOuter
        drawLine(
            color = color,
            start = Offset(center.x + dx * inner, center.y + dy * inner),
            end = Offset(center.x + dx * outer, center.y + dy * outer),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Glifo de línea de un lugar, centrado en [center] y dentro de un cuadrado de [size] px: una mancuerna (gimnasio), una casa
 * con su puerta (en casa) y un pino (espacios públicos). Pensados para verse pequeños (≈ 24 dp) y reconocerse de un vistazo.
 */
internal fun DrawScope.drawWeekPlaceGlyph(
    place: TrainingPlace,
    center: Offset,
    size: Float,
    color: Color,
    strokePx: Float,
) {
    val u = size / 24f
    val ox = center.x - size / 2f
    val oy = center.y - size / 2f
    fun at(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, at(x1, y1), at(x2, y2), strokePx, StrokeCap.Round)

    when (place) {
        TrainingPlace.GYM -> {
            // Mancuerna de perfil: barra, discos interiores y discos exteriores más cortos.
            seg(6.2f, 12f, 17.8f, 12f)
            seg(6.2f, 7.2f, 6.2f, 16.8f)
            seg(17.8f, 7.2f, 17.8f, 16.8f)
            seg(3.4f, 9.4f, 3.4f, 14.6f)
            seg(20.6f, 9.4f, 20.6f, 14.6f)
        }
        TrainingPlace.HOME -> {
            // Tejado, paredes y puerta.
            seg(3.4f, 11.2f, 12f, 4.2f)
            seg(12f, 4.2f, 20.6f, 11.2f)
            seg(5.6f, 9.8f, 5.6f, 19.8f)
            seg(5.6f, 19.8f, 18.4f, 19.8f)
            seg(18.4f, 19.8f, 18.4f, 9.8f)
            seg(10.2f, 19.8f, 10.2f, 14.4f)
            seg(10.2f, 14.4f, 13.8f, 14.4f)
            seg(13.8f, 14.4f, 13.8f, 19.8f)
        }
        TrainingPlace.PUBLIC -> {
            // Pino de dos pisos con su tronco: se reconoce como árbol incluso a 24 dp.
            seg(12f, 3.2f, 7.2f, 10.4f)
            seg(7.2f, 10.4f, 9.8f, 10.4f)
            seg(9.8f, 10.4f, 5.4f, 16.6f)
            seg(5.4f, 16.6f, 18.6f, 16.6f)
            seg(18.6f, 16.6f, 14.2f, 10.4f)
            seg(14.2f, 10.4f, 16.8f, 10.4f)
            seg(16.8f, 10.4f, 12f, 3.2f)
            seg(12f, 16.6f, 12f, 20.8f)
        }
    }
}

/**
 * Marca de «hecho» que se traza de punta a punta: [progress] (0..1) es cuánto del trazo está dibujado. [radius] es el radio
 * del disco sobre el que va. Sin asignaciones: recorta la polilínea a mano.
 */
internal fun DrawScope.drawWeekCheck(center: Offset, radius: Float, progress: Float, color: Color, strokePx: Float) {
    if (progress <= 0f) return
    val ax = center.x - radius * 0.36f
    val ay = center.y + radius * 0.02f
    val bx = center.x - radius * 0.08f
    val by = center.y + radius * 0.30f
    val ex = center.x + radius * 0.40f
    val ey = center.y - radius * 0.27f
    val first = hypot(bx - ax, by - ay)
    val second = hypot(ex - bx, ey - by)
    val drawn = progress.coerceAtMost(1f) * (first + second)
    if (drawn <= first) {
        val f = drawn / first
        drawLine(color, Offset(ax, ay), Offset(ax + (bx - ax) * f, ay + (by - ay) * f), strokePx, StrokeCap.Round)
    } else {
        val f = (drawn - first) / second
        drawLine(color, Offset(ax, ay), Offset(bx, by), strokePx, StrokeCap.Round)
        drawLine(color, Offset(bx, by), Offset(bx + (ex - bx) * f, by + (ey - by) * f), strokePx, StrokeCap.Round)
    }
}

/** Chevrón «v» de [size] px de ancho centrado en [center]; [rotationDeg] lo gira (180° = hacia arriba, el selector abierto). */
internal fun DrawScope.drawWeekChevron(center: Offset, size: Float, rotationDeg: Float, color: Color, strokePx: Float) {
    val half = size / 2f
    val drop = size * 0.28f
    rotate(rotationDeg, center) {
        drawLine(color, Offset(center.x - half, center.y - drop), Offset(center.x, center.y + drop), strokePx, StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y + drop), Offset(center.x + half, center.y - drop), strokePx, StrokeCap.Round)
    }
}
