package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryStatus
import kotlin.math.*

// ─── Ring Constants ──────────────────────────────────────────────────────────

private val RingColors = listOf(Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFFFFD740))
private val RingLabels = listOf("MÚSCULOS", "SNC", "COLUMNA")
private val RingLabelsShort = listOf("Músc.", "SNC", "Col.")

private val RingDescriptions = listOf(
    "Indica qué tan recuperados están tus músculos. Un nivel bajo significa que tus fibras necesitan descansar para evitar sobrecargas.",
    "Es tu 'batería' de energía mental y coordinación. Si está baja, puedes sentirte más lento de reflejos o con la mente cansada.",
    "Mide el impacto acumulado en tu espalda y articulaciones. Te ayuda a saber cuándo es mejor bajar la carga.",
)

private val RingQuestions = listOf(
    "Cada zona anatómica se recupera a distinto ritmo pos-esfuerzo. Abre 'Batería por zona' para calibrar con precisión.",
    "¿Sientes el cuerpo inusualmente pesado o la mente nublada al despertar? Puedes recalibrar deslizando el anillo.",
    "¿Notas rigidez acentuada o la espalda 'comprimida' durante el día? Puedes recalibrar tu columna deslizando el anillo.",
)

@Composable
fun HomeRingsSection(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    ringsViewMode: HomeViewModel.RingsViewMode,
    hasActiveProgram: Boolean = true,
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    onRingSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressValues = remember(muscularProgress, sncProgress, columnaProgress) {
        listOf(muscularProgress, sncProgress, columnaProgress)
    }

    val ringColors = remember(hasActiveProgram) {
        if (hasActiveProgram) RingColors
        else listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))
    }

    val pagerState = rememberPagerState(
        initialPage = if (ringsViewMode == HomeViewModel.RingsViewMode.RINGS) 0 else 1,
        pageCount = { 2 }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "TUS RINGS",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                when (page) {
                    0 -> CombinedRingsView(progressValues, ringColors, onRingSelect, hasActiveProgram)
                    1 -> IndividualRingsView(progressValues, ringColors, onRingSelect, hasActiveProgram, perMuscle)
                }
            }
        }
    }
}

@Composable
private fun CombinedRingsView(
    progressValues: List<Float>,
    ringColors: List<Color>,
    onRingSelect: (Int) -> Unit,
    hasActiveProgram: Boolean = true,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.height(110.dp).fillMaxWidth()) {
                AugeRingsCanvas(progressValues[0], progressValues[1], progressValues[2], ringColors)
                CurvedLabelsCanvas(RingLabels, ringColors)

                Row(Modifier.fillMaxSize()) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectTapGestures(onLongPress = { if (hasActiveProgram) onRingSelect(i) })
                                }
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                progressValues.forEachIndexed { i, progress ->
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ringColors[i],
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IndividualRingsView(
    progressValues: List<Float>,
    ringColors: List<Color>,
    onRingSelect: (Int) -> Unit,
    hasActiveProgram: Boolean = true,
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var showMuscleDetail by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(140.dp)
                        .pointerInput(currentIndex) {
                            detectTapGestures(onLongPress = { if (hasActiveProgram) onRingSelect(currentIndex) })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(ringColors[currentIndex].copy(alpha = 0.2f), Color.Transparent),
                                center = center,
                                radius = size.minDimension / 2,
                            ),
                            radius = size.minDimension / 2,
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        SingleRingCanvas(
                            value = progressValues[currentIndex],
                            color = ringColors[currentIndex],
                            size = 110f,
                            strokeWidth = 7f,
                        )
                        InternalCurvedLabel(
                            label = RingLabels[currentIndex],
                            color = ringColors[currentIndex],
                            ringSize = 110f
                        )
                    }
                }

                Row(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(3) { i ->
                        val isActive = i == currentIndex
                        Box(
                            Modifier
                                .width(if (isActive) 20.dp else 8.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isActive) ringColors[currentIndex]
                                    else ringColors[currentIndex].copy(alpha = 0.2f)
                                )
                        )
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    RingDescriptions[currentIndex],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(8.dp),
                ) {
                    Text(
                        RingQuestions[currentIndex],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }

                if (currentIndex == 0) {
                    FilledTonalButton(
                        onClick = { showMuscleDetail = !showMuscleDetail },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            if (showMuscleDetail) "Cerrar" else "Batería",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        if (hasActiveProgram) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(3) { i ->
                    TextButton(onClick = { currentIndex = i }) {
                        Text(
                            RingLabelsShort[i],
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (i == currentIndex) FontWeight.Black else FontWeight.Normal,
                            color = if (i == currentIndex) ringColors[i] else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = showMuscleDetail && currentIndex == 0) {
            MuscleBatteryAccordion(perMuscle = perMuscle)
        }
    }
}

@Composable
private fun SingleRingCanvas(
    value: Float,
    color: Color,
    size: Float = 140f,
    strokeWidth: Float = 8f,
) {
    val animatedValue by animateFloatAsState(targetValue = value, label = "ringValue")
    val density = LocalDensity.current.density
    Canvas(Modifier.size(size.dp)) {
        val strokePx = strokeWidth * density
        val r = (this.size.minDimension - strokePx) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)

        drawCircle(
            color.copy(alpha = 0.15f),
            r,
            c,
            style = Stroke(strokePx),
        )
        drawArc(
            color,
            -90f,
            360f * animatedValue,
            false,
            Offset(c.x - r, c.y - r),
            Size(r * 2, r * 2),
            style = Stroke(strokePx),
        )
    }
}

@Composable
private fun AugeRingsCanvas(mp: Float, sp: Float, cp: Float, ringColors: List<Color> = RingColors) {
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        val r = size.width / 5.8f
        val s = r * 1.9f
        val cy = size.height / 2f
        val cx = size.width / 2f
        val strokePx = 8.dp.toPx()
        val data = ringColors.zip(listOf(mp, sp, cp))
        val centers = listOf(Offset(cx - s, cy), Offset(cx, cy), Offset(cx + s, cy))

        centers.forEachIndexed { i, c ->
            drawCircle(
                data[i].first.copy(alpha = 0.15f),
                r,
                c,
                style = Stroke(strokePx),
            )
            drawArc(
                data[i].first,
                -90f,
                360f * data[i].second,
                false,
                Offset(c.x - r, c.y - r),
                Size(r * 2, r * 2),
                style = Stroke(strokePx),
            )
        }
    }
}

@Composable
private fun CurvedLabelsCanvas(labels: List<String>, ringColors: List<Color>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val r = widthPx / 5.8f
        val s = r * 1.9f
        val cy = heightPx / 2f
        val cx = widthPx / 2f
        val centers = listOf(Offset(cx - s, cy), Offset(cx, cy), Offset(cx + s, cy))

        labels.forEachIndexed { i, label ->
            val center = centers[i]
            val textRadius = r - (14f * density)
            val charAngleSpan = 13f
            val totalSpan = charAngleSpan * (label.length - 1)
            val startAngle = -90f - (totalSpan / 2f)

            label.forEachIndexed { charIndex, char ->
                val angle = startAngle + charIndex * charAngleSpan
                val angleRad = (angle * PI / 180.0).toFloat()
                val x = center.x + textRadius * cos(angleRad)
                val y = center.y + textRadius * sin(angleRad)

                Text(
                    text = char.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 7.5.sp,
                        color = ringColors[i].copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .offset(
                            x = (x / density).dp - 4.dp,
                            y = (y / density).dp - 6.dp
                        )
                        .graphicsLayer { rotationZ = angle + 90f }
                )
            }
        }
    }
}

