package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.*

/**
 * Rest timer overlay focused on countdown controls and the last completed set.
 */
@Composable
fun RestTimerOverlay(
    state: WorkoutRestModalState,
    remainingSeconds: Int,
    hazeState: HazeState,
    pendingRestSuggestion: PendingRestSuggestion? = null,
    lastSetOutcome: SetOutcomeV2? = null,
    lastCompletedSet: CompletedSet? = null,
    isAdaptiveActive: Boolean = false,
    sessionAccentColor: Color = Color.White,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSkip: () -> Unit,
    skipExerciseLabel: String? = null,
    onSkipExercise: (() -> Unit)? = null,
    onUseAdaptive: (() -> Unit)? = null,
) {
    val totalSeconds = state.activeSeconds.coerceAtLeast(1)
    val timerProgress = (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    blurRadius = 28.dp,
                    tint = HazeTint(Color.Black.copy(alpha = 0.58f)),
                    backgroundColor = Color(0xFF070707).copy(alpha = 0.70f),
                    noiseFactor = 0.03f,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            // ─── COUNTDOWN TIMER ───
            Box(
                modifier = Modifier.size(204.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    val outerRadius = (size.minDimension - strokeWidth) / 2f
                    val outerRect = Size(outerRadius * 2f, outerRadius * 2f)

                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = outerRect,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    )

                    drawArc(
                        color = sessionAccentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * timerProgress,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = outerRect,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    )

                }

                // Centered Information
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = formatTime(remainingSeconds.coerceAtLeast(0)),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 46.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when (state.kind) {
                            RestTimerKind.SUPERSET_INTRA -> "superserie"
                            RestTimerKind.SUPERSET_ROUND -> "siguiente ronda"
                            RestTimerKind.WARMUP -> "aproximación"
                            RestTimerKind.BETWEEN_SIDES -> "entre lados"
                            RestTimerKind.STANDARD -> "descanso"
                        }.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    
                    if (state.exerciseName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.exerciseName,
                            style = MaterialTheme.typography.bodySmall,
                            color = sessionAccentColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    }
                }
            }

            // Sleek timer adjusters (+/- 15s)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Restar 15 segundos",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    shape = WorkoutUiTokens.ChipShape,
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Text(
                        text = "Ajustar tiempo",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Sumar 15 segundos",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val alertMessages = remember(
                state.notificationsEnabled,
                state.exactAlarmGranted,
                state.soundReady,
            ) {
                buildList {
                    if (!state.notificationsEnabled) add("Notificaciones desactivadas")
                    if (!state.exactAlarmGranted) add("Alarma exacta no disponible")
                    if (!state.soundReady) add("Audio silencioso")
                }
            }
            if (alertMessages.isNotEmpty()) {
                WorkoutGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = WorkoutUiTokens.InnerCardShape,
                    border = BorderStroke(1.dp, Color(0xFFFFD740).copy(alpha = 0.25f)),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Alertas del descanso",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFD740),
                        )
                        alertMessages.forEach { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.76f),
                            )
                        }
                    }
                }
            }

            val referenceSet = lastCompletedSet ?: pendingRestSuggestion?.lastSet
            val referenceOutcome = lastSetOutcome ?: referenceSet?.setOutcomeV2
            if (referenceSet != null) {
                WorkoutGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = WorkoutUiTokens.InnerCardShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = sessionAccentColor.copy(alpha = 0.82f),
                            modifier = Modifier.size(16.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val loadStr = if (referenceSet.weight > 0.0) "${formatWeight(referenceSet.weight)} kg" else "Peso corporal"
                            val valueStr = if (referenceSet.timeSeconds != null) "${referenceSet.timeSeconds}s" else "${referenceSet.reps} reps"
                            Text(
                                text = "Último set",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.48f)
                            )
                            Text(
                                text = "$loadStr x $valueStr",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (referenceOutcome?.isContextPr == true || referenceOutcome?.isGlobalPr == true) {
                                RestTinyBadge("Récord", Color(0xFFFFD740))
                            }
                            if (referenceSet.isFailure || referenceSet.isFailedSet) {
                                RestTinyBadge("Fallo", Color(0xFFFF5252))
                            }
                            if (referenceSet.dropSets.isNotEmpty()) {
                                RestTinyBadge("Drop", Color(0xFF40C4FF))
                            }
                        }
                    }
                }
            }

            // ─── ADAPTIVE REST TIMER SUGGESTIONS ───
            if (pendingRestSuggestion != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = WorkoutUiTokens.InnerCardShape,
                        color = if (isAdaptiveActive) Color.White.copy(alpha = 0.05f) else sessionAccentColor.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, if (isAdaptiveActive) Color.White.copy(alpha = 0.15f) else sessionAccentColor.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isAdaptiveActive) "DESCANSO DINÁMICO" else "PLAN DE SESIÓN",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Black,
                                color = if (isAdaptiveActive) Color.White.copy(alpha = 0.5f) else sessionAccentColor
                            )
                            if (state.isManualOverride) {
                                Text(
                                    text = "Manual",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.62f),
                                )
                            }
                            Text(
                                text = formatTime(state.plannedSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    if (!isAdaptiveActive && onUseAdaptive != null) {
                        OutlinedButton(
                            onClick = onUseAdaptive,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = WorkoutUiTokens.InnerCardShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD740)),
                            border = BorderStroke(1.dp, Color(0xFFFFD740).copy(alpha = 0.4f)),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Usar Dinámico",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = formatTime(pendingRestSuggestion.adaptiveSeconds),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── ACTION DOCK BUTTONS ───
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // SKIP REST PRIMARY BUTTON
                WorkoutPrimaryActionButton(
                    text = "Saltar descanso",
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.Black
                        )
                    }
                )

                // SKIP REMAINING EXERCISE SETS (SECONDARY BUTTON)
                if (onSkipExercise != null) {
                    OutlinedButton(
                        onClick = onSkipExercise,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = WorkoutUiTokens.ChipShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f),
                        ),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.16f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = skipExerciseLabel ?: "Saltar series restantes e ir al siguiente ejercicio",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RestTinyBadge(
    text: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.13f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

// Helpers
private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return String.format("%02d:%02d", min, sec)
}

private fun formatWeight(weight: Double): String {
    return if (weight % 1.0 == 0.0) {
        weight.toInt().toString()
    } else {
        String.format("%.1f", weight)
    }
}

private fun formatIntensity(intensity: Double): String {
    return if (intensity % 1.0 == 0.0) {
        intensity.toInt().toString()
    } else {
        String.format("%.1f", intensity)
    }
}
