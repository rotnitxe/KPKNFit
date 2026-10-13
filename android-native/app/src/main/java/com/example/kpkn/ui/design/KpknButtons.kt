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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Acción de texto (KPKN_UI_V2 §6): `action` en tinta y, si hay [icon], de 16 dp a la derecha. Zona táctil de 48 dp de alto.
 */
@Composable
fun KpknTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = KpknType.action,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (icon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = WizardColors.text)
        }
    }
}

/**
 * Píldora principal (KPKN_UI_V2 §6): 56 dp de alto, redondeo completo, fondo de tinta y texto negro Inter SemiBold 16. Es un botón
 * (`Role.Button`), sin ripple; al pulsar escala a 0,97 con resorte. [tag] es la etiqueta de prueba, si la hay; [icon] va delante del
 * texto y [enabled] = false la pinta apagada.
 */
@Composable
fun KpknPrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    KpknPill(text = text, onClick = onClick, modifier = modifier, tag = tag, icon = icon, enabled = enabled, filled = true)
}

/**
 * Píldora secundaria (KPKN_UI_V2 §6): igual que la principal, con borde de 1 dp en tinta al 24 % y texto en tinta, sin relleno.
 */
@Composable
fun KpknSecondaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    KpknPill(text = text, onClick = onClick, modifier = modifier, tag = tag, icon = icon, enabled = enabled, filled = false)
}

@Composable
private fun KpknPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    tag: String?,
    icon: ImageVector?,
    enabled: Boolean,
    filled: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "kpknPillScale",
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
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(KpknShape.pill)
            .background(fill, KpknShape.pill)
            .then(if (!filled) Modifier.border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ink)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = WizardTypography.cta,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Botón de icono (KPKN_UI_V2 §6): círculo `inset` de 40 dp con el icono a 20 dp o, con [filled], de tinta con el icono negro. Su zona
 * táctil es de 48 dp y [contentDescription] le da nombre. Al pulsar escala a 0,97 con resorte.
 */
@Composable
fun KpknIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "kpknIconScale",
    )
    val ink = tint ?: (if (filled) WizardColors.ctaContent else WizardColors.text)
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (filled) WizardColors.text else KpknSurface.inset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ink)
        }
    }
}
