package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** La actividad que hace la figura del recuadro de cardio. */
internal enum class CardioFigureKind { RUN, WALK, BIKE, ELLIPTICAL, ROW, STAIRS, SKI, SLED }

/** Qué figura se dibuja y sobre qué: `indoor` pone la cinta o el rodillo en lugar del suelo o las ruedas. */
internal data class CardioFigureSpec(val kind: CardioFigureKind, val indoor: Boolean)

/**
 * La figura por el tipo de cardio del ejercicio. En cinta o carrera, un paso a menos de 6,5 km/h se camina (la recuperación andando
 * de unas series en cinta se ve caminando).
 */
internal fun cardioFigureSpec(type: CardioType, speedKmh: Double? = null): CardioFigureSpec = when (type) {
    CardioType.WALK -> CardioFigureSpec(CardioFigureKind.WALK, indoor = false)
    CardioType.TREADMILL, CardioType.CURVED_TREADMILL ->
        CardioFigureSpec(if (speedKmh != null && speedKmh < 6.5) CardioFigureKind.WALK else CardioFigureKind.RUN, indoor = true)
    CardioType.RUN_OUTDOOR -> CardioFigureSpec(if (speedKmh != null && speedKmh < 6.5) CardioFigureKind.WALK else CardioFigureKind.RUN, indoor = false)
    CardioType.ELLIPTICAL -> CardioFigureSpec(CardioFigureKind.ELLIPTICAL, indoor = true)
    CardioType.ROW_MACHINE -> CardioFigureSpec(CardioFigureKind.ROW, indoor = true)
    CardioType.BIKE_STATIONARY, CardioType.AIR_BIKE -> CardioFigureSpec(CardioFigureKind.BIKE, indoor = true)
    CardioType.BIKE_OUTDOOR -> CardioFigureSpec(CardioFigureKind.BIKE, indoor = false)
    CardioType.STAIR_CLIMBER -> CardioFigureSpec(CardioFigureKind.STAIRS, indoor = true)
    CardioType.SKI_ERG -> CardioFigureSpec(CardioFigureKind.SKI, indoor = true)
    CardioType.SLED -> CardioFigureSpec(CardioFigureKind.SLED, indoor = false)
}

/** El icono de línea de la actividad (las que no tienen uno propio usan el de carrera). */
internal fun cardioFigureIcon(kind: CardioFigureKind): ImageVector = when (kind) {
    CardioFigureKind.WALK -> KpknLineIcons.Walk
    CardioFigureKind.BIKE -> KpknLineIcons.Bike
    CardioFigureKind.ROW -> KpknLineIcons.Row
    CardioFigureKind.STAIRS -> KpknLineIcons.Stairs
    else -> KpknLineIcons.Run
}

internal fun cardioFigureDescription(kind: CardioFigureKind): String = when (kind) {
    CardioFigureKind.RUN -> "Figura corriendo"
    CardioFigureKind.WALK -> "Figura caminando"
    CardioFigureKind.BIKE -> "Figura pedaleando"
    CardioFigureKind.ELLIPTICAL -> "Figura en la elíptica"
    CardioFigureKind.ROW -> "Figura remando"
    CardioFigureKind.STAIRS -> "Figura subiendo escaleras"
    CardioFigureKind.SKI -> "Figura en el ergómetro de esquí"
    CardioFigureKind.SLED -> "Figura empujando el trineo"
}

/**
 * Ciclos de movimiento por segundo: el ritmo sigue la zona del paso (más rápido en trabajo que en recuperación) y, en bici, la
 * cadencia del bloque si la hay. 0 = quieta (sin empezar, en pausa o terminado).
 */
internal fun cardioFigureCadence(kind: CardioFigureKind, zone: Int, moving: Boolean, rpm: Int? = null): Float {
    if (!moving) return 0f
    if (kind == CardioFigureKind.BIKE && rpm != null && rpm in 30..140) return rpm / 60f
    val base = when (kind) {
        CardioFigureKind.RUN -> 1.25f
        CardioFigureKind.WALK -> .9f
        CardioFigureKind.BIKE -> 1.2f
        CardioFigureKind.ELLIPTICAL -> .95f
        CardioFigureKind.ROW -> .42f
        CardioFigureKind.STAIRS -> .85f
        CardioFigureKind.SKI -> .6f
        CardioFigureKind.SLED -> .7f
    }
    return base * (.6f + .16f * zone.coerceIn(1, 5))
}

