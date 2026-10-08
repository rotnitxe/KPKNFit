package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.eOutBack
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Kit de trazos de los símbolos de Entreno (lugares e implementos).
 *
 * Todos los símbolos son la misma familia que la Torre de la marca y las escenas de la bienvenida: línea fina de
 * extremos redondos, tinta cálida sobre negro y UN acento de módulo por símbolo. Cada símbolo es un [SymbolArt]:
 * una función pura del tiempo `t` (segundos del bucle) sobre un lienzo lógico propio; quien lo muestra
 * ([SymbolCanvas]) lleva el reloj, la selección y el cuadro estático. Aquí vive lo que comparten todos:
 * paleta, grosores, el «pincel» ([SymbolPen]), la figura de palitos con cinemática inversa y las curvas de tiempo.
 */

/** Paleta de los símbolos: la tinta de la marca y los acentos de módulo. */
internal object SymbolPalette {
    val ink: Color = WizardColors.text
    val musculo = Color(0xFFF49A6E)
    val energia = Color(0xFFF7CF73)
    val ok = Color(0xFF43D18C)
    val columna = Color(0xFF8FB2FF)
    val mente = Color(0xFFC9B8FF)

    /** Tinta de la marca de «hecho» sobre el disco verde. */
    val onOk = Color(0xFF08130D)

    /**
     * Opacidad de un símbolo sin seleccionar. Se aplica al símbolo ENTERO (como capa), no trazo a trazo: así donde dos
     * trazos se cruzan (articulaciones, discos sobre la barra) no se ve un punto más claro.
     */
    const val DIM = 0.38f

    /** Opacidad de los detalles secundarios (suelo, hierba, marcas) respecto a la tinta. */
    const val SOFT = 0.5f

    /** Opacidad de las ayudas de movimiento (estelas, brazos) una vez seleccionado. */
    const val MOTION = 0.6f

    /** Opacidad del lado lejano de una figura de perfil respecto al cercano (da profundidad sin más trazos). */
    const val FAR_LIMB = 0.68f
}

/** Grosores de trazo en unidades lógicas (el lienzo ya viene escalado). */
internal object SymbolStroke {
    /** Detalles finos: rayas, rayos, marcas. */
    const val FINE = 1.5f

    /** Trazo común de la familia (≈ 2 dp a 56 dp de ancho). */
    const val LINE = 2.2f

    /** Figura de palitos y estructuras principales. */
    const val BODY = 2.8f
}

// ---------------------------------------------------------------- contrato de un símbolo

/**
 * Un símbolo dibujado: función pura de `t`. [drawStatic] pinta lo que no se mueve (suelo, estructuras) y
 * [drawDynamic] lo que se mueve con el tiempo; así, al deseleccionar, solo lo móvil se funde hacia el cuadro
 * estático sin que el entorno se «duplique».
 */
internal interface SymbolArt {
    /** Lienzo lógico. */
    val width: Float
    val height: Float

    /** Acento de módulo del símbolo (solo se ve seleccionado). */
    val accent: Color

    /** Duración del bucle en segundos. */
    val period: Float

    /** Instante del bucle que se muestra cuando el símbolo no corre: su cuadro estático representativo. */
    val restT: Float

    fun drawStatic(pen: SymbolPen)
    fun drawDynamic(pen: SymbolPen, t: Float)
}

/** Base de los símbolos de implemento: lienzo cuadrado de 64 × 64 unidades. */
internal abstract class EquipmentArt(
    override val accent: Color,
    override val period: Float,
    override val restT: Float,
) : SymbolArt {
    override val width: Float get() = 64f
    override val height: Float get() = 64f
}

/** Base de las escenas de lugar: lienzo vertical de 120 × 170 unidades. */
internal abstract class SceneArt(
    override val accent: Color,
    override val period: Float,
    override val restT: Float,
) : SymbolArt {
    override val width: Float get() = 120f
    override val height: Float get() = 170f
}

// ---------------------------------------------------------------- curvas de tiempo (puras)

/** Una vuelta completa en radianes y un grado en radianes. */
internal const val TAU = (2.0 * PI).toFloat()
internal const val DEG = (PI / 180.0).toFloat()

