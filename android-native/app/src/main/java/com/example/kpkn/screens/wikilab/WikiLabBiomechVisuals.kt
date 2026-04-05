package com.example.kpkn.screens.wikilab

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class VisualPattern {
    HINGE,
    SQUAT,
    HORIZONTAL_PUSH,
    HORIZONTAL_PULL,
    VERTICAL_PUSH,
    VERTICAL_PULL,
    LUNGE,
    ROTATION,
    CARRY,
    GENERIC,
}

private enum class Segment {
    TORSO,
    UPPER_ARM,
    FOREARM,
    THIGH,
    SHIN,
    CORE,
}

private enum class BiomechPlane(val label: String) {
    SAGITTAL("Sagital"),
    FRONTAL("Frontal"),
    TRANSVERSE("Transversal"),
}

private enum class AnimSpeed(val label: String, val durationMs: Int) {
    SLOW("Lento", 3900),
    NORMAL("Normal", 2600),
    FAST("Rápido", 1700),
}

private enum class VisualLegend(
    val label: String,
    val description: String,
    val color: Color,
) {
    ALL(
        label = "Todo",
        description = "Muestra todos los vectores y referencias mecánicas al mismo tiempo.",
        color = Color(0xFF455A64),
    ),
    GRAVITY(
        label = "Gravedad",
        description = "Flecha roja: dirección de la carga hacia abajo sobre el cuerpo.",
        color = Color(0xFFE53935),
    ),
    GROUND_REACTION(
        label = "Reacción",
        description = "Flecha verde: fuerza que devuelve el suelo desde el apoyo.",
        color = Color(0xFF43A047),
    ),
    TORQUE(
        label = "Torque",
        description = "Flecha turquesa: momento rotacional dominante del gesto.",
        color = Color(0xFF00A6A6),
    ),
    LEVER_ARM(
        label = "Palanca",
        description = "Línea punteada: brazo de palanca entre el eje y la carga.",
        color = Color(0xFF5E35B1),
    ),
}

private data class Pose(
    val torsoDeg: Float,
    val upperArmDeg: Float,
    val foreArmDeg: Float,
    val thighDeg: Float,
    val shinDeg: Float,
)

private data class VisualSpec(
    val pattern: VisualPattern,
    val accent: Color,
    val title: String,
    val subtitle: String,
    val highlight: Set<Segment>,
)

@Composable
internal fun ExerciseBiomechVisual(exercise: ExerciseMuscleInfo) {
    val spec = remember(exercise.id) { specForExercise(exercise) }
    BiomechVisualCard(spec)
}

@Composable
internal fun MuscleBiomechVisual(muscleId: String, muscleName: String, color: Color) {
    val spec = remember(muscleId, muscleName, color) { specForMuscle(muscleId, muscleName, color) }
    BiomechVisualCard(spec)
}

@Composable
internal fun JointBiomechVisual(jointType: String, jointName: String? = null) {
    val spec = remember(jointType, jointName) { specForJoint(jointType, jointName) }
    BiomechVisualCard(spec)
}

