package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

// ─── Section Header (reusable) ──────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

// ─── Home Rings Section ─────────────────────────────────────────────────────

@Composable
fun HomeRingsSection(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    ringsViewMode: HomeViewModel.RingsViewMode,
    hasActiveProgram: Boolean = true,
    onRingSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressValues = listOf(muscularProgress, sncProgress, columnaProgress)

    // "Tus RINGS" ahora vive aquí para que item 0 sea solo el saludo
    androidx.compose.material3.Text(
        "Tus RINGS".uppercase(),
        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
    )

    // Sin programa activo → rings en escala de grises
    val ringColors = if (hasActiveProgram) RingColors
    else listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))

    val pagerState = rememberPagerState(
        initialPage = if (ringsViewMode == HomeViewModel.RingsViewMode.RINGS) 0 else 1,
        pageCount = { 2 }
    )
    val scope = rememberCoroutineScope()

    // Sincronizar pager con viewModel
    LaunchedEffect(pagerState.currentPage) {
        val newMode = if (pagerState.currentPage == 0)
            HomeViewModel.RingsViewMode.RINGS
        else
            HomeViewModel.RingsViewMode.INDIVIDUAL
        // Nota: aquí deberías llamar a onRingsViewChange si lo pasas como parámetro
    }

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
                1 -> IndividualRingsView(progressValues, ringColors, onRingSelect, hasActiveProgram)
            }
        }
    }
}

// ─── Combined Rings View (3 rings side by side) ─────────────────────────────

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Rings canvas with curved labels
            Box(Modifier.height(110.dp).fillMaxWidth()) {
                AugeRingsCanvas(progressValues[0], progressValues[1], progressValues[2], ringColors)
                CurvedLabelsCanvas(RingLabels, ringColors)

                if (hasActiveProgram) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onRingSelect(0) })
                        })
                        Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onRingSelect(1) })
                        })
                        Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onRingSelect(2) })
                        })
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        for (i in 0..2) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            // Tooltip will be shown via parent state if needed
                                        })
                                    },
                            )
                        }
                    }
                }
            }

            // Percentages row (centered below each ring)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in progressValues.indices) {
                    val progress = progressValues[i]
                    Box(
                        Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = ringColors[i],
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

// ─── Individual Rings View (carousel with descriptions) ─────────────────────

@Composable
private fun IndividualRingsView(
    progressValues: List<Float>,
    ringColors: List<Color>,
    onRingSelect: (Int) -> Unit,
    hasActiveProgram: Boolean = true,
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
            // Large ring with glow
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(140.dp)
                        .pointerInput(Unit) {
                            if (hasActiveProgram) {
                                detectTapGestures(onLongPress = { onRingSelect(currentIndex) })
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Glow (reduced by 20%)
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(ringColors[currentIndex].copy(alpha = 0.2f), Color.Transparent),
                                center = center,
                                radius = size.minDimension,
                            ),
                            radius = size.minDimension,
                        )
                    }
                    // Ring
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

                // Stepper dots
                Row(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (i in 0..2) {
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

            // Description + question (compacto)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    RingDescriptions[currentIndex],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )

                // Calibration question (compact)
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

                // Muscle detail button (only for muscular)
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

        // Clickable ring zones for navigation
        if (hasActiveProgram) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (i in 0..2) {
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

        // Muscle accordion
        AnimatedVisibility(visible = showMuscleDetail && currentIndex == 0) {
            MuscleBatteryAccordion()
        }
    }
}

// ─── Single Ring Canvas ─────────────────────────────────────────────────────

@Composable
private fun SingleRingCanvas(
    value: Float,
    color: Color,
    size: Float = 140f,
    strokeWidth: Float = 8f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val animatedValue by animateFloatAsState(targetValue = value, label = "ringValue")
    Canvas(modifier.size(size.dp)) {
        val strokePx = strokeWidth * density
        val r = (this.size.minDimension - strokePx) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val circumference = (2 * PI * r).toFloat()
        val offset = circumference - animatedValue * circumference

        // Background ring
        drawCircle(
            color.copy(alpha = 0.15f),
            r,
            c,
            style = Stroke(strokePx),
        )
        // Progress arc
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

// ─── Auge Rings Canvas (3 overlapping rings) ────────────────────────────────

@Composable
private fun AugeRingsCanvas(mp: Float, sp: Float, cp: Float, ringColors: List<Color> = RingColors) {
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        val r = size.width / 5.8f
        val s = r * 1.9f
        val cy = size.height / 2f
        val cx = size.width / 2f
        val data = ringColors.zip(listOf(mp, sp, cp))
        val centers = listOf(Offset(cx - s, cy), Offset(cx, cy), Offset(cx + s, cy))

        for (i in centers.indices) {
            val c = centers[i]
            val color = data[i].first
            val progress = data[i].second
            
            drawCircle(
                color.copy(alpha = 0.15f),
                r,
                c,
                style = Stroke(8f * density),
            )
            drawArc(
                color,
                -90f,
                360f * progress,
                false,
                Offset(c.x - r, c.y - r),
                Size(r * 2, r * 2),
                style = Stroke(8f * density),
            )
        }
    }
}

// ─── Ring Label ──────────────────────────────────────────────────────────────

@Composable
private fun RingLabel(
    label: String,
    color: Color,
    progress: Float,
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f),
        )
    }
}

