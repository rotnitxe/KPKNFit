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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.*
import com.example.kpkn.ui.components.KpknAlertDialog

// ─── Ring Constants ──────────────────────────────────────────────────────────

internal val RingColors = listOf(
    Color(0xFFC96B5C), // Músculos — terracota
    Color(0xFF4FA3A5), // Energía — turquesa grisáceo
    Color(0xFF9A86C8), // Columna — lavanda apagado
)
private val RingLabels = listOf("Músculos", "Energía", "Columna")

@Composable
fun HomeRingsSection(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    hasActiveProgram: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progressValues = remember(muscularProgress, sncProgress, columnaProgress, isLoading) {
        if (isLoading) listOf(0f, 0f, 0f)
        else listOf(muscularProgress, sncProgress, columnaProgress)
    }

    val ringColors = remember(hasActiveProgram, isLoading) {
        when {
            isLoading -> listOf(Color(0xFF444444), Color(0xFF555555), Color(0xFF666666))
            hasActiveProgram -> RingColors
            else -> listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))
        }
    }

    var showInfoDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MIS RINGS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                onClick = { showInfoDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "¿Qué es esto?",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        CombinedRingsView(progressValues, ringColors, hasActiveProgram, isLoading = isLoading)
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
    isLoading: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            Box(
                Modifier
                    .height(196.dp)
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        val desc = if (isLoading) {
                            "Calculando recuperación"
                        } else {
                            "Rings: Músculos ${(progressValues[0] * 100).toInt()}%, " +
                                "Energía ${(progressValues[1] * 100).toInt()}%, " +
                                "Columna ${(progressValues[2] * 100).toInt()}%"
                        }
                        contentDescription = desc
                        stateDescription = desc
                    },
            ) {
                AugeRingsCanvas(progressValues[0], progressValues[1], progressValues[2], ringColors)
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            // Keep the captions clear of the lowered energy ring.
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                progressValues.forEachIndexed { i, progress ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            RingLabels[i].uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ringColors[i],
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            if (isLoading) "…" else "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
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
internal fun AugeRingsCanvas(mp: Float, sp: Float, cp: Float, ringColors: List<Color> = RingColors) {
    val values = listOf(mp, sp, cp)
    Canvas(Modifier.fillMaxSize()) {
        val radius = min(size.width / 5f, size.height * 0.38f)
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val dx = radius * 1.45f
        val dy = radius * 0.48f
        val centers = listOf(
            Offset(centerX - dx, centerY - dy),
            Offset(centerX, centerY + dy),
            Offset(centerX + dx, centerY - dy),
        )
        val stroke = 5.dp.toPx()

        centers.forEachIndexed { index, center ->
            val color = ringColors[index]
            val progress = values[index].coerceIn(0f, 1f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawCircle(color.copy(alpha = 0.28f), radius, center, style = Stroke(stroke))
            listOf(18.dp.toPx() to 0.12f, 12.dp.toPx() to 0.20f, 7.dp.toPx() to 0.32f).forEach { (width, alpha) ->
                drawArc(
                    color = color.copy(alpha = alpha), startAngle = -90f, sweepAngle = 360f * progress,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * progress,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
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
    KpknAlertDialog(
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
                    color = com.example.kpkn.ui.theme.RingRed,
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
                    color = com.example.kpkn.ui.theme.RingBlue,
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
                    color = com.example.kpkn.ui.theme.RingYellow,
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