@Composable
internal fun PatternBiomechVisual(patternId: String, patternName: String) {
    val spec = remember(patternId, patternName) { specForPattern(patternId, patternName) }
    BiomechVisualCard(spec)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BiomechVisualCard(spec: VisualSpec) {
    var plane by remember(spec.title) { mutableStateOf(BiomechPlane.SAGITTAL) }
    var speed by remember(spec.title) { mutableStateOf(AnimSpeed.NORMAL) }
    var activeLegend by remember(spec.title) { mutableStateOf(VisualLegend.ALL) }
    var tooltipLegend by remember(spec.title) { mutableStateOf<VisualLegend?>(null) }
    val scope = rememberCoroutineScope()
    var tooltipJob by remember(spec.title) { mutableStateOf<Job?>(null) }

    val transition = rememberInfiniteTransition(label = "wikilab-bio")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speed.durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (speed.durationMs * 0.72f).toInt()),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = spec.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = spec.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BiomechPlane.entries.forEach { option ->
                    FilterChip(
                        selected = plane == option,
                        onClick = { plane = option },
                        label = { Text(option.label) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimSpeed.entries.forEach { option ->
                    FilterChip(
                        selected = speed == option,
                        onClick = { speed = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            spec.accent.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        ),
                    ),
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(230.dp)) {
                drawBiomechScene(
                    spec = spec,
                    phase = phase,
                    pulse = pulse,
                    plane = plane,
                    legend = activeLegend,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Leyenda interactiva",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VisualLegend.entries.forEach { item ->
                    Box(
                        modifier = Modifier.combinedClickable(
                            onClick = { activeLegend = item },
                            onLongClick = {
                                tooltipLegend = item
                                tooltipJob?.cancel()
                                tooltipJob = scope.launch {
                                    delay(2200)
                                    tooltipLegend = null
                                }
                            },
                        ),
                    ) {
                        FilterChip(
                            selected = activeLegend == item,
                            onClick = { activeLegend = item },
                            label = { Text(item.label) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .height(10.dp)
                                        .fillMaxWidth(0.02f)
                                        .clip(RoundedCornerShape(50))
                                        .background(item.color),
                                )
                            },
                        )
                    }
                }
            }
            tooltipLegend?.let { legend: VisualLegend ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                ) {
                    Text(
                        text = legend.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            Text(
                activeLegend.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.drawBiomechScene(
    spec: VisualSpec,
    phase: Float,
    pulse: Float,
    plane: BiomechPlane,
    legend: VisualLegend,
) {
    val neutral = Color(0xFF90A4AE)
    val accent = spec.accent
    val floorY = size.height * 0.84f
    val baseCx = size.width * 0.5f

    val pose = interpolatedPose(spec.pattern, phase)

    val xOffset = when (plane) {
        BiomechPlane.SAGITTAL -> 0f
        BiomechPlane.FRONTAL -> (sin(phase * PI * 2.0).toFloat()) * 18f
        BiomechPlane.TRANSVERSE -> (cos(phase * PI * 2.0).toFloat()) * 22f
    }
    val cx = baseCx + xOffset

    val hip = Offset(cx, floorY - size.height * 0.24f)
    val shoulder = pointFrom(hip, size.height * 0.26f, pose.torsoDeg)
    val head = pointFrom(shoulder, size.height * 0.10f, pose.torsoDeg)

    val elbowFront = pointFrom(shoulder, size.height * 0.18f, pose.upperArmDeg)
    val wristFront = pointFrom(elbowFront, size.height * 0.16f, pose.foreArmDeg)
    val elbowBack = pointFrom(shoulder, size.height * 0.16f, pose.upperArmDeg - 18f)
    val wristBack = pointFrom(elbowBack, size.height * 0.14f, pose.foreArmDeg - 16f)

    val kneeFront = pointFrom(hip, size.height * 0.24f, pose.thighDeg)
    val ankleFront = pointFrom(kneeFront, size.height * 0.21f, pose.shinDeg)
    val kneeBack = pointFrom(hip, size.height * 0.23f, pose.thighDeg + 12f)
    val ankleBack = pointFrom(kneeBack, size.height * 0.20f, pose.shinDeg + 10f)

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, accent.copy(alpha = 0.06f)),
            startY = size.height * 0.5f,
            endY = size.height,
        ),
        topLeft = Offset.Zero,
        size = size,
    )

    drawLine(
        color = neutral.copy(alpha = 0.45f),
        start = Offset(0f, floorY),
        end = Offset(size.width, floorY),
        strokeWidth = 2.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), phase * 60f),
    )

    drawMotionCue(spec.pattern, shoulder, hip, kneeFront, accent, phase, pulse)
    drawForceOverlay(
        pattern = spec.pattern,
        shoulder = shoulder,
        hip = hip,
        wrist = wristFront,
        ankle = ankleFront,
        accent = accent,
        phase = phase,
        plane = plane,
        legend = legend,
    )
    drawLeverOverlay(
        pattern = spec.pattern,
        shoulder = shoulder,
        hip = hip,
        knee = kneeFront,
        ankle = ankleFront,
        accent = accent,
        phase = phase,
        legend = legend,
    )

    drawSegment(shoulder, elbowBack, Segment.UPPER_ARM, spec, accent, neutral, shadow = true)
    drawSegment(elbowBack, wristBack, Segment.FOREARM, spec, accent, neutral, shadow = true)
    drawSegment(hip, kneeBack, Segment.THIGH, spec, accent, neutral, shadow = true)
    drawSegment(kneeBack, ankleBack, Segment.SHIN, spec, accent, neutral, shadow = true)

    drawSegment(hip, shoulder, Segment.TORSO, spec, accent, neutral)
    drawSegment(shoulder, elbowFront, Segment.UPPER_ARM, spec, accent, neutral)
    drawSegment(elbowFront, wristFront, Segment.FOREARM, spec, accent, neutral)
    drawSegment(hip, kneeFront, Segment.THIGH, spec, accent, neutral)
    drawSegment(kneeFront, ankleFront, Segment.SHIN, spec, accent, neutral)

    if (Segment.CORE in spec.highlight) {
        drawCircle(
            color = accent.copy(alpha = 0.12f + 0.12f * pulse),
            radius = size.height * 0.07f,
            center = Offset((hip.x + shoulder.x) / 2f, (hip.y + shoulder.y) / 2f),
        )
    }

    drawCircle(color = Color(0xFF37474F), radius = size.height * 0.05f, center = head)
    drawCircle(color = Color.White.copy(alpha = 0.12f), radius = size.height * 0.018f, center = Offset(head.x + 6f, head.y - 4f))

    drawJoint(shoulder, accent, pulse, Segment.TORSO in spec.highlight)
    drawJoint(elbowFront, accent, pulse, Segment.UPPER_ARM in spec.highlight)
    drawJoint(wristFront, accent, pulse, Segment.FOREARM in spec.highlight)
    drawJoint(hip, accent, pulse, Segment.CORE in spec.highlight || Segment.TORSO in spec.highlight)
    drawJoint(kneeFront, accent, pulse, Segment.THIGH in spec.highlight)
    drawJoint(ankleFront, accent, pulse, Segment.SHIN in spec.highlight)

    drawPlaneHint(plane, accent)
}

private fun DrawScope.drawSegment(
    from: Offset,
    to: Offset,
    segment: Segment,
    spec: VisualSpec,
    accent: Color,
    neutral: Color,
    shadow: Boolean = false,
) {
    val highlighted = segment in spec.highlight
    val color = when {
        shadow -> neutral.copy(alpha = 0.34f)
        highlighted -> accent
        else -> neutral.copy(alpha = 0.75f)
    }
    val width = when {
        shadow -> 7f
        highlighted -> 10f
        else -> 8f
    }

    if (highlighted && !shadow) {
        drawLine(
            color = accent.copy(alpha = 0.20f),
            start = from,
            end = to,
            strokeWidth = width + 10f,
            cap = StrokeCap.Round,
        )
    }

    drawLine(color = color, start = from, end = to, strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.drawJoint(center: Offset, accent: Color, pulse: Float, active: Boolean) {
    drawCircle(color = Color(0xFF263238), radius = 8f, center = center)
    if (active) {
        drawCircle(
            color = accent.copy(alpha = 0.35f + 0.20f * pulse),
            radius = 12f,
            center = center,
            style = Stroke(width = 3f),
        )
    }
}

private fun DrawScope.drawMotionCue(
    pattern: VisualPattern,
    shoulder: Offset,
    hip: Offset,
    knee: Offset,
    accent: Color,
    phase: Float,
    pulse: Float,
) {
    fun cueArc(center: Offset, radius: Float, startDeg: Float, sweep: Float) {
        drawArc(
            color = accent.copy(alpha = 0.45f),
            startAngle = startDeg,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), phase * 60f)),
        )
        val dotAngle = (startDeg + sweep * phase) * PI.toFloat() / 180f
        val dot = Offset(
            x = center.x + cos(dotAngle) * radius,
            y = center.y + sin(dotAngle) * radius,
        )
        drawCircle(accent.copy(alpha = 0.6f + 0.3f * pulse), radius = 5f, center = dot)
    }

    when (pattern) {
        VisualPattern.HORIZONTAL_PUSH -> cueArc(shoulder, 46f, -35f, 80f)
        VisualPattern.HORIZONTAL_PULL -> cueArc(shoulder, 46f, 160f, -85f)
        VisualPattern.VERTICAL_PUSH -> cueArc(shoulder, 42f, -120f, 65f)
        VisualPattern.VERTICAL_PULL -> cueArc(shoulder, 42f, -45f, -65f)
        VisualPattern.HINGE -> cueArc(hip, 52f, -130f, 70f)
        VisualPattern.SQUAT -> {
            cueArc(hip, 50f, 210f, 85f)
            cueArc(knee, 42f, 230f, 72f)
        }
        VisualPattern.LUNGE -> {
            cueArc(hip, 50f, 210f, 80f)
            cueArc(knee, 44f, 220f, 70f)
        }
        VisualPattern.ROTATION -> cueArc(Offset((shoulder.x + hip.x) / 2f, (shoulder.y + hip.y) / 2f), 36f, 30f, 300f)
        VisualPattern.CARRY -> cueArc(shoulder, 26f, 70f, -140f)
        VisualPattern.GENERIC -> cueArc(hip, 46f, 220f, 80f)
    }
}