/** La pose de partida (y la de movimiento reducido): a media zancada, que se lee mejor que de pie. */
private const val StillCycles = .15f

/**
 * La figura de palotes en tinta crema haciendo la actividad, sobre su máquina o su suelo. Se anima con el reloj de fotogramas solo
 * si [cyclesPerSecond] > 0, la pantalla está en RESUMED y no hay movimiento reducido; si no, queda quieta en la última pose. La fase
 * solo se lee al dibujar: animar no recompone.
 */
@Composable
internal fun CardioActivityFigure(
    spec: CardioFigureSpec,
    cyclesPerSecond: Float,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    ink: Color = WizardColors.text,
) {
    val reduced = wizardReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = !reduced && cyclesPerSecond > 0f && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val cycles = remember { mutableFloatStateOf(StillCycles) }
    val rate by rememberUpdatedState(cyclesPerSecond)
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (last >= 0L) cycles.floatValue = (cycles.floatValue + (now - last) / 1_000_000_000f * rate) % 1_000f
                last = now
            }
        }
    }
    Canvas(modifier.semantics { contentDescription = cardioFigureDescription(spec.kind) }.testTag("cardio-figure-${spec.kind.name.lowercase()}")) {
        drawCardioFigure(spec, cycles.floatValue, ink.copy(alpha = if (dimmed) .4f else 1f))
    }
}

// ---- Dibujo -------------------------------------------------------------------------------------------------------------------------
// Unidades de figura: los pies en y = 0, la cabeza cerca de y = 1, x positiva hacia delante. Los ángulos de las extremidades se miden
// desde la vertical hacia abajo, positivos hacia delante.

private const val Torso = .30f
private const val Thigh = .245f
private const val Shin = .245f
private const val UpperArm = .16f
private const val Forearm = .15f
private const val HeadR = .068f
private const val Neck = .035f
private const val Tau = (2 * PI).toFloat()

private fun down(from: Offset, angle: Float, len: Float) = Offset(from.x + len * sin(angle), from.y - len * cos(angle))
private fun up(from: Offset, angle: Float, len: Float) = Offset(from.x + len * sin(angle), from.y + len * cos(angle))
private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

/** Cinemática de dos huesos: la articulación intermedia hacia el lado de [bend]. */
private fun joint(a: Offset, b: Offset, l1: Float, l2: Float, bend: Offset): Offset {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val raw = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
    val ux = dx / raw
    val uy = dy / raw
    val d = raw.coerceIn(abs(l1 - l2) + 1e-3f, l1 + l2 - 1e-3f)
    val along = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
    val h = sqrt(max(0f, l1 * l1 - along * along))
    var px = -uy
    var py = ux
    if (px * bend.x + py * bend.y < 0f) { px = -px; py = -py }
    return Offset(a.x + ux * along + px * h, a.y + uy * along + py * h)
}

/** Una pose: lo lejano (B) se dibuja atenuado detrás del tronco. */
private class Pose(
    val hip: Offset,
    val shoulder: Offset,
    val head: Offset,
    val kneeA: Offset, val footA: Offset,
    val kneeB: Offset, val footB: Offset,
    val elbowA: Offset, val handA: Offset,
    val elbowB: Offset, val handB: Offset,
)

private fun pose(hip: Offset, lean: Float, kneeA: Offset, footA: Offset, kneeB: Offset, footB: Offset,
    elbowA: Offset, handA: Offset, elbowB: Offset, handB: Offset, shoulder: Offset = up(hip, lean, Torso)): Pose =
    Pose(hip, shoulder, up(shoulder, lean * .6f, Neck + HeadR), kneeA, footA, kneeB, footB, elbowA, handA, elbowB, handB)

private fun gait(theta: Float, lean: Float, thighSwing: Float, kneeBase: Float, kneeSwing: Float, hipY: Float, armSwing: Float, elbow: Float): Pose {
    val hip = Offset(0f, hipY)
    val shoulder = up(hip, lean, Torso)
    fun leg(phi: Float): Pair<Offset, Offset> {
        val thigh = thighSwing * sin(phi)
        val knee = kneeBase + kneeSwing * max(0f, cos(phi))
        val k = down(hip, thigh, Thigh)
        return k to down(k, thigh - knee, Shin)
    }
    fun arm(phi: Float): Pair<Offset, Offset> {
        val upper = -armSwing * sin(phi)
        val e = down(shoulder, upper, UpperArm)
        return e to down(e, upper + elbow, Forearm)
    }
    val (ka, fa) = leg(theta)
    val (kb, fb) = leg(theta + PI.toFloat())
    val (ea, ha) = arm(theta)
    val (eb, hb) = arm(theta + PI.toFloat())
    return pose(hip, lean, ka, fa, kb, fb, ea, ha, eb, hb, shoulder)
}

