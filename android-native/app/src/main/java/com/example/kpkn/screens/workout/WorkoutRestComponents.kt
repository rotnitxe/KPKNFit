package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
internal fun RestTimerOverlay(
    state: WorkoutRestModalState,
    remainingSeconds: Int,
    hazeState: HazeState,
    recoveryStatus: RestRecoveryStatus? = null,
    coachMessage: CoachMessage? = null,
    pendingRestSuggestion: PendingRestSuggestion? = null,
    isAdaptiveActive: Boolean = false,
    sessionAccentColor: Color = Color.White,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSkip: () -> Unit,
    onSkipExercise: (() -> Unit)? = null,
    onUseAdaptive: (() -> Unit)? = null,
) {
    val totalSeconds = state.activeSeconds.coerceAtLeast(1)
    val progress = (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    blurRadius = 24.dp,
                    tint = HazeTint(Color.Black.copy(alpha = 0.55f)),
                    backgroundColor = Color(0xFF0A0A0A).copy(alpha = 0.65f),
                    noiseFactor = 0.04f,
                ),
            )
            .zIndex(6f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                val trackColor = Color.White.copy(alpha = 0.12f)
                val progressColor = Color.White
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 6.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val rect = Size(radius * 2f, radius * 2f)

                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = rect,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )

                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = rect,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Text(
                        text = formatTime(remainingSeconds.coerceAtLeast(0)),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 52.sp,
                    )
                    Text(
                        text = when (state.kind) {
                            RestTimerKind.SUPERSET_INTRA -> "intra superserie"
                            RestTimerKind.SUPERSET_ROUND -> "entre superseries"
                            RestTimerKind.WARMUP -> "aproximacion"
                            RestTimerKind.BETWEEN_SIDES -> "entre lados"
                            RestTimerKind.STANDARD -> "descanso"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                        letterSpacing = 3.sp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Reducir",
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Aumentar",
                        tint = Color.White,
                    )
                }
            }

            if (recoveryStatus != null) {
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(6.dp))
                            Text("Recuperación", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text("${(recoveryStatus.recoveryPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                                color = when {
                                    recoveryStatus.isReady -> Color(0xFF4CAF50)
                                    recoveryStatus.recoveryPercent >= 0.5f -> Color(0xFFFFD740)
                                    else -> Color(0xFFFF5252)
                                })
                        }
                        LinearProgressIndicator(
                            progress = { recoveryStatus.recoveryPercent.toFloat() / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = when {
                                recoveryStatus.isReady -> Color(0xFF4CAF50)
                                recoveryStatus.recoveryPercent >= 0.5f -> Color(0xFFFFD740)
                                else -> Color(0xFFFF5252)
                            },
                            trackColor = Color.White.copy(alpha = 0.1f),
                        )
                        if (recoveryStatus.isReady) {
                            Text("Listo para continuar", style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (coachMessage != null) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = sessionAccentColor.copy(alpha = 0.1f),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Lightbulb, null, Modifier.size(14.dp), tint = sessionAccentColor)
                        Spacer(Modifier.width(6.dp))
                        Text(coachMessage.body, style = MaterialTheme.typography.labelSmall,
                            color = sessionAccentColor, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (pendingRestSuggestion != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAdaptiveActive) Color.White.copy(alpha = 0.06f)
                                else sessionAccentColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isAdaptiveActive) Color.White.copy(alpha = 0.2f)
                                                                       else sessionAccentColor.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(if (isAdaptiveActive) "Dinámico" else "Plan",
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = if (isAdaptiveActive) Color.White.copy(alpha = 0.6f) else sessionAccentColor)
                            Text(formatTime(state.activeSeconds),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black,
                                color = Color.White)
                        }
                    }

                    if (!isAdaptiveActive && onUseAdaptive != null) {
                        OutlinedButton(
                            onClick = onUseAdaptive,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD740)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD740).copy(alpha = 0.4f)),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Dinámico", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(formatTime(pendingRestSuggestion.adaptiveSeconds),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Saltar descanso", maxLines = 1)
                }
                if (onSkipExercise != null) {
                    OutlinedButton(
                        onClick = onSkipExercise,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.85f),
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Saltar ejercicio", maxLines = 1)
                    }
                }
            }
        }
    }
}
