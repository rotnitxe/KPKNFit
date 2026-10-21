package com.example.kpkn.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.roundToInt

/** Pista apagada del interruptor: gris casi negro, sin filete. */
private val SwitchOffTrack = Color(0xFF2A2A2A)

/**
 * Interruptor de la app (KPKN_UI_V2): pista de 52 × 32 dp y pulgar de 24 dp. Apagado, pista `#2A2A2A` y pulgar en `textFaint`; encendido,
 * pista de tinta y pulgar negro. El pulgar se desliza con resorte. Su zona táctil mide 48 dp de alto y es `Role.Switch`, con el estado
 * de encendido expuesto. Deshabilitado, todo al 38 %. Con [onCheckedChange] nulo es solo de lectura.
 */
@Composable
fun KpknSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduced = wizardReducedMotion()
    val interactive = enabled && onCheckedChange != null
    val interaction = remember { MutableInteractionSource() }
    val thumbSpec: AnimationSpec<Float> = if (reduced) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    }
    val thumb = animateFloatAsState(targetValue = if (checked) 1f else 0f, animationSpec = thumbSpec, label = "kpknSwitchThumb")
    val track by animateColorAsState(
        targetValue = if (checked) WizardColors.text else SwitchOffTrack,
        animationSpec = tween(durationMillis = 180),
        label = "kpknSwitchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) WizardColors.ctaContent else WizardColors.textFaint,
        animationSpec = tween(durationMillis = 180),
        label = "kpknSwitchThumbColor",
    )

    // La capa táctil mide 48 dp de alto; el dibujo de 52 × 32 queda centrado dentro.
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                enabled = interactive,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = { next -> onCheckedChange?.invoke(next) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 32.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(KpknShape.pill)
                .background(track, KpknShape.pill),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // El pulgar recorre 20 dp: de 4 dp desde el borde izquierdo a 24 dp.
                    .offset { IntOffset(x = (4.dp.toPx() + thumb.value * 20.dp.toPx()).roundToInt(), y = 0) }
                    .size(24.dp)
                    .background(thumbColor, KpknShape.pill),
            )
        }
    }
}