private fun runPose(theta: Float) = gait(theta, lean = .14f, thighSwing = .62f, kneeBase = .25f, kneeSwing = 1.25f,
    hipY = .478f + .035f * abs(sin(theta)), armSwing = .75f, elbow = 1.45f)

private fun walkPose(theta: Float) = gait(theta, lean = .04f, thighSwing = .42f, kneeBase = .08f, kneeSwing = .55f,
    hipY = .478f + .012f * cos(2f * theta), armSwing = .38f, elbow = .3f)

private fun sledPose(theta: Float): Pose {
    val base = gait(theta, lean = .85f, thighSwing = .5f, kneeBase = .35f, kneeSwing = .9f, hipY = .44f + .02f * abs(sin(theta)), armSwing = 0f, elbow = 0f)
    val ea = down(base.shoulder, 1.45f, UpperArm)
    val ha = down(ea, 1.55f, Forearm)
    return Pose(base.hip, base.shoulder, base.head, base.kneeA, base.footA, base.kneeB, base.footB, ea, ha, ea, ha)
}

private fun stairsPose(theta: Float): Pose {
    val hip = Offset(0f, .47f + .02f * cos(2f * theta))
    val lean = .2f
    val shoulder = up(hip, lean, Torso)
    fun leg(phi: Float): Pair<Offset, Offset> {
        val lift = .5f - .5f * cos(phi)
        val thigh = .15f + .85f * lift
        val k = down(hip, thigh, Thigh)
        return k to down(k, thigh - (.1f + 1.25f * lift), Shin)
    }
    val (ka, fa) = leg(theta)
    val (kb, fb) = leg(theta + PI.toFloat())
    val hand = Offset(hip.x + .22f, hip.y + .05f)
    val elbow = joint(shoulder, hand, UpperArm, Forearm, Offset(-.4f, -1f))
    return pose(hip, lean, ka, fa, kb, fb, elbow, hand, elbow, hand, shoulder)
}

private fun bikePose(theta: Float, crank: Offset, crankR: Float, bar: Offset): Pose {
    val hip = Offset(-.10f, .44f)
    val lean = .75f
    val shoulder = up(hip, lean, Torso)
    val a = PI.toFloat() / 2f - theta
    val pa = Offset(crank.x + crankR * cos(a), crank.y + crankR * sin(a))
    val pb = Offset(crank.x - crankR * cos(a), crank.y - crankR * sin(a))
    val ka = joint(hip, pa, Thigh, Shin, Offset(1f, .3f))
    val kb = joint(hip, pb, Thigh, Shin, Offset(1f, .3f))
    val elbow = joint(shoulder, bar, UpperArm, Forearm, Offset(-.3f, -1f))
    return pose(hip, lean, ka, pa, kb, pb, elbow, bar, elbow, bar, shoulder)
}

private fun ellipticalPose(theta: Float): Pose {
    val hip = Offset(0f, .50f + .012f * sin(2f * theta))
    val lean = .08f
    val shoulder = up(hip, lean, Torso)
    val a = -theta
    val fa = Offset(.02f + .12f * cos(a), .06f + .04f * sin(a))
    val fb = Offset(.02f - .12f * cos(a), .06f - .04f * sin(a))
    val ha = Offset(.18f - .09f * cos(a), .70f)
    val hb = Offset(.18f + .09f * cos(a), .70f)
    return pose(hip, lean,
        joint(hip, fa, Thigh, Shin, Offset(1f, 0f)), fa, joint(hip, fb, Thigh, Shin, Offset(1f, 0f)), fb,
        joint(shoulder, ha, UpperArm, Forearm, Offset(-1f, -.5f)), ha, joint(shoulder, hb, UpperArm, Forearm, Offset(-1f, -.5f)), hb, shoulder)
}

/** Tracción rápida (38 % del ciclo) y vuelta lenta, como un remo de verdad: 0 = ataque, 1 = final. */
private fun strokeProgress(cycles: Float): Float {
    val q = cycles - kotlin.math.floor(cycles)
    return if (q < .38f) smooth(q / .38f) else 1f - smooth((q - .38f) / .62f)
}

