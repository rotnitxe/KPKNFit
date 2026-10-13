package com.example.kpkn.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Casilla de la app (KPKN_UI_V2): igual que [KpknRadioRow], con un cuadrado de 20 dp y radio de 6 dp. Marcada, se rellena de tinta y lleva
 * el check [KpknLineIcons.Check] en negro. Toda la fila es `Role.Checkbox` y expone `checked`.
 */
@Composable
fun KpknCheckRow(
    checked: Boolean,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    KpknOptionRow(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = interaction,
            indication = null,
            onValueChange = { onClick() },
        ),
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        mark = { KpknCheckMark(checked = checked) },
    )
}

/** Casilla de 20 dp: anillo de 1,75 dp que pasa a tinta y, marcada, relleno de tinta con el check en negro. */
@Composable
private fun KpknCheckMark(checked: Boolean) {
    val reduced = wizardReducedMotion()
    val tickSpec: AnimationSpec<Float> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    }
    val tick = animateFloatAsState(targetValue = if (checked) 1f else 0f, animationSpec = tickSpec, label = "kpknCheckTick")
    val fill by animateColorAsState(
        targetValue = if (checked) WizardColors.text else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "kpknCheckFill",
    )
    val ring by animateColorAsState(
        targetValue = if (checked) WizardColors.text else WizardColors.textFaint,
        animationSpec = tween(durationMillis = 180),
        label = "kpknCheckRing",
    )
    val corner = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(corner)
            .background(fill, corner)
            .border(width = 1.75.dp, color = ring, shape = corner),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = KpknLineIcons.Check,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    alpha = tick.value
                    scaleX = tick.value
                    scaleY = tick.value
                },
            tint = WizardColors.ctaContent,
        )
    }
}
