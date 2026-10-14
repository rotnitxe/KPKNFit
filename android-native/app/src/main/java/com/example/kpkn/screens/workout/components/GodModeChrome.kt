package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.exercises.GodModeTechniqueScope
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.KpknSheetWhiteButton

internal fun godModeEnterSpec() = tween<Float>(
    durationMillis = WorkoutUiTokens.GodModeEnterMs,
    easing = FastOutSlowInEasing,
)

internal fun godModeExitSpec() = tween<Float>(
    durationMillis = WorkoutUiTokens.GodModeExitMs,
    easing = FastOutSlowInEasing,
)

@Composable
internal fun Modifier.godModeScrim(active: Boolean): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (active) WorkoutUiTokens.GodModeScrimAlpha else 0f,
        animationSpec = if (active) godModeEnterSpec() else godModeExitSpec(),
        label = "godModeScrim",
    )
    return this.drawWithContent {
        drawContent()
        if (alpha > 0.01f) {
            drawRect(Color.Black.copy(alpha = alpha))
        }
    }
}

@Composable
internal fun GodModeSetActionsOverlay(
    visible: Boolean,
    isDropSet: Boolean,
    isRestPause: Boolean,
    canDeleteSet: Boolean,
    onDropSet: () -> Unit,
    onRestPause: () -> Unit,
    onDeleteSet: () -> Unit,
    onOmitSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.98f, animationSpec = tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 0.98f, animationSpec = tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Convertir serie normal a:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (isDropSet) {
                        "Dropset: baja ~5 kg para 3 reps más. No tires la carga."
                    } else if (isRestPause) {
                        "Rest-pause: mismo peso, 15 s de pausa, luego 3 reps."
                    } else {
                        "Dropset = −5 kg / 3 reps. Rest-pause = mismo peso, 15 s, 3 reps."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GodModeOverlayAction(
                        label = "Dropset",
                        selected = isDropSet,
                        onClick = onDropSet,
                        modifier = Modifier.weight(1f),
                    )
                    GodModeOverlayAction(
                        label = "Rest-Pause",
                        selected = isRestPause,
                        onClick = onRestPause,
                        modifier = Modifier.weight(1f),
                    )
                }
                GodModeOverlayAction(
                    label = "Eliminar serie",
                    enabled = canDeleteSet,
                    danger = true,
                    onClick = onDeleteSet,
                    modifier = Modifier.fillMaxWidth(),
                )
                GodModeOverlayAction(
                    label = "Omitir serie",
                    onClick = onOmitSet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GodModeOverlayAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = when {
            selected -> Color.White.copy(alpha = 0.22f)
            danger -> WorkoutUiTokens.dangerColor().copy(alpha = 0.22f)
            else -> Color(0xFF2B2B2B).copy(alpha = 0.92f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                selected -> Color.White.copy(alpha = 0.55f)
                danger -> WorkoutUiTokens.dangerColor().copy(alpha = 0.45f)
                else -> Color.White.copy(alpha = 0.14f)
            },
        ),
        modifier = modifier.heightIn(min = 34.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    danger -> WorkoutUiTokens.dangerColor()
                    selected -> Color.White
                    else -> Color.White.copy(alpha = if (enabled) 0.88f else 0.38f)
                },
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun GodModeTechniqueScopeDialog(
    technique: SeriesTechnique,
    onPick: (GodModeTechniqueScope) -> Unit,
    onDismiss: () -> Unit,
) {
    val label = if (technique == SeriesTechnique.DROPSET) "Drop-set" else "Rest-pause"
    KpknGlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Aplicar $label",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                color = Color.White,
            )
            KpknSheetWhiteButton(text = "Esta serie", onClick = { onPick(GodModeTechniqueScope.THIS) })
            KpknSheetWhiteButton(text = "Restantes", onClick = { onPick(GodModeTechniqueScope.REMAINING) })
            KpknSheetWhiteButton(text = "Todas", onClick = { onPick(GodModeTechniqueScope.ALL) })
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.White)
            }
        }
    }
}
