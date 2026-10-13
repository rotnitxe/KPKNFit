package com.example.kpkn.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import kotlin.math.roundToInt

private val ContextMenuWidth = 228.dp
private val ContextMenuRowHeight = 52.dp
private val ContextMenuGap = 8.dp
private val ContextMenuEdge = 12.dp

/**
 * Menú de vidrio anclado a [anchor], en coordenadas de ventana (KPKN_UI_V2 §2): un panel de 228 dp alineado con el borde derecho del
 * ancla, debajo de ella o, si no cabe, encima. [rows] es el número de filas que lleva, para colocarlo. Tocar fuera lo cierra
 * ([onDismiss]). Sin estado: quien lo abre decide cuándo, con qué ancla y qué filas pone con [KpknContextMenuRow]. Vive en un
 * [KpknPortal] para que el desenfoque lo muestree.
 */
@Composable
fun KpknContextMenu(
    anchor: Rect,
    rows: Int,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val hazeState = LocalHazeState.current
    val reduced = wizardReducedMotion()
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val interaction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    // Dónde está el propio contenedor en la ventana: el ancla y el panel se miden en la misma escala.
    var origin by remember { mutableStateOf<Offset?>(null) }
    KpknPortal {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInWindow() },
        ) {
            // Velo invisible: cualquier toque fuera del panel lo cierra.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
            )
            val start = origin
            if (start != null) {
                val widthPx = with(density) { ContextMenuWidth.toPx() }
                val rowPx = with(density) { ContextMenuRowHeight.toPx() }
                val heightPx = rowPx * rows + with(density) { 12.dp.toPx() }
                val gapPx = with(density) { ContextMenuGap.toPx() }
                val edgePx = with(density) { ContextMenuEdge.toPx() }
                val screenWidthPx = with(density) { maxWidth.toPx() }
                val screenHeightPx = with(density) { maxHeight.toPx() }
                val left = (anchor.right - start.x - widthPx)
                    .coerceAtLeast(edgePx)
                    .coerceAtMost(maxOf(edgePx, screenWidthPx - widthPx - edgePx))
                val belowY = anchor.bottom - start.y + gapPx
                val aboveY = anchor.top - start.y - gapPx - heightPx
                val top = if (belowY + heightPx <= screenHeightPx - edgePx) belowY else maxOf(edgePx, aboveY)
                AnimatedVisibility(
                    visibleState = shown,
                    modifier = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) },
                    enter = if (reduced) {
                        fadeIn(animationSpec = tween(durationMillis = 120))
                    } else {
                        fadeIn(animationSpec = tween(durationMillis = 160)) +
                            scaleIn(
                                animationSpec = tween(durationMillis = 180),
                                initialScale = 0.96f,
                                transformOrigin = TransformOrigin(1f, 0f),
                            )
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .width(ContextMenuWidth)
                            // Velo del token al 96 %, como el respaldo sin desenfoque de KPKN_UI_V2 §2: el texto de debajo no se transparenta.
                            .kpknGlassOrFallback(
                                hazeState,
                                KpknShape.module,
                                additionalScrim = KpknSurface.module.copy(alpha = 0.96f),
                                variant = KpknGlassVariant.ISLAND,
                            )
                            .padding(vertical = 6.dp),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Una fila de [KpknContextMenu]: 52 dp como mínimo, texto en tinta (en `danger` si es destructiva, en `textMuted` si va sangrada)
 * y sin ripple. No cierra el menú: quien la pulsa decide si lo cierra.
 */
@Composable
fun KpknContextMenuRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    indented: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ContextMenuRowHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = if (indented) 32.dp else 18.dp, end = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = when {
                destructive -> WizardColors.danger
                indented -> WizardColors.textMuted
                else -> WizardColors.text
            },
        )
    }
}
