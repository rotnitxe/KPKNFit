package com.example.kpkn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Diámetro de cada punto y largo del punto de la página actual. */
private val DotHeight = 6.dp
private val DotActiveWidth = 18.dp

/** Área táctil de cada punto cuando el indicador cambia de página. */
private val DotTouchSize = 48.dp

/** Duración del estiramiento al cambiar de página (con movimiento reducido es inmediato). */
private const val DotMillis = 220

/**
 * Indicador de páginas público. Un punto por página; el de la página [current] se estira en píldora.
 *
 * Con [onSelect], cada punto es un objetivo táctil de 48 dp que cambia de página y se anuncia como «Página 2 de 4».
 * Sin él, el indicador solo informa de en qué página estás.
 */
@Composable
fun PagerDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
) {
    if (count <= 0) return
    val millis = if (wizardReducedMotion()) 0 else DotMillis
    val selectedIndex = current.coerceIn(0, count - 1)
    val reading = if (onSelect == null) {
        Modifier.semantics { contentDescription = "Página ${selectedIndex + 1} de $count" }
    } else {
        Modifier
    }
    Row(
        modifier = modifier.then(reading),
        horizontalArrangement = if (onSelect == null) Arrangement.spacedBy(8.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until count) {
            val selected = index == selectedIndex
            val width by animateDpAsState(
                targetValue = if (selected) DotActiveWidth else DotHeight,
                animationSpec = tween(millis),
                label = "pagerDotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) WizardColors.text else WizardColors.textFaint,
                animationSpec = tween(millis),
                label = "pagerDotColor",
            )
            if (onSelect == null) {
                PagerDot(width = width, color = color)
            } else {
                Box(
                    modifier = Modifier
                        .size(DotTouchSize)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .semantics { contentDescription = "Página ${index + 1} de $count" },
                    contentAlignment = Alignment.Center,
                ) {
                    PagerDot(width = width, color = color)
                }
            }
        }
    }
}

@Composable
private fun PagerDot(width: Dp, color: Color) {
    Box(Modifier.size(width, DotHeight).clip(CircleShape).background(color))
}
