package com.example.kpkn.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Píldora compacta de la app (KPKN_UI_V2 §6, P2): como [KpknPrimaryPill] y [KpknSecondaryPill], pero de [height] (44 dp por defecto) y
 * con el texto `action`. La zona táctil llega a 48 dp. Con [filled] va en tinta con texto negro; sin [filled], con borde de 1 dp en tinta
 * al 24 % y texto en tinta. Con [fullWidth] ocupa todo el ancho de su contenedor. Es un botón (`Role.Button`), sin ripple; al pulsar
 * escala a 0,97 con resorte.
 */
@Composable
fun KpknCompactPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    filled: Boolean = true,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "kpknCompactPillScale",
    )
    val fill = when {
        !filled -> Color.Transparent
        enabled -> WizardColors.text
        else -> WizardColors.ctaDisabled
    }
    val ink = when {
        !enabled -> WizardColors.ctaDisabledContent
        filled -> WizardColors.ctaContent
        else -> WizardColors.text
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .height(height)
                .clip(KpknShape.pill)
                .background(fill, KpknShape.pill)
                .then(if (!filled) Modifier.border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill) else Modifier)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.action,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
