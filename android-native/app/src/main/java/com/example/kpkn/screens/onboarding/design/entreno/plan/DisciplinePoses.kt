package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.onboarding.design.entreno.DEG
import com.example.kpkn.screens.onboarding.design.entreno.FigGeo
import com.example.kpkn.screens.onboarding.design.entreno.FigPose
import com.example.kpkn.screens.onboarding.design.entreno.LiftPoses
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.ik
import com.example.kpkn.screens.onboarding.design.entreno.smooth
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Poses y utilidades de los símbolos de disciplina (punto 7 del wizard). Geometría pura por instante, en el lienzo de 64
 * de los símbolos: las figuras de levantamiento salen de `LiftPoses` (su misma cinemática) llevadas al lienzo; la piedra de
 * strongman y los brazos del culturista se resuelven aquí. Las pruebas miden que los huesos mantienen su largo y que los
 * pies no se hunden bajo el suelo.
 */

/** Escala de [LiftPoses] (lienzo de 80 × 76, suelo en y = 70) al lienzo de 64: la figura entra con el disco arriba. */
internal const val LIFT_CANVAS_SCALE = 0.78f

/** Traslado de [LiftPoses] al lienzo de 64: con él, el suelo (y = 70) queda en y = 60. */
internal const val LIFT_CANVAS_DX = 0.8f
internal const val LIFT_CANVAS_DY = 5.4f

/** Radio de la cabeza y del disco de la barra de [LiftPoses] en el lienzo de 64. */
internal const val LIFT_HEAD_R = FigGeo.HEAD_R * LIFT_CANVAS_SCALE
internal const val LIFT_PLATE_R = LiftPoses.PLATE_R * LIFT_CANVAS_SCALE

/** Suelo de la escena de strongman (y del lienzo de 64) y radio de la piedra de atlas. */
internal const val SM_GROUND = 60f
internal const val STONE_RADIUS = 8.5f

/** Medidas de la figura de strongman: tronco, muslo y espinilla, brazo y antebrazo; la cabeza, a esa distancia del hombro. */
private const val SM_TORSO = 14f
private const val SM_THIGH = 10f
private const val SM_SHIN = 10f
private const val SM_UARM = 7f
private const val SM_FARM = 7f
private const val SM_HEAD_D = 6f

/** Un punto de [LiftPoses] llevado al lienzo de 64. */
internal fun liftToCanvas(p: Offset): Offset =
    Offset(LIFT_CANVAS_SCALE * p.x + LIFT_CANVAS_DX, LIFT_CANVAS_SCALE * p.y + LIFT_CANVAS_DY)

/** Ángulo en grados del vector (dx, dy), con la convención de giro de [SymbolPen.rotated]. */
internal fun dirDeg(dx: Float, dy: Float): Float = atan2(dy, dx) / DEG

/**
 * Articulaciones de una figura de perfil en unidades del lienzo de 64. Se reutiliza: cada cuadro escribe sobre el mismo
 * objeto. «N» es el lado cercano y «F» el lejano; `bar` es el centro del disco (solo en los levantamientos).
 */
internal class ProfileJoints {
    var hip = Offset.Zero
    var shoulder = Offset.Zero
    var head = Offset.Zero
    var kneeN = Offset.Zero
    var footN = Offset.Zero
    var kneeF = Offset.Zero
    var footF = Offset.Zero
    var elbowN = Offset.Zero
    var handN = Offset.Zero
    var elbowF = Offset.Zero
    var handF = Offset.Zero
    var bar = Offset.Zero

    /** Copia una pose de [LiftPoses] llevándola al lienzo de 64 (mismo factor y traslado en todos los puntos). */
    fun setFromLift(p: FigPose) {
        hip = liftToCanvas(p.hip)
        shoulder = liftToCanvas(p.shoulder)
        head = liftToCanvas(p.head)
        kneeN = liftToCanvas(p.kneeN)
        footN = liftToCanvas(p.footN)
        kneeF = liftToCanvas(p.kneeF)
        footF = liftToCanvas(p.footF)
        elbowN = liftToCanvas(p.elbowN)
        handN = liftToCanvas(p.handN)
        elbowF = liftToCanvas(p.elbowF)
        handF = liftToCanvas(p.handF)
        bar = liftToCanvas(p.bar)
    }

    /** Los pares de articulaciones que forman los huesos (la prueba mide que su largo no cambia). */
    internal fun bones(): List<Pair<Offset, Offset>> = listOf(
        hip to shoulder, hip to kneeN, kneeN to footN, hip to kneeF, kneeF to footF,
        shoulder to elbowN, elbowN to handN, shoulder to elbowF, elbowF to handF,
    )

    /** Todas las articulaciones (la prueba mide que ninguna se sale del lienzo). */
    internal fun joints(): List<Offset> =
        listOf(head, shoulder, hip, kneeN, footN, kneeF, footF, elbowN, handN, elbowF, handF)
}

/** Resuelve [lift] en la fase [u] y la deja en [out], en el lienzo de 64. [fig] es el trabajo de [LiftPoses], reutilizado. */
internal fun solveLiftCanvas(lift: LiftMark, u: Float, fig: FigPose, out: ProfileJoints) {
    LiftPoses.solve(lift, u, fig)
    out.setFromLift(fig)
}

/**
 * Figura de perfil como `FigPen.figure`: lado lejano, tronco, lado cercano y cabeza en aro, con el negro de la página debajo
 * (tapa lo que pasa por detrás, como el disco de la barra).
 */