/** Tiempo dentro de un bucle: `t` envuelto a `0 ≤ t < period`. Entradas inválidas dan 0. */
internal fun wrapLoop(t: Float, period: Float): Float {
    if (!(period > 0f) || !period.isFinite() || !t.isFinite()) return 0f
    val m = t % period
    val r = if (m < 0f) m + period else m
    return if (r >= period) 0f else r
}

/**
 * Instante del bucle de un símbolo que corre: arranca en [restT] (el cuadro estático, así no hay salto al
 * seleccionar) y avanza [elapsed] segundos.
 */
internal fun loopTime(restT: Float, elapsed: Float, period: Float): Float =
    wrapLoop(restT + (if (elapsed.isFinite()) elapsed else 0f), period)

/** Sube y baja una vez por unidad de `x`: 0 → 1 → 0, suave (coseno). */
internal fun pingPong(x: Float): Float = 0.5f - 0.5f * cos(TAU * x)

/** Elevación con pausas: sube entre [a] y [b], se queda, y baja entre [c] y [d] (0 → 1 → 0). */
internal fun riseHoldFall(t: Float, a: Float, b: Float, c: Float, d: Float): Float =
    smooth(seg(t, a, b)) - smooth(seg(t, c, d))

/** Suavizado de 0 a 1 (entrada y salida suaves). */
internal fun smooth(x: Float): Float = x * x * (3f - 2f * x)

/** El mismo color con su opacidad multiplicada por [k] (el lado lejano de una figura, estelas). */
internal fun Color.fainter(k: Float): Color = copy(alpha = alpha * k)

// ---------------------------------------------------------------- pincel

/**
 * Pincel de un símbolo. Dibuja en unidades del lienzo lógico sobre el [DrawScope] que le dan en [begin] (ya
 * escalado, así los grosores también están en unidades lógicas). Reutiliza un `Path` y sus `Stroke`: no
 * reserva memoria por cuadro una vez calentado.
 *
 * Los tonos son siempre de opacidad «plena»: quien muestra el símbolo ([SymbolCanvas]) lo atenúa como capa entera
 * mientras no está seleccionado ([SymbolPalette.DIM]). Con la selección [sel], [accent] pasa de tinta al color
 * de módulo y las ayudas de movimiento ([motion]) aparecen.
 */
internal class SymbolPen {
    internal lateinit var ds: DrawScope
    private val scratch = Path()
    private val widths = FloatArray(10)
    private val strokes = arrayOfNulls<Stroke>(10)
    private var strokeCount = 0

    /** Opacidad de grupo: multiplica la de cada primitiva (para fundir un cuadro con otro). */
    var ga = 1f

    /** Tinta principal. */
    var ink: Color = SymbolPalette.ink
        private set

    /** Detalles secundarios (suelo, rieles lejanos, marcas). */
    var soft: Color = SymbolPalette.ink
        private set

    /** El acento: tinta sin seleccionar, color de módulo seleccionado. */
    var accent: Color = SymbolPalette.ink
        private set

    /** Ayudas de movimiento (estelas, brazos): solo existen seleccionado. */
    var motion: Color = SymbolPalette.ink
        private set

    /** Pone el pincel a punto para un cuadro. */
    fun begin(scope: DrawScope, sel: Float, accentColor: Color) {
        ds = scope
        ga = 1f
        val ink0 = SymbolPalette.ink
        ink = ink0
        soft = ink0.copy(alpha = SymbolPalette.SOFT)
        accent = lerp(ink0, accentColor, sel)
        motion = ink0.copy(alpha = SymbolPalette.MOTION * sel)
    }

    private fun c(color: Color): Color = if (ga >= 1f) color else color.copy(alpha = color.alpha * ga)

    private fun stroke(w: Float): Stroke {
        for (i in 0 until strokeCount) if (widths[i] == w) return strokes[i]!!
        val s = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (strokeCount < widths.size) {
            widths[strokeCount] = w
            strokes[strokeCount] = s
            strokeCount++
        }
        return s
    }