private fun DrawScope.drawForceOverlay(
    pattern: VisualPattern,
    shoulder: Offset,
    hip: Offset,
    wrist: Offset,
    ankle: Offset,
    accent: Color,
    phase: Float,
    plane: BiomechPlane,
    legend: VisualLegend,
) {
    val gravitySelected = legend == VisualLegend.ALL || legend == VisualLegend.GRAVITY
    val reactionSelected = legend == VisualLegend.ALL || legend == VisualLegend.GROUND_REACTION
    val torqueSelected = legend == VisualLegend.ALL || legend == VisualLegend.TORQUE

    val gravityColor = Color(0xFFE53935).copy(alpha = if (gravitySelected) 0.82f else 0.18f)
    val reactionColor = Color(0xFF43A047).copy(alpha = if (reactionSelected) 0.82f else 0.18f)
    val torqueColor = accent.copy(alpha = if (torqueSelected) 0.88f else 0.18f)

    fun arrow(from: Offset, to: Offset, color: Color, dashed: Boolean = false) {
        drawLine(
            color = color,
            start = from,
            end = to,
            strokeWidth = 3.2f,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 7f), phase * 40f) else null,
        )
        val vx = to.x - from.x
        val vy = to.y - from.y
        val len = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(1f)
        val ux = vx / len
        val uy = vy / len
        val left = Offset(to.x - ux * 11f + -uy * 6f, to.y - uy * 11f + ux * 6f)
        val right = Offset(to.x - ux * 11f + uy * 6f, to.y - uy * 11f - ux * 6f)
        drawLine(color, to, left, strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color, to, right, strokeWidth = 3f, cap = StrokeCap.Round)
    }

    if (gravitySelected || legend == VisualLegend.ALL) {
        arrow(
            from = Offset((shoulder.x + hip.x) / 2f, (shoulder.y + hip.y) / 2f - 26f),
            to = Offset((shoulder.x + hip.x) / 2f, (shoulder.y + hip.y) / 2f + 36f),
            color = gravityColor,
        )
    }

    if (reactionSelected || legend == VisualLegend.ALL) {
        arrow(
            from = Offset(ankle.x, ankle.y + 34f),
            to = Offset(ankle.x, ankle.y - 24f),
            color = reactionColor,
        )
    }

    val torqueAnchor = when (pattern) {
        VisualPattern.HORIZONTAL_PUSH, VisualPattern.HORIZONTAL_PULL, VisualPattern.VERTICAL_PUSH, VisualPattern.VERTICAL_PULL -> shoulder
        VisualPattern.CARRY -> wrist
        else -> hip
    }

    val torqueLen = when (plane) {
        BiomechPlane.SAGITTAL -> 42f
        BiomechPlane.FRONTAL -> 34f
        BiomechPlane.TRANSVERSE -> 48f
    }
    val torqueTo = Offset(
        torqueAnchor.x + torqueLen * cos((phase * PI * 2f).toFloat()),
        torqueAnchor.y + torqueLen * sin((phase * PI * 2f).toFloat()),
    )
    if (torqueSelected || legend == VisualLegend.ALL) {
        arrow(torqueAnchor, torqueTo, torqueColor, dashed = true)
    }
}

