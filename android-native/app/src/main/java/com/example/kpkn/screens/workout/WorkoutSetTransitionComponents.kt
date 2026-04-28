package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutSetTransitionBanner(
    transitionTarget: WorkoutStageTransitionTarget?,
    pulseToken: Long?,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isRecentPulse = isWorkoutPulseActive(pulseToken, nowMs)
    var visible by remember(transitionTarget?.exerciseId, transitionTarget?.order) { mutableStateOf(transitionTarget != null) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "workout-transition-banner-scale",
    )

    LaunchedEffect(transitionTarget?.exerciseId, transitionTarget?.order, pulseToken) {
        visible = transitionTarget != null
        if (transitionTarget != null) {
            val started = System.currentTimeMillis()
            do {
                nowMs = System.currentTimeMillis()
                kotlinx.coroutines.delay(120L)
            } while ((System.currentTimeMillis() - started) < 1800L || isWorkoutPulseActive(pulseToken, nowMs))
            visible = false
        }
    }

    AnimatedVisibility(
        visible = transitionTarget != null && visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val accent = if (isRecentPulse) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = accent.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isRecentPulse) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = accent,
                )
                Column(
                    modifier = Modifier.widthIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = transitionTarget?.label ?: "Siguiente bloque",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isRecentPulse) {
                            "Carga futura recalculada y lista para registrar."
                        } else {
                            "Cambio de serie dentro del ejercicio actual."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accent.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = if (isRecentPulse) "Autoajuste" else "Flujo activo",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