private fun rowPose(cycles: Float, foot: Offset): Pose {
    val s = strokeProgress(cycles)
    val hip = Offset(.04f - .16f * s, .17f)
    val lean = .5f - .9f * smooth((s - .2f) / .6f)
    val shoulder = up(hip, lean, Torso)
    val knee = joint(hip, foot, Thigh, Shin, Offset(0f, 1f))
    val catchHand = Offset(shoulder.x + .30f, shoulder.y - .06f)
    val finishHand = Offset(shoulder.x + .10f, shoulder.y - .12f)
    val hand = lerp(catchHand, finishHand, smooth((s - .55f) / .45f))
    val elbow = joint(shoulder, hand, UpperArm, Forearm, Offset(-1f, -.3f))
    return pose(hip, lean, knee, foot, knee, foot, elbow, hand, elbow, hand, shoulder)
}

private fun skiPose(cycles: Float): Pose {
    val s = strokeProgress(cycles)
    val hip = Offset(0f, .48f - .06f * s)
    val lean = .08f + .7f * s
    val shoulder = up(hip, lean, Torso)
    val fa = Offset(.03f, 0f)
    val fb = Offset(-.03f, 0f)
    val upper = 2.7f * (1f - s) - .25f * s
    val e = down(shoulder, upper, UpperArm)
    val h = down(e, upper + .25f, Forearm)
    return pose(hip, lean, joint(hip, fa, Thigh, Shin, Offset(1f, 0f)), fa, joint(hip, fb, Thigh, Shin, Offset(1f, 0f)), fb, e, h, e, h, shoulder)
}