    // ------------------------------------------------------------ trazos

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, w: Float = SymbolStroke.LINE) {
        ds.drawLine(c(color), Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)
    }

    fun line(a: Offset, b: Offset, color: Color, w: Float = SymbolStroke.LINE) {
        ds.drawLine(c(color), a, b, w, StrokeCap.Round)
    }

    /** Dos tramos unidos (codo, rodilla). */
    fun poly(a: Offset, b: Offset, e: Offset, color: Color, w: Float = SymbolStroke.LINE) {
        scratch.reset()
        scratch.moveTo(a.x, a.y)
        scratch.lineTo(b.x, b.y)
        scratch.lineTo(e.x, e.y)
        ds.drawPath(scratch, c(color), style = stroke(w))
    }

    /** Polilínea de cuatro puntos, opcionalmente cerrada. */
    fun poly4(
        x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float,
        color: Color, w: Float = SymbolStroke.LINE, close: Boolean = true,
    ) {
        scratch.reset()
        scratch.moveTo(x1, y1)
        scratch.lineTo(x2, y2)
        scratch.lineTo(x3, y3)
        scratch.lineTo(x4, y4)
        if (close) scratch.close()
        ds.drawPath(scratch, c(color), style = stroke(w))
    }

    /** Tres puntos unidos: `x1,y1 → x2,y2 → x3,y3`. */
    fun poly3(
        x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
        color: Color, w: Float = SymbolStroke.LINE,
    ) {
        scratch.reset()
        scratch.moveTo(x1, y1)
        scratch.lineTo(x2, y2)
        scratch.lineTo(x3, y3)
        ds.drawPath(scratch, c(color), style = stroke(w))
    }

    /** Curva cuadrática de `(x1,y1)` a `(x2,y2)` con control `(cx,cy)`. */
    fun curve(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float, color: Color, w: Float = SymbolStroke.LINE) {
        scratch.reset()
        scratch.moveTo(x1, y1)
        scratch.quadraticTo(cx, cy, x2, y2)
        ds.drawPath(scratch, c(color), style = stroke(w))
    }

    fun ring(cx: Float, cy: Float, r: Float, color: Color, w: Float = SymbolStroke.LINE) {
        if (r <= 0f) return
        ds.drawCircle(c(color), r, Offset(cx, cy), style = stroke(w))
    }

    fun dot(cx: Float, cy: Float, r: Float, color: Color) {
        if (r <= 0f) return
        ds.drawCircle(c(color), r, Offset(cx, cy))
    }

    /** Arco de circunferencia: ángulos en grados desde las 3 en punto, en sentido horario. */
    fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, color: Color, w: Float = SymbolStroke.LINE) {
        if (r <= 0f) return
        ds.drawArc(
            color = c(color), startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
            topLeft = Offset(cx - r, cy - r), size = Size(2f * r, 2f * r), style = stroke(w),
        )
    }

    /** Arco de elipse (la cuerda de saltar). */
    fun ovalArc(cx: Float, cy: Float, rx: Float, ry: Float, startDeg: Float, sweepDeg: Float, color: Color, w: Float = SymbolStroke.LINE) {
        if (rx <= 0f || ry <= 0f) return
        ds.drawArc(
            color = c(color), startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
            topLeft = Offset(cx - rx, cy - ry), size = Size(2f * rx, 2f * ry), style = stroke(w),
        )
    }

    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, w: Float = SymbolStroke.LINE) {
        if (rx <= 0f || ry <= 0f) return
        ds.drawOval(c(color), Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry), style = stroke(w))
    }

    /** Rectángulo de esquinas redondas, solo contorno. */
    fun box(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Color, w: Float = SymbolStroke.LINE) {
        ds.drawRoundRect(c(color), Offset(l, t), Size(r - l, b - t), CornerRadius(rad, rad), style = stroke(w))
    }

    /** Un trazo ya construido (se arma una sola vez fuera del dibujo). */
    fun path(p: Path, color: Color, w: Float = SymbolStroke.LINE) {
        ds.drawPath(p, c(color), style = stroke(w))
    }

    /**
     * El relleno de una forma ya construida: sirve para tapar lo que pasa por detrás (una piedra delante de un brazo, el hueco
     * de un brazo dibujado como tubo) con el negro de la página; nunca para colorear.
     */
    fun fillPath(p: Path, color: Color) {
        ds.drawPath(p, c(color))
    }

    /** Un rectángulo de esquinas redondas relleno (ver [fillPath]). */
    fun fillBox(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Color) {
        ds.drawRoundRect(c(color), Offset(l, t), Size(r - l, b - t), CornerRadius(rad, rad))
    }

    /** Dibuja [block] con la opacidad de grupo multiplicada por [a] (aparecer y desaparecer una pieza). */
    inline fun fade(a: Float, block: () -> Unit) {
        val old = ga
        ga = old * (if (a < 0f) 0f else if (a > 1f) 1f else a)
        if (ga > 0.003f) block()
        ga = old
    }

    // ------------------------------------------------------------ transformaciones

    inline fun shifted(dx: Float, dy: Float, block: () -> Unit) {
        ds.withTransform({ translate(dx, dy) }) { block() }
    }

    inline fun scaled(sx: Float, sy: Float, px: Float, py: Float, block: () -> Unit) {
        ds.withTransform({ scale(sx, sy, Offset(px, py)) }) { block() }
    }

    inline fun rotated(deg: Float, px: Float, py: Float, block: () -> Unit) {
        ds.withTransform({ rotate(deg, Offset(px, py)) }) { block() }
    }

    // ------------------------------------------------------------ marca de «hecho»

    /**
     * Marca de «hecho» (disco verde con una marca que se dibuja). Va en píxeles, no en unidades lógicas, para
     * medir lo mismo en todos los símbolos. [p] es el avance: el disco aparece con un pequeño rebote y la marca
     * se traza de punta a punta (recorte de la polilínea).
     */
    fun doneBadge(scope: DrawScope, cx: Float, cy: Float, r: Float, p: Float) {
        if (p <= 0.001f) return
        val grow = eOutBack(seg(p, 0f, 0.55f))
        scope.drawCircle(SymbolPalette.ok.copy(alpha = ga.coerceIn(0f, 1f)), r * grow, Offset(cx, cy))
        val k = seg(p, 0.3f, 1f)
        if (k <= 0f) return
        val w = r * 0.27f
        val ax = cx - r * 0.40f
        val ay = cy + r * 0.02f
        val bx = cx - r * 0.10f
        val by = cy + r * 0.32f
        val ex = cx + r * 0.44f
        val ey = cy - r * 0.30f
        val l1 = hypot(bx - ax, by - ay)
        val l2 = hypot(ex - bx, ey - by)
        val d = k * (l1 + l2)
        val col = SymbolPalette.onOk
        if (d <= l1) {
            val f = d / l1
            scope.drawLine(col, Offset(ax, ay), Offset(ax + (bx - ax) * f, ay + (by - ay) * f), w, StrokeCap.Round)
        } else {
            val f = (d - l1) / l2
            scope.drawLine(col, Offset(ax, ay), Offset(bx, by), w, StrokeCap.Round)
            scope.drawLine(col, Offset(bx, by), Offset(bx + (ex - bx) * f, by + (ey - by) * f), w, StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- figura de palitos

/** Medidas de una figura de palitos (en unidades del lienzo). */
internal class FigureDims(
    val thigh: Float,
    val shin: Float,
    val torso: Float,
    val uarm: Float,
    val farm: Float,
    val head: Float,
)

/**
 * Articulaciones de una figura de perfil mirando a la derecha. «N» es el lado cercano (se dibuja pleno) y «F» el
 * lejano (más tenue): así la figura gana profundidad sin más trazos.
 */
internal class Pose(
    val hip: Offset,
    val kneeN: Offset, val footN: Offset,
    val kneeF: Offset, val footF: Offset,
    val sh: Offset,
    val head: Offset,
    val elbowN: Offset, val handN: Offset,
    val elbowF: Offset, val handF: Offset,
)

/**
 * Cinemática inversa de dos segmentos: devuelve la articulación intermedia (codo, rodilla) de una cadena que
 * sale de [a], mide [l1] + [l2] y termina en [b]. [sign] elige hacia qué lado se dobla (en un perfil que mira a
 * la derecha: −1 = rodilla hacia delante, +1 = codo hacia atrás). Si [b] queda fuera de alcance, la cadena se
 * estira hacia él.
 */
internal fun ik(a: Offset, b: Offset, l1: Float, l2: Float, sign: Float): Offset {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val d = max(0.001f, min(hypot(dx, dy), l1 + l2 - 0.01f))
    val base = atan2(dy, dx)
    val al = acos(((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f))
    return Offset(a.x + l1 * cos(base + sign * al), a.y + l1 * sin(base + sign * al))
}

/** Vector unitario desde la vertical hacia abajo: `a > 0` apunta hacia delante (+x). */
internal fun downDir(a: Float): Offset = Offset(sin(a), cos(a))

/** Dibuja una figura de perfil: lado lejano, tronco, lado cercano y cabeza (aro). */
internal fun SymbolPen.figure(p: Pose, headR: Float, near: Color, far: Color, w: Float = SymbolStroke.BODY) {
    poly(p.sh, p.elbowF, p.handF, far, w)
    poly(p.hip, p.kneeF, p.footF, far, w)
    line(p.hip, p.sh, near, w)
    poly(p.hip, p.kneeN, p.footN, near, w)
    poly(p.sh, p.elbowN, p.handN, near, w)
    ring(p.head.x, p.head.y, headR, near, w * 0.9f)
}

/**
 * Pose de carrera (perfil, mira a la derecha) en la fase [ph] (radianes) con la planta más baja sobre
 * `groundY` y la cadera en `hipX`. Es la carrera de la escena del hito de Entreno, con otras medidas.
 */
internal fun runPose(d: FigureDims, ph: Float, hipX: Float, groundY: Float): Pose {
    val lean = 0.2f
    val half = PI.toFloat()
    // Pierna 0 (cercana) y pierna 1 (lejana), en oposición de fase.
    val th0 = 0.58f * sin(ph)
    val bend0 = 0.25f + 1.15f * max(0f, cos(ph))
    val knee0 = downDir(th0) * d.thigh
    val foot0 = knee0 + downDir(th0 - bend0) * d.shin
    val th1 = 0.58f * sin(ph + half)
    val bend1 = 0.25f + 1.15f * max(0f, cos(ph + half))
    val knee1 = downDir(th1) * d.thigh
    val foot1 = knee1 + downDir(th1 - bend1) * d.shin
    val low = max(foot0.y, foot1.y)
    val hip = Offset(hipX, groundY - low - 0.035f * (d.thigh + d.shin) * abs(cos(ph)))
    val sh = hip + Offset(sin(lean), -cos(lean)) * d.torso
    // Brazos en oposición a las piernas.
    val a0 = -0.65f * sin(ph + half)
    val elbow0 = sh + downDir(a0) * d.uarm
    val hand0 = elbow0 + downDir(a0 + 1.5f) * d.farm
    val a1 = -0.65f * sin(ph)
    val elbow1 = sh + downDir(a1) * d.uarm
    val hand1 = elbow1 + downDir(a1 + 1.5f) * d.farm
    return Pose(
        hip, hip + knee0, hip + foot0, hip + knee1, hip + foot1,
        sh, sh + Offset(sin(lean) * 0.9f, -cos(lean)) * (d.head * 1.55f),
        elbow0, hand0, elbow1, hand1,
    )
}

// ---------------------------------------------------------------- piezas comunes

/**
 * Mancuerna de perfil (vista del lado largo), centrada en `(cx, cy)`, de `half` unidades de semilargo y
 * girada `tiltDeg`. Las cabezas van en [heads] y el mango en [handle].
 */
internal fun SymbolPen.dumbbell(
    cx: Float, cy: Float, half: Float, tiltDeg: Float, heads: Color, handle: Color, w: Float = SymbolStroke.LINE,
) {
    val hh = half * 0.62f
    val oh = half * 0.38f
    rotated(tiltDeg, cx, cy) {
        line(cx - half * 0.46f, cy, cx + half * 0.46f, cy, handle, w)
        box(cx - half * 0.72f, cy - hh, cx - half * 0.46f, cy + hh, 1.6f, heads, w)
        box(cx + half * 0.46f, cy - hh, cx + half * 0.72f, cy + hh, 1.6f, heads, w)
        box(cx - half, cy - oh, cx - half * 0.78f, cy + oh, 1.4f, heads, w)
        box(cx + half * 0.78f, cy - oh, cx + half, cy + oh, 1.4f, heads, w)
    }
}

/** Disco de pesa visto de frente (la barra le atraviesa el centro): aro, aro interior y buje. */
internal fun SymbolPen.plateFace(cx: Float, cy: Float, r: Float, rim: Color, hub: Color, w: Float = SymbolStroke.LINE) {
    ring(cx, cy, r, rim, w)
    ring(cx, cy, r * 0.56f, rim, w * 0.7f)
    dot(cx, cy, max(1.1f, r * 0.14f), hub)
}
