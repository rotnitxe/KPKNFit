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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.exercises.GodModeTechniqueScope
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import com.example.kpkn.ui.components.kpknSheetWhiteFieldColors

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
internal fun GodModeRoadmapBadge(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val visual = WorkoutUiTokens.GodModeBadgeVisual
    Box(
        modifier = modifier
            .size(WorkoutUiTokens.MinTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visual)
                .clip(CircleShape)
                .background(
                    if (danger) WorkoutUiTokens.dangerColor().copy(alpha = 0.92f)
                    else KpknGlass.FallbackScrim.copy(alpha = 0.94f),
                )
                .border(1.dp, KpknGlass.BorderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (danger) Color.White else Color.White.copy(alpha = if (enabled) 0.82f else 0.38f),
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
internal fun BoxScope.GodModeRoadmapBadges(
    visible: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    showDelete: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible && showDelete,
        enter = fadeIn(tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.86f, animationSpec = tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 0.86f, animationSpec = tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing)),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .wrapContentSize(unbounded = true, align = Alignment.TopEnd),
    ) {
        GodModeRoadmapBadge(
            icon = Icons.Default.Close,
            contentDescription = "Eliminar ejercicio",
            onClick = onDelete,
            enabled = canDelete,
            danger = true,
            modifier = Modifier.offset(x = 8.dp, y = (-8).dp),
        )
    }
}

@Composable
internal fun GodModePlusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
) {
    Box(
        modifier = modifier
            .width(WorkoutUiTokens.GodModePlusCardWidth)
            .height(WorkoutUiTokens.GodModeRoadmapCardHeight)
            .clip(WorkoutUiTokens.InnerCardShape)
            .background(accent.copy(alpha = 0.26f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f), WorkoutUiTokens.InnerCardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Agregar ejercicio",
            tint = Color.White.copy(alpha = 0.86f),
        )
    }
}

@Composable
internal fun GodModeBatchBar(
    selectedCount: Int,
    onCreateSuperset: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    onUltraFast: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$selectedCount",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            color = Color.White,
        )
        GodModeTextAction("Superserie", onCreateSuperset, enabled = selectedCount >= 2)
        GodModeTextAction("Omitir", onSkip)
        GodModeTextAction("Eliminar", onDelete, danger = true)
        GodModeTextAction("Ultrarrápido", onUltraFast)
        GodModeTextAction("Cerrar", onClear)
    }
}

@Composable
private fun GodModeTextAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (danger) WorkoutUiTokens.dangerColor().copy(alpha = 0.22f) else Color(0xFF2B2B2B),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (danger) WorkoutUiTokens.dangerColor() else Color.White.copy(alpha = if (enabled) 0.88f else 0.38f),
            maxLines = 1,
        )
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

@Composable
internal fun GodModeNicknameDialog(
    initialValue: String,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apodo del ejercicio", fontWeight = androidx.compose.ui.text.font.FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reemplaza el nombre en las sesiones. En el catálogo se verá el nombre original y a.k.a. este apodo.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Apodo") },
                    colors = kpknSheetWhiteFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (initialValue.isNotBlank()) {
                    TextButton(onClick = onRemove) { Text("Quitar") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
