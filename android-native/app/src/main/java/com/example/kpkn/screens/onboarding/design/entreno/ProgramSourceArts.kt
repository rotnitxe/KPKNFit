package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.kpkn.domain.onboarding.ProgramSource
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Símbolos de «¿Cómo quieres tu programa?» (lienzo de 64 × 64, la familia de la torre de la marca): una pasada, no un bucle.
 * Sin elegir se ve el cuadro de reposo; elegido, la animación corre una vez y se queda en su cuadro final ([HOLD_PERIOD] hace
 * que el reloj compartido no vuelva a empezar). Con «reducir movimiento» el cuadro de reposo ES el final ([programSourceArtOf]).
 */

/** Periodo tan largo que una pasada nunca vuelve a empezar: la animación se queda en su cuadro final. */
private const val HOLD_PERIOD = 86_400f

/** Fin de la pasada de la torre (tres tramos de 0,6 s, uno por anillo) y del lápiz (dos renglones). */
private const val TOWER_END = 1.8f
private const val PENCIL_END = 1.7f

/** Periodo del bucle de la hoja que se desliza en el teléfono (solo se ofrece con la importación disponible). */
private const val IMPORT_PERIOD = 3.2f

/** Un anillo de la torre: su centro vertical, sus radios y su color de módulo. */
private class TowerRing(val cy: Float, val rx: Float, val ry: Float, val color: Color)

/** Abajo, columna; en medio, músculo; arriba, energía (la misma asignación que el logo y los RINGS). */
private val TOWER_RINGS = listOf(
    TowerRing(cy = 46f, rx = 20f, ry = 7f, color = SymbolPalette.columna),
    TowerRing(cy = 32f, rx = 16f, ry = 5.6f, color = SymbolPalette.musculo),
    TowerRing(cy = 19f, rx = 12f, ry = 4.2f, color = SymbolPalette.energia),
)

/**
 * La torre de KPKN: tres elipses apiladas. Sin elegir son tres pistas tenues; elegida, cada anillo se enciende en su turno,
 * de abajo arriba (0,6 s cada uno), con un arco de progreso que da la vuelta entera y se queda lleno. Sin resplandor.
 */
internal class ProgramTowerArt(lit: Boolean) : EquipmentArt(SymbolPalette.energia, HOLD_PERIOD, if (lit) TOWER_END else 0f) {
    /** Un solo trazo reutilizado en cada cuadro (sin reservar memoria por cuadro). */
    private val arc = Path()

