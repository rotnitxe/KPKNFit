package com.example.kpkn.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

// ─── Ring Constants ──────────────────────────────────────────────────────────

private val RingColors = listOf(Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFFFFD740))
private val RingLabels = listOf("MÚSCULOS", "ENERGÍA", "COLUMNA")

@Composable
fun HomeRingsSection(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    hasActiveProgram: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val progressValues = remember(muscularProgress, sncProgress, columnaProgress) {
        listOf(muscularProgress, sncProgress, columnaProgress)
    }

    val ringColors = remember(hasActiveProgram) {
        if (hasActiveProgram) RingColors
        else listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))
    }

    var showInfoDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MIS RINGS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "¿Qué es esto?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { showInfoDialog = true }
            )
        }

        CombinedRingsView(progressValues, ringColors, hasActiveProgram)
        Spacer(Modifier.height(4.dp))
    }

    if (showInfoDialog) {
        RingsInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
private fun CombinedRingsView(
    progressValues: List<Float>,
    ringColors: List<Color>,
    hasActiveProgram: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            Box(Modifier.height(110.dp).fillMaxWidth()) {
                AugeRingsCanvas(progressValues[0], progressValues[1], progressValues[2], ringColors)
                CurvedLabelsCanvas(RingLabels, ringColors)
            }

            // Porcentajes alineados bajo cada ring
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(24.dp),
            ) {
                val density = LocalDensity.current.density
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = 110f * density
                val r = min(widthPx / 5.8f, heightPx * 0.42f)
                val s = r * 1.9f
                val cx = widthPx / 2f
                val centerXs = listOf(cx - s, cx, cx + s)

                progressValues.forEachIndexed { i, progress ->
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (centerXs[i] - 30f * density).toInt(),
                                    (4f * density).toInt()
                                )
                            }
                            .width(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = ringColors[i],
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
    }
}

@Composable
internal fun SingleRingCanvas(
    value: Float,
    color: Color,
    ringDiameter: Float = 140f,
    strokeWidth: Float = 8f,
) {
    val animatedValue by animateFloatAsState(targetValue = value, label = "ringValue")
    val density = LocalDensity.current.density
    Canvas(Modifier.size(ringDiameter.dp)) {
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
    Canvas(Modifier.fillMaxSize()) {
        val r = min(size.width / 5.8f, size.height * 0.42f)
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

        val r = min(widthPx / 5.8f, heightPx * 0.42f)
        val s = r * 1.9f
        val cy = heightPx / 2f
        val cx = widthPx / 2f
        val centers = listOf(Offset(cx - s, cy), Offset(cx, cy), Offset(cx + s, cy))

        labels.forEachIndexed { i, label ->
            val center = centers[i]
            val textRadius = r - (14f * density)
            
            // Paso angular dinámico para mantener constante la separación de las letras
            val desiredArcLength = 7.2f * density
            val charAngleSpan = ((desiredArcLength / textRadius) * (180f / PI.toFloat())).coerceIn(8f, 18f)
            
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
                        .offset { IntOffset((x - 4f * density).toInt(), (y - 6f * density).toInt()) }
                        .graphicsLayer { rotationZ = angle + 90f }
                )
            }
        }
    }
}

internal fun batteryColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF22C55E)
    score >= 50 -> Color(0xFFFACC15)
    else        -> Color(0xFFEF4444)
}

// ─── RINGS Info Dialog ─────────────────────────────────────────────────────

@Composable
private fun RingsInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "¿Qué son los RINGS?",
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Tus RINGS son la representación visual del estado de tu cuerpo, dividido en tres áreas relevantes del entrenamiento:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                Text(
                    "1. RING Músculos:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF5252),
                )
                Text(
                    "Muestra el promedio del estado de todos tus músculos, puede entenderse de dos formas, qué tan recuperados están o qué tan preparados están para una sesión de entrenamiento. El anillo representa el estado general de todos tus músculos, si deseas corregir el porcentaje porque no representa tu estado real, puedes dirigirte al músculo en específico y corregir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                Text(
                    "2. RING Energía:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF448AFF),
                )
                Text(
                    "Representa qué tan fresco y enfocado te sientes en el día, con ello se busca representar cómo te sientes a nivel neural.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                Text(
                    "3. RING Columna:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD740),
                )
                Text(
                    "Representa qué tan preparada está tu columna para un entrenamiento; especialmente relevante si realizas sentadillas libres o peso muerto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    "¿Cómo se actualizan los RINGS?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Los RINGS se recalibran automáticamente en tres momentos clave: antes de cada entrenamiento (donde puedes ajustar tu estado del día), al finalizar una sesión y en el feedback de recuperación que recibirás al día siguiente. Así el algoritmo mejora progresivamente sin que necesites intervenir manualmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Entendido",
                    fontWeight = FontWeight.Black,
                )
            }
        }
    )
}
