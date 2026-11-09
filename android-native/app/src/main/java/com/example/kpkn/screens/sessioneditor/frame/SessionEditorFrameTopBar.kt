package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.EdMenuRow
import com.example.kpkn.screens.sessioneditor.components.edText
import com.example.kpkn.ui.design.motion.kpknAnimate
import dev.chrisbanes.haze.HazeState

/** Alto de la fila superior del editor bajo la barra de estado: 8 dp de margen + 48 dp de islas. Es también el hueco superior de la lista. */
internal val SessionEditorFrameTopBarHeight = 56.dp

/**
 * Isla superior del editor (Pulido v1 · EDITOR · A2, escenas `ed-lista`, `ed-lista-scroll`, `ed-dia` y `ed-pildora-mas`): UNA isla de vidrio
 * (`.ed-top`: 56 dp de alto como mínimo, radio 30, 4 dp de aire, 4 de separación) con «Atrás» y «⋯» como círculos de 48 dp y, en medio, la
 * píldora del día (`#ffffff0c`, 48 dp) con «‹ Jue · Semana 3 ⌄ ›». Las flechas pasan al día anterior o siguiente con sesión; tocar el texto
 * abre la semana ([weekOpen] gira la flecha ⌄) y «⋯» abre «Transferir» y «Versiones» en un popover de vidrio. El contenido de la lista pasa
 * por debajo, desenfocado: el vidrio lee [hazeState].
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
    hazeState: HazeState? = null,
    weekOpen: Boolean = false,
) {
    var islandBounds by remember { mutableStateOf<Rect?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    val reduced = wizardReducedMotion()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, top = 8.dp, end = 12.dp)
            .defaultMinSize(minHeight = 56.dp)
            .onGloballyPositioned { islandBounds = it.boundsInWindow() },
    ) {
        // El vidrio es una capa detrás: así el contenido no queda recortado por él.
        Box(modifier = Modifier.matchParentSize().edGlass(hazeState, EdTopIslandShape))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // pulido:animacion ed-J10 «Atrás» late de escala .9 a 1 en 200 ms al tocarlo
            EdIslandCircleButton(icon = EdListIcons.Back, contentDescription = "Atrás", onClick = onBack, tag = "session_editor_back", tapScaleFrom = 0.9f)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Color(0x0CFFFFFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EdDayArrow(
                    icon = EdListIcons.Back,
                    contentDescription = "Día anterior",
                    enabled = canGoPrevious,
                    onClick = onPrevious,
                )
                // pulido:transicion ed-T10 .ed-daybtn svg giro 0,22 s --ed-ease · ed-X02 rotate(180deg) con la semana abierta
                val chevronTurn by animateFloatAsState(
                    targetValue = if (weekOpen) 180f else 0f,
                    animationSpec = if (reduced) tween(0) else tween(220, easing = EdMotion.Standard),
                    label = "edWeekChevron",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = "Abrir la semana: $dayLabel" }
                        .testTag("session_editor_open_week")
                        .edPressable(RoundedCornerShape(22.dp), pressedColor = Color.Transparent, onClickLabel = "Abrir la semana", onClick = onOpenWeek),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // pulido:animacion ed-A01 .ed-daylbl ed-lbl-r 0,22 s --ed-ease · ed-A02 .ed-daylbl.back ed-lbl-l (el texto nuevo entra desde el lado del día)
                    Text(
                        text = dayLabel,
                        modifier = Modifier
                            .weight(1f)
                            .kpknAnimate(
                                if (forward) EdFamilies.LabelFromRight else EdFamilies.LabelFromLeft,
                                restartKey = dayLabel,
                            ),
                        style = edText(13.5f, lineHeight = 15.5f),
                        // Día y semana caben en dos líneas al aumentar la letra.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Box(Modifier.width(4.dp))
                    Icon(
                        imageVector = EdListIcons.Down,
                        contentDescription = null,
                        tint = EdColors.muted,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = chevronTurn },
                    )
                }
                EdDayArrow(
                    icon = EdListIcons.Forward,
                    contentDescription = "Día siguiente",
                    enabled = canGoNext,
                    onClick = onNext,
                )
            }
            EdIslandCircleButton(icon = EdListIcons.More, contentDescription = "Más opciones", onClick = { moreOpen = true }, tag = "session_editor_more")
        }
    }

    val anchor = islandBounds
    if (moreOpen && anchor != null) {
        EdGlassPopover(anchor = anchor, width = 208.dp, side = EdPopoverSide.BelowEnd, onDismiss = { moreOpen = false }, hazeState = hazeState) {
            EdMenuRow(
                title = "Transferir",
                icon = EdListIcons.Swap,
                onClick = {
                    moreOpen = false
                    onTransfer()
                },
            )
            EdMenuRow(
                title = "Versiones",
                icon = EdListIcons.History,
                onClick = {
                    moreOpen = false
                    onHistory()
                },
            )
        }
    }
}

/** `.ed-ib`: círculo de 48 dp con un icono de 22 dp y fondo #ffffff14 al pulsarlo (atrás y ⋯). */
@Composable
private fun EdIslandCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String,
    tapScaleFrom: Float = 1f,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(tag)
            .semantics { this.contentDescription = contentDescription }
            .edPressable(CircleShape, tapScaleFrom = tapScaleFrom, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = EdColors.ink, modifier = Modifier.size(22.dp))
    }
}

/** `.ed-arrow`: flecha de día de 44 × 48 dp con icono de 20: tinta si hay sesión a donde ir y `dim` si no. Sin sesión vecina no hace nada. */
@Composable
private fun EdDayArrow(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 48.dp)
            .semantics { this.contentDescription = contentDescription }
            .edPressable(RoundedCornerShape(22.dp), pressedColor = Color(0x14FFFFFF), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // pulido:transicion ed-T04 .ed-arrow color 0,16 s ease
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) EdColors.ink else EdColors.dim,
            modifier = Modifier.size(20.dp),
        )
    }
}
