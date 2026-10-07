package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.kpkn.screens.onboarding.design.WizardColors
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Kit de las figuras de palitos de los levantamientos y de los ejercicios de peso corporal: geometría pura
 * (cinemática inversa de dos segmentos, pose reutilizable) y el «pincel» que las dibuja. La misma familia que las
 * escenas de la bienvenida y los símbolos de Entreno: línea fina de extremos redondos, tinta cálida sobre negro,
 * lado lejano más tenue, cabeza en aro.
 */

/** Medidas de la figura en unidades del lienzo lógico (una figura de pie mide ≈ 53). */
internal object FigGeo {
    const val THIGH = 12.5f
    const val SHIN = 12.5f
    const val TORSO = 17f
    const val UARM = 8.6f
    const val FARM = 8.2f
    const val HEAD_R = 4.7f
    const val NECK = 2.2f

    /** Pierna estirada y brazo estirado. */
    const val LEG = THIGH + SHIN
    const val ARM = UARM + FARM

    /**
     * Vector unitario «hacia arriba» girado [angle] radianes hacia +x (ángulo > 0 inclina hacia delante). Con
     * `angle = π/2` apunta a la derecha; con `π`, hacia abajo.
     */
    fun up(angle: Float): Offset = Offset(sin(angle), -cos(angle))

