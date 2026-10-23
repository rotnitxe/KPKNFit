package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** Alto de la fila superior fija del editor (56 dp, como la del original en el resto de pantallas). */
internal val SessionEditorFrameTopBarHeight = 56.dp

/**
 * Fila superior fija del editor (ED-A · A1). A la izquierda, «Atrás»: guarda lo pendiente y sale. En el centro, la píldora «‹ Jue ·
 * Semana 3 ⌄ ›»: las flechas pasan al día anterior o siguiente con sesión (sin pasar por días vacíos) y tocar el texto abre la semana.
 * A la derecha, «⋯» con Transferir y Versiones, lo que había bajo la portada en el original. Todo mide 48 dp o más.
 *
 * @param forward true si el día nuevo viene después del anterior: el texto de la píldora entra desde la derecha; si no, desde la izquierda.
 */
@Composable
internal fun SessionEditorFrameTopBar(
    dayLabel: String,
    forward: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenWeek: () -> Unit,
    onTransfer: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreAnchor by remember { mutableStateOf<Rect?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    val reduced = wizardReducedMotion()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(SessionEditorFrameTopBarHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknIconButton(
            icon = KpknLineIcons.ChevronLeft,
            contentDescription = "Atrás",
            onClick = onBack,
        )
        Spacer(Modifier.width(6.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(KpknSurface.inset, KpknShape.pill)
                .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrameDayArrow(
                icon = KpknLineIcons.ChevronLeft,
                contentDescription = "Día anterior",
                enabled = canGoPrevious,
                onClick = onPrevious,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = "Abrir la semana: $dayLabel" }
                    .testTag("session_editor_open_week")
                    .clickable(role = Role.Button, onClickLabel = "Abrir la semana", onClick = onOpenWeek),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AnimatedContent(
                    targetState = dayLabel,
                    modifier = Modifier.weight(1f, fill = false),
                    transitionSpec = { frameLabelTransition(forward = forward, reduced = reduced) },
                    label = "frameDayLabel",
                ) { label ->
                    Text(
                        text = label,
                        style = KpknType.bodyStrong.copy(fontSize = 13.sp, lineHeight = 16.sp),
                        color = WizardColors.text,
                        // Día y semana caben en dos líneas al aumentar la letra.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = KpknLineIcons.ChevronDown,
                    contentDescription = null,
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
            FrameDayArrow(
                icon = KpknLineIcons.ChevronRight,
                contentDescription = "Día siguiente",
                enabled = canGoNext,
                onClick = onNext,
            )
        }
        Spacer(Modifier.width(6.dp))
        KpknIconButton(
            icon = KpknLineIcons.More,
            contentDescription = "Más opciones",
            onClick = { moreOpen = true },
            modifier = Modifier.onGloballyPositioned { moreAnchor = it.boundsInWindow() },
        )
    }

    val anchor = moreAnchor
    if (moreOpen && anchor != null) {
        KpknContextMenu(anchor = anchor, rows = 2, onDismiss = { moreOpen = false }) {
            KpknContextMenuRow(
                label = "Transferir",
                onClick = {
                    moreOpen = false
                    onTransfer()
                },
            )
            KpknContextMenuRow(
                label = "Versiones",
                onClick = {
                    moreOpen = false
                    onHistory()
                },
            )
        }
    }
}

/** Flecha de día de 48 dp: tinta si hay sesión a donde ir y apagada si no. Sin sesión vecina no hace nada. */
@Composable
private fun FrameDayArrow(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .clip(CircleShape)
            .clickable(role = Role.Button) { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) WizardColors.text else WizardColors.textFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Entrada y salida del texto de la píldora: desliza 220 ms con `FastOutSlowIn`; con movimiento reducido, solo un fundido. */
private fun frameLabelTransition(forward: Boolean, reduced: Boolean): ContentTransform {
    if (reduced) {
        return fadeIn(animationSpec = tween(durationMillis = 120)) togetherWith
            fadeOut(animationSpec = tween(durationMillis = 120))
    }
    val spec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)
    val enter: EnterTransition = if (forward) {
        slideInHorizontally(animationSpec = spec) { it / 2 } + fadeIn(animationSpec = tween(220))
    } else {
        slideInHorizontally(animationSpec = spec) { -it / 2 } + fadeIn(animationSpec = tween(220))
    }
    val exit: ExitTransition = if (forward) {
        slideOutHorizontally(animationSpec = spec) { -it / 2 } + fadeOut(animationSpec = tween(220))
    } else {
        slideOutHorizontally(animationSpec = spec) { it / 2 } + fadeOut(animationSpec = tween(220))
    }
    return enter togetherWith exit
}
