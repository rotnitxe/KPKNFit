package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.services.workout.VoicePipelineStage

@Composable
fun WorkoutVoiceFab(
    isEnabled: Boolean,
    voiceStage: VoicePipelineStage,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val isListening = voiceStage == VoicePipelineStage.LISTENING
    val isProcessing = voiceStage == VoicePipelineStage.PROCESSING ||
            voiceStage == VoicePipelineStage.CONFIRM_WAIT ||
            voiceStage == VoicePipelineStage.TTS_SPEAKING

    val containerColor = when {
        isListening -> Color(0xFF4CAF50)
        isProcessing -> MaterialTheme.colorScheme.tertiary
        voiceStage == VoicePipelineStage.ERROR_RECOVERY -> Color(0xFFFFA000)
        isEnabled -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val icon = when {
        isEnabled -> Icons.Default.Mic
        else -> Icons.Default.MicOff
    }

    SmallFloatingActionButton(
        onClick = onToggle,
        modifier = modifier
            .zIndex(5f)
            .then(
                if (isListening) Modifier.scale(pulseScale).alpha(pulseAlpha)
                else Modifier
            ),
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isEnabled) "Desactivar voz" else "Activar voz",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutVoiceStatusBar(
    voiceStage: VoicePipelineStage,
    voicePartialText: String,
    voiceErrorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val isVisible = voiceStage != VoicePipelineStage.DISABLED

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier.fillMaxWidth().zIndex(4f),
        enter = fadeIn(tween(150)) + scaleIn(tween(150)),
        exit = fadeOut(tween(200)) + scaleOut(tween(200)),
    ) {
        val (statusText, bgColor) = when (voiceStage) {
            VoicePipelineStage.LISTENING -> {
                if (voicePartialText.isNotBlank()) "Escuchando: \"$voicePartialText\"" to Color(0xFF4CAF50)
                else "Escuchando comandos..." to Color(0xFF4CAF50)
            }
            VoicePipelineStage.PROCESSING -> "Procesando..." to Color(0xFF795548)
            VoicePipelineStage.CONFIRM_WAIT -> "\"Sí\" para confirmar, \"No\" para cancelar" to Color(0xFF7CB342)
            VoicePipelineStage.TTS_SPEAKING -> "Hablando..." to Color(0xFF1565C0)
            VoicePipelineStage.ERROR_RECOVERY -> "Error, reintentando..." to Color(0xFFFFA000)
            VoicePipelineStage.DISABLED -> "" to Color.Transparent
        }

        if (statusText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(0.dp),
                color = bgColor.copy(alpha = 0.92f),
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (voiceErrorMessage != null && voiceStage == VoicePipelineStage.ERROR_RECOVERY) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = voiceErrorMessage,
                            color = Color(0xFFFFCDD2),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
