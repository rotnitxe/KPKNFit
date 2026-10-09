package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.cos
import kotlin.math.sin

/*
 * Símbolos de las preguntas de RINGS (E3d): los días, los ejercicios principales, la intensidad y las sensaciones. Son de la
 * misma familia que `EntrenoSymbolKit` (tinta cálida, un acento por símbolo, trazo fino) y se pintan con `SymbolCanvas` o
 * `SymbolCell`. Lo que no se mueve va en `drawStatic`; lo que se mueve, en `drawDynamic`, y solo corre con el símbolo elegido.
 */

/** Centro del lienzo de 64 unidades. */
private const val CX = 32f
private const val CY = 32f

/** Radio del anillo de las escalas de sensación. */
private const val RING_R = 22f

/** Suelo de la figura sentada. */
private const val REST_FLOOR_Y = 54f

/** Amplitud de la respiración de la figura sentada, en unidades del lienzo. */
private const val BREATH_LIFT = 0.8f

/** Barra de la intensidad: centro, altura del centro, semilargo y semialto de los discos. */
private const val BAR_CX = 32f
private const val BAR_Y = 38f
private const val BAR_HALF = 25f
private const val PLATE_HALF = 7.5f

/** Gotas de esfuerzo (solo «Muy dura»): radio, caída en su ciclo y desfase entre ellas. */
private const val DROP_R = 2.2f
private const val DROP_FALL = 6f
private const val DROP_STEP = 0.33f
private val DROP_X = floatArrayOf(16f, 48f, 32f)
private val DROP_Y = floatArrayOf(12f, 10f, 5f)

/**
 * La «?» trazada: un gancho de unas 21 unidades de alto centrado en (`cx`, `cy`). [progress] de 0 a 1 la dibuja en orden: el
 * gancho, el palito hasta el centro y, al final, el punto. Con 0 no dibuja nada.
 */
private fun SymbolPen.questionMark(cx: Float, cy: Float, progress: Float, color: Color) {
    val sweep = 180f * seg(progress, 0f, 0.7f)
    if (sweep > 0.5f) arc(cx, cy - 6.5f, 6f, 180f, sweep, color, SymbolStroke.BODY)
    val stem = seg(progress, 0.7f, 0.9f)
    if (stem > 0.01f) {
        // El palito nace en el extremo derecho del gancho (las tres) y baja hacia el centro.
        line(cx + 6f, cy - 6.5f, cx + 6f * (1f - stem), cy - 6.5f + 7.5f * stem, color, SymbolStroke.BODY)
    }
    val dotK = seg(progress, 0.9f, 1f)
    if (dotK > 0.01f) dot(cx, cy + 6.5f, 2.4f * dotK, color)
}

/** Pose de la figura sentada en el suelo, de perfil y mirando a la derecha. [lift] sube hombros y cabeza (la respiración). */
private fun sittingPose(lift: Float): Pose = Pose(
    hip = Offset(24f, 53f),
    kneeN = Offset(34f, 38f),
    footN = Offset(42f, 53f),
    kneeF = Offset(38f, 40f),
    footF = Offset(47f, 53f),
    sh = Offset(20f, 35f - lift),
    head = Offset(18.6f, 28.6f - lift),
    elbowN = Offset(25f, 44f),
    handN = Offset(32f, 38f),
    elbowF = Offset(22f, 44f),
    handF = Offset(29f, 40f),
)

