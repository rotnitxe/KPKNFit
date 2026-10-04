package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Tarjeta de selección de las referencias: icono a la izquierda, título y
 * subtítulo, radio a la derecha.
 *
 * La selección nunca depende solo del color: además del borde blanco grueso hay
 * un radio relleno con punto, `selected` para TalkBack y `Role.RadioButton`.
 *
 * [footer] es un pie opcional dentro del marco de la tarjeta (p. ej. el enlace «Ver cómo funciona» de
 * los planes). Va FUERA de la zona que se toca para elegir: es un control aparte (su propio botón, con su
 * propia semántica), no un hijo fundido en el radio. Sin pie, la tarjeta es exactamente la de siempre.
 */
@Composable
fun WizardChoiceCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    val minHeight = WizardSpacing.touchTarget + 16.dp
    val borderWidth = if (selected) WizardColors.selectedBorderWidth else WizardColors.unselectedBorderWidth
    val borderColor = if (selected) WizardColors.selectedBorder else WizardColors.cardBorder
    if (footer == null) {
        WizardChoiceRow(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .background(WizardColors.cardFill, WizardShapes.card)
                .border(width = borderWidth, color = borderColor, shape = WizardShapes.card),
            title = title,
            subtitle = subtitle,
            icon = icon,
            selected = selected,
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WizardColors.cardFill, WizardShapes.card)
                .border(width = borderWidth, color = borderColor, shape = WizardShapes.card),
        ) {
            WizardChoiceRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minHeight),
                title = title,
                subtitle = subtitle,
                icon = icon,
                selected = selected,
                enabled = enabled,
                onClick = onClick,
            )
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) { footer() }
        }
    }
}

/** La zona que se toca para elegir: icono, título, subtítulo y radio, con la semántica de radio. */
@Composable
private fun WizardChoiceRow(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WizardColors.text.copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = WizardTypography.cardTitle,
                color = WizardColors.text.copy(alpha = alpha),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = WizardTypography.cardSubtitle,
                    color = WizardColors.textMuted.copy(alpha = alpha),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        WizardRadioMark(selected = selected, enabled = enabled)
    }
}

/**
 * Radio de la referencia: círculo relleno de blanco con punto oscuro cuando está
 * seleccionado; anillo gris cuando no.
 */
@Composable
fun WizardRadioMark(selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(WizardColors.markFill.copy(alpha = alpha), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(WizardColors.markDot, CircleShape),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = WizardColors.markBorder.copy(alpha = alpha),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
