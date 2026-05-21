package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.services.workout.VoicePipelineStage
import com.example.kpkn.services.workout.VoiceSessionState

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
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    hazeState: HazeState? = null,
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

    val primaryButtonText = remember(exercise, setIndex, activeSide, isUnilateral) {
        if (exercise == null) "Completar Serie"
        else if (isUnilateral && activeSide != null) {
            val sideLabel = if (activeSide == "left") "Izquierda" else "Derecha"
            "Completar S${setIndex + 1} · $sideLabel"
        } else {
            "Completar S${setIndex + 1}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(5f)
    ) {
        // --- Live Voice Status Overlay (anchored just above the glass dock) ---
        AnimatedVisibility(
            visible = voiceSessionState.stage != VoicePipelineStage.DISABLED && voiceIndicatorText.isNotBlank(),
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.54f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsing active dot
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

        // --- Glassmorphic Command Dock ---
        WorkoutGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
            shape = WorkoutUiTokens.CardShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Microphone Toggle button with smooth visual feedback
                Surface(
                    onClick = onToggleVoice,
                    modifier = Modifier
                        .size(44.dp)
                        .then(
                            if (isListening) Modifier
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                            else Modifier
                        ),
                    shape = CircleShape,
                    color = if (voiceSessionEnabled) voiceIndicatorColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (voiceSessionEnabled) voiceIndicatorColor.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (voiceSessionEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (voiceSessionEnabled) "Desactivar control por voz" else "Activar control por voz",
                            tint = if (voiceSessionEnabled) voiceIndicatorColor else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Primary Adaptative Action Button
                WorkoutPrimaryActionButton(
                    text = primaryButtonText,
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    containerColor = sessionAccentColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            }
        }
    }
}
