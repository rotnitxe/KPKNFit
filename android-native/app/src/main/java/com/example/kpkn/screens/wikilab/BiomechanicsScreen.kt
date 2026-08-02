package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.biomechanics.*
import kotlin.math.*

private enum class BiomechanicsPanel {
    SIMULACION,
    CUERPO,
    ANALISIS,
}

// ═══════════════════════════════════════════════════════════════════════
// BIOMECHANICS SCREEN — "Palitos Biomecánicos"
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BiomechanicsScreen(
    onBack: () -> Unit,
) {
    var selectedLift by remember { mutableStateOf(LiftType.SQUAT_LOW_BAR) }
    var depth by remember { mutableFloatStateOf(0.7f) }
    var weightKg by remember { mutableFloatStateOf(100f) }
    var stanceWidth by remember { mutableFloatStateOf(0.3f) }
    var barPosition by remember { mutableFloatStateOf(0.5f) }
    var athleteHeightCm by remember { mutableFloatStateOf(175f) }
    var athleteWeightKg by remember { mutableFloatStateOf(80f) }
    var femurLengthCm by remember { mutableFloatStateOf(48f) }
    var tibiaLengthCm by remember { mutableFloatStateOf(42f) }
    var torsoLengthCm by remember { mutableFloatStateOf(50f) }
    var humerusLengthCm by remember { mutableFloatStateOf(34f) }
    var forearmLengthCm by remember { mutableFloatStateOf(28f) }
    var selectedPanel by remember { mutableStateOf(BiomechanicsPanel.SIMULACION) }

    val anthropometry = remember(
        athleteHeightCm,
        athleteWeightKg,
        femurLengthCm,
        tibiaLengthCm,
        torsoLengthCm,
        humerusLengthCm,
        forearmLengthCm,
    ) {
        Anthropometry(
            heightCm = athleteHeightCm.toDouble(),
            weightKg = athleteWeightKg.toDouble(),
            femurLengthCm = femurLengthCm.toDouble(),
            tibiaLengthCm = tibiaLengthCm.toDouble(),
            torsoLengthCm = torsoLengthCm.toDouble(),
            humerusLengthCm = humerusLengthCm.toDouble(),
            forearmLengthCm = forearmLengthCm.toDouble(),
            footLengthCm = (athleteHeightCm * 0.154f).toDouble(),
            armSpanCm = (humerusLengthCm + forearmLengthCm + torsoLengthCm * 0.38f).toDouble() * 2.0,
        )
    }

    val showSquatControls = remember(selectedLift) {
        selectedLift.name.contains("SQUAT")
    }
    val showBarPositionControl = showSquatControls && selectedLift != LiftType.SQUAT_FRONT

    val solve = remember(selectedLift, depth, weightKg, stanceWidth, barPosition, anthropometry) {
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
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Biomecánica",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LiftSelector(selected = selectedLift, onSelect = { selectedLift = it }) }
            item { StickmanCanvas(solve = solve, anthropometry = anthropometry) }
            item { QuickSummaryRow(solve) }
            item { BiomechanicsInterpretationCard(solve) }
            item {
                BiomechanicsPanelSelector(
                    selectedPanel = selectedPanel,
                    onSelect = { selectedPanel = it },
                )
            }
            when (selectedPanel) {
                BiomechanicsPanel.SIMULACION -> {
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
                            isSquat = showSquatControls,
                            showBarPositionControl = showBarPositionControl,
                            lift = selectedLift,
                        )
                    }
                }
                BiomechanicsPanel.CUERPO -> {
                    item {
                        AnthropometryControlsCard(
                            heightCm = athleteHeightCm,
                            onHeightChange = { athleteHeightCm = it },
                            athleteWeightKg = athleteWeightKg,
                            onAthleteWeightChange = { athleteWeightKg = it },
                            femurLengthCm = femurLengthCm,
                            onFemurLengthChange = { femurLengthCm = it },
                            tibiaLengthCm = tibiaLengthCm,
                            onTibiaLengthChange = { tibiaLengthCm = it },
                            torsoLengthCm = torsoLengthCm,
                            onTorsoLengthChange = { torsoLengthCm = it },
                            humerusLengthCm = humerusLengthCm,
                            onHumerusLengthChange = { humerusLengthCm = it },
                            forearmLengthCm = forearmLengthCm,
                            onForearmLengthChange = { forearmLengthCm = it },
                        )
                    }
                    item { AnthropometryCard(anthropometry) }
                }
                BiomechanicsPanel.ANALISIS -> {
                    item { JointAnglesCard(solve) }
                    item { LeverMechanicsCard(solve) }
                    item { TorqueAnalysisCard(solve) }
                }
            }
            item { BiomechanicsDisclaimerCard() }
            item { Spacer(Modifier.height(140.dp)) }
        }
    }
}