/** «No entrené»: una figura sentada en el suelo, con las rodillas dobladas y los brazos sobre ellas. Respira despacio. */
internal object RingsRestArt : EquipmentArt(SymbolPalette.ok, period = 3.2f, restT = 0f) {
    override fun drawStatic(pen: SymbolPen) {
        pen.line(6f, REST_FLOOR_Y, 58f, REST_FLOOR_Y, pen.soft, SymbolStroke.FINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        // En el cuadro estático (t = 0) la respiración no se mueve.
        val breath = sin(TAU * t / period) * BREATH_LIFT
        pen.figure(
            sittingPose(breath),
            headR = 3.6f,
            near = pen.accent,
            far = pen.accent.fainter(SymbolPalette.FAR_LIMB),
        )
    }
}

/** «No lo recuerdo»: un calendario con el signo de interrogación trazado en la hoja. */
internal object RingsCalendarArt : EquipmentArt(SymbolPalette.mente, period = 3f, restT = 2f) {
    override fun drawStatic(pen: SymbolPen) {
        pen.box(12f, 16f, 52f, 54f, 5f, pen.ink, SymbolStroke.LINE)
        pen.line(12f, 26f, 52f, 26f, pen.ink, SymbolStroke.LINE)
        pen.line(23f, 10f, 23f, 20f, pen.ink, SymbolStroke.LINE)
        pen.line(41f, 10f, 41f, 20f, pen.ink, SymbolStroke.LINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        pen.questionMark(CX, 40f, seg(t, 0f, 1.1f), pen.accent)
    }
}

/** «No lo sé» en los ejercicios principales: un círculo con el signo de interrogación trazado dentro. */
internal object RingsUnknownArt : EquipmentArt(SymbolPalette.mente, period = 3f, restT = 2f) {
    override fun drawStatic(pen: SymbolPen) {
        pen.ring(CX, CY, 21f, pen.ink, SymbolStroke.LINE)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        pen.questionMark(CX, CY, seg(t, 0f, 1.1f), pen.accent)
    }
}

/** «Ninguno de estos»: un círculo cruzado por una barra, el conjunto vacío. Estático. */
internal object RingsNoneArt : EquipmentArt(SymbolPalette.ink, period = 1f, restT = 0f) {
    override fun drawStatic(pen: SymbolPen) {
        pen.ring(CX, CY, 21f, pen.ink, SymbolStroke.LINE)
        pen.line(19f, 45f, 45f, 19f, pen.ink, SymbolStroke.BODY)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) = Unit
}

/**
 * Intensidad: una barra con discos que se dobla hacia arriba por los extremos ([bendDeg] grados). Con [drops], unas gotas de
 * esfuerzo caen sobre ella. El acento es la energía de la marca.
 */
internal class RingsBarbellArt(bendDeg: Float, private val drops: Boolean) :
    EquipmentArt(SymbolPalette.energia, period = 2.4f, restT = 0f) {
    private val cb = cos(bendDeg * DEG)
    private val sb = sin(bendDeg * DEG)

    override fun drawStatic(pen: SymbolPen) {
        val lx = BAR_CX - BAR_HALF * cb
        val rx = BAR_CX + BAR_HALF * cb
        val ey = BAR_Y - BAR_HALF * sb
        pen.poly3(lx, ey, BAR_CX, BAR_Y, rx, ey, pen.ink, SymbolStroke.LINE)
        // Un disco en cada extremo, perpendicular a la barra y en el acento del símbolo.
        plate(pen, lx, ey, sb, -cb)
        plate(pen, rx, ey, sb, cb)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        if (!drops) return
        for (i in DROP_X.indices) {
            val k = (t / period + i * DROP_STEP) % 1f
            pen.dot(DROP_X[i], DROP_Y[i] + DROP_FALL * k, DROP_R, SymbolPalette.columna.copy(alpha = 1f - k))
        }
    }

    /** Un disco centrado en (x, y) y orientado con el vector perpendicular (px, py). */
    private fun plate(pen: SymbolPen, x: Float, y: Float, px: Float, py: Float) {
        pen.line(x - px * PLATE_HALF, y - py * PLATE_HALF, x + px * PLATE_HALF, y + py * PLATE_HALF, pen.accent, SymbolStroke.BODY)
    }
}

/** Intensidad «Suave»: la barra recta. */
internal val RingsEasyBar: SymbolArt = RingsBarbellArt(bendDeg = 0f, drops = false)

/** Intensidad «Moderada»: la barra se dobla 4°. */
internal val RingsModerateBar: SymbolArt = RingsBarbellArt(bendDeg = 4f, drops = false)

/** Intensidad «Dura»: la barra se dobla 9°. */
internal val RingsHardBar: SymbolArt = RingsBarbellArt(bendDeg = 9f, drops = false)

/** Intensidad «Muy dura»: la barra se dobla 15° y caen unas gotas de esfuerzo. */
internal val RingsVeryHardBar: SymbolArt = RingsBarbellArt(bendDeg = 15f, drops = true)

/**
 * Una escala de sensación: el anillo de un canal de la torre con el arco lleno al [fraction] (1, 0,75, 0,5, 0,3 o 0,1). Solo
 * el elegido se anima: una onda de su color sale del anillo y se desvanece.
 */
internal class RingsLevelArt(fraction: Float, ringColor: Color) : EquipmentArt(ringColor, period = 1.8f, restT = 1.79f) {
    private val fill = fraction.coerceIn(0f, 1f)

