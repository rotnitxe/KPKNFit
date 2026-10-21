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
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import androidx.compose.foundation.shape.CircleShape

/**
 * Opción de radio de la app (KPKN_UI_V2): fila de 56 dp como mínimo, con el radio propio de 20 dp. El anillo es de 1,75 dp en
 * `textFaint`; elegido, el anillo pasa a tinta y entra un punto de tinta de 10 dp con escala. Título en `bodyStrong` y subtítulo en
 * `caption`. Toda la fila es `Role.RadioButton` y expone `selected`.
 */
@Composable
fun KpknRadioRow(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    KpknOptionRow(
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        mark = { KpknRadioMark(selected = selected) },
    )
}

/** Radio de 20 dp: anillo de 1,75 dp y punto de tinta que entra con escala al elegirse. */
@Composable
private fun KpknRadioMark(selected: Boolean) {
    val reduced = wizardReducedMotion()
    val dotSpec: AnimationSpec<Float> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    }
    val dot = animateFloatAsState(targetValue = if (selected) 1f else 0f, animationSpec = dotSpec, label = "kpknRadioDot")
    val ring by animateColorAsState(
        targetValue = if (selected) WizardColors.text else WizardColors.textFaint,
        animationSpec = tween(durationMillis = 180),
        label = "kpknRadioRing",
    )
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(width = 1.75.dp, color = ring, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = dot.value
                    scaleY = dot.value
                    alpha = dot.value
                }
                .clip(CircleShape)
                .background(WizardColors.text, CircleShape),
        )
    }
}