private fun DrawScope.drawLeverOverlay(
    pattern: VisualPattern,
    shoulder: Offset,
    hip: Offset,
    knee: Offset,
    ankle: Offset,
    accent: Color,
    phase: Float,
    legend: VisualLegend,
) {
    val selected = legend == VisualLegend.ALL || legend == VisualLegend.LEVER_ARM
    val leverColor = accent.copy(alpha = 0.55f)
    val base = when (pattern) {
        VisualPattern.SQUAT, VisualPattern.LUNGE -> knee
        VisualPattern.HINGE -> hip
        else -> shoulder
    }
    val load = when (pattern) {
        VisualPattern.SQUAT, VisualPattern.LUNGE -> ankle
        VisualPattern.HINGE -> shoulder
        else -> hip
    }

    if (selected) {
        drawLine(
            color = leverColor,
            start = base,
            end = load,
            strokeWidth = 2.6f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), phase * 70f),
        )

        drawCircle(
            color = leverColor.copy(alpha = 0.25f),
            radius = 12f + 5f * phase,
            center = base,
            style = Stroke(width = 2.5f),
        )
    }
}

private fun DrawScope.drawPlaneHint(plane: BiomechPlane, accent: Color) {
    val x = size.width - 118f
    val y = 18f
    drawRoundRect(
        color = Color(0xFF102027).copy(alpha = 0.15f),
        topLeft = Offset(x, y),
        size = Size(100f, 46f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
    )
    drawLine(
        color = accent.copy(alpha = 0.75f),
        start = Offset(x + 14f, y + 24f),
        end = Offset(x + 86f, y + 24f),
        strokeWidth = 2.8f,
    )
    when (plane) {
        BiomechPlane.SAGITTAL -> drawCircle(accent, radius = 4.5f, center = Offset(x + 28f, y + 24f))
        BiomechPlane.FRONTAL -> drawCircle(accent, radius = 4.5f, center = Offset(x + 50f, y + 24f))
        BiomechPlane.TRANSVERSE -> drawCircle(accent, radius = 4.5f, center = Offset(x + 72f, y + 24f))
    }
}

private fun DrawScope.pointFrom(origin: Offset, length: Float, angleDeg: Float): Offset {
    val rad = angleDeg * PI.toFloat() / 180f
    return Offset(
        x = origin.x + cos(rad) * length,
        y = origin.y + sin(rad) * length,
    )
}

private fun interpolatedPose(pattern: VisualPattern, t: Float): Pose {
    val a: Pose
    val b: Pose
    when (pattern) {
        VisualPattern.HORIZONTAL_PUSH -> {
            a = Pose(-86f, -40f, -12f, 96f, 88f)
            b = Pose(-86f, -8f, 10f, 98f, 90f)
        }
        VisualPattern.HORIZONTAL_PULL -> {
            a = Pose(-84f, 6f, -8f, 96f, 88f)
            b = Pose(-84f, -40f, -35f, 98f, 90f)
        }
        VisualPattern.VERTICAL_PUSH -> {
            a = Pose(-86f, -86f, -82f, 98f, 90f)
            b = Pose(-86f, -56f, -48f, 100f, 92f)
        }
        VisualPattern.VERTICAL_PULL -> {
            a = Pose(-84f, -36f, -22f, 98f, 90f)
            b = Pose(-84f, -76f, -66f, 100f, 92f)
        }
        VisualPattern.HINGE -> {
            a = Pose(-74f, -20f, 0f, 120f, 98f)
            b = Pose(-48f, -16f, 4f, 102f, 88f)
        }
        VisualPattern.SQUAT -> {
            a = Pose(-80f, -20f, -6f, 112f, 92f)
            b = Pose(-60f, -18f, -4f, 76f, 56f)
        }
        VisualPattern.LUNGE -> {
            a = Pose(-78f, -22f, -5f, 112f, 90f)
            b = Pose(-62f, -18f, -3f, 84f, 60f)
        }
        VisualPattern.ROTATION -> {
            a = Pose(-84f, -24f, -2f, 102f, 92f)
            b = Pose(-78f, -10f, 18f, 102f, 92f)
        }
        VisualPattern.CARRY -> {
            a = Pose(-86f, -78f, -82f, 98f, 88f)
            b = Pose(-86f, -76f, -80f, 108f, 96f)
        }
        VisualPattern.GENERIC -> {
            a = Pose(-84f, -32f, -12f, 102f, 90f)
            b = Pose(-78f, -16f, -2f, 94f, 82f)
        }
    }
    return Pose(
        torsoDeg = lerp(a.torsoDeg, b.torsoDeg, t),
        upperArmDeg = lerp(a.upperArmDeg, b.upperArmDeg, t),
        foreArmDeg = lerp(a.foreArmDeg, b.foreArmDeg, t),
        thighDeg = lerp(a.thighDeg, b.thighDeg, t),
        shinDeg = lerp(a.shinDeg, b.shinDeg, t),
    )
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun specForExercise(exercise: ExerciseMuscleInfo): VisualSpec {
    val name = exercise.name.lowercase()
    val force = exercise.force?.lowercase().orEmpty()
    val (pattern, highlight, subtitle) = when {
        name.contains("sentadilla") || name.contains("squat") -> Triple(
            VisualPattern.SQUAT,
            setOf(Segment.CORE, Segment.THIGH, Segment.SHIN),
            "Modelo de flexión-extensión dominante con lectura de fuerza y palancas.",
        )
        name.contains("peso muerto") || force.contains("bisagra") || name.contains("hinge") -> Triple(
            VisualPattern.HINGE,
            setOf(Segment.TORSO, Segment.CORE, Segment.THIGH),
            "Modelo de bisagra de cadera y control espinal bajo carga.",
        )
        force.contains("empuje") -> Triple(
            VisualPattern.HORIZONTAL_PUSH,
            setOf(Segment.UPPER_ARM, Segment.FOREARM, Segment.CORE),
            "Modelo de empuje con transferencia desde tronco a extremidad superior.",
        )
        force.contains("tir") -> Triple(
            VisualPattern.HORIZONTAL_PULL,
            setOf(Segment.TORSO, Segment.UPPER_ARM, Segment.FOREARM),
            "Modelo de tracción con énfasis en cintura escapular.",
        )
        name.contains("press militar") || name.contains("overhead") -> Triple(
            VisualPattern.VERTICAL_PUSH,
            setOf(Segment.UPPER_ARM, Segment.FOREARM, Segment.CORE),
            "Modelo de empuje vertical y estabilidad central.",
        )
        name.contains("dominada") || name.contains("jalon") || name.contains("pull") -> Triple(
            VisualPattern.VERTICAL_PULL,
            setOf(Segment.TORSO, Segment.UPPER_ARM, Segment.FOREARM),
            "Modelo de tirón vertical con control escapular.",
        )
        name.contains("zancada") || name.contains("lunge") -> Triple(
            VisualPattern.LUNGE,
            setOf(Segment.CORE, Segment.THIGH, Segment.SHIN),
            "Modelo unilateral con transferencia de fuerza en apoyo asimétrico.",
        )
        else -> Triple(
            VisualPattern.GENERIC,
            setOf(Segment.CORE, Segment.THIGH),
            "Modelo general del gesto dominante del ejercicio.",
        )
    }
    return VisualSpec(
        pattern = pattern,
        accent = Color(0xFF00A6A6),
        title = "Simulación mecánica",
        subtitle = subtitle,
        highlight = highlight,
    )
}

private fun specForMuscle(muscleId: String, muscleName: String, color: Color): VisualSpec {
    val id = muscleId.lowercase()
    val (pattern, highlight, subtitle) = when {
        id.contains("pectoral") -> Triple(
            VisualPattern.HORIZONTAL_PUSH,
            setOf(Segment.UPPER_ARM, Segment.CORE),
            "Cómo participa este grupo muscular en un empuje horizontal.",
        )
        id.contains("dorsal") || id.contains("espalda") || id.contains("trapecio") -> Triple(
            VisualPattern.HORIZONTAL_PULL,
            setOf(Segment.TORSO, Segment.UPPER_ARM, Segment.FOREARM),
            "Cómo participa este grupo muscular en una tracción.",
        )
        id.contains("deltoides") || id.contains("hombro") -> Triple(
            VisualPattern.VERTICAL_PUSH,
            setOf(Segment.UPPER_ARM, Segment.CORE),
            "Cómo se expresa este grupo en elevación y empuje vertical.",
        )
        id.contains("glúte") || id.contains("isquio") || id.contains("erect") -> Triple(
            VisualPattern.HINGE,
            setOf(Segment.CORE, Segment.TORSO, Segment.THIGH),
            "Cómo contribuye en bisagra de cadera y soporte posterior.",
        )
        id.contains("cuádr") || id.contains("pantorr") || id.contains("pierna") -> Triple(
            VisualPattern.SQUAT,
            setOf(Segment.THIGH, Segment.SHIN, Segment.CORE),
            "Cómo participa en patrones dominantes de rodilla.",
        )
        id.contains("abdomen") || id.contains("core") -> Triple(
            VisualPattern.ROTATION,
            setOf(Segment.CORE, Segment.TORSO),
            "Cómo estabiliza y transmite fuerza en el tronco.",
        )
        else -> Triple(
            VisualPattern.GENERIC,
            setOf(Segment.CORE, Segment.THIGH),
            "Patrón mecánico representativo de este grupo muscular.",
        )
    }
    return VisualSpec(
        pattern = pattern,
        accent = color,
        title = "Patrón biomecánico de $muscleName",
        subtitle = subtitle,
        highlight = highlight,
    )
}

private fun specForJoint(jointType: String, jointName: String?): VisualSpec {
    val (pattern, subtitle) = when (jointType.lowercase()) {
        "ball-socket" -> VisualPattern.ROTATION to "Movilidad multiplanar, vectores de carga y control del centro articular."
        "hinge" -> VisualPattern.HINGE to "Movimiento de bisagra con lectura de palanca y línea de fuerza."
        else -> VisualPattern.SQUAT to "Flexión-extensión predominante con referencia de eje articular."
    }
    return VisualSpec(
        pattern = pattern,
        accent = Color(0xFF1E88E5),
        title = "Mecánica de ${jointName ?: "la articulación"}",
        subtitle = subtitle,
        highlight = setOf(Segment.THIGH, Segment.SHIN, Segment.CORE),
    )
}

private fun specForPattern(patternId: String, patternName: String): VisualSpec {
    val id = patternId.lowercase()
    val (pattern, highlight, subtitle) = when {
        id.contains("horizontal-push") -> Triple(
            VisualPattern.HORIZONTAL_PUSH,
            setOf(Segment.UPPER_ARM, Segment.FOREARM, Segment.CORE),
            "Secuencia de empuje horizontal con lectura de torque y palanca.",
        )
        id.contains("horizontal-pull") -> Triple(
            VisualPattern.HORIZONTAL_PULL,
            setOf(Segment.TORSO, Segment.UPPER_ARM, Segment.FOREARM),
            "Secuencia de tracción horizontal con control escapular.",
        )
        id.contains("vertical-push") -> Triple(
            VisualPattern.VERTICAL_PUSH,
            setOf(Segment.UPPER_ARM, Segment.FOREARM, Segment.CORE),
            "Secuencia de empuje vertical y estabilidad del tronco.",
        )
        id.contains("vertical-pull") -> Triple(
            VisualPattern.VERTICAL_PULL,
            setOf(Segment.TORSO, Segment.UPPER_ARM, Segment.FOREARM),
            "Secuencia de tirón vertical con tracción dorsal.",
        )
        id.contains("hinge") -> Triple(
            VisualPattern.HINGE,
            setOf(Segment.TORSO, Segment.CORE, Segment.THIGH),
            "Secuencia de bisagra con dominancia de cadera.",
        )
        id.contains("squat") -> Triple(
            VisualPattern.SQUAT,
            setOf(Segment.THIGH, Segment.SHIN, Segment.CORE),
            "Secuencia dominante de rodilla con soporte del tronco.",
        )
        id.contains("lunge") -> Triple(
            VisualPattern.LUNGE,
            setOf(Segment.THIGH, Segment.SHIN, Segment.CORE),
            "Secuencia unilateral de estabilidad y producción de fuerza.",
        )
        id.contains("rotation") || id.contains("anti-rotation") -> Triple(
            VisualPattern.ROTATION,
            setOf(Segment.CORE, Segment.TORSO),
            "Secuencia de rotación y control anti-rotacional del tronco.",
        )
        id.contains("carry") -> Triple(
            VisualPattern.CARRY,
            setOf(Segment.CORE, Segment.TORSO, Segment.UPPER_ARM),
            "Secuencia de marcha cargada con tensión global.",
        )
        else -> Triple(
            VisualPattern.GENERIC,
            setOf(Segment.CORE, Segment.THIGH),
            "Secuencia mecánica general del patrón.",
        )
    }
    return VisualSpec(
        pattern = pattern,
        accent = Color(0xFF7E57C2),
        title = "Simulación del patrón: $patternName",
        subtitle = subtitle,
        highlight = highlight,
    )
}