internal fun SymbolPen.profileFigure(j: ProfileJoints, near: Color, far: Color, w: Float, headR: Float) {
    poly(j.shoulder, j.elbowF, j.handF, far, w)
    poly(j.hip, j.kneeF, j.footF, far, w)
    line(j.hip, j.shoulder, near, w)
    poly(j.hip, j.kneeN, j.footN, near, w)
    poly(j.shoulder, j.elbowN, j.handN, near, w)
    dot(j.head.x, j.head.y, headR + w * 0.4f, Color.Black)
    ring(j.head.x, j.head.y, headR, near, w * 0.85f)
}

/** Elipse rellena girada [deg] alrededor de su centro (un bíceps sobre su brazo). Respeta la opacidad de grupo. */
internal fun SymbolPen.fillOvalRot(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, color: Color) {
    if (rx <= 0f || ry <= 0f) return
    rotated(deg, cx, cy) {
        ds.drawOval(color.copy(alpha = color.alpha * ga), Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry))
    }
}

/** Postes del banco, como en `LiftMarksPicker`: bajan desde la tabla hasta el suelo. */
private val BENCH_POSTS = floatArrayOf(34f, 64f)

/** El banco del press como lo dibuja `LiftMarksPicker`: la tabla desde la cadera hacia la cabeza y dos postes hasta el suelo. */
internal fun SymbolPen.benchStage(table: Color, post: Color, w: Float, postW: Float) {
    val y = liftToCanvas(Offset(0f, LiftPoses.BENCH_Y)).y
    val ground = liftToCanvas(Offset(0f, LiftPoses.GROUND_LINE)).y
    line(
        liftToCanvas(Offset(LiftPoses.BENCH_TABLE_LEFT, LiftPoses.BENCH_Y)),
        liftToCanvas(Offset(LiftPoses.BENCH_TABLE_RIGHT, LiftPoses.BENCH_Y)),
        table, w,
    )
    for (px in BENCH_POSTS) {
        val x = liftToCanvas(Offset(px, 0f)).x
        line(Offset(x, y), Offset(x, ground), post, postW)
    }
}

// ---------------------------------------------------------------- strongman: la piedra de atlas

/**
 * Fotogramas clave de la piedra (instante; piedra x e y; cadera x e y; inclinación del tronco y de la cabeza, en grados;
 * pie cercano y lejano en x; ángulo de la mano cercana y lejana sobre el borde de la piedra, en grados; suelta de 0 a 1).
 * Entre dos claves la interpolación es suave. Tras la última, la figura se yergue y suelta la piedra arriba.
 */
private val STRONG_KEYS: Array<FloatArray> = arrayOf(
    floatArrayOf(0.0f, 26f, 51.5f, 8f, 46f, 60f, 0f, 15f, 4f, 215f, 185f, 0f),
    floatArrayOf(1.0f, 23f, 44f, 9f, 47f, 35f, 0f, 10f, 16f, 200f, 170f, 0f),
    floatArrayOf(1.8f, 27f, 33f, 14f, 44f, 15f, -10f, 17f, 12f, 160f, 135f, 0f),
    floatArrayOf(2.6f, 49f, 33.5f, 24f, 44f, 35f, 10f, 30f, 21f, 190f, 160f, 0f),
    floatArrayOf(3.0f, 49f, 33.5f, 24.5f, 43f, 5f, 0f, 22f, 27f, 190f, 160f, 1f),
)

/** Brazos colgando (relativos al hombro) a los que van las manos cuando la figura suelta la piedra. */
private val SM_HANG_N = Offset(1f, 12f)
private val SM_HANG_F = Offset(-0.5f, 12.5f)

/** Parámetros de la pose de strongman en [t] (11 valores en [out]), interpolados entre fotogramas clave. */
internal fun strongParams(t: Float, out: FloatArray) {
    var i = 0
    while (i < STRONG_KEYS.size - 2 && t > STRONG_KEYS[i + 1][0]) i++
    val a = STRONG_KEYS[i]
    val b = STRONG_KEYS[i + 1]
    val k = smooth(seg(t, a[0], b[0]))
    for (c in 1 until a.size) out[c - 1] = lerpF(a[c], b[c], k)
}

/**
 * Pose de la figura de strongman en [t], de perfil mirando a la derecha. [p] es un vector de trabajo de 11 valores. Los pies
 * van en el suelo; las manos, sobre el borde de la piedra o colgando cuando la figura la suelta. Cada hueso se resuelve por
 * cinemática inversa, así que su largo no cambia.
 */
internal fun strongPose(t: Float, p: FloatArray, out: ProfileJoints) {
    strongParams(t, p)
    val hip = Offset(p[2], p[3])
    val sh = hip + FigGeo.up(p[4] * DEG) * SM_TORSO
    out.hip = hip
    out.shoulder = sh
    out.head = sh + FigGeo.up((p[4] + p[5]) * DEG) * SM_HEAD_D
    out.footN = Offset(p[6], SM_GROUND)
    out.footF = Offset(p[7], SM_GROUND)
    out.kneeN = ik(hip, out.footN, SM_THIGH, SM_SHIN, -1f)
    out.kneeF = ik(hip, out.footF, SM_THIGH, SM_SHIN, -1f)
    val stone = Offset(p[0], p[1])
    val gripN = stone + Offset(cos(p[8] * DEG), sin(p[8] * DEG)) * STONE_RADIUS
    val gripF = stone + Offset(cos(p[9] * DEG), sin(p[9] * DEG)) * STONE_RADIUS
    out.handN = FigGeo.lerp(gripN, sh + SM_HANG_N, p[10])
    out.handF = FigGeo.lerp(gripF, sh + SM_HANG_F, p[10])
    out.elbowN = ik(sh, out.handN, SM_UARM, SM_FARM, 1f)
    out.elbowF = ik(sh, out.handF, SM_UARM, SM_FARM, 1f)
}