    /**
     * Cinemática inversa de dos segmentos: la articulación intermedia (rodilla, codo) de una cadena que sale de [a],
     * mide [l1] + [l2] y acaba en [b]. [sign] elige hacia qué lado se dobla respecto del segmento a→b:
     * con el pie debajo de la cadera, −1 lleva la rodilla hacia delante (+x); con la mano arriba del hombro, +1 lleva
     * el codo hacia delante. Si [b] queda fuera de alcance la cadena se estira hacia él.
     */
    fun ik(a: Offset, b: Offset, l1: Float, l2: Float, sign: Float): Offset {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d = max(0.001f, min(hypot(dx, dy), l1 + l2 - 0.01f))
        val base = atan2(dy, dx)
        val al = acos(((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f))
        return Offset(a.x + l1 * cos(base + sign * al), a.y + l1 * sin(base + sign * al))
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun lerp(a: Offset, b: Offset, t: Float): Offset = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    /** Entrada y salida suaves de 0 a 1. */
    fun smooth(x: Float): Float {
        val c = if (x < 0f) 0f else if (x > 1f) 1f else x
        return c * c * (3f - 2f * c)
    }

    /** `x` acotado a 0..1. */
    fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x
}

/**
 * Articulaciones de una figura de perfil mirando a la derecha. «N» es el lado cercano (pleno) y «F» el lejano (más
 * tenue). Se reutiliza: los resolutores escriben aquí y nadie reserva memoria por cuadro.
 */
internal class FigPose {
    var hip = Offset.Zero
    var kneeN = Offset.Zero
    var footN = Offset.Zero
    var kneeF = Offset.Zero
    var footF = Offset.Zero
    var shoulder = Offset.Zero
    var head = Offset.Zero
    var elbowN = Offset.Zero
    var handN = Offset.Zero
    var elbowF = Offset.Zero
    var handF = Offset.Zero

    /** Centro del disco de la barra (solo en levantamientos). */
    var bar = Offset.Unspecified

    /** Altura del banco (solo en press de banca); `NaN` si no hay. */
    var bench = Float.NaN

    /**
     * Resuelve la figura: tronco desde [hipP] con inclinación [lean] (rad, + = hacia delante), piernas y brazos por
     * cinemática inversa hacia los pies y las manos. [elbowSign] elige hacia dónde se doblan los codos y [headTilt]
     * inclina la cabeza respecto del tronco. Si [elbowN] es especificado, el brazo cercano usa ese codo (relativo al
     * hombro) en lugar de la cinemática inversa.
     */
    fun solve(
        hipP: Offset,
        lean: Float,
        footNP: Offset,
        footFP: Offset,
        handNP: Offset,
        handFP: Offset,
        elbowSign: Float,
        headTilt: Float = 0f,
        elbowRel: Offset = Offset.Unspecified,
    ) {
        hip = hipP
        shoulder = hipP + FigGeo.up(lean) * FigGeo.TORSO
        head = shoulder + FigGeo.up(lean + headTilt) * (FigGeo.NECK + FigGeo.HEAD_R)
        footN = footNP
        footF = footFP
        kneeN = FigGeo.ik(hipP, footNP, FigGeo.THIGH, FigGeo.SHIN, -1f)
        kneeF = FigGeo.ik(hipP, footFP, FigGeo.THIGH, FigGeo.SHIN, -1f)
        handN = handNP
        handF = handFP
        if (elbowRel.isSpecified) {
            elbowN = shoulder + elbowRel
            elbowF = shoulder + Offset(elbowRel.x + 0.8f, elbowRel.y - 0.2f)
        } else {
            elbowN = FigGeo.ik(shoulder, handNP, FigGeo.UARM, FigGeo.FARM, elbowSign)
            elbowF = FigGeo.ik(shoulder, handFP, FigGeo.UARM, FigGeo.FARM, elbowSign)
        }
    }
}

/** Paleta y grosores de las figuras (en unidades lógicas; el lienzo ya viene escalado). */
internal object FigStyle {
    val ink: Color = WizardColors.text

    /** Acento del módulo músculo. */
    val muscle: Color = Color(0xFFF49A6E)

    /** Verde de «hecho». */
    val ok: Color = Color(0xFF43D18C)

    /** Opacidad del lado lejano de la figura. */
    const val FAR = 0.68f

    /** Opacidad de los detalles del entorno (suelo, postes, banco). */
    const val SOFT = 0.42f

    /** Opacidad del disco de la barra, que va detrás del cuerpo. */
    const val PLATE = 0.55f

    /** Grosor de la figura y de la barra de dominadas. */
    const val BODY = 2.6f
    const val BAR = 2.4f
    const val FINE = 1.4f
}

/**
 * Pincel de las figuras. Dibuja en unidades del lienzo lógico sobre el [DrawScope] ya escalado y reutiliza un
 * `Path` y sus `Stroke`: no reserva memoria por cuadro una vez calentado.
 */
internal class FigPen {
    lateinit var ds: DrawScope
    private val scratch = Path()
    private val widths = FloatArray(10)
    private val strokes = arrayOfNulls<Stroke>(10)
    private var strokeCount = 0

    fun begin(scope: DrawScope) {
        ds = scope
    }

    /** El `Stroke` de ancho [w], creado una sola vez (nada de cajas `Float` en un mapa: se llama en cada cuadro). */
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

    fun line(a: Offset, b: Offset, color: Color, w: Float = FigStyle.BODY) {
        ds.drawLine(color, a, b, w, StrokeCap.Round)
    }

    /** Dos tramos unidos (rodilla, codo). */
    fun poly(a: Offset, b: Offset, c: Offset, color: Color, w: Float = FigStyle.BODY) {
        scratch.reset()
        scratch.moveTo(a.x, a.y)
        scratch.lineTo(b.x, b.y)
        scratch.lineTo(c.x, c.y)
        ds.drawPath(scratch, color, style = stroke(w))
    }

    fun ring(center: Offset, r: Float, color: Color, w: Float = FigStyle.BODY) {
        if (r <= 0f) return
        ds.drawCircle(color, r, center, style = stroke(w))
    }

    fun dot(center: Offset, r: Float, color: Color) {
        if (r <= 0f) return
        ds.drawCircle(color, r, center)
    }

    /**
     * Dibuja la figura: lado lejano, tronco, lado cercano y cabeza. La cabeza va con el fondo negro de la página
     * por debajo (tapa lo que pasa por detrás: brazos que suben junto a la cara, disco de la barra).
     */
    fun figure(p: FigPose, ink: Color, w: Float = FigStyle.BODY, farAlpha: Float = FigStyle.FAR, backdrop: Color = Color.Black) {
        val far = ink.copy(alpha = ink.alpha * farAlpha)
        poly(p.shoulder, p.elbowF, p.handF, far, w)
        poly(p.hip, p.kneeF, p.footF, far, w)
        line(p.hip, p.shoulder, ink, w)
        poly(p.hip, p.kneeN, p.footN, ink, w)
        poly(p.shoulder, p.elbowN, p.handN, ink, w)
        dot(p.head, FigGeo.HEAD_R + w * 0.4f, backdrop)
        ring(p.head, FigGeo.HEAD_R, ink, w * 0.85f)
    }

    /** Disco de pesa visto de lado (la barra le atraviesa el centro): aro, aro interior y buje. */
    fun plate(center: Offset, r: Float, ink: Color, alpha: Float = FigStyle.PLATE) {
        ring(center, r, ink.copy(alpha = ink.alpha * alpha), 1.5f)
        ring(center, r * 0.56f, ink.copy(alpha = ink.alpha * alpha * 0.85f), 1.1f)
        dot(center, 1.3f, ink.copy(alpha = ink.alpha * min(1f, alpha + 0.2f)))
    }
}

/**
 * Reloj de las figuras: segundos desde que empezó, envueltos cada [WRAP] segundos para que el `Float` no pierda
 * precisión. Solo corre si [active]; si no, se queda en 0 (el cuadro de reposo lo decide quien dibuja). Usa
 * `withInfiniteAnimationFrameNanos` (y no `withFrameNanos`): se detiene con la app en segundo plano y las pruebas de
 * Compose cancelan el bucle, así `waitForIdle()` sigue volviendo.
 */
@Composable
internal fun rememberFigClock(active: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        time.floatValue = 0f
        var start = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0L) start = now
                time.floatValue = ((now - start) / 1_000_000_000.0 % FIG_CLOCK_WRAP).toFloat()
            }
        }
    }
    return time
}

/** Cada cuántos segundos vuelve a 0 el reloj de las figuras (múltiplo de todos los periodos de movimiento). */
internal const val FIG_CLOCK_WRAP = 60.0