@Composable
private fun BiomechanicsPanelSelector(
    selectedPanel: BiomechanicsPanel,
    onSelect: (BiomechanicsPanel) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            BiomechanicsPanel.SIMULACION to "Simulación",
            BiomechanicsPanel.CUERPO to "Medidas",
            BiomechanicsPanel.ANALISIS to "Análisis",
        ).forEach { (panel, label) ->
            FilterChip(
                selected = selectedPanel == panel,
                onClick = { onSelect(panel) },
                label = { Text(label, fontWeight = if (selectedPanel == panel) FontWeight.Black else FontWeight.Medium) },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LIFT SELECTOR
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiftSelector(selected: LiftType, onSelect: (LiftType) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Text(
            "LEVANTAMIENTO",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            color = Color.White.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(8.dp), CircleShape, Color(0xFF1E88E5)) {}
            Spacer(Modifier.width(6.dp))
            Text(
                "Tren Superior",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5),
            )
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LiftType.entries.filter { it.bodyPart == "upper" }.forEach { lift ->
                FilterChip(
                    selected = selected == lift,
                    onClick  = { onSelect(lift) },
                    label    = { Text(lift.label, style = MaterialTheme.typography.labelSmall) },
                    shape    = RoundedCornerShape(4.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(8.dp), CircleShape, Color(0xFF43A047)) {}
            Spacer(Modifier.width(6.dp))
            Text(
                "Tren Inferior",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF43A047),
            )
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LiftType.entries.filter { it.bodyPart == "lower" }.forEach { lift ->
                FilterChip(
                    selected = selected == lift,
                    onClick  = { onSelect(lift) },
                    label    = { Text(lift.label, style = MaterialTheme.typography.labelSmall) },
                    shape    = RoundedCornerShape(4.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// STICKMAN CANVAS — frame + dispatch
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun StickmanCanvas(
    solve: BiomechanicalSolve,
    anthropometry: Anthropometry,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D1B2A), Color(0xFF1B2838), Color(0xFF0D1B2A)),
                )
            )
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                solve.liftType.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            val diffColor = when {
                solve.overallDifficulty < 0.35 -> Color(0xFF4FC3F7)
                solve.overallDifficulty < 0.65 -> Color(0xFFFFB74D)
                else -> Color(0xFFFF7043)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = diffColor.copy(alpha = 0.15f)) {
                Text(
                    "${"%.0f".format(solve.overallDifficulty * 100)}% demanda relativa",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = diffColor,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            drawStickman(solve, size.width, size.height, anthropometry)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CanvasLegendItem(Color(0xFF00E676), "Cuerpo")
            CanvasLegendItem(Color(0xFFFFD600), "Articulaciones")
            CanvasLegendItem(Color(0xFFBBBBBB), "Barra")
            CanvasLegendItem(Color(0xFF42A5F5), "Momento")
        }
    }
}

@Composable
private fun CanvasLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(6.dp), CircleShape, color) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// DRAWING INFRASTRUCTURE
// ═══════════════════════════════════════════════════════════════════════

private data class DC(
    val body: Color,
    val joint: Color,
    val bar: Color,
    val moment: Color,
)

/** Degrees → radians (Float) */
private fun r(deg: Double) = Math.toRadians(deg).toFloat()
private fun r(deg: Float)  = Math.toRadians(deg.toDouble()).toFloat()

/** Midpoint of two Offsets */
private fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

/** Segment between two points */
private fun DrawScope.seg(a: Offset, b: Offset, c: Color, w: Float = 5f) =
    drawLine(c, a, b, strokeWidth = w, cap = StrokeCap.Round)

/** Joint dot with glow */
private fun DrawScope.jnt(p: Offset, c: Color, innerR: Float = 6f) {
    drawCircle(c.copy(alpha = 0.28f), radius = innerR * 1.9f, center = p)
    drawCircle(c, radius = innerR, center = p)
}

/** Head circle */
private fun DrawScope.hd(center: Offset, radius: Float, c: Color) {
    drawCircle(c.copy(alpha = 0.14f), radius = radius, center = center)
    drawCircle(c, radius = radius, center = center, style = Stroke(width = 4f))
}

/** Angle label near a joint */
private fun DrawScope.lbl(p: Offset, deg: Int, dx: Float = 15f, dy: Float = -14f) {
    drawContext.canvas.nativeCanvas.drawText(
        "$deg°", p.x + dx, p.y + dy,
        android.graphics.Paint().apply {
            color     = android.graphics.Color.rgb(255, 214, 0)
            textSize  = 22f
            isAntiAlias = true
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
        },
    )
}

/** Horizontal moment-arm indicator */
private fun DrawScope.momentArmLine(from: Offset, len: Float, c: Color) {
    if (len < 3f) return
    val to = Offset(from.x + len + 8f, from.y)
    drawLine(c.copy(alpha = 0.75f), from, to, strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawCircle(c, 3.5f, to)
}

/** Barbell: shaft + two plates */
private fun DrawScope.barbell(
    left: Offset,
    right: Offset,
    plateR: Float,
    c: Color,
) {
    seg(left, right, c, 6f)
    listOf(left, right).forEach { e ->
        drawCircle(c, plateR, e)
        drawCircle(c.copy(alpha = 0.25f), plateR * 1.38f, e)
    }
}

/** Gravity reference dashes */
private fun DrawScope.gravityLine(cx: Float, fromY: Float, toY: Float) {
    drawLine(
        Color.White.copy(alpha = 0.07f),
        Offset(cx, fromY), Offset(cx, toY),
        strokeWidth = 1f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
    )
}

// ═══════════════════════════════════════════════════════════════════════
// MAIN ENTRY POINT — dispatch per lift type
// ═══════════════════════════════════════════════════════════════════════

private fun DrawScope.drawStickman(
    solve: BiomechanicalSolve,
    cw: Float,
    ch: Float,
    anthro: Anthropometry,
) {
    val cx     = cw / 2f
    val floorY = ch - 18f

    // Pixel scale: fit standing body height in ~82 % of canvas
    val bodyCm = anthro.tibiaLengthCm + anthro.femurLengthCm + anthro.torsoLengthCm
    val scale  = (ch * 0.82f) / bodyCm.toFloat()

    val tibiaPx   = (anthro.tibiaLengthCm   * scale).toFloat()
    val femurPx   = (anthro.femurLengthCm   * scale).toFloat()
    val torsoPx   = (anthro.torsoLengthCm   * scale).toFloat()
    val humerusPx = (anthro.humerusLengthCm * scale).toFloat()
    val forearmPx = (anthro.forearmLengthCm * scale).toFloat()
    val headR     = (torsoPx * 0.22f).coerceIn(22f, 44f)

    val S = DC(
        body   = Color(0xFF00E676),
        joint  = Color(0xFFFFD600),
        bar    = Color(0xFFBBBBBB),
        moment = Color(0xFF42A5F5),
    )

    // Floor
    drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, floorY), Offset(cw, floorY), strokeWidth = 1.5f)
    gravityLine(cx, 0f, floorY)

    when (solve.liftType) {
        LiftType.BENCH_PRESS ->
            poseBench(solve, cx, ch, humerusPx, forearmPx, torsoPx, headR, S)

        LiftType.OVERHEAD_PRESS ->
            poseOverhead(solve, cx, floorY, tibiaPx, femurPx, torsoPx, humerusPx, forearmPx, headR, S)

        LiftType.BARBELL_ROW ->
            poseRow(solve, cx, floorY, tibiaPx, femurPx, torsoPx, humerusPx, forearmPx, headR, S)

        LiftType.PULL_UP ->
            posePullUp(solve, cx, ch, torsoPx, humerusPx, forearmPx, femurPx, tibiaPx, headR, S)

        LiftType.DIP ->
            poseDip(solve, cx, ch, torsoPx, humerusPx, forearmPx, tibiaPx, headR, S)

        LiftType.HIP_THRUST ->
            poseHipThrust(solve, cx, floorY, ch, tibiaPx, femurPx, torsoPx, headR, S)

        LiftType.LUNGE ->
            poseLunge(solve, cx, floorY, tibiaPx, femurPx, torsoPx, humerusPx, forearmPx, headR, S)

        LiftType.DEADLIFT_CONVENTIONAL, LiftType.DEADLIFT_SUMO ->
            poseDeadlift(solve, cx, floorY, tibiaPx, femurPx, torsoPx, humerusPx, forearmPx, headR, S)

        else -> // SQUAT variants
            poseSquat(solve, cx, floorY, tibiaPx, femurPx, torsoPx, humerusPx, forearmPx, headR, S)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: SQUAT (LOW-BAR, HIGH-BAR, FRONT)
// ═══════════════════════════════════════════════════════════════════════

/**
 * Side-profile squat. Joint positions derived from:
 *   - solve.femurAngle: forward tilt of femur from vertical (30°–90°)
 *   - solve.torsoAngle: forward tilt of torso from vertical (15°–45°)
 *   tibia angle is set proportionally to femur so the knee tracks forward.
 */
private fun DrawScope.poseSquat(
    solve: BiomechanicalSolve,
    cx: Float, floorY: Float,
    tibiaPx: Float, femurPx: Float, torsoPx: Float,
    humerusPx: Float, forearmPx: Float, headR: Float,
    S: DC,
) {
    val femurRad = r(solve.femurAngle)
    val torsoRad = r(solve.torsoAngle)
    val tibiaRad = r(solve.femurAngle * 0.35) // knee tracks forward, tibia less than femur

    val stance = 28f   // half-stance in canvas px
    val xShift = 30f   // center the figure for a squatting pose

    val lAnkle = Offset(cx - stance + xShift, floorY)
    val rAnkle = Offset(cx + stance + xShift, floorY)

    // Knee: up and forward from ankle
    val lKnee = Offset(lAnkle.x + sin(tibiaRad) * tibiaPx, lAnkle.y - cos(tibiaRad) * tibiaPx)
    val rKnee = Offset(rAnkle.x + sin(tibiaRad) * tibiaPx, rAnkle.y - cos(tibiaRad) * tibiaPx)

    // Hip: UP and BACKWARD from knee (femur tilts forward → hip sits behind knee)
    val lHip = Offset(lKnee.x - sin(femurRad) * femurPx, lKnee.y - cos(femurRad) * femurPx)
    val rHip = Offset(rKnee.x - sin(femurRad) * femurPx, rKnee.y - cos(femurRad) * femurPx)
    val hipMid = mid(lHip, rHip)

    // Shoulder: up and forward from hip (torso leans forward)
    val shoulder = Offset(hipMid.x + sin(torsoRad) * torsoPx, hipMid.y - cos(torsoRad) * torsoPx)

    // Head: above shoulder (follows neck line)
    val headCtr = Offset(
        shoulder.x + sin(torsoRad) * (headR * 0.8f),
        shoulder.y - headR - 4f,
    )

    // Bar on back (squat): sits at shoulder level, perpendicular to torso
    val barHW = 70f
    val barCtr = Offset(shoulder.x - sin(torsoRad) * 8f, shoulder.y + cos(torsoRad) * 8f)
    barbell(
        left  = Offset(barCtr.x - barHW, barCtr.y),
        right = Offset(barCtr.x + barHW, barCtr.y),
        plateR = 16f, c = S.bar,
    )

    // Arms gripping bar
    val gripSpread = barHW * 0.55f
    val lGrip = Offset(barCtr.x - gripSpread, barCtr.y)
    val rGrip = Offset(barCtr.x + gripSpread, barCtr.y)
    seg(Offset(shoulder.x - 18f, shoulder.y), lGrip, S.body.copy(alpha = 0.65f), 4f)
    seg(Offset(shoulder.x + 18f, shoulder.y), rGrip, S.body.copy(alpha = 0.65f), 4f)

    // Legs (back leg slightly faded)
    seg(lAnkle, lKnee, S.body.copy(alpha = 0.50f), 4f)
    seg(lKnee, lHip,   S.body.copy(alpha = 0.50f), 4f)
    seg(rAnkle, rKnee, S.body, 5f)
    seg(rKnee, rHip,   S.body, 5f)
    seg(lHip, rHip,    S.body, 4f)  // hip width line

    // Torso
    seg(hipMid, shoulder, S.body, 6f)
    // Shoulder width
    seg(Offset(shoulder.x - 18f, shoulder.y), Offset(shoulder.x + 18f, shoulder.y), S.body, 4f)

    // Head & feet
    hd(headCtr, headR, S.body)
    seg(rAnkle, Offset(rAnkle.x + 20f, rAnkle.y), S.body, 3f)
    seg(lAnkle, Offset(lAnkle.x - 20f, lAnkle.y), S.body, 3f)

    // Joints
    listOf(rKnee, lKnee, hipMid, shoulder, rAnkle, lAnkle).forEach { jnt(it, S.joint) }

    // Moment arms + angle labels
    val maxT = solve.estimatedTorque.values.maxOrNull() ?: 1.0
    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "rodilla" -> rKnee
            "cadera"  -> hipMid
            "tobillo" -> rAnkle
            else      -> null
        } ?: return@forEach
        val t   = solve.estimatedTorque[ja.joint] ?: 0.0
        val len = ((t / maxT) * 40f).toFloat()
        momentArmLine(pos, len, S.moment)
        lbl(pos, ja.angleDegrees.toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: DEADLIFT (CONVENTIONAL + SUMO)
// ═══════════════════════════════════════════════════════════════════════

/**
 * Hip-hinge from floor. At depth→0 the torso is nearly horizontal (bar at floor).
 * At depth→1 the torso rises toward vertical (lockout).
 *
 * Engine gives:
 *   solve.torsoAngle = 90 - hipAngle*0.7, decreases as depth increases
 *   (69° at bottom → 13° at top) — we use this directly as forward lean.
 *   solve.femurAngle = kneeAngle (20°–60°), used for knee bend.
 */
private fun DrawScope.poseDeadlift(
    solve: BiomechanicalSolve,
    cx: Float, floorY: Float,
    tibiaPx: Float, femurPx: Float, torsoPx: Float,
    humerusPx: Float, forearmPx: Float, headR: Float,
    S: DC,
) {
    val torsoRad = r(solve.torsoAngle)               // forward lean of torso
    val kneeBend = r(solve.femurAngle * 0.45)        // shin angle (small for DL)

    // Sumo has wider stance
    val stance = if (solve.liftType == LiftType.DEADLIFT_SUMO) 44f else 22f

    val lAnkle = Offset(cx - stance, floorY)
    val rAnkle = Offset(cx + stance, floorY)

    // Shins stay nearly vertical in deadlift
    val lKnee = Offset(lAnkle.x + sin(kneeBend) * tibiaPx, lAnkle.y - cos(kneeBend) * tibiaPx)
    val rKnee = Offset(rAnkle.x + sin(kneeBend) * tibiaPx, rAnkle.y - cos(kneeBend) * tibiaPx)

    // Hip: above and slightly back from knee
    val hipOffset = r(solve.femurAngle * 0.25)
    val lHip = Offset(lKnee.x - sin(hipOffset) * femurPx, lKnee.y - cos(hipOffset) * femurPx)
    val rHip = Offset(rKnee.x - sin(hipOffset) * femurPx, rKnee.y - cos(hipOffset) * femurPx)
    val hipMid = mid(lHip, rHip)

    // Shoulder: leaning forward from hip
    val shoulder = Offset(hipMid.x + sin(torsoRad) * torsoPx, hipMid.y - cos(torsoRad) * torsoPx)

    // Head follows torso direction
    val headCtr = Offset(
        shoulder.x + sin(torsoRad) * (headR * 0.7f),
        shoulder.y - headR - 2f,
    )

    // Bar at floor level (standard 22 cm off ground)
    val barY    = floorY - (tibiaPx * 0.15f).coerceAtLeast(16f)
    val barHW   = 72f
    barbell(Offset(cx - barHW, barY), Offset(cx + barHW, barY), plateR = 18f, c = S.bar)

    // Arms: hang from shoulder down to bar
    val lWrist = Offset(cx - 30f, barY)
    val rWrist = Offset(cx + 30f, barY)
    val lElbow = Offset(
        (shoulder.x - 10f + lWrist.x) / 2f,
        (shoulder.y + lWrist.y) / 2f,
    )
    val rElbow = Offset(
        (shoulder.x + 10f + rWrist.x) / 2f,
        (shoulder.y + rWrist.y) / 2f,
    )

    // Back leg faded
    seg(lAnkle, lKnee, S.body.copy(alpha = 0.50f), 4f)
    seg(lKnee, lHip,   S.body.copy(alpha = 0.50f), 4f)
    seg(rAnkle, rKnee, S.body, 5f)
    seg(rKnee, rHip,   S.body, 5f)
    seg(lHip, rHip,    S.body, 4f)

    // Torso
    seg(hipMid, shoulder, S.body, 6f)
    seg(Offset(shoulder.x - 16f, shoulder.y), Offset(shoulder.x + 16f, shoulder.y), S.body, 4f)

    // Arms
    seg(Offset(shoulder.x - 12f, shoulder.y), lElbow, S.body.copy(alpha = 0.70f), 4f)
    seg(lElbow, lWrist, S.body.copy(alpha = 0.70f), 4f)
    seg(Offset(shoulder.x + 12f, shoulder.y), rElbow, S.body.copy(alpha = 0.70f), 4f)
    seg(rElbow, rWrist, S.body.copy(alpha = 0.70f), 4f)

    hd(headCtr, headR, S.body)
    seg(lAnkle, Offset(lAnkle.x - 18f, lAnkle.y), S.body, 3f)
    seg(rAnkle, Offset(rAnkle.x + 18f, rAnkle.y), S.body, 3f)

    listOf(rKnee, lKnee, hipMid, shoulder, rAnkle, lAnkle, rWrist, lWrist).forEach { jnt(it, S.joint) }

    val maxT = solve.estimatedTorque.values.maxOrNull() ?: 1.0
    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "rodilla"       -> rKnee
            "cadera"        -> hipMid
            "columna lumbar" -> mid(hipMid, shoulder)
            else            -> null
        } ?: return@forEach
        momentArmLine(pos, ((( solve.estimatedTorque[ja.joint] ?: 0.0) / maxT) * 40f).toFloat(), S.moment)
        lbl(pos, ja.angleDegrees.toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: BENCH PRESS
// ═══════════════════════════════════════════════════════════════════════

/**
 * Person lying horizontally. Arms push bar up.
 * solve.jointAngles has "hombro" and "codo" angles.
 * depth=0 → bar at chest (arms bent), depth=1 → arms extended.
 */
private fun DrawScope.poseBench(
    solve: BiomechanicalSolve,
    cx: Float, ch: Float,
    humerusPx: Float, forearmPx: Float, torsoPx: Float,
    headR: Float, S: DC,
) {
    val personY = ch * 0.58f  // figure lies at 58% of canvas height

    // Shoulder angle from vertical (0° = arms vertical/up, 90° = horizontal)
    val shoulderAngleDeg = solve.jointAngles.find { it.joint == "hombro" }?.angleDegrees ?: 45.0
    val elbowAngleDeg    = solve.jointAngles.find { it.joint == "codo"   }?.angleDegrees ?: 60.0

    // Convert to drawing angle: at depth=0 arms bent ~90° from body, at depth=1 arms extended up
    // shoulderAngle in engine grows with depth (40→90°). Map to canvas: 0°=down, 90°=up
    val armLiftRad = r((shoulderAngleDeg).coerceIn(0.0, 90.0))
    val elbowBendRad = r((elbowAngleDeg * 0.6).coerceIn(0.0, 90.0))

    // Body horizontal
    val hipPos      = Offset(cx - torsoPx * 0.45f, personY)
    val shoulderPos = Offset(cx + torsoPx * 0.55f, personY)
    val headCtr     = Offset(shoulderPos.x + headR * 1.5f, personY)

    // Legs bent (person on a bench)
    val kneePos = Offset(hipPos.x, personY - torsoPx * 0.38f)
    val footPos = Offset(hipPos.x + torsoPx * 0.25f, kneePos.y)

    // Arms lift upward from shoulder (depth drives angle)
    val lElbow = Offset(
        shoulderPos.x - sin(armLiftRad) * humerusPx * 0.5f,
        shoulderPos.y - cos(armLiftRad) * humerusPx,
    )
    val rElbow = Offset(
        shoulderPos.x + sin(armLiftRad) * humerusPx * 0.5f,
        shoulderPos.y - cos(armLiftRad) * humerusPx,
    )
    val lWrist = Offset(lElbow.x - sin(elbowBendRad) * forearmPx * 0.4f, lElbow.y - cos(elbowBendRad) * forearmPx)
    val rWrist = Offset(rElbow.x + sin(elbowBendRad) * forearmPx * 0.4f, rElbow.y - cos(elbowBendRad) * forearmPx)

    val barMid = mid(lWrist, rWrist)
    val barHW  = 70f

    // Bench surface (brown)
    drawLine(
        Color(0xFF5D4037).copy(alpha = 0.65f),
        Offset(hipPos.x - torsoPx * 0.05f, personY + 14f),
        Offset(shoulderPos.x + headR * 0.5f, personY + 14f),
        strokeWidth = 10f, cap = StrokeCap.Round,
    )

    // Bar
    barbell(Offset(barMid.x - barHW, barMid.y), Offset(barMid.x + barHW, barMid.y), 15f, S.bar)

    // Body
    seg(hipPos, shoulderPos, S.body, 6f)
    seg(hipPos, kneePos, S.body, 5f)
    seg(kneePos, footPos, S.body, 4f)

    // Arms
    seg(shoulderPos, lElbow, S.body.copy(alpha = 0.75f), 4f)
    seg(lElbow, lWrist, S.body.copy(alpha = 0.75f), 4f)
    seg(shoulderPos, rElbow, S.body.copy(alpha = 0.75f), 4f)
    seg(rElbow, rWrist, S.body.copy(alpha = 0.75f), 4f)

    hd(headCtr, headR, S.body)

    listOf(hipPos, shoulderPos, lElbow, rElbow, lWrist, rWrist, kneePos).forEach { jnt(it, S.joint) }

    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "hombro" -> shoulderPos
            "codo"   -> rElbow
            else     -> null
        } ?: return@forEach
        lbl(pos, ja.angleDegrees.toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: OVERHEAD PRESS
// ═══════════════════════════════════════════════════════════════════════

/**
 * Standing. Bar travels from front-rack (depth=0) to overhead (depth=1).
 * Arms rise above head as depth increases.
 */
private fun DrawScope.poseOverhead(
    solve: BiomechanicalSolve,
    cx: Float, floorY: Float,
    tibiaPx: Float, femurPx: Float, torsoPx: Float,
    humerusPx: Float, forearmPx: Float, headR: Float,
    S: DC,
) {
    // Standing: small angles
    val torsoRad = r(solve.torsoAngle.coerceIn(0.0, 30.0))
    val kneeBend = r(8.0) // almost straight legs

    val lAnkle = Offset(cx - 22f, floorY)
    val rAnkle = Offset(cx + 22f, floorY)
    val lKnee  = Offset(lAnkle.x, lAnkle.y - tibiaPx)
    val rKnee  = Offset(rAnkle.x, rAnkle.y - tibiaPx)
    val lHip   = Offset(lKnee.x,  lKnee.y  - femurPx)
    val rHip   = Offset(rKnee.x,  rKnee.y  - femurPx)
    val hipMid = mid(lHip, rHip)

    val shoulder = Offset(hipMid.x + sin(torsoRad) * torsoPx, hipMid.y - cos(torsoRad) * torsoPx)
    val headCtr  = Offset(shoulder.x + sin(torsoRad) * (headR * 0.5f), shoulder.y - headR - 4f)

    // shoulder angle from engine: grows from 20° (front rack) to 90° (overhead)
    val shoulderAngleDeg = solve.jointAngles.find { it.joint == "hombro" }?.angleDegrees ?: 45.0
    val elbowAngleDeg    = solve.jointAngles.find { it.joint == "codo"   }?.angleDegrees ?: 30.0

    // Map shoulder angle: 0° = arms at sides, 90° = fully overhead
    val armAngle  = r(shoulderAngleDeg)  // goes up as it increases
    val elbowBend = r(elbowAngleDeg * 0.5)

    val lElbow = Offset(shoulder.x - sin(armAngle) * humerusPx, shoulder.y - cos(armAngle) * humerusPx)
    val rElbow = Offset(shoulder.x + sin(armAngle) * humerusPx, shoulder.y - cos(armAngle) * humerusPx)
    val lWrist = Offset(lElbow.x - sin(elbowBend) * forearmPx * 0.3f, lElbow.y - cos(elbowBend) * forearmPx)
    val rWrist = Offset(rElbow.x + sin(elbowBend) * forearmPx * 0.3f, rElbow.y - cos(elbowBend) * forearmPx)

    val barMid = mid(lWrist, rWrist)
    val barHW  = 72f
    barbell(Offset(barMid.x - barHW, barMid.y), Offset(barMid.x + barHW, barMid.y), 14f, S.bar)

    seg(lAnkle, lKnee, S.body.copy(alpha = 0.50f), 4f)
    seg(lKnee, lHip,   S.body.copy(alpha = 0.50f), 4f)
    seg(rAnkle, rKnee, S.body, 5f)
    seg(rKnee, rHip,   S.body, 5f)
    seg(lHip, rHip,    S.body, 4f)
    seg(hipMid, shoulder, S.body, 6f)
    seg(Offset(shoulder.x - 16f, shoulder.y), Offset(shoulder.x + 16f, shoulder.y), S.body, 4f)

    seg(shoulder, lElbow, S.body.copy(alpha = 0.75f), 4f)
    seg(lElbow, lWrist,   S.body.copy(alpha = 0.75f), 4f)
    seg(shoulder, rElbow, S.body.copy(alpha = 0.75f), 4f)
    seg(rElbow, rWrist,   S.body.copy(alpha = 0.75f), 4f)

    hd(headCtr, headR, S.body)
    seg(rAnkle, Offset(rAnkle.x + 18f, rAnkle.y), S.body, 3f)
    seg(lAnkle, Offset(lAnkle.x - 18f, lAnkle.y), S.body, 3f)

    listOf(rKnee, lKnee, hipMid, shoulder, rElbow, lElbow, rWrist, lWrist).forEach { jnt(it, S.joint) }

    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "hombro"        -> shoulder
            "codo"          -> rElbow
            "columna lumbar" -> mid(hipMid, shoulder)
            else            -> null
        } ?: return@forEach
        lbl(pos, ja.angleDegrees.toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: BARBELL ROW
// ═══════════════════════════════════════════════════════════════════════

/**
 * Bent-over row. Torso is horizontal at bottom, angled at top.
 * solve.torsoAngle grows with depth (more horizontal = more forward lean).
 */
private fun DrawScope.poseRow(
    solve: BiomechanicalSolve,
    cx: Float, floorY: Float,
    tibiaPx: Float, femurPx: Float, torsoPx: Float,
    humerusPx: Float, forearmPx: Float, headR: Float,
    S: DC,
) {
    val torsoRad = r(solve.torsoAngle.coerceIn(15.0, 75.0))
    val kneeBend = r(12.0)

    val lAnkle = Offset(cx - 22f, floorY)
    val rAnkle = Offset(cx + 22f, floorY)
    val lKnee  = Offset(lAnkle.x + sin(kneeBend) * tibiaPx, lAnkle.y - cos(kneeBend) * tibiaPx)
    val rKnee  = Offset(rAnkle.x + sin(kneeBend) * tibiaPx, rAnkle.y - cos(kneeBend) * tibiaPx)

    val hipBend = r(15.0)
    val lHip = Offset(lKnee.x - sin(hipBend) * femurPx, lKnee.y - cos(hipBend) * femurPx)
    val rHip = Offset(rKnee.x - sin(hipBend) * femurPx, rKnee.y - cos(hipBend) * femurPx)
    val hipMid = mid(lHip, rHip)

    // Torso: leans FORWARD (large angle = near horizontal)
    val shoulder = Offset(hipMid.x + sin(torsoRad) * torsoPx, hipMid.y - cos(torsoRad) * torsoPx)
    val headCtr  = Offset(shoulder.x + sin(torsoRad) * (headR * 0.9f), shoulder.y - headR)

    // Arms: hang from shoulder, elbow bends as bar is pulled up
    val elbowAngleDeg = solve.jointAngles.find { it.joint == "codo" }?.angleDegrees ?: 30.0
    val elbowBend = r(elbowAngleDeg * 0.55)

    // Wrist at bar height (near floor)
    val barY  = floorY - tibiaPx * 0.18f
    val barHW = 68f
    barbell(Offset(cx - barHW, barY), Offset(cx + barHW, barY), 16f, S.bar)

    val lWrist = Offset(cx - 28f, barY)
    val rWrist = Offset(cx + 28f, barY)

    val lElbow = Offset(
        shoulder.x - 12f + (lWrist.x - shoulder.x + 12f) * 0.5f,
        shoulder.y + (lWrist.y - shoulder.y) * 0.5f - elbowBend * 20f,
    )
    val rElbow = Offset(
        shoulder.x + 12f + (rWrist.x - shoulder.x - 12f) * 0.5f,
        shoulder.y + (rWrist.y - shoulder.y) * 0.5f - elbowBend * 20f,
    )

    seg(lAnkle, lKnee, S.body.copy(alpha = 0.50f), 4f)
    seg(lKnee, lHip,   S.body.copy(alpha = 0.50f), 4f)
    seg(rAnkle, rKnee, S.body, 5f)
    seg(rKnee, rHip,   S.body, 5f)
    seg(lHip, rHip,    S.body, 4f)
    seg(hipMid, shoulder, S.body, 6f)
    seg(Offset(shoulder.x - 14f, shoulder.y), Offset(shoulder.x + 14f, shoulder.y), S.body, 4f)

    seg(Offset(shoulder.x - 14f, shoulder.y), lElbow, S.body.copy(alpha = 0.75f), 4f)
    seg(lElbow, lWrist, S.body.copy(alpha = 0.75f), 4f)
    seg(Offset(shoulder.x + 14f, shoulder.y), rElbow, S.body.copy(alpha = 0.75f), 4f)
    seg(rElbow, rWrist, S.body.copy(alpha = 0.75f), 4f)

    hd(headCtr, headR, S.body)
    seg(rAnkle, Offset(rAnkle.x + 18f, rAnkle.y), S.body, 3f)
    seg(lAnkle, Offset(lAnkle.x - 18f, lAnkle.y), S.body, 3f)

    listOf(rKnee, lKnee, hipMid, shoulder, rElbow, lElbow, rWrist, lWrist).forEach { jnt(it, S.joint) }

    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "hombro" -> shoulder
            "codo"   -> rElbow
            else     -> null
        } ?: return@forEach
        lbl(pos, ja.angleDegrees.toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: PULL-UP
// ═══════════════════════════════════════════════════════════════════════

/**
 * Body hanging from bar. Arms overhead.
 * depth=0 → arms fully extended (dead hang). depth=1 → chin above bar.
 */
private fun DrawScope.posePullUp(
    solve: BiomechanicalSolve,
    cx: Float, ch: Float,
    torsoPx: Float, humerusPx: Float, forearmPx: Float,
    femurPx: Float, tibiaPx: Float,
    headR: Float, S: DC,
) {
    val barY        = ch * 0.08f     // bar at top
    val barHW       = 72f
    barbell(Offset(cx - barHW, barY), Offset(cx + barHW, barY), 10f, S.bar)

    val elbowAngle  = solve.jointAngles.find { it.joint == "codo"   }?.angleDegrees ?: 30.0
    val shoulderAng = solve.jointAngles.find { it.joint == "hombro" }?.angleDegrees ?: 30.0

    // Arms extend downward from bar, bent according to depth
    val elbowBend   = r(elbowAngle)
    val lWrist      = Offset(cx - 30f, barY)
    val rWrist      = Offset(cx + 30f, barY)
    val lElbow      = Offset(lWrist.x, lWrist.y + cos(elbowBend) * forearmPx)
    val rElbow      = Offset(rWrist.x, rWrist.y + cos(elbowBend) * forearmPx)

    val shoulderRad = r(shoulderAng)
    val lShoulder   = Offset(lElbow.x + sin(shoulderRad) * humerusPx * 0.3f, lElbow.y + cos(shoulderRad) * humerusPx)
    val rShoulder   = Offset(rElbow.x - sin(shoulderRad) * humerusPx * 0.3f, rElbow.y + cos(shoulderRad) * humerusPx)
    val shoulderMid = mid(lShoulder, rShoulder)

    val hipPos  = Offset(shoulderMid.x, shoulderMid.y + torsoPx)
    val lKnee   = Offset(hipPos.x - 16f, hipPos.y + femurPx * 0.85f)
    val rKnee   = Offset(hipPos.x + 16f, hipPos.y + femurPx * 0.85f)
    val lAnkle  = Offset(lKnee.x, lKnee.y + tibiaPx * 0.85f)
    val rAnkle  = Offset(rKnee.x, rKnee.y + tibiaPx * 0.85f)
    val headCtr = Offset(shoulderMid.x, shoulderMid.y - headR - 4f)

    // Arms
    seg(lWrist, lElbow,     S.body.copy(alpha = 0.75f), 4f)
    seg(lElbow, lShoulder,  S.body.copy(alpha = 0.75f), 4f)
    seg(rWrist, rElbow,     S.body.copy(alpha = 0.75f), 4f)
    seg(rElbow, rShoulder,  S.body.copy(alpha = 0.75f), 4f)

    // Shoulder line
    seg(lShoulder, rShoulder, S.body, 4f)
    // Torso
    seg(shoulderMid, hipPos, S.body, 6f)
    // Hip line
    seg(Offset(hipPos.x - 14f, hipPos.y), Offset(hipPos.x + 14f, hipPos.y), S.body, 4f)
    // Legs (hanging, slightly bent)
    seg(Offset(hipPos.x - 14f, hipPos.y), lKnee, S.body.copy(alpha = 0.60f), 4f)
    seg(lKnee, lAnkle, S.body.copy(alpha = 0.60f), 4f)
    seg(Offset(hipPos.x + 14f, hipPos.y), rKnee, S.body, 5f)
    seg(rKnee, rAnkle, S.body, 5f)

    hd(headCtr, headR, S.body)

    listOf(lWrist, rWrist, lElbow, rElbow, lShoulder, rShoulder, shoulderMid, hipPos, lKnee, rKnee).forEach {
        jnt(it, S.joint)
    }

    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "hombro" -> shoulderMid
            "codo"   -> rElbow
            else     -> null
        } ?: return@forEach
        lbl(pos, ja.angleDegrees.toInt(), dx = 14f)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: DIP
// ═══════════════════════════════════════════════════════════════════════

/**
 * Body between bars, arms supporting weight. depth=0 → arms extended, depth=1 → deep dip.
 */
private fun DrawScope.poseDip(
    solve: BiomechanicalSolve,
    cx: Float, ch: Float,
    torsoPx: Float, humerusPx: Float, forearmPx: Float,
    tibiaPx: Float, headR: Float,
    S: DC,
) {
    val barY  = ch * 0.22f
    val barHW = 74f

    // Draw both parallel bars (left and right side dots represent bars)
    val lBarCenter = Offset(cx - barHW, barY)
    val rBarCenter = Offset(cx + barHW, barY)
    drawCircle(S.bar, 10f, lBarCenter)
    drawCircle(S.bar.copy(alpha = 0.3f), 16f, lBarCenter)
    drawCircle(S.bar, 10f, rBarCenter)
    drawCircle(S.bar.copy(alpha = 0.3f), 16f, rBarCenter)

    val elbowAngle  = solve.jointAngles.find { it.joint == "codo"   }?.angleDegrees ?: 45.0
    val shoulderAng = solve.jointAngles.find { it.joint == "hombro" }?.angleDegrees ?: 20.0

    // Arms go straight down from bars, bent at elbow
    val elbowBend = r(elbowAngle)
    val lWrist    = Offset(cx - barHW, barY)
    val rWrist    = Offset(cx + barHW, barY)
    val lElbow    = Offset(lWrist.x, lWrist.y + cos(elbowBend) * forearmPx)
    val rElbow    = Offset(rWrist.x, rWrist.y + cos(elbowBend) * forearmPx)
    val lShoulder = Offset(lElbow.x + sin(r(shoulderAng)) * humerusPx, lElbow.y + cos(r(shoulderAng)) * humerusPx)
    val rShoulder = Offset(rElbow.x - sin(r(shoulderAng)) * humerusPx, rElbow.y + cos(r(shoulderAng)) * humerusPx)
    val shMid     = mid(lShoulder, rShoulder)

    val torsoLean = r(solve.torsoAngle.coerceIn(0.0, 30.0))
    val hipPos    = Offset(shMid.x + sin(torsoLean) * torsoPx, shMid.y + cos(torsoLean) * torsoPx)
    val lKnee     = Offset(hipPos.x - 14f, hipPos.y + tibiaPx * 0.75f)
    val rKnee     = Offset(hipPos.x + 14f, hipPos.y + tibiaPx * 0.75f)
    val headCtr   = Offset(shMid.x - sin(torsoLean) * (headR + 4f), shMid.y - headR - 4f)

    seg(lWrist, lElbow, S.body.copy(alpha = 0.75f), 4f)
    seg(lElbow, lShoulder, S.body.copy(alpha = 0.75f), 4f)
    seg(rWrist, rElbow, S.body.copy(alpha = 0.75f), 4f)
    seg(rElbow, rShoulder, S.body.copy(alpha = 0.75f), 4f)
    seg(lShoulder, rShoulder, S.body, 4f)
    seg(shMid, hipPos, S.body, 6f)
    seg(Offset(hipPos.x - 14f, hipPos.y), lKnee, S.body.copy(alpha = 0.60f), 4f)
    seg(Offset(hipPos.x + 14f, hipPos.y), rKnee, S.body, 5f)

    hd(headCtr, headR, S.body)

    listOf(lElbow, rElbow, shMid, hipPos, lKnee, rKnee).forEach { jnt(it, S.joint) }

    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "hombro" -> shMid
            "codo"   -> rElbow
            else     -> null
        } ?: return@forEach
        lbl(pos, ja.angleDegrees.toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: HIP THRUST
// ═══════════════════════════════════════════════════════════════════════

/**
 * Shoulders on bench, feet on floor, bar on hips.
 * depth=0 → hips low. depth=1 → hips fully extended (parallel).
 *
 * Engine: hipAngle goes from 10° (low) to 110° (extended). Stored in jointAngles["cadera"].
 */
private fun DrawScope.poseHipThrust(
    solve: BiomechanicalSolve,
    cx: Float, floorY: Float, ch: Float,
    tibiaPx: Float, femurPx: Float, torsoPx: Float,
    headR: Float, S: DC,
) {
    val hipAngleDeg = solve.jointAngles.find { it.joint == "cadera" }?.angleDegrees ?: 45.0
    val kneeAngleDeg = solve.jointAngles.find { it.joint == "rodilla" }?.angleDegrees ?: 60.0

    // Foot flat on floor, shin at a small forward angle
    val shinAngle = r(15.0)
    val rAnkle = Offset(cx + 32f, floorY)
    val rKnee  = Offset(rAnkle.x - sin(shinAngle) * tibiaPx, rAnkle.y - cos(shinAngle) * tibiaPx)

    // Bench: upper-back support at left of canvas, elevated
    val benchX = cx - torsoPx * 0.65f
    val benchY = ch * 0.40f
    drawLine(Color(0xFF5D4037).copy(alpha = 0.7f),
        Offset(benchX - torsoPx * 0.15f, benchY),
        Offset(benchX + torsoPx * 0.25f, benchY),
        strokeWidth = 12f, cap = StrokeCap.Round,
    )

    // Shoulder on bench
    val shoulderPos = Offset(benchX, benchY)

    // Hip driven up: hipAngle drives the torso angle from horizontal
    val torsoRad = r((hipAngleDeg * 0.75).coerceIn(10.0, 80.0))
    val hipPos = Offset(
        shoulderPos.x + cos(torsoRad) * torsoPx,
        shoulderPos.y + sin(torsoRad) * torsoPx,
    )

    // Bar on hips
    val barHW = 65f
    barbell(Offset(hipPos.x - barHW, hipPos.y), Offset(hipPos.x + barHW, hipPos.y), 16f, S.bar)

    val lKnee = Offset(rKnee.x - 45f, rKnee.y)  // left knee (back leg, faded)
    val lAnkle = Offset(rAnkle.x - 45f, rAnkle.y)

    // Femur: from hip to knee
    seg(hipPos, rKnee,  S.body, 5f)
    seg(hipPos, lKnee,  S.body.copy(alpha = 0.45f), 4f)
    seg(rKnee,  rAnkle, S.body, 5f)
    seg(lKnee,  lAnkle, S.body.copy(alpha = 0.45f), 4f)

    // Torso
    seg(shoulderPos, hipPos, S.body, 6f)
    seg(Offset(shoulderPos.x - 14f, shoulderPos.y), Offset(shoulderPos.x + 14f, shoulderPos.y), S.body, 4f)

    // Head (resting on bench direction)
    val headCtr = Offset(shoulderPos.x - headR * 1.5f, shoulderPos.y - headR * 0.5f)
    hd(headCtr, headR, S.body)

    // Feet
    seg(rAnkle, Offset(rAnkle.x + 20f, rAnkle.y), S.body, 3f)
    seg(lAnkle, Offset(lAnkle.x - 20f, lAnkle.y), S.body, 3f)

    listOf(shoulderPos, hipPos, rKnee, lKnee, rAnkle, lAnkle).forEach { jnt(it, S.joint) }

    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "cadera"   -> hipPos
            "rodilla"  -> rKnee
            else       -> null
        } ?: return@forEach
        lbl(pos, ja.angleDegrees.toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════
// POSE: LUNGE
// ═══════════════════════════════════════════════════════════════════════

/**
 * Asymmetric stance. Front leg bent, rear leg extended back.
 */
private fun DrawScope.poseLunge(
    solve: BiomechanicalSolve,
    cx: Float, floorY: Float,
    tibiaPx: Float, femurPx: Float, torsoPx: Float,
    humerusPx: Float, forearmPx: Float, headR: Float,
    S: DC,
) {
    val frontKneeAngleDeg = solve.jointAngles.find { it.joint == "rodilla frontal" }?.angleDegrees ?: 60.0
    val hipAngleDeg       = solve.jointAngles.find { it.joint == "cadera frontal"  }?.angleDegrees ?: 35.0

    // Front foot forward, back foot behind
    val frontAnkle = Offset(cx + 40f, floorY)
    val backAnkle  = Offset(cx - 50f, floorY)

    // Front leg (right): bent at knee
    val frontTibiaRad = r(frontKneeAngleDeg * 0.35)
    val frontKnee = Offset(
        frontAnkle.x - sin(frontTibiaRad) * tibiaPx,
        frontAnkle.y - cos(frontTibiaRad) * tibiaPx,
    )
    val frontFemurRad = r(hipAngleDeg * 0.8)
    val frontHip = Offset(
        frontKnee.x - sin(frontFemurRad) * femurPx,
        frontKnee.y - cos(frontFemurRad) * femurPx,
    )

    // Back leg (left): extended behind, knee bent toward floor
    val backKneeFrac = 0.6f
    val backKnee = Offset(
        backAnkle.x + tibiaPx * 0.3f,
        backAnkle.y - tibiaPx * backKneeFrac,
    )
    val backHip = Offset(frontHip.x, frontHip.y)  // shared hip

    val hipMid = mid(frontHip, backHip)
    val torsoRad = r(5.0 + hipAngleDeg * 0.15)
    val shoulder = Offset(hipMid.x + sin(torsoRad) * torsoPx, hipMid.y - cos(torsoRad) * torsoPx)
    val headCtr  = Offset(shoulder.x + sin(torsoRad) * (headR * 0.5f), shoulder.y - headR - 4f)

    // Back knee at floor level (rear knee touches down at depth)
    // Draw
    seg(backAnkle, backKnee,  S.body.copy(alpha = 0.50f), 4f)
    seg(backKnee,  backHip,   S.body.copy(alpha = 0.50f), 4f)
    seg(frontAnkle, frontKnee, S.body, 5f)
    seg(frontKnee,  frontHip,  S.body, 5f)
    seg(frontHip, backHip, S.body, 4f)
    seg(hipMid, shoulder, S.body, 6f)
    seg(Offset(shoulder.x - 16f, shoulder.y), Offset(shoulder.x + 16f, shoulder.y), S.body, 4f)

    // Arms at sides
    val lElbow = Offset(shoulder.x - 14f, shoulder.y + humerusPx * 0.65f)
    val rElbow = Offset(shoulder.x + 14f, shoulder.y + humerusPx * 0.65f)
    seg(Offset(shoulder.x - 14f, shoulder.y), lElbow, S.body.copy(alpha = 0.65f), 4f)
    seg(Offset(shoulder.x + 14f, shoulder.y), rElbow, S.body.copy(alpha = 0.65f), 4f)

    hd(headCtr, headR, S.body)
    seg(frontAnkle, Offset(frontAnkle.x + 18f, frontAnkle.y), S.body, 3f)
    seg(backAnkle,  Offset(backAnkle.x - 18f, backAnkle.y),   S.body, 3f)

    listOf(frontKnee, backKnee, frontHip, backHip, hipMid, shoulder, frontAnkle, backAnkle).forEach {
        jnt(it, S.joint)
    }

    val maxT = solve.estimatedTorque.values.maxOrNull() ?: 1.0
    solve.jointAngles.forEach { ja ->
        val pos = when (ja.joint) {
            "rodilla frontal" -> frontKnee
            "cadera frontal"  -> frontHip
            "rodilla trasera" -> backKnee
            else              -> null
        } ?: return@forEach
        momentArmLine(pos, (((solve.estimatedTorque[ja.joint] ?: 0.0) / maxT) * 36f).toFloat(), S.moment)
        lbl(pos, ja.angleDegrees.toInt())
    }
}

// ═══════════════════════════════════════════════════════════════════════
// QUICK SUMMARY ROW
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun QuickSummaryRow(solve: BiomechanicalSolve) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        solve.jointAngles.take(3).forEach { ja ->
            val color = demandColor(ja.torqueRatio)
            Card(
                modifier = Modifier.widthIn(min = 100.dp),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                border = BorderStroke(1.dp, Color(0xFF2C2C2C)),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${ja.angleDegrees.toInt()}°",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                        fontWeight = FontWeight.Black,
                        color = color,
                    )
                    Text(
                        ja.joint.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                    Text(
                        demandLabel(ja.torqueRatio),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif, color = color),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BiomechanicsInterpretationCard(solve: BiomechanicalSolve) {
    val dominantJoint = solve.estimatedTorque.maxByOrNull { it.value }?.key.orEmpty()
    val dominantLabel = dominantJoint.replaceFirstChar { it.uppercase() }
    val uprightBias = if (solve.torsoAngle < 25.0) {
        "Postura relativamente erguida: suele desplazar más trabajo hacia rodilla y demanda torácica."
    } else {
        "Postura más inclinada: suele cargar más cadena posterior y eleva el brazo de momento del tronco."
    }
    val limiter = when {
        dominantJoint.contains("tobillo") -> "El cuello de botella probable es la movilidad de tobillo o la estabilidad del pie."
        dominantJoint.contains("rodilla") -> "La lectura principal está en cuánto avance de rodilla puedes tolerar sin perder pie ni cadera."
        dominantJoint.contains("cadera") -> "La cadera domina la postura actual; revisa brace, dorsales y capacidad de bisagra."
        dominantJoint.contains("lumbar") -> "La demanda se concentra en el tronco; conviene comparar variantes antes de subir carga."
        dominantJoint.contains("hombro") -> "La región limitante probable es hombro-escápula; compara rango y trayectoria."
        else -> "La limitación más probable aparece donde el gesto pide más estabilidad que rango."
    }

    WikiLabInsightCard(
        title = "INTERPRETACIÓN RÁPIDA",
        accent = Color(0xFF26A69A),
        icon = Icons.Default.Insights,
        summary = "La posición actual concentra la mayor demanda relativa en $dominantLabel. Úsalo para comparar variantes o cambios de técnica dentro del mismo atleta.",
        bullets = listOf(
            uprightBias,
            limiter,
            "Si cambias una sola variable, revisa qué articulación gana o pierde torque antes de concluir que una variante es \"mejor\".",
        ),
        footer = "Esta lectura es comparativa y educativa; no es un diagnóstico ni una prescripción clínica.",
    )
}

// ═══════════════════════════════════════════════════════════════════════
// CONTROLS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BiomechanicsControls(
    depth: Float, onDepthChange: (Float) -> Unit,
    weightKg: Float, onWeightChange: (Float) -> Unit,
    stanceWidth: Float, onStanceChange: (Float) -> Unit,
    barPosition: Float, onBarPositionChange: (Float) -> Unit,
    isSquat: Boolean,
    showBarPositionControl: Boolean,
    lift: LiftType,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, null, Modifier.size(15.dp), tint = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.width(7.dp))
            Text(
                "CONTROLES",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(12.dp))
        ControlSlider("Profundidad", "${(depth * 100).toInt()}%",    depth,      onDepthChange,   0f..1f,    19)
        ControlSlider("Peso",        "${weightKg.toInt()} kg",        weightKg,   onWeightChange,  20f..300f, 27)
        if (isSquat) {
            ControlSlider("Apertura", "${(stanceWidth * 100).toInt()}%", stanceWidth, onStanceChange, 0.1f..1f, 9)
            if (showBarPositionControl) {
                val barLabel = when {
                    barPosition < 0.2f -> "Muy baja"
                    barPosition < 0.45f -> "Baja"
                    barPosition < 0.7f -> "Media"
                    else -> "Alta"
                }
                ControlSlider("Posición barra", barLabel, barPosition, onBarPositionChange, 0f..1f, 9)
            } else {
                Text(
                    "La sentadilla frontal fija la barra en rack alto, así que aquí solo ajustamos base y profundidad.",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (lift) {
                    LiftType.SQUAT_LOW_BAR -> "En low-bar, subir la barra vuelve la postura algo más vertical y suele aumentar demanda de rodilla."
                    LiftType.SQUAT_HIGH_BAR -> "En high-bar, bajar un poco la barra suele repartir más trabajo a cadera y tronco."
                    else -> "La base y la profundidad son las dos variables que más cambian esta variante."
                },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun AnthropometryControlsCard(
    heightCm: Float,
    onHeightChange: (Float) -> Unit,
    athleteWeightKg: Float,
    onAthleteWeightChange: (Float) -> Unit,
    femurLengthCm: Float,
    onFemurLengthChange: (Float) -> Unit,
    tibiaLengthCm: Float,
    onTibiaLengthChange: (Float) -> Unit,
    torsoLengthCm: Float,
    onTorsoLengthChange: (Float) -> Unit,
    humerusLengthCm: Float,
    onHumerusLengthChange: (Float) -> Unit,
    forearmLengthCm: Float,
    onForearmLengthChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, Modifier.size(15.dp), tint = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.width(7.dp))
            Text(
                "ANTROPOMETRÍA AJUSTABLE",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Estos controles cambian la geometría estimada del stickman. Úsalos para comparar proporciones, no para representar medidas clínicas exactas.",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
            color = Color.White.copy(alpha = 0.7f),
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(12.dp))
        ControlSlider("Altura", "${heightCm.toInt()} cm", heightCm, onHeightChange, 150f..205f, 54)
        ControlSlider("Peso corporal", "${athleteWeightKg.toInt()} kg", athleteWeightKg, onAthleteWeightChange, 45f..150f, 52)
        ControlSlider("Fémur", "${femurLengthCm.toInt()} cm", femurLengthCm, onFemurLengthChange, 38f..60f, 21)
        ControlSlider("Tibia", "${tibiaLengthCm.toInt()} cm", tibiaLengthCm, onTibiaLengthChange, 32f..52f, 19)
        ControlSlider("Torso", "${torsoLengthCm.toInt()} cm", torsoLengthCm, onTorsoLengthChange, 40f..65f, 24)
        ControlSlider("Húmero", "${humerusLengthCm.toInt()} cm", humerusLengthCm, onHumerusLengthChange, 26f..42f, 15)
        ControlSlider("Antebrazo", "${forearmLengthCm.toInt()} cm", forearmLengthCm, onForearmLengthChange, 22f..38f, 15)
    }
}

@Composable
private fun ControlSlider(
    label: String, value: String,
    current: Float, onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>, steps: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(108.dp))
        Slider(value = current, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// JOINT ANGLES CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun JointAnglesCard(solve: BiomechanicalSolve) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Straighten, null, Modifier.size(15.dp), tint = Color(0xFFFFD600))
            Spacer(Modifier.width(7.dp))
            Text("ÁNGULOS ARTICULARES", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(12.dp))
        solve.jointAngles.forEach { ja ->
            val angleColor = demandColor(ja.torqueRatio)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ja.joint.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${ja.angleDegrees.toInt()}°", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Black, color = angleColor)
                    Text("${ja.leverType.label} · ${demandLabel(ja.torqueRatio)}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.5f))
                }
            }
            LinearProgressIndicator(
                progress = { (ja.angleDegrees / 120f).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = angleColor,
                trackColor = angleColor.copy(alpha = 0.1f),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LEVER MECHANICS CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun LeverMechanicsCard(solve: BiomechanicalSolve) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Architecture, null, Modifier.size(15.dp), tint = Color(0xFF1E88E5))
            Spacer(Modifier.width(7.dp))
            Text("MECÁNICA DE PALANCAS", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(12.dp))
        solve.leverClassification.forEach { (joint, lever) ->
            val leverColor = when (lever) {
                LeverType.FIRST_CLASS  -> Color(0xFFE53935)
                LeverType.SECOND_CLASS -> Color(0xFFFF8F00)
                LeverType.THIRD_CLASS  -> Color(0xFF1E88E5)
                LeverType.FOURTH_CLASS -> Color(0xFF9C27B0)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = leverColor.copy(alpha = 0.12f)) {
                        Text(lever.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = leverColor)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(joint.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Text(lever.description, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.7f), lineHeight = 16.sp)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFF2C2C2C))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TORQUE ANALYSIS CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun TorqueAnalysisCard(solve: BiomechanicalSolve) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, null, Modifier.size(15.dp), tint = Color(0xFFFF7043))
            Spacer(Modifier.width(7.dp))
            Text("COMPARACIÓN INTERNA DE TORQUE", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Estos valores comparan la postura actual consigo misma. Sirven para ver dónde cae la mayor demanda relativa, no para etiquetar riesgo absoluto.",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
            color = Color.White.copy(alpha = 0.7f),
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(12.dp))
        val maxTorque = solve.estimatedTorque.values.maxOrNull() ?: 1.0
        solve.estimatedTorque.forEach { (joint, torqueNm) ->
            val ratio = (torqueNm / maxTorque).coerceIn(0.0, 1.0)
            val torqueColor = demandColor(ratio)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(joint.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.SemiBold)
                    Text("${"%.0f".format(torqueNm)} Nm · ${demandLabel(ratio)}", style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Black, color = torqueColor)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { ratio.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = torqueColor, trackColor = torqueColor.copy(alpha = 0.1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFF2C2C2C))
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Demanda Relativa", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.Bold)
                Text("Comparación combinada de torque y ángulos dentro de esta postura", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.5f))
            }
            val diffColor = demandColor(solve.overallDifficulty)
            Text("${"%.0f".format(solve.overallDifficulty * 100)}%", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Black, color = diffColor)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// ANTHROPOMETRY CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun AnthropometryCard(anthro: Anthropometry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, Modifier.size(15.dp), tint = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.width(7.dp))
            Text("RESUMEN ANTROPOMÉTRICO", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E88E5).copy(alpha = 0.08f)) {
            Text(anthro.anthropometricProfile, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.SemiBold, color = Color(0xFF1E88E5))
        }
        Spacer(Modifier.height(12.dp))
        val measurements = listOf(
            "Altura" to "${anthro.heightCm.toInt()} cm",
            "Peso"   to "${anthro.weightKg.toInt()} kg",
            "Fémur"  to "${anthro.femurLengthCm} cm",
            "Tibia"  to "${anthro.tibiaLengthCm} cm",
            "Torso"  to "${anthro.torsoLengthCm} cm",
            "Húmero" to "${anthro.humerusLengthCm} cm",
            "Antebrazo" to "${anthro.forearmLengthCm} cm",
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            measurements.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { (label, value) ->
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text("$label: ", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.5f))
                            Text(value, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFF2C2C2C))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AnthroPropItem("Fémur/Torso", "%.2f".format(anthro.femurRatio))
            AnthroPropItem("Tibia/Fémur", "%.2f".format(anthro.tibiaRatio))
            AnthroPropItem("Brazo/Torso", "%.2f".format(anthro.armToTorsoRatio))
        }
    }
}

@Composable
private fun BiomechanicsDisclaimerCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF2C2C2C)), RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, Modifier.size(15.dp), tint = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.width(7.dp))
            Text(
                "CÓMO LEER ESTA PANTALLA",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Text(
            "Palitos Biomecánicos es una herramienta educativa. Te ayuda a comparar variantes, posiciones y proporciones dentro del mismo escenario.",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
            color = Color.White.copy(alpha = 0.7f),
            lineHeight = 18.sp,
        )
        Text(
            "No sustituye evaluación clínica, videoanálisis completo ni prescripción individual. Si ves una zona \"alta\", léela como mayor demanda relativa de la postura actual.",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
            color = Color.White.copy(alpha = 0.7f),
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun AnthroPropItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.Bold)
    }
}

private fun demandColor(value: Double): Color = when {
    value < 0.33 -> Color(0xFF42A5F5)
    value < 0.66 -> Color(0xFFFFB74D)
    else -> Color(0xFFFF7043)
}

private fun demandLabel(value: Double): String = when {
    value < 0.33 -> "Baja"
    value < 0.66 -> "Media"
    else -> "Alta"
}
