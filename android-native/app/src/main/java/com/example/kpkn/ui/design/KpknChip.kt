package com.example.kpkn.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Píldora de filtro (KPKN_UI_V2): 32 dp de alto. Sin elegir, `inset` con filete de blanco al 8 % y texto en tinta; elegida, tinta con
 * texto negro. Toda la píldora es `Role.Checkbox` y expone `selected`. Su zona táctil mide 48 dp. Sustituye a `FilterChip`.
 */
@Composable
fun KpknChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    /** Alto visible de la píldora; 32 dp salvo que una pantalla pida otro (Programas usa 30 dp, como su lienzo). */
    height: Dp = 32.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) WizardColors.text else KpknSurface.inset,
        animationSpec = tween(durationMillis = 180),
        label = "kpknChipFill",
    )
    val ink = if (selected) WizardColors.ctaContent else WizardColors.text
    val edge = if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(height)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(KpknShape.pill)
                .background(fill, KpknShape.pill)
                .border(width = 1.dp, color = edge, shape = KpknShape.pill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ink,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = KpknType.chip,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
