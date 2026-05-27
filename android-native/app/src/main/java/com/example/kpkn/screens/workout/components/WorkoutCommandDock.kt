package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.services.workout.VoicePipelineStage
import com.example.kpkn.services.workout.VoiceSessionState

@Suppress("UNUSED_PARAMETER")
@Composable
fun WorkoutCommandDock(
    exercise: Exercise?,
    setIndex: Int,
    activeSide: String?,
    isUnilateral: Boolean,
    voiceSessionEnabled: Boolean,
    voiceSessionState: VoiceSessionState,
    onToggleVoice: () -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    primaryActionEnabled: Boolean = true,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    hazeState: HazeState? = null,
    isUpdateMode: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dock_voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val isListening = voiceSessionState.stage == VoicePipelineStage.LISTENING
    val isProcessing = voiceSessionState.stage == VoicePipelineStage.PROCESSING ||
            voiceSessionState.stage == VoicePipelineStage.CONFIRM_WAIT ||
            voiceSessionState.stage == VoicePipelineStage.TTS_SPEAKING

    val voiceIndicatorColor = when {
        isListening -> Color(0xFF4CAF50)
        isProcessing -> MaterialTheme.colorScheme.tertiary
        voiceSessionState.stage == VoicePipelineStage.ERROR_RECOVERY -> Color(0xFFFF9800)
        voiceSessionEnabled -> MaterialTheme.colorScheme.secondary
        else -> Color.White.copy(alpha = 0.38f)
    }

    val voiceIndicatorText = when (voiceSessionState.stage) {
        VoicePipelineStage.LISTENING -> {
            if (voiceSessionState.partialText.isNotBlank()) "Escuchando: \"${voiceSessionState.partialText}\""
            else "Escuchando comandos de voz..."
        }
        VoicePipelineStage.PROCESSING -> "Procesando..."
        VoicePipelineStage.CONFIRM_WAIT -> "¿Confirmar? Di \"Sí\" o \"No\""
        VoicePipelineStage.TTS_SPEAKING -> "Hablando..."
        VoicePipelineStage.ERROR_RECOVERY -> "Reintentando..."
        VoicePipelineStage.DISABLED -> ""
    }

    @Suppress("UNUSED_VARIABLE")
    val primaryButtonText = remember(exercise, setIndex, activeSide, isUnilateral) {
        if (exercise == null) "Completar Serie"
        else if (isUnilateral && activeSide != null) {
            val sideLabel = if (activeSide == "left") "Izquierda" else "Derecha"
            "Completar S${setIndex + 1} · $sideLabel"
        } else {
            "Completar S${setIndex + 1}"
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(5f)
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = voiceSessionState.stage != VoicePipelineStage.DISABLED && voiceIndicatorText.isNotBlank(),
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 292.dp),
                    shape = WorkoutUiTokens.ChipShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(voiceIndicatorColor)
                                .then(
                                    if (isListening) Modifier
                                        .scale(pulseScale)
                                        .alpha(pulseAlpha)
                                    else Modifier
                                )
                        )
                        Text(
                            text = voiceIndicatorText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (voiceSessionState.errorMessage != null && voiceSessionState.stage == VoicePipelineStage.ERROR_RECOVERY) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "(${voiceSessionState.errorMessage})",
                                color = Color(0xFFFFCDD2),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(51.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                FloatingActionButton(
                    onClick = { if (primaryActionEnabled) onPrimaryAction() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(45.dp),
                    shape = CircleShape,
                    containerColor = if (primaryActionEnabled) sessionAccentColor else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (primaryActionEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f),
                ) {
                    Icon(
                        imageVector = if (!primaryActionEnabled) {
                            Icons.Default.HourglassTop
                        } else if (isUpdateMode) {
                            Icons.Default.Update
                        } else {
                            Icons.Default.Check
                        },
                        contentDescription = if (!primaryActionEnabled) {
                            "Registrando serie"
                        } else if (isUpdateMode) {
                            "Actualizar serie"
                        } else {
                            "Completar serie"
                        },
                        modifier = Modifier.size(26.dp),
                    )
                }
                SmallFloatingActionButton(
                    onClick = onToggleVoice,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(32.dp)
                        .then(
                            if (isListening) Modifier
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                            else Modifier
                        ),
                    shape = CircleShape,
                    containerColor = if (voiceSessionEnabled) voiceIndicatorColor.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    contentColor = if (voiceSessionEnabled) Color.Black else Color.White.copy(alpha = 0.78f),
                ) {
                    Icon(
                        imageVector = if (voiceSessionEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (voiceSessionEnabled) "Desactivar control por voz" else "Activar control por voz",
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}
