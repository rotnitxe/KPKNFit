package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolStroke
import com.example.kpkn.screens.onboarding.design.entreno.dumbbell
import com.example.kpkn.screens.onboarding.design.lerpF

/*
 * Dibujo del tablero de la semana, en la misma familia de trazo que los símbolos de lugar y de material (el kit de
 * `design/entreno/`): la mancuerna de una sesión, la chispa de la sesión principal y el disco de una ranura. Todo
 * en unidades de un lienzo lógico de 64 × 64 que se escala al tamaño del disco.
 */

/** Lado del lienzo lógico del glifo. */
internal const val SESSION_GLYPH_UNITS = 64f

/** Inclinación de la mancuerna (grados, sentido horario): el extremo derecho baja y deja libre la esquina de la chispa. */
private const val SESSION_GLYPH_TILT = 34f

/**
 * La mancuerna de una sesión: mango de tinta y discos en el acento de músculo (el pincel ya está en la escala del lienzo
 * lógico y con su acento puesto).
 */
internal fun SymbolPen.sessionDumbbell() {
    dumbbell(
        cx = 32f, cy = 33f, half = 21f, tiltDeg = SESSION_GLYPH_TILT,
        heads = accent, handle = ink, w = SymbolStroke.LINE + 0.2f,
    )
}

/** Estrella cóncava de cuatro puntas y radio 1: la «chispa». Se construye una sola vez y se reutiliza. */
internal fun buildSparkPath(): Path = Path().apply {
    val waist = 0.16f
    moveTo(0f, -1f)
    quadraticTo(waist, -waist, 1f, 0f)
    quadraticTo(waist, waist, 0f, 1f)
    quadraticTo(-waist, waist, -1f, 0f)
    quadraticTo(-waist, -waist, 0f, -1f)
    close()
}

private fun DrawScope.drawSpark(path: Path, cx: Float, cy: Float, radius: Float, color: Color) {
    withTransform({
        translate(cx, cy)
        scale(radius, radius, Offset.Zero)
    }) {
        drawPath(path, color)
    }
}

/**
 * El glifo de una ficha sobre un lienzo cuadrado: [ring] (0..1) dibuja su propio disco con filete de tinta (cuando está
 * levantada o elegida) y, si es la sesión principal, las chispas de energía en la esquina.
 */
internal fun DrawScope.drawFichaGlyph(pen: SymbolPen, sparkPath: Path, isMain: Boolean, ring: Float) {
    val side = size.minDimension
    val c = center
    if (ring > 0.01f) {
        val r = side / 2f - 1.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.08f * ring), r, c)
        drawCircle(SymbolPalette.ink.copy(alpha = 0.78f * ring), r, c, style = Stroke(1.5.dp.toPx()))
    }
    val k = side / SESSION_GLYPH_UNITS
    pen.begin(this, 1f, SymbolPalette.musculo)
    withTransform({ scale(k, k, Offset.Zero) }) {
        pen.sessionDumbbell()
    }
    if (isMain) {
        drawSpark(sparkPath, 49f * k, 15f * k, 7f * k, SymbolPalette.energia)
        drawSpark(sparkPath, 57.5f * k, 27f * k, 3.2f * k, SymbolPalette.energia)
    }
}

/**
 * El disco de una ranura (el «sitio» de un día). Reposo: un filete tenue y, si el día es de descanso, un guion; con una
 * sesión encima se rellena de vidrio neutro. Los cuatro estados (0..1) se mezclan:
 *  - [occupied]: hay una ficha en este día;
 *  - [hover]: la ficha que se arrastra está encima (el disco se agranda y se enciende);
 *  - [ready]: hay una sesión elegida y este día puede recibirla (filete de tinta);
 *  - [ghost]: de aquí se levantó la ficha que se arrastra (queda su silueta).
 *
 * [railBefore] y [railAfter] son lo que mide, en píxeles, el hilo de la semana a cada lado del disco (0 = sin hilo).
 */
internal fun DrawScope.drawSlotDisc(
    pen: SymbolPen,
    dash: PathEffect,
    occupied: Float,
    hover: Float,
    ready: Float,
    ghost: Float,
    railBefore: Float = 0f,
    railAfter: Float = 0f,
) {
    val c = center
    val side = size.minDimension
    val r = side / 2f - 1.dp.toPx()
    val grow = 1f + 0.08f * hover
    val ink = SymbolPalette.ink
    // El riel (en píxeles de largo hacia cada lado, fuera del disco): hilo tenue que une los siete días.
    val railGap = 4.dp.toPx()
    val railColor = Color.White.copy(alpha = 0.1f)
    if (railBefore > 0f) drawLine(railColor, Offset(c.x - r - railBefore, c.y), Offset(c.x - r - railGap, c.y), 1.dp.toPx())
    if (railAfter > 0f) drawLine(railColor, Offset(c.x + r + railGap, c.y), Offset(c.x + r + railAfter, c.y), 1.dp.toPx())
    withTransform({ scale(grow, grow, c) }) {
        val fill = lerpF(0f, 0.06f, occupied) + 0.07f * hover
        if (fill > 0.001f) drawCircle(Color.White.copy(alpha = fill), r, c)
        drawCircle(Color.White.copy(alpha = lerpF(0.09f, 0.14f, occupied)), r, c, style = Stroke(1.dp.toPx()))
        if (ready > 0.01f) drawCircle(ink.copy(alpha = 0.42f * ready), r, c, style = Stroke(1.5.dp.toPx()))
        if (hover > 0.01f) drawCircle(ink.copy(alpha = 0.92f * hover), r, c, style = Stroke(1.5.dp.toPx()))
        val rest = (1f - occupied) * (1f - ghost)
        if (rest > 0.01f) {
            val half = 7.dp.toPx()
            drawLine(ink.copy(alpha = 0.36f * rest), Offset(c.x - half, c.y), Offset(c.x + half, c.y), 2.dp.toPx(), StrokeCap.Round)
        }
        if (ghost > 0.01f) {
            drawCircle(ink.copy(alpha = 0.42f * ghost), r, c, style = Stroke(1.dp.toPx(), pathEffect = dash))
            val k = side / SESSION_GLYPH_UNITS
            pen.begin(this, 1f, SymbolPalette.musculo)
            pen.ga = 0.24f * ghost
            withTransform({ scale(k, k, Offset.Zero) }) { pen.sessionDumbbell() }
            pen.ga = 1f
        }
    }
}
