package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.biomechanics.*
import kotlin.math.*

// ─── MAIN SCREEN ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiomechanicsScreen(
    onBack: () -> Unit,
) {
    var selectedLift by remember { mutableStateOf(LiftType.SQUAT_LOW_BAR) }
    var depth by remember { mutableFloatStateOf(0.7f) }
    var weightKg by remember { mutableFloatStateOf(100f) }
    var stanceWidth by remember { mutableFloatStateOf(0.3f) }
    var barPosition by remember { mutableFloatStateOf(0.5f) }

    val anthropometry = remember { Anthropometry() }

    val solve = remember(selectedLift, depth, weightKg, stanceWidth, barPosition) {
        BiomechanicsEngine.solve(
            lift = selectedLift,
            depth = depth.toDouble(),
            anthropometry = anthropometry,
            barWeightKg = weightKg.toDouble(),
            stanceWidth = stanceWidth.toDouble(),
            barPosition = barPosition.toDouble(),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Palitos Biomecánicos", fontWeight = FontWeight.Black)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Lift Selector ───────────────────────────────────────────
            item {
                LiftSelector(
                    selected = selectedLift,
                    onSelect = { selectedLift = it },
                )
            }

            // ─── Stickman Canvas ─────────────────────────────────────────
            item {
                StickmanCanvas(
                    solve = solve,
                    anthropometry = anthropometry,
                    depth = depth,
                )
            }

            // ─── Controls ────────────────────────────────────────────────
            item {
                BiomechanicsControls(
                    depth = depth,
                    onDepthChange = { depth = it },
                    weightKg = weightKg,
                    onWeightChange = { weightKg = it },
                    stanceWidth = stanceWidth,
                    onStanceChange = { stanceWidth = it },
                    barPosition = barPosition,
                    onBarPositionChange = { barPosition = it },
                    isSquat = selectedLift.name.contains("SQUAT"),
                    isDeadlift = selectedLift.name.contains("DEADLIFT"),
                )
            }

            // ─── Joint Angles ────────────────────────────────────────────
            item {
                JointAnglesCard(solve)
            }

            // ─── Lever Mechanics ─────────────────────────────────────────
            item {
                LeverMechanicsCard(solve)
            }

            // ─── Torque Analysis ─────────────────────────────────────────
            item {
                TorqueAnalysisCard(solve)
            }

            // ─── Anthropometry ───────────────────────────────────────────
            item {
                AnthropometryCard(anthropometry)
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── LIFT SELECTOR ────────────────────────────────────────────────────────

@Composable
private fun LiftSelector(
    selected: LiftType,
    onSelect: (LiftType) -> Unit,
) {
    Column {
        Text(
            "LEVANTAMIENTO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        // Group by body part
        val upperBody = LiftType.entries.filter { it.bodyPart == "upper" }
        val lowerBody = LiftType.entries.filter { it.bodyPart == "lower" }

        Text(
            "Tren Superior",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E88E5),
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            upperBody.forEach { lift ->
                FilterChip(
                    selected = selected == lift,
                    onClick = { onSelect(lift) },
                    label = {
                        Text(
                            lift.label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Tren Inferior",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF43A047),
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            lowerBody.forEach { lift ->
                FilterChip(
                    selected = selected == lift,
                    onClick = { onSelect(lift) },
                    label = {
                        Text(
                            lift.label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }
    }
}

// ─── STICKMAN CANVAS ──────────────────────────────────────────────────────

@Composable
private fun StickmanCanvas(
    solve: BiomechanicalSolve,
    anthropometry: Anthropometry,
    depth: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A2E))
            .padding(8.dp),
    ) {
        // Lift name
        Text(
            solve.liftType.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            drawStickman(
                solve = solve,
                canvasWidth = size.width,
                canvasHeight = size.height,
                anthropometry = anthropometry,
            )
        }
    }
}

private fun DrawScope.drawStickman(
    solve: BiomechanicalSolve,
    canvasWidth: Float,
    canvasHeight: Float,
    anthropometry: Anthropometry,
) {
    val cx = canvasWidth / 2f
    val scale = canvasHeight / 250f

    // Joint positions based on solve
    val headY = canvasHeight * 0.12f
    val shoulderY = headY + 30f * scale
    val elbowY = shoulderY + anthropometry.humerusLengthCm.toFloat() * scale * 0.8f
    val wristY = elbowY + anthropometry.forearmLengthCm.toFloat() * scale * 0.8f

    val hipY = shoulderY + anthropometry.torsoLengthCm.toFloat() * scale * 0.9f
    val kneeY = hipY + anthropometry.femurLengthCm.toFloat() * scale * 0.9f
    val ankleY = kneeY + anthropometry.tibiaLengthCm.toFloat() * scale * 0.9f

    // Angular offsets based on solve
    val torsoOffset = sin(Math.toRadians(solve.torsoAngle)).toFloat() * anthropometry.torsoLengthCm.toFloat() * scale * 0.3f
    val hipX = cx + torsoOffset
    val shoulderX = cx - torsoOffset * 0.5f

    val stickColor = Color(0xFF00FF88)
    val jointColor = Color(0xFFFFD600)
    val barColor = Color(0xFFCCCCCC)

    // Draw body
    val path = Path().apply {
        // Head
        moveTo(shoulderX, headY)
        // Neck
        lineTo(shoulderX, shoulderY)
        // Torso
        lineTo(hipX, hipY)

        // Left leg
        val leftKneeX = hipX - 20f
        val leftAnkleX = hipX - 25f
        moveTo(hipX, hipY)
        lineTo(leftKneeX, kneeY)
        lineTo(leftAnkleX, ankleY)

        // Right leg
        val rightKneeX = hipX + 20f
        val rightAnkleX = hipX + 25f
        moveTo(hipX, hipY)
        lineTo(rightKneeX, kneeY)
        lineTo(rightAnkleX, ankleY)

        // Left arm
        val leftElbowX = shoulderX - 15f
        val leftWristX = shoulderX - 20f
        moveTo(shoulderX, shoulderY)
        lineTo(leftElbowX, elbowY)
        lineTo(leftWristX, wristY)

        // Right arm
        val rightElbowX = shoulderX + 15f
        val rightWristX = shoulderX + 20f
        moveTo(shoulderX, shoulderY)
        lineTo(rightElbowX, elbowY)
        lineTo(rightWristX, wristY)
    }

    drawPath(
        path = path,
        color = stickColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round),
    )

    // Head circle
    drawCircle(
        color = stickColor,
        radius = 18f,
        center = Offset(shoulderX, headY),
        style = Stroke(width = 3f),
    )

    // Joint dots
    val joints = listOf(
        Offset(shoulderX, shoulderY),
        Offset(hipX, hipY),
        Offset(hipX - 20f, kneeY),
        Offset(hipX + 20f, kneeY),
        Offset(shoulderX - 15f, elbowY),
        Offset(shoulderX + 15f, elbowY),
    )

    joints.forEach { pos ->
        drawCircle(
            color = jointColor,
            radius = 6f,
            center = pos,
        )
    }

    // Bar representation for lifts
    if (solve.liftType.bodyPart == "upper") {
        // Horizontal bar at wrists
        drawLine(
            color = barColor,
            start = Offset(shoulderX - 60f, wristY),
            end = Offset(shoulderX + 60f, wristY),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
        // Weight plates
        drawCircle(barColor, 12f, Offset(shoulderX - 60f, wristY))
        drawCircle(barColor, 12f, Offset(shoulderX + 60f, wristY))
    } else {
        // Bar behind neck for squats, or in hands for deadlift
        val barY = when (solve.liftType) {
            LiftType.DEADLIFT_CONVENTIONAL, LiftType.DEADLIFT_SUMO -> wristY
            else -> shoulderY - 10f
        }
        drawLine(
            color = barColor,
            start = Offset(shoulderX - 50f, barY),
            end = Offset(shoulderX + 50f, barY),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
        drawCircle(barColor, 14f, Offset(shoulderX - 50f, barY))
        drawCircle(barColor, 14f, Offset(shoulderX + 50f, barY))
    }

    // Angle arc indicators
    solve.jointAngles.forEach { ja ->
        val arcCenter = when (ja.joint) {
            "rodilla", "rodilla frontal", "rodilla trasera" -> Offset(hipX - 20f, kneeY)
            "cadera", "cadera frontal" -> Offset(hipX, hipY)
            "hombro" -> Offset(shoulderX, shoulderY)
            "codo" -> Offset(shoulderX - 15f, elbowY)
            "tobillo" -> Offset(hipX - 25f, ankleY)
            else -> null
        }

        if (arcCenter != null) {
            // Draw angle text
            drawContext.canvas.nativeCanvas.drawText(
                "${ja.angleDegrees.toInt()}°",
                arcCenter.x + 12f,
                arcCenter.y - 8f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(255, 214, 0)
                    textSize = 28f
                    isAntiAlias = true
                }
            )
        }
    }
}

// ─── CONTROLS ─────────────────────────────────────────────────────────────

@Composable
private fun BiomechanicsControls(
    depth: Float,
    onDepthChange: (Float) -> Unit,
    weightKg: Float,
    onWeightChange: (Float) -> Unit,
    stanceWidth: Float,
    onStanceChange: (Float) -> Unit,
    barPosition: Float,
    onBarPositionChange: (Float) -> Unit,
    isSquat: Boolean,
    isDeadlift: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "CONTROLES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        // Depth
        Text("Profundidad: ${(depth * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Slider(
            value = depth,
            onValueChange = onDepthChange,
            valueRange = 0f..1f,
            steps = 19,
        )

        // Weight
        Text("Peso: ${weightKg.toInt()} kg", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Slider(
            value = weightKg,
            onValueChange = onWeightChange,
            valueRange = 20f..300f,
            steps = 27,
        )

        // Stance width (squats only)
        if (isSquat) {
            Text("Apertura: ${(stanceWidth * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Slider(
                value = stanceWidth,
                onValueChange = onStanceChange,
                valueRange = 0.1f..1f,
                steps = 9,
            )
        }

        // Bar position (squats only)
        if (isSquat) {
            Text("Posición barra: ${if (barPosition < 0.5) "Baja" else if (barPosition < 0.8) "Media" else "Alta"}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Slider(
                value = barPosition,
                onValueChange = onBarPositionChange,
                valueRange = 0f..1f,
                steps = 9,
            )
        }
    }
}

// ─── JOINT ANGLES CARD ────────────────────────────────────────────────────

@Composable
private fun JointAnglesCard(solve: BiomechanicalSolve) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "ÁNGULOS ARTICULARES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        solve.jointAngles.forEach { ja ->
            val angleColor = when {
                ja.angleDegrees < 30 -> Color(0xFF43A047)
                ja.angleDegrees < 60 -> Color(0xFFFF8F00)
                else -> Color(0xFFE53935)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ja.joint.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${ja.angleDegrees.toInt()}°",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = angleColor,
                )
            }

            LinearProgressIndicator(
                progress = { (ja.angleDegrees / 120f).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = angleColor,
                trackColor = angleColor.copy(alpha = 0.15f),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── LEVER MECHANICS CARD ─────────────────────────────────────────────────

@Composable
private fun LeverMechanicsCard(solve: BiomechanicalSolve) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "MECÁNICA DE PALANCAS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        solve.leverClassification.forEach { (joint, lever) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (lever) {
                            LeverType.FIRST_CLASS -> Color(0xFFE53935).copy(alpha = 0.12f)
                            LeverType.SECOND_CLASS -> Color(0xFFFF8F00).copy(alpha = 0.12f)
                            LeverType.THIRD_CLASS -> Color(0xFF1E88E5).copy(alpha = 0.12f)
                            LeverType.FOURTH_CLASS -> Color(0xFF9C27B0).copy(alpha = 0.12f)
                        },
                    ) {
                        Text(
                            lever.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (lever) {
                                LeverType.FIRST_CLASS -> Color(0xFFE53935)
                                LeverType.SECOND_CLASS -> Color(0xFFFF8F00)
                                LeverType.THIRD_CLASS -> Color(0xFF1E88E5)
                                LeverType.FOURTH_CLASS -> Color(0xFF9C27B0)
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        joint.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    lever.description.take(100) + "...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

// ─── TORQUE ANALYSIS CARD ─────────────────────────────────────────────────

@Composable
private fun TorqueAnalysisCard(solve: BiomechanicalSolve) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "ANÁLISIS DE TORQUE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        val maxTorque = solve.estimatedTorque.values.maxOrNull() ?: 1.0

        solve.estimatedTorque.forEach { (joint, torqueNm) ->
            val ratio = (torqueNm / maxTorque).coerceIn(0.0, 1.0)
            val torqueColor = when {
                ratio < 0.3 -> Color(0xFF43A047)
                ratio < 0.7 -> Color(0xFFFF8F00)
                else -> Color(0xFFE53935)
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        joint.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${"%.0f".format(torqueNm)} Nm",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = torqueColor,
                    )
                }
                LinearProgressIndicator(
                    progress = { ratio.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = torqueColor,
                    trackColor = torqueColor.copy(alpha = 0.15f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Overall difficulty
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Dificultad Global",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            val diffColor = when {
                solve.overallDifficulty < 0.4 -> Color(0xFF43A047)
                solve.overallDifficulty < 0.7 -> Color(0xFFFF8F00)
                else -> Color(0xFFE53935)
            }
            Text(
                "${"%.0f".format(solve.overallDifficulty * 100)}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = diffColor,
            )
        }
    }
}

// ─── ANTHROPOMETRY CARD ───────────────────────────────────────────────────

@Composable
private fun AnthropometryCard(anthro: Anthropometry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                "ANTROPOMETRÍA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Profile description
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ) {
            Text(
                anthro.anthropometricProfile,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Measurements
        val measurements = listOf(
            "Altura" to "${anthro.heightCm.toInt()} cm",
            "Peso" to "${anthro.weightKg.toInt()} kg",
            "Fémur" to "${anthro.femurLengthCm} cm",
            "Tibia" to "${anthro.tibiaLengthCm} cm",
            "Torso" to "${anthro.torsoLengthCm} cm",
            "Húmero" to "${anthro.humerusLengthCm} cm",
            "Antebrazo" to "${anthro.forearmLengthCm} cm",
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            measurements.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    row.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$label: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                value,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // Ratios
        Spacer(Modifier.height(8.dp))
        Text("Proporciones:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(
            "Fémur/Torso: ${"%.2f".format(anthro.femurRatio)} • Tíbia/Fémur: ${"%.2f".format(anthro.tibiaRatio)} • Brazo/Torso: ${"%.2f".format(anthro.armToTorsoRatio)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── FLOW ROW HELPER ──────────────────────────────────────────────────────

@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
    ) {
        content()
    }
}
