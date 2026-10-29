package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/**
 * Una opción de un menú anclado (P2). [destructive] la pinta en `danger`. [onClick] se ejecuta después de cerrar el menú. Con [icon] el menú
 * toma la forma `.menu` del lienzo v6 (m-day): 236 dp, filas de 46 dp con el icono de 18 dp delante; [dividerBefore] pone el `hr` encima.
 */
internal data class ProgramAnchoredMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val icon: ImageVector? = null,
    val dividerBefore: Boolean = false,
)

private val MenuWidth = 228.dp
private val MenuRowHeight = 52.dp
private val MenuGap = 8.dp
private val MenuEdge = 12.dp
private val LienzoMenuWidth = 236.dp
private val LienzoMenuRowHeight = 46.dp
private val LienzoMenuStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp)

/**
 * Menú de vidrio anclado a [anchor] (P2, KPKN_UI_V2 §2): un panel de 228 dp alineado con el borde derecho del ancla, debajo de ella o,
 * si no cabe, encima. Tocar fuera lo cierra. Como «Más opciones», vive en un [KpknPortal] para que el desenfoque lo muestree. Sin estado:
 * la pantalla decide cuándo se abre y con qué ancla.
 */
@Composable
internal fun ProgramItemMenu(
    items: List<ProgramAnchoredMenuItem>,
    anchor: Rect,
    onDismiss: () -> Unit,
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
                    .semantics { contentDescription = "Cerrar el menú" }
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onDismiss),
            )
            val start = origin
            if (start != null) {
                val lienzo = items.any { it.icon != null }
                val menuWidth = if (lienzo) LienzoMenuWidth else MenuWidth
                val widthPx = with(density) { menuWidth.toPx() }
                val rowPx = with(density) { (if (lienzo) LienzoMenuRowHeight else MenuRowHeight).toPx() }
                val heightPx = rowPx * items.size + with(density) { (12.dp + 13.dp * items.count { it.dividerBefore }).toPx() }
                val gapPx = with(density) { MenuGap.toPx() }
                val edgePx = with(density) { MenuEdge.toPx() }
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
                            .width(menuWidth)
                            .kpknGlassOrFallback(hazeState, if (lienzo) RoundedCornerShape(22.dp) else KpknShape.module, variant = KpknGlassVariant.ISLAND)
                            .padding(horizontal = if (lienzo) 6.dp else 0.dp, vertical = 6.dp),
                    ) {
                        items.forEach { item ->
                            if (item.dividerBefore) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 8.dp)
                                        .height(1.dp)
                                        .background(Color.White.copy(alpha = 0.08f)),
                                )
                            }
                            if (lienzo) {
                                LienzoAnchoredMenuRow(item = item, onDismiss = onDismiss)
                            } else {
                                ProgramAnchoredMenuRow(item = item, onDismiss = onDismiss)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Una fila del menú: 52 dp como mínimo, texto en tinta (o en `danger` si es destructiva) y sin ripple. */
@Composable
private fun ProgramAnchoredMenuRow(item: ProgramAnchoredMenuItem, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MenuRowHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {
                    onDismiss()
                    item.onClick()
                },
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = item.label,
            style = KpknType.bodyStrong,
            color = if (item.destructive) WizardColors.danger else WizardColors.text,
        )
    }
}

/** Una fila `.menu button` del lienzo: 46 dp, 12 dp a los lados, el icono de 18 dp y el texto a 12 dp, Inter 14 sp. */
@Composable
private fun LienzoAnchoredMenuRow(item: ProgramAnchoredMenuItem, onDismiss: () -> Unit) {
    val color = if (item.destructive) WizardColors.danger else WizardColors.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = LienzoMenuRowHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {
                    onDismiss()
                    item.onClick()
                },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item.icon?.let {
            Icon(it, contentDescription = null, tint = if (item.destructive) WizardColors.danger else WizardColors.textMuted, modifier = Modifier.size(18.dp))
        }
        Text(text = item.label, style = LienzoMenuStyle, color = color)
    }
}
