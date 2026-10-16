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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.exercises.GodModeTechniqueScope
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

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

/** Insignia de la tira del GodMode (LR1c2): círculo `inset` con filete de 1 dp y el icono en tinta; la de borrar va en `danger`. */
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
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(WorkoutUiTokens.MinTouchTarget)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visual)
                .clip(CircleShape)
                .background(if (danger) WizardColors.danger else KpknSurface.inset, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    danger -> WizardColors.ctaContent
                    enabled -> WizardColors.text
                    else -> WizardColors.text.copy(alpha = 0.38f)
                },
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
            icon = KpknLineIcons.Close,
            contentDescription = "Eliminar ejercicio",
            onClick = onDelete,
            enabled = canDelete,
            danger = true,
            modifier = Modifier.offset(x = 8.dp, y = (-8).dp),
        )
    }
}

/**
 * Tarjeta «+» de la tira del GodMode (LR1c2): píldora `inset` con filete y el «+» en tinta. El acento de la sesión ya no la tiñe; la
 * firma se conserva porque la tira de LR1a la llama con el acento.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun GodModePlusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
) {
    val chromeScale = LocalViewportAdapt.current.uniformScale
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .width(WorkoutUiTokens.GodModePlusCardWidth)
            .height(WorkoutUiTokens.liveRoadmapCarouselCardHeight(chromeScale))
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = KpknLineIcons.Plus,
            contentDescription = "Agregar ejercicio",
            tint = WizardColors.text,
        )
    }
}

/** Barra del modo por lotes (LR1c2): el contador en `metricM` y las acciones como píldoras `inset`; «Eliminar», en `danger`. */
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
            style = KpknType.metricM,
            color = WizardColors.text,
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
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill)
                .border(
                    1.dp,
                    if (danger) WizardColors.danger.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                    KpknShape.pill,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = KpknType.chip,
                color = when {
                    danger -> WizardColors.danger
                    enabled -> WizardColors.text
                    else -> WizardColors.text.copy(alpha = 0.38f)
                },
                maxLines = 1,
            )
        }
    }
}

/**
 * Código muerto, a la espera de la tarjeta de la serie (LV8): las acciones de la serie del modo dios («Convertir a dropset / rest-pause»,
 * «Eliminar serie» y «Omitir serie») ya viven en las opciones de la serie (`SeriesOptionsSheet`, LV11): «Saltar» y «Borrar» con su
 * «Deshacer», y el tipo de serie con su alcance. La tarjeta (`SetExecutionCard`) aún la llama con `visible = false`; cuando LV8 suelte esa
 * llamada y sus parámetros (`godModeActionsVisible`, `isDropSet`, `isRestPause`, `canDeleteSet`, `onDropSet`, `onRestPause`,
 * `onDeleteSet`, `onOmitSet`), este stub se borra.
 */
@Suppress("UNUSED_PARAMETER")
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
) = Unit
