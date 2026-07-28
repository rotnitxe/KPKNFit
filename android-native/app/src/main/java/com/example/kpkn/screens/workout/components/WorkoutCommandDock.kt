package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import kotlinx.coroutines.delay

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
    isRecording: Boolean = false,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    hazeState: HazeState? = null,
    isUpdateMode: Boolean = false,
    voicePushToTalk: Boolean = false,
    onPushToTalkStart: () -> Unit = {},
    onPushToTalkEnd: () -> Unit = {},
) {
    val isListening = voiceSessionState.stage == VoicePipelineStage.LISTENING
    val isArmed = voiceSessionState.stage == VoicePipelineStage.ARMED
    val isProcessing = voiceSessionState.stage == VoicePipelineStage.PROCESSING ||
            voiceSessionState.stage == VoicePipelineStage.CONFIRM_WAIT ||
            voiceSessionState.stage == VoicePipelineStage.TTS_SPEAKING

    val rmsNorm = remember(voiceSessionState.rmsLevel, isListening) {
        if (!isListening || voiceSessionState.rmsLevel == 0f) null
        else ((voiceSessionState.rmsLevel + 2f) / 12f).coerceIn(0f, 1f)
    }
    val (pulseScale, pulseAlpha) = if (rmsNorm != null) {
        (1f + rmsNorm * 0.28f) to (0.45f + rmsNorm * 0.55f)
    } else {
        rememberVoicePulse(enabled = voiceSessionEnabled && isListening)
    }

    val voiceIndicatorColor = when {
        isListening -> Color(0xFF4CAF50)
        isArmed -> Color(0xFF81C784)
        isProcessing -> MaterialTheme.colorScheme.tertiary
        voiceSessionState.stage == VoicePipelineStage.MIC_BUSY -> Color(0xFFFF9800)
        voiceSessionState.stage == VoicePipelineStage.RECONNECTING -> Color(0xFFFFB74D)
        voiceSessionState.stage == VoicePipelineStage.ERROR_RECOVERY -> Color(0xFFFF9800)
        voiceSessionEnabled -> MaterialTheme.colorScheme.secondary
        else -> Color.White.copy(alpha = 0.38f)
    }

    val voiceIndicatorText = when {
        voiceSessionState.stage == VoicePipelineStage.ARMED ->
            "Listo · mantén el mic para hablar"
        // Fallback/pausas antes que LISTENING: el stage suele seguir en LISTENING.
        voiceSessionState.usingNativeFallback -> "Fallback local en curso..."
        voiceSessionState.fallbackPaused -> "Fallback pausado por límite"
        voiceSessionState.stage == VoicePipelineStage.MIC_BUSY ->
            "Micrófono ocupado (llamada u otra app)"
        voiceSessionState.stage == VoicePipelineStage.RECONNECTING ->
            "Reconectando micrófono..."
        voiceSessionState.stage == VoicePipelineStage.LISTENING -> {
            if (voiceSessionState.partialText.isNotBlank()) "Escuchando: \"${voiceSessionState.partialText}\""
            else if (voicePushToTalk) "Escuchando (suelta al terminar)..."
            else "Escuchando comandos de voz..."
        }
        voiceSessionState.stage == VoicePipelineStage.PROCESSING -> "Procesando..."
        voiceSessionState.stage == VoicePipelineStage.CONFIRM_WAIT -> {
            if (voiceSessionState.pendingAddSetPersistence) {
                "¿Solo sesión o permanente?"
            } else {
                "¿Confirmar? Di \"Sí\" o \"No\""
            }
        }
        voiceSessionState.stage == VoicePipelineStage.TTS_SPEAKING -> "Hablando..."
        voiceSessionState.stage == VoicePipelineStage.ERROR_RECOVERY ->
            voiceSessionState.errorMessage?.let { "Error: $it" } ?: "Reintentando..."
        voiceSessionEnabled -> "Control por voz activo"
        else -> ""
    }

    val showVoiceChip = voiceSessionEnabled && voiceIndicatorText.isNotBlank()
    val micInteractionSource = remember { MutableInteractionSource() }
    val micPressed by micInteractionSource.collectIsPressedAsState()
    var suppressMicClickAfterHold by remember { mutableStateOf(false) }

    // Push-to-talk: hold starts ASR after 160ms; release ends it.
    // Quick tap (no hold) uses onClick to toggle the session off.
    LaunchedEffect(micPressed, voicePushToTalk, voiceSessionEnabled) {
        if (!voicePushToTalk || !voiceSessionEnabled) return@LaunchedEffect
        if (micPressed) {
            delay(160)
            if (micPressed) {
                suppressMicClickAfterHold = true
                onPushToTalkStart()
            }
        } else {
            onPushToTalkEnd()
        }
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
                visible = showVoiceChip,
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
                        // Live mic level meter when RMS is available
                        if (isListening && voiceSessionState.rmsLevel != 0f) {
                            val rmsNorm = ((voiceSessionState.rmsLevel + 2f) / 12f).coerceIn(0.08f, 1f)
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height((12.dp * rmsNorm).coerceAtLeast(3.dp))
                                    .clip(CircleShape)
                                    .background(voiceIndicatorColor.copy(alpha = 0.85f)),
                            )
                        }
                        Text(
                            text = voiceIndicatorText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (voiceSessionState.lastHeardSummary.isNotBlank()) {
                            Text(
                                text = voiceSessionState.lastHeardSummary,
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (voiceSessionState.usingOnDeviceRecognizer && isListening) {
                            Text(
                                text = if (voiceSessionState.usingNativeFallback) "fallback" else "vosk local",
                                color = Color(0xFF81C784),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                        voiceSessionState.activeRouteLabel?.takeIf { it.isNotBlank() }?.let { route ->
                            Text(
                                text = route,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (voiceSessionState.errorMessage != null && voiceSessionState.stage == VoicePipelineStage.ERROR_RECOVERY) {
                            Spacer(modifier.width(4.dp))
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
                    .width(128.dp)
                    .height(72.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                FloatingActionButton(
                    onClick = { if (primaryActionEnabled) onPrimaryAction() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(56.dp),
                    shape = CircleShape,
                    containerColor = if (primaryActionEnabled) sessionAccentColor else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (primaryActionEnabled) {
                        if (0.2126f * sessionAccentColor.red + 0.7152f * sessionAccentColor.green + 0.0722f * sessionAccentColor.blue > 0.45f) Color.Black else Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
                    },
                ) {
                    Icon(
                        imageVector = when {
                            isRecording -> Icons.Default.HourglassTop
                            isUpdateMode -> Icons.Default.Update
                            else -> Icons.Default.Check
                        },
                        contentDescription = when {
                            isRecording -> "Registrando serie"
                            !primaryActionEnabled -> "Completar serie no disponible"
                            isUpdateMode -> "Actualizar: $primaryButtonText"
                            else -> primaryButtonText
                        },
                        modifier = Modifier.size(28.dp),
                    )
                }
                SmallFloatingActionButton(
                    onClick = {
                        if (voicePushToTalk && voiceSessionEnabled && suppressMicClickAfterHold) {
                            suppressMicClickAfterHold = false
                            return@SmallFloatingActionButton
                        }
                        onToggleVoice()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 0.dp, top = 0.dp)
                        .size(48.dp)
                        .then(
                            if (isListening) Modifier
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                            else Modifier
                        ),
                    shape = CircleShape,
                    containerColor = when {
                        voiceSessionEnabled && isListening -> Color(0xFF4CAF50)
                        voiceSessionEnabled && isArmed -> Color(0xFF81C784)
                        voiceSessionEnabled -> voiceIndicatorColor.copy(alpha = 0.95f)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    },
                    contentColor = if (voiceSessionEnabled) Color.Black else Color.White.copy(alpha = 0.92f),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp,
                    ),
                    interactionSource = micInteractionSource,
                ) {
                    Icon(
                        imageVector = if (voiceSessionEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = when {
                            !voiceSessionEnabled -> "Activar control por voz"
                            voicePushToTalk -> "Mantén para hablar. Toque corto para apagar."
                            else -> "Desactivar control por voz"
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberVoicePulse(enabled: Boolean): Pair<Float, Float> {
    if (!enabled) return 1f to 1f
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
    return pulseScale to pulseAlpha
}
