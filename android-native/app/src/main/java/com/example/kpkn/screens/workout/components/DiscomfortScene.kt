package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.kpkn.data.models.DiscomfortSection
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * Las animaciones curadas de la ficha de una molestia (LV6, F7): una por sección del catálogo (hombro y brazos, columna y cuello, cadera,
 * rodilla, tobillo y pie, general). Todas siguen el modelo de la rodilla: trazo grueso de tinta, la zona resaltada en su color con un
 * pulso, la articulación en un círculo y un aro que se abre desde ella. Un ciclo dura 2,6 s. Con «reducir movimiento» (o fuera de pantalla)
 * queda un fotograma quieto con la zona resaltada.
 */

private val SceneInk = Color(0xFFF2EEE6)
private val JointFill = Color(0xFF0E0E0E)
private val JointStroke = Color(0xFFF49A6E)

/** Un ciclo de movimiento: 2,6 s, como la ficha de la rodilla de F7. */
private const val SceneCycle = 2.6f

/** El fotograma quieto: a poco más de la mitad del movimiento, con la zona resaltada. */
private const val StaticWave = 0.6f

private const val SceneWidth = 110f
private const val SceneHeight = 120f

/** Color de la zona de una sección: lo usan la etiqueta de la ficha, los puntos de los chips y la propia escena. */
internal fun discomfortZoneColor(section: DiscomfortSection): Color = when (section) {
    DiscomfortSection.KNEE -> Color(0xFFEF6F64)
    DiscomfortSection.SPINE_NECK -> Color(0xFFF7CF73)
    DiscomfortSection.HIP_PELVIS -> Color(0xFF8FB2FF)
    DiscomfortSection.SHOULDERS_ARMS -> Color(0xFFF49A6E)
    DiscomfortSection.ANKLE_FOOT -> Color(0xFF43D18C)
    DiscomfortSection.GENERAL -> Color(0xFFC4BFB6)
}

/** Lo que cambia en un fotograma: [wave] (0 a 1 y vuelta) mueve la articulación y el pulso; [ripple] (0 a 1) abre el aro, o null si no hay. */
private class SceneFrame(val wave: Float, val ripple: Float?)

@Composable
internal fun DiscomfortScene(
    section: DiscomfortSection,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val reduced = wizardReducedMotion()
    val visibility = rememberFeedbackAnimationVisibility()
    val moving = animated && !reduced && visibility.visible
    val clock = rememberRestClock(moving)
    val tint = discomfortZoneColor(section)
    Canvas(modifier = modifier.then(visibility.modifier)) {
        val unit = min(size.width / SceneWidth, size.height / SceneHeight)
        if (unit <= 0f) return@Canvas
        val offsetX = (size.width - SceneWidth * unit) / 2f
        val offsetY = (size.height - SceneHeight * unit) / 2f
        val frame = if (moving) {
            val phase = (clock.seconds % SceneCycle + SceneCycle) % SceneCycle / SceneCycle
            SceneFrame(wave = 0.5f - 0.5f * cos(2.0 * PI * phase).toFloat(), ripple = phase)
        } else {
            SceneFrame(wave = StaticWave, ripple = null)
        }
        withTransform({
            translate(offsetX, offsetY)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            when (section) {
                DiscomfortSection.KNEE -> drawKnee(frame, tint)
                DiscomfortSection.SHOULDERS_ARMS -> drawShoulder(frame, tint)
                DiscomfortSection.SPINE_NECK -> drawSpine(frame, tint)
                DiscomfortSection.HIP_PELVIS -> drawHip(frame, tint)
                DiscomfortSection.ANKLE_FOOT -> drawAnkle(frame, tint)
                DiscomfortSection.GENERAL -> drawGeneral(frame, tint)
            }
        }
    }
}

// ── Piezas comunes ──────────────────────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBone(from: Offset, to: Offset) {
    drawLine(SceneInk, from, to, strokeWidth = 7f, cap = StrokeCap.Round)
}

/** La zona resaltada: un trazo del color de la zona que respira (más grueso y más claro con [pulse]). */
private fun DrawScope.drawHighlight(path: Path, tint: Color, pulse: Float) {
    drawPath(
        path,
        tint.copy(alpha = 0.35f + 0.65f * pulse),
        style = Stroke(width = 5f + 3f * pulse, cap = StrokeCap.Round),
    )
}

/** La articulación: un círculo oscuro con borde cálido. */
private fun DrawScope.drawJoint(center: Offset) {
    drawCircle(JointFill, radius = 6.5f, center = center)
    drawCircle(JointStroke, radius = 6.5f, center = center, style = Stroke(width = 2.5f))
}

/** El aro que se abre desde la articulación y se desvanece. */
private fun DrawScope.drawRipple(center: Offset, tint: Color, progress: Float?) {
    val p = progress ?: return
    val eased = 1f - (1f - p) * (1f - p)
    drawCircle(
        color = tint.copy(alpha = 0.9f * (1f - eased)),
        radius = 6f * (0.6f + 1.6f * eased),
        center = center,
        style = Stroke(width = 1.5f),
    )
}

private fun DrawScope.drawHalo(center: Offset, tint: Color, radius: Float = 40f) {
    drawCircle(tint.copy(alpha = 0.06f), radius = radius, center = center)
}

// ── Rodilla (el modelo de F7) ───────────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawKnee(frame: SceneFrame, tint: Color) {
    drawHalo(Offset(55f, 58f), tint)
    drawBone(Offset(40f, 10f), Offset(55f, 56f))
    withTransform({
        translate(55f, 56f)
        rotate(62f * frame.wave, pivot = Offset.Zero)
    }) {
        drawBone(Offset.Zero, Offset(-6f, 52f))
        val tendon = Path().apply {
            moveTo(8f, -4f)
            quadraticTo(10f, 12f, 4f, 22f)
        }
        drawHighlight(tendon, tint, frame.wave)
    }
    drawJoint(Offset(64f, 50f))
    drawRipple(Offset(64f, 64f), tint, frame.ripple)
}