    override fun drawStatic(pen: SymbolPen) {
        pen.ring(CX, CY, RING_R, pen.soft, SymbolStroke.LINE)
        if (fill > 0f) pen.arc(CX, CY, RING_R, -90f, 360f * fill, accent, SymbolStroke.BODY)
    }

    override fun drawDynamic(pen: SymbolPen, t: Float) {
        val p = (t / period).coerceIn(0f, 1f)
        val alpha = (1f - p) * (1f - p) * 0.55f
        if (alpha > 0.01f) {
            pen.ring(CX, CY, RING_R * (1f + 0.4f * eOutCubic(p)), accent.copy(alpha = alpha), SymbolStroke.FINE)
        }
    }
}

/**
 * Un músculo de [MuscleArt] estático, de 56 dp, para el paso de series por grupo: la silueta tenue y la región del músculo en
 * el acento de Músculos. Es decorativo: el nombre del grupo va al lado.
 */
@Composable
internal fun RingsMuscleGlyph(muscle: MuscleSymbol, modifier: Modifier = Modifier) {
    val shape = remember(muscle) { MuscleArt.shape(muscle) }
    Canvas(modifier.clipToBounds().clearAndSetSemantics { }) {
        drawRingsMuscle(shape)
    }
}

/** El desvanecido de la silueta: opaca en el centro del encuadre y transparente hacia su borde (el de la cuadrícula de músculos). */
private fun silhouetteFade(w: MuscleWindow): Brush {
    val ink = WizardColors.textFaint
    return Brush.radialGradient(
        colorStops = arrayOf(0f to ink, 0.62f to ink, 1f to ink.copy(alpha = 0f)),
        center = Offset(w.cx, w.cy),
        radius = w.size * 0.56f,
    )
}

private fun DrawScope.drawRingsMuscle(shape: MuscleShape) {
    val window = shape.spec.window
    val s = size.minDimension / window.size
    // Los trazos van en unidades del cuerpo: el lienzo se escala por `s` y el grosor en dp vuelve a medir lo mismo.
    val thin = Stroke(width = 1.dp.toPx() / s)
    val fiber = Stroke(width = 0.8.dp.toPx() / s)
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(s, s, Offset.Zero)
        translate(-window.cx, -window.cy)
    }) {
        // La silueta se desvanece hacia el borde de la ventana, como en la cuadrícula de músculos: el músculo llena la pieza.
        drawPath(MuscleArt.silhouette(shape.spec.view), silhouetteFade(window), alpha = 0.6f, style = thin)
        drawPath(shape.region, SymbolPalette.musculo.copy(alpha = 0.86f))
        drawPath(shape.detailAll, WizardColors.text.copy(alpha = 0.28f), style = fiber)
    }
}
