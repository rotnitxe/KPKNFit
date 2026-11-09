package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknShadow
import dev.chrisbanes.haze.HazeState
import kotlin.math.max
import kotlin.math.roundToInt

/** De qué lado del ancla se abre el popover (`popAt` de `scenes-ed.js`). */
internal enum class EdPopoverSide {
    /** Debajo del ancla, con el borde derecho alineado (el menú «⋯» de la isla de arriba). */
    BelowEnd,

    /** Encima del ancla, con el borde izquierdo alineado (el menú «Añadir» de la barra de abajo). */
    AboveStart,
}

/** Dónde queda el popover, en px de ventana, y desde qué punto crece (`--ox`, `--oy`, `--dy`). */
internal data class EdPopoverPlacement(val x: Int, val y: Int, val originXFraction: Float, val originYFraction: Float, val dyPx: Float)

/**
 * Posición de un popover (`ed-pop`): 12 dp de margen lateral, 6 dp (debajo) u 8 dp (encima) de separación y origen del crecimiento en el
 * centro del ancla, acotado a 20 dp de los bordes del menú. Pura, para probarla.
 */
internal fun edPopoverPlacement(
    side: EdPopoverSide,
    anchor: Rect,
    menuWidthPx: Int,
    menuHeightPx: Int,
    containerWidthPx: Int,
    containerHeightPx: Int,
    density: Float,
    originXOverride: Float? = null,
): EdPopoverPlacement {
    val edge = (12 * density).roundToInt()
    val maxX = max(edge, containerWidthPx - edge - menuWidthPx)
    val x = when (side) {
        EdPopoverSide.BelowEnd -> (anchor.right - menuWidthPx).roundToInt()
        EdPopoverSide.AboveStart -> anchor.left.roundToInt()
    }.coerceIn(edge, maxX)
    val originX = originXOverride ?: ((anchor.center.x - x) / menuWidthPx.coerceAtLeast(1)).coerceIn(
        (20 * density) / menuWidthPx.coerceAtLeast(1),
        1f - (20 * density) / menuWidthPx.coerceAtLeast(1),
    )
    return when (side) {
        EdPopoverSide.BelowEnd -> EdPopoverPlacement(x, (anchor.bottom + 6 * density).roundToInt(), originX, 0f, -6f * density)
        EdPopoverSide.AboveStart -> {
            val y = (anchor.top - 8 * density - menuHeightPx).roundToInt().coerceAtLeast(edge)
            EdPopoverPlacement(x, y, originX, 1f, 6f * density)
        }
    }
}

/**
 * `.ed-pop.glassy`: menú de vidrio anclado (el «⋯» de la isla y el «Añadir» de la barra). Radio 20, relleno #1b1b1dd9 con desenfoque 22 y
 * saturación 1,5 (`vidrio-isla`), filete #ffffff1c, sombra `popover` y 6 dp de aire. Entra en 200 ms (opacidad, escala .92 y 6 dp de
 * desplazamiento desde su ancla). Tocar fuera lo cierra. Vive en un [KpknPortal].
 */
@Composable
internal fun EdGlassPopover(
    anchor: Rect,
    width: Dp,
    side: EdPopoverSide,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    originXFraction: Float? = null,
    hazeState: HazeState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedHazeState = hazeState ?: LocalHazeState.current
    val density = LocalDensity.current
    val reduced = wizardReducedMotion()
    var origin by remember { mutableStateOf<Offset?>(null) }
    val shape = RoundedCornerShape(20.dp)
    // pulido:animacion ed-A08 .ed-pop popin 0,2 s --ed-ease (opacidad 0→1, escala .92→1, desplazamiento 6 dp)
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) progress.animateTo(1f, tween(200, easing = EdMotion.Standard)) }
    KpknPortal {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .onGloballyPositioned { origin = it.positionInWindow() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Cerrar el menú" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDismiss),
            )
            val start = origin
            if (start != null) {
                Layout(
                    modifier = Modifier.fillMaxSize(),
                    content = {
                        Column(
                            modifier = modifier
                                .width(width)
                                // pulido:sombra ed-S05 .ed-pop 0 18px 44px #000d (preset popover)
                                .kpknShadow(KpknShadow.Popover, shape)
                                // pulido:vidrio ed-V02 .ed-pop.glassy blur 22 · saturate 1,5
                                .kpknGlassOrFallback(resolvedHazeState, shape, withBorder = false, preset = KpknGlassPreset.VidrioIsla)
                                .background(Color(0xD91B1B1D))
                                .border(1.dp, Color(0x1CFFFFFF), shape)
                                .padding(6.dp),
                            content = content,
                        )
                    },
                ) { measurables, constraints ->
                    val menu = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
                    val anchorLocal = Rect(anchor.left - start.x, anchor.top - start.y, anchor.right - start.x, anchor.bottom - start.y)
                    val placement = edPopoverPlacement(
                        side = side,
                        anchor = anchorLocal,
                        menuWidthPx = menu.width,
                        menuHeightPx = menu.height,
                        containerWidthPx = constraints.maxWidth,
                        containerHeightPx = constraints.maxHeight,
                        density = density.density,
                        originXOverride = originXFraction,
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        menu.placeWithLayer(IntOffset(placement.x, placement.y)) {
                            val p = progress.value
                            alpha = p
                            scaleX = 0.92f + 0.08f * p
                            scaleY = scaleX
                            translationY = placement.dyPx * (1f - p)
                            transformOrigin = TransformOrigin(placement.originXFraction, placement.originYFraction)
                        }
                    }
                }
            }
        }
    }
}