// ─── Muscle Battery Accordion ───────────────────────────────────────────────

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
private fun MuscleBatteryAccordion(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HorizontalDivider()
        Text(
            "Batería por zona muscular",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
        )

        // Demo data — in Phase 4, reads from per-muscle AUGE engine
        for (groupIndex in MUSCLE_GROUPS.indices) {
            val group = MUSCLE_GROUPS[groupIndex]
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
                        "100%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF22C55E),
                    )
                }
                for (muscleIndex in group.muscles.indices) {
                    val muscle = group.muscles[muscleIndex]
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
                                "100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF22C55E),
                            )
                        }
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                            color = Color(0xFF22C55E),
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        )
                    }
                }
            }
        }
    }
}

// ─── Curved Labels Canvas ───────────────────────────────────────────────────

@Composable
private fun CurvedLabelsCanvas(labels: List<String>, ringColors: List<Color>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current.density

        val r = widthPx / 5.8f
        val s = r * 1.9f
        val cy = heightPx / 2f
        val cx = widthPx / 2f
        val centers = listOf(Offset(cx - s, cy), Offset(cx, cy), Offset(cx + s, cy))

        for (i in labels.indices) {
            val center = centers[i]
            val label = labels[i]
            // Posicionado internamente "pegado" al borde del anillo
            val textRadius = r - (14f * density)
            
            val charAngleSpan = 13f
            val totalSpan = charAngleSpan * (label.length - 1)
            val startAngle = -90f - (totalSpan / 2f)

            for (charIndex in label.indices) {
                val char = label[charIndex]
                val angle = startAngle + charIndex * charAngleSpan
                val angleRad = (angle * PI / 180.0).toFloat()
                val x = center.x + textRadius * cos(angleRad)
                val y = center.y + textRadius * sin(angleRad)

                Text(
                    char.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontSize = 7.5.sp,
                    color = ringColors[i].copy(alpha = 0.7f),
                    modifier = Modifier
                        .offset(
                            x = (x / density).dp - 4.dp,
                            y = (y / density).dp - 6.dp
                        )
                        .graphicsLayer {
                            rotationZ = angle + 90f
                        }
                )
            }
        }
    }
}

// ─── Internal Curved Label (for Single Ring) ────────────────────────────────

@Composable
private fun InternalCurvedLabel(
    label: String,
    color: Color,
    ringSize: Float,
) {
    val density = LocalDensity.current.density
    val ringSizePx = ringSize * density
    val strokeWidthPx = 7f * density
    val offsetInsidePx = 14f * density

    val r = (ringSizePx - strokeWidthPx) / 2f
    val textRadius = r - offsetInsidePx
    
    val charAngleSpan = 13f
    val totalSpan = charAngleSpan * (label.length - 1)
    val startAngle = -90f - (totalSpan / 2f)

    Box(Modifier.size(ringSize.dp)) {
        for (index in label.indices) {
            val char = label[index]
            val angle = startAngle + index * charAngleSpan
            val angleRad = (angle * PI / 180.0).toFloat()
            val x = (ringSizePx / 2f) + textRadius * cos(angleRad)
            val y = (ringSizePx / 2f) + textRadius * sin(angleRad)

            Text(
                char.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                fontSize = 8.sp,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier
                    .offset(
                        x = (x / density).dp - 4.dp,
                        y = (y / density).dp - 6.dp
                    )
                    .graphicsLayer {
                        rotationZ = angle + 90f
                    }
            )
        }
    }
}

// ─── Rings View Tab (Icon-only tab for RINGS/INDIVIDUAL switching) ────────────

@Composable
private fun RingsViewTab(
    isSelected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
    tooltip: String? = null,
    modifier: Modifier = Modifier,
) {
    var showTooltip by remember { mutableStateOf(false) }

    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (tooltip == null) {
                            onClick()
                        } else {
                            showTooltip = !showTooltip
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp)) {
            iconContent()
        }

        // Tooltip
        if (showTooltip && tooltip != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(top = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(6.dp),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(6.dp),
                    )
                    .padding(8.dp),
            ) {
                Text(
                    tooltip,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

// ─── Three Rings Icon (for RINGS tab) ────────────────────────────────────────

@Composable
private fun ThreeRingsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val size = this.size
        val r = size.minDimension / 8f  // radius of each ring
        val s = r * 1.8f  // spacing between centers
        val cy = size.height / 2f
        val cx = size.width / 2f

        // 3 overlapping rings
        val colors = listOf(Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFFFFD740))
        val positions = listOf(
            Offset(cx - s, cy),  // left (red)
            Offset(cx, cy),      // center (blue)
            Offset(cx + s, cy),  // right (yellow)
        )

        for (i in positions.indices) {
            val center = positions[i]
            drawCircle(
                colors[i],
                r,
                center,
                style = Stroke(1.5f),
            )
        }
    }
}

// ─── Single Ring Icon (for INDIVIDUAL tab) ──────────────────────────────────

@Composable
private fun SingleRingTabIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val size = this.size
        val r = size.minDimension / 3f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            Color(0xFF448AFF),  // use blue as primary ring color
            r,
            center,
            style = Stroke(1.5f),
        )
    }
}
