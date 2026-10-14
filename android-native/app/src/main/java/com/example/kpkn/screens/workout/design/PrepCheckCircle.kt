package com.example.kpkn.screens.workout.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Estado de un elemento del checklist de calentamiento o movilidad (KPKN LS5b). */
enum class PrepCheckState { PENDING, CURRENT, DONE }

/**
 * Círculo de 24 dp del checklist (KPKN LS5b): vacío y con filete tenue si está pendiente; con anillo de tinta y un punto si es el
 * elemento actual; relleno de tinta con el check en negro si está hecho. Sin estado: lo decide quien lo monta.
 */
@Composable
fun PrepCheckCircle(
    state: PrepCheckState,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val spec: AnimationSpec<Color> = if (reduced) snap() else tween(durationMillis = 180)
    val fill by animateColorAsState(
        targetValue = if (state == PrepCheckState.DONE) WizardColors.text else Color.Transparent,
        animationSpec = spec,
        label = "prepCheckFill",
    )
    val ring by animateColorAsState(
        targetValue = if (state == PrepCheckState.PENDING) WizardColors.textFaint else WizardColors.text,
        animationSpec = spec,
        label = "prepCheckRing",
    )
    val ringWidth = if (state == PrepCheckState.PENDING) 1.75.dp else 2.dp
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(width = ringWidth, color = ring, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            PrepCheckState.DONE -> Icon(
                imageVector = KpknLineIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WizardColors.ctaContent,
            )
            PrepCheckState.CURRENT -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(WizardColors.text, CircleShape),
            ) {}
            PrepCheckState.PENDING -> Unit
        }
    }
}