private fun DrawScope.drawCardioFigure(spec: CardioFigureSpec, cycles: Float, ink: Color) {
    val scale = min(size.height * .80f, size.width * .55f)
    if (scale <= 0f) return
    val ox = size.width / 2f
    val gy = size.height * .92f
    fun px(u: Offset) = Offset(ox + u.x * scale, gy - u.y * scale)
    val stroke = (scale * .028f).coerceIn(2.dp.toPx(), 3.dp.toPx())
    val faint = ink.copy(alpha = ink.alpha * .32f)
    val far = ink.copy(alpha = ink.alpha * .5f)
    fun line(a: Offset, b: Offset, color: Color = ink, width: Float = stroke) = drawLine(color, px(a), px(b), width, StrokeCap.Round)
    fun circle(c: Offset, r: Float, color: Color, width: Float = stroke * .7f) = drawCircle(color, r * scale, px(c), style = Stroke(width))
    val theta = cycles * Tau

    // El suelo o la máquina, detrás de la figura.
    fun movingGround(travelPerCycle: Float) {
        line(Offset(-ox / scale, 0f), Offset((size.width - ox) / scale, 0f), faint, stroke * .6f)
        val gap = .2f
        val shift = (cycles * travelPerCycle) % gap
        var x = -ox / scale - shift
        while (x < (size.width - ox) / scale) {
            line(Offset(x, -.035f), Offset(x + .06f, -.035f), faint, stroke * .6f)
            x += gap
        }
    }
    fun treadmill(travelPerCycle: Float) {
        line(Offset(-.42f, -.02f), Offset(.42f, -.02f), faint, stroke * 1.4f)
        val gap = .14f
        val shift = (cycles * travelPerCycle) % gap
        var x = .40f - shift
        while (x > -.40f) { line(Offset(x, -.02f), Offset(x - .03f, -.02f), ink.copy(alpha = ink.alpha * .55f), stroke * .7f); x -= gap }
        line(Offset(.42f, -.02f), Offset(.50f, .60f), faint)
        line(Offset(.50f, .60f), Offset(.40f, .64f), faint)
    }

    val p: Pose = when (spec.kind) {
        CardioFigureKind.RUN, CardioFigureKind.WALK -> {
            val travel = if (spec.kind == CardioFigureKind.RUN) .9f else .6f
            if (spec.indoor) treadmill(travel) else movingGround(travel)
            if (spec.kind == CardioFigureKind.RUN) runPose(theta) else walkPose(theta)
        }
        CardioFigureKind.SLED -> {
            movingGround(.4f)
            val pose = sledPose(theta)
            val front = pose.handA.x + .02f
            line(Offset(front, .02f), Offset(front + .30f, .02f), faint, stroke * 1.2f)
            line(Offset(front, .02f), Offset(front, .2f), faint)
            line(Offset(front + .30f, .02f), Offset(front + .30f, .2f), faint)
            line(Offset(front, .2f), Offset(front + .30f, .2f), faint)
            line(pose.handA, Offset(front, .2f), faint)
            pose
        }
        CardioFigureKind.BIKE -> {
            val crank = Offset(.06f, .17f)
            val bar = Offset(.30f, .52f)
            val seat = Offset(-.10f, .41f)
            if (spec.indoor) {
                line(Offset(-.30f, 0f), Offset(.42f, 0f), faint, stroke * 1.2f)
                line(seat, Offset(-.05f, 0f), faint)
                val fly = Offset(.34f, .14f)
                circle(fly, .1f, faint)
                line(fly, Offset(fly.x + .1f * cos(-theta * 2f), fly.y + .1f * sin(-theta * 2f)), faint, stroke * .6f)
                line(Offset(.28f, .48f), fly, faint)
            } else {
                movingGround(.8f)
                listOf(Offset(-.24f, .12f), Offset(.38f, .12f)).forEach { w ->
                    circle(w, .12f, faint)
                    line(w, Offset(w.x + .12f * cos(-theta * 2f), w.y + .12f * sin(-theta * 2f)), faint, stroke * .6f)
                }
                line(seat, Offset(-.24f, .12f), faint)
                line(Offset(.28f, .48f), Offset(.38f, .12f), faint)
            }
            line(seat, crank, faint)
            line(seat, Offset(.28f, .48f), faint)
            line(Offset(.28f, .48f), bar, faint)
            circle(crank, .085f, faint, stroke * .5f)
            bikePose(theta, crank, .085f, bar)
        }
        CardioFigureKind.ELLIPTICAL -> {
            line(Offset(-.30f, 0f), Offset(.36f, 0f), faint, stroke * 1.2f)
            val pose = ellipticalPose(theta)
            line(pose.handA, Offset(.26f, .02f), faint)
            line(pose.handB, Offset(.26f, .02f), faint.copy(alpha = faint.alpha * .6f))
            line(pose.footA, Offset(-.28f, .02f), faint)
            pose
        }
        CardioFigureKind.ROW -> {
            val foot = Offset(.30f, .09f)
            line(Offset(-.36f, .10f), Offset(.40f, .10f), faint, stroke * 1.2f)
            line(Offset(.40f, .10f), Offset(.46f, 0f), faint)
            line(Offset(-.36f, .10f), Offset(-.38f, 0f), faint)
            val fly = Offset(.50f, .22f)
            circle(fly, .09f, faint)
            line(fly, Offset(fly.x + .09f * cos(-theta), fly.y + .09f * sin(-theta)), faint, stroke * .6f)
            val pose = rowPose(cycles, foot)
            line(pose.handA, fly, faint, stroke * .5f)
            line(Offset(pose.hip.x - .06f, .13f), Offset(pose.hip.x + .06f, .13f), ink, stroke * 1.3f)
            pose
        }
        CardioFigureKind.STAIRS -> {
            line(Offset(-.30f, 0f), Offset(.40f, 0f), faint, stroke * 1.2f)
            // Los peldaños bajan bajo los pies al ritmo del paso.
            val step = .12f
            val shift = (cycles * step) % step
            for (i in 0..3) {
                val y = i * step - shift
                if (y in 0f..(3 * step)) line(Offset(-.18f + i * .1f, y), Offset(-.02f + i * .1f, y), faint, stroke * .8f)
            }
            line(Offset(.24f, .52f), Offset(.30f, 0f), faint)
            stairsPose(theta)
        }
        CardioFigureKind.SKI -> {
            line(Offset(-.30f, 0f), Offset(.46f, 0f), faint, stroke * 1.2f)
            line(Offset(.38f, 0f), Offset(.38f, 1.0f), faint)
            val pose = skiPose(cycles)
            line(Offset(.38f, .98f), pose.handA, faint, stroke * .5f)
            pose
        }
    }

    // Lo lejano, el tronco y la cabeza, y lo cercano encima.
    line(p.hip, p.kneeB, far); line(p.kneeB, p.footB, far)
    line(p.shoulder, p.elbowB, far); line(p.elbowB, p.handB, far)
    line(p.hip, p.shoulder)
    drawCircle(ink, HeadR * scale, px(p.head), style = Stroke(stroke))
    line(p.hip, p.kneeA); line(p.kneeA, p.footA)
    line(p.shoulder, p.elbowA); line(p.elbowA, p.handA)
}