@Composable
private fun InternalCurvedLabel(label: String, color: Color, ringSize: Float) {
    val density = LocalDensity.current.density
    Box(Modifier.size(ringSize.dp)) {
        val ringSizePx = ringSize * density
        val textRadius = (ringSizePx / 2f) - (18f * density)
        val charAngleSpan = 13f
        val totalSpan = charAngleSpan * (label.length - 1)
        val startAngle = -90f - (totalSpan / 2f)

        label.forEachIndexed { index, char ->
            val angle = startAngle + index * charAngleSpan
            val angleRad = (angle * PI / 180.0).toFloat()
            val x = (ringSizePx / 2f) + textRadius * cos(angleRad)
            val y = (ringSizePx / 2f) + textRadius * sin(angleRad)

            Text(
                text = char.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 8.sp,
                    color = color.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .offset(
                        x = (x / density).dp - 4.dp,
                        y = (y / density).dp - 6.dp
                    )
                    .graphicsLayer { rotationZ = angle + 90f }
            )
        }
    }
}

private data class MuscleGroup(val label: String, val muscles: List<String>)

private val MUSCLE_GROUPS = listOf(
    MuscleGroup("Pecho", listOf("Pectorales")),
    MuscleGroup("Espalda", listOf("Dorsales", "Trapecio", "Eresp. Espinales")),
    MuscleGroup("Hombros", listOf("Delt. Ant.", "Delt. Lat.", "Delt. Post.")),
    MuscleGroup("Brazos", listOf("Bíceps", "Tríceps", "Antebrazo")),
    MuscleGroup("Core", listOf("Abdomen", "Core")),
    MuscleGroup("Piernas", listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas")),
)

@Composable
private fun MuscleBatteryAccordion(
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Text(
            "Batería por zona muscular",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
        )

        MUSCLE_GROUPS.forEach { group ->
            // Group recovery = average of its muscles (or 100 if none in map)
            val groupScores = group.muscles.mapNotNull { m -> perMuscle[m]?.recoveryScore }
            val groupAvg = if (groupScores.isEmpty()) 100 else groupScores.average().toInt()
            val groupColor = batteryColor(groupAvg)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        group.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Text(
                        "$groupAvg%",
                        style = MaterialTheme.typography.labelSmall,
                        color = groupColor,
                    )
                }
                group.muscles.forEach { muscle ->
                    val status = perMuscle[muscle]
                    val score = status?.recoveryScore ?: 100
                    val barColor = batteryColor(score)

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                muscle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Text(
                                "$score%",
                                style = MaterialTheme.typography.labelSmall,
                                color = barColor,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { score / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        )
                    }
                }
            }
        }
    }
}

private fun batteryColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF22C55E)
    score >= 50 -> Color(0xFFFACC15)
    else        -> Color(0xFFEF4444)
}