    override fun drawStatic(pen: SymbolPen) {
        for (ring in TOWER_RINGS) pen.oval(32f, ring.cy, ring.rx, ring.ry, pen.soft, SymbolStroke.LINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        for ((i, ring) in TOWER_RINGS.withIndex()) {
            val progress = smooth(seg(t, i * 0.6f, (i + 1) * 0.6f))
            if (progress > 0.001f) pen.ellipseArc(arc, 32f, ring.cy, ring.rx, ring.ry, 360f * progress, ring.color, SymbolStroke.BODY)
        }
    }
}

/** Arco de progreso de una elipse: arranca arriba y recorre [sweepDeg] en sentido horario. */
private fun SymbolPen.ellipseArc(arc: Path, cx: Float, cy: Float, rx: Float, ry: Float, sweepDeg: Float, color: Color, w: Float) {
    if (sweepDeg < 0.5f) return
    arc.reset()
    val steps = max(2, (sweepDeg / 6f).toInt())
    for (k in 0..steps) {
        val a = (-90f + sweepDeg * k / steps) * DEG
        val x = cx + rx * cos(a)
        val y = cy + ry * sin(a)
        if (k == 0) arc.moveTo(x, y) else arc.lineTo(x, y)
    }
    path(arc, color, w)
}

/**
 * Una hoja en blanco inclinada y un lápiz que traza dos renglones y se detiene. Sin elegir, la hoja vacía y el lápiz al inicio
 * del primer renglón; elegida, traza el primero, pasa al segundo y se queda al final de los dos.
 */
internal class ProgramSheetArt(lit: Boolean) : EquipmentArt(SymbolPalette.mente, HOLD_PERIOD, if (lit) PENCIL_END else 0f) {
    override fun drawStatic(pen: SymbolPen) {
        pen.rotated(SHEET_TILT, SHEET_PIVOT_X, SHEET_PIVOT_Y) {
            pen.box(14f, 8f, 46f, 56f, 3f, pen.soft, SymbolStroke.LINE)
        }
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        val first = smooth(seg(t, 0.1f, 0.8f))
        val second = smooth(seg(t, 1.0f, PENCIL_END))
        // La punta: sigue el primer renglón, salta al inicio del segundo y sigue el segundo hasta que se detiene.
        val tipX: Float
        val tipY: Float
        when {
            t < 0.8f -> { tipX = 22f + 16f * first; tipY = 24f }
            t < 1f -> {
                val move = seg(t, 0.8f, 1f)
                tipX = lerpF(38f, 22f, move)
                tipY = lerpF(24f, 34f, move)
            }
            else -> { tipX = 22f + 12f * second; tipY = 34f }
        }
        pen.rotated(SHEET_TILT, SHEET_PIVOT_X, SHEET_PIVOT_Y) {
            if (first > 0f) pen.line(22f, 24f, 22f + 16f * first, 24f, pen.accent, SymbolStroke.LINE)
            if (second > 0f) pen.line(22f, 34f, 22f + 12f * second, 34f, pen.accent, SymbolStroke.LINE)
            pen.line(tipX, tipY, tipX + 6f, tipY - 6f, pen.accent, SymbolStroke.BODY)
            pen.dot(tipX, tipY, 1.4f, pen.ink)
        }
    }
}

private const val SHEET_TILT = -6f
private const val SHEET_PIVOT_X = 30f
private const val SHEET_PIVOT_Y = 32f

/**
 * Una hoja con cuadrícula de hoja de cálculo que se desliza dentro del contorno de un teléfono (bucle de 3,2 s). Solo se ofrece
 * cuando la importación está disponible.
 */
internal class ProgramImportArt : EquipmentArt(SymbolPalette.columna, IMPORT_PERIOD, 0f) {
    override fun drawStatic(pen: SymbolPen) {
        pen.box(16f, 6f, 48f, 58f, 5f, pen.soft, SymbolStroke.LINE)
        pen.line(29f, 11f, 35f, 11f, pen.soft, SymbolStroke.FINE)
        pen.line(28f, 53f, 36f, 53f, pen.soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        // Vaivén entre los bordes interiores del teléfono: la hoja mide 16 de 32 unidades y nunca los cruza.
        val left = 17f + 14f * pingPong(wrapLoop(t, IMPORT_PERIOD) / IMPORT_PERIOD)
        val right = left + 16f
        pen.box(left, 16f, right, 44f, 1.5f, pen.accent, SymbolStroke.LINE)
        pen.line(left, 22f, right, 22f, pen.accent, SymbolStroke.FINE)
        pen.line(left, 28f, right, 28f, pen.accent, SymbolStroke.FINE)
        pen.line(left, 34f, right, 34f, pen.accent, SymbolStroke.FINE)
        pen.line(left, 40f, right, 40f, pen.accent, SymbolStroke.FINE)
        pen.line(left + 5.3f, 16f, left + 5.3f, 44f, pen.accent, SymbolStroke.FINE)
        pen.line(left + 10.7f, 16f, left + 10.7f, 44f, pen.accent, SymbolStroke.FINE)
    }
}

/** Los tres símbolos en vivo: sin elegir y elegidos se animan una pasada (o bucle, la importación). */
private val LIVE_ARTS: Map<ProgramSource, SymbolArt> = mapOf(
    ProgramSource.RECOMMEND to ProgramTowerArt(lit = false),
    ProgramSource.LATER to ProgramSheetArt(lit = false),
    ProgramSource.IMPORT to ProgramImportArt(),
)

/** Los mismos símbolos en su cuadro final, para «reducir movimiento» (sin bucles). */
private val REDUCED_ARTS: Map<ProgramSource, SymbolArt> = mapOf(
    ProgramSource.RECOMMEND to ProgramTowerArt(lit = true),
    ProgramSource.LATER to ProgramSheetArt(lit = true),
    ProgramSource.IMPORT to ProgramImportArt(),
)

/** El dibujo de una opción del paso de programa; con [reduced], su cuadro final estático. */
internal fun programSourceArtOf(source: ProgramSource, reduced: Boolean): SymbolArt =
    (if (reduced) REDUCED_ARTS else LIVE_ARTS).getValue(source)