// ── Hombro y brazos ─────────────────────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawShoulder(frame: SceneFrame, tint: Color) {
    drawHalo(Offset(48f, 46f), tint)
    drawCircle(SceneInk, radius = 7f, center = Offset(36f, 11f), style = Stroke(width = 4f))
    drawBone(Offset(36f, 22f), Offset(36f, 104f))
    withTransform({
        translate(36f, 36f)
        rotate(-150f * frame.wave, pivot = Offset.Zero)
    }) {
        drawBone(Offset.Zero, Offset(2f, 58f))
        val front = Path().apply {
            moveTo(7f, -2f)
            quadraticTo(11f, 12f, 7f, 26f)
        }
        drawHighlight(front, tint, frame.wave)
    }
    drawJoint(Offset(43f, 34f))
    drawRipple(Offset(43f, 46f), tint, frame.ripple)
}

// ── Columna y cuello ────────────────────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSpine(frame: SceneFrame, tint: Color) {
    drawHalo(Offset(52f, 66f), tint)
    val start = Offset(46f, 104f)
    val end = Offset(46f, 30f)
    val control = Offset(46f + 24f * frame.wave, 68f)
    val count = 9
    fun at(t: Float): Offset {
        val u = 1f - t
        return Offset(
            u * u * start.x + 2f * u * t * control.x + t * t * end.x,
            u * u * start.y + 2f * u * t * control.y + t * t * end.y,
        )
    }
    val points = (0 until count).map { at(it / (count - 1).toFloat()) }
    // La pelvis en la base y la cabeza sobre la última vértebra, en la dirección de la curva.
    drawBone(Offset(34f, 106f), Offset(58f, 106f))
    val tangent = Offset(end.x - control.x, end.y - control.y)
    val length = hypot(tangent.x, tangent.y).coerceAtLeast(1f)
    val head = Offset(end.x + tangent.x / length * 15f, end.y + tangent.y / length * 15f)
    drawCircle(SceneInk, radius = 8.5f, center = head, style = Stroke(width = 4f))
    for (i in 0 until points.size - 1) {
        drawLine(SceneInk, points[i], points[i + 1], strokeWidth = 4f, cap = StrokeCap.Round)
    }
    points.forEach { p ->
        drawCircle(JointFill, radius = 4.4f, center = p)
        drawCircle(SceneInk, radius = 4.4f, center = p, style = Stroke(width = 2.4f))
    }
    // La zona lumbar (las primeras vértebras) resaltada.
    val lumbar = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1..3) lineTo(points[i].x, points[i].y)
    }
    drawHighlight(lumbar, tint, frame.wave)
    drawJoint(Offset(points[2].x + 9f, points[2].y))
    drawRipple(Offset(points[2].x + 9f, points[2].y + 8f), tint, frame.ripple)
}

// ── Cadera y pelvis ─────────────────────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawHip(frame: SceneFrame, tint: Color) {
    drawHalo(Offset(52f, 52f), tint)
    drawBone(Offset(38f, 6f), Offset(46f, 44f))
    drawBone(Offset(36f, 46f), Offset(58f, 40f))
    withTransform({
        translate(46f, 44f)
        rotate(-65f * frame.wave, pivot = Offset.Zero)
    }) {
        drawBone(Offset.Zero, Offset(2f, 62f))
        val crease = Path().apply {
            moveTo(7f, -2f)
            quadraticTo(11f, 12f, 7f, 24f)
        }
        drawHighlight(crease, tint, frame.wave)
    }
    drawJoint(Offset(54f, 42f))
    drawRipple(Offset(54f, 56f), tint, frame.ripple)
}

// ── Tobillo y pie ───────────────────────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawAnkle(frame: SceneFrame, tint: Color) {
    drawHalo(Offset(50f, 70f), tint)
    drawLine(SceneInk.copy(alpha = 0.4f), Offset(6f, 101f), Offset(104f, 101f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawBone(Offset(44f, 14f), Offset(50f, 86f))
    val achilles = Path().apply {
        moveTo(42f, 48f)
        quadraticTo(36f, 68f, 43f, 82f)
    }
    drawHighlight(achilles, tint, frame.wave)
    withTransform({
        translate(50f, 86f)
        rotate(-26f * frame.wave, pivot = Offset.Zero)
    }) {
        drawBone(Offset(-8f, 5f), Offset(36f, 8f))
    }
    drawJoint(Offset(57f, 82f))
    drawRipple(Offset(57f, 82f), tint, frame.ripple)
}

// ── General ─────────────────────────────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawGeneral(frame: SceneFrame, tint: Color) {
    // Un aura que respira alrededor de todo el cuerpo.
    drawCircle(tint.copy(alpha = 0.05f + 0.07f * frame.wave), radius = 36f + 6f * frame.wave, center = Offset(55f, 62f))
    drawCircle(SceneInk, radius = 9f, center = Offset(55f, 18f), style = Stroke(width = 4f))
    drawBone(Offset(55f, 30f), Offset(55f, 72f))
    // Los brazos suben un poco al inspirar.
    val lift = 14f * frame.wave
    drawBone(Offset(55f, 40f), Offset(31f, 66f - lift))
    drawBone(Offset(55f, 40f), Offset(79f, 66f - lift))
    drawBone(Offset(55f, 72f), Offset(42f, 110f))
    drawBone(Offset(55f, 72f), Offset(68f, 110f))
    drawJoint(Offset(55f, 52f))
    drawRipple(Offset(55f, 52f), tint, frame.ripple)
}
