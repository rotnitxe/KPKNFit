package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/*
 * Controles PROVISIONALES de los pasos de Entreno v2: funcionales y sobrios (texto, filas y chips simples), sin
 * pretender diseño final. Los paquetes visuales sustituyen el cuerpo de cada `EntrenoXxxStep` por los símbolos
 * animados; ninguna de estas piezas debe copiarse a ese diseño. Todas escriben SOLO por la API del ViewModel.
 */

/**
 * Fila de opción provisional: marca circular + texto (+ línea de apoyo), sin caja ni borde de tarjeta. Con [multi]
 * se anuncia como casilla; si no, como opción de una selección única. Objetivo táctil ≥ 48 dp.
 */
@Composable
internal fun EntrenoOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    multi: Boolean = true,
    tag: String? = null,
) {
    val semanticsModifier = if (multi) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WizardSpacing.touchTarget)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .then(semanticsModifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EntrenoMark(selected = selected, enabled = enabled)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = WizardTypography.cardTitle,
                color = if (enabled) WizardColors.text else WizardColors.textFaint,
            )
            if (supporting != null) {
                Text(text = supporting, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
            }
        }
    }
}

/** Marca circular de una opción: anillo gris vacío o disco de tinta cuando está elegida. */
@Composable
private fun EntrenoMark(selected: Boolean, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(1.5.dp, if (selected && enabled) WizardColors.selectedBorder else WizardColors.markBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(if (enabled) WizardColors.markFill else WizardColors.textFaint, CircleShape),
            )
        }
    }
}

/**
 * Chip provisional de una opción corta (día, nivel, minutos, unidad): vidrio neutro tenue cuando no está elegido y
 * tinta cuando sí. [description] es lo que anuncia TalkBack cuando el texto visible es una abreviatura.
 */
@Composable
internal fun EntrenoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
    multi: Boolean = false,
    tag: String? = null,
) {
    val semanticsModifier = if (multi) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = WizardSpacing.touchTarget)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clip(WizardShapes.pill)
            .background(if (selected) WizardColors.text else WizardColors.glassFill)
            .border(1.dp, WizardColors.glassBorder, WizardShapes.pill)
            .then(semanticsModifier)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = WizardTypography.cardTitle,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> WizardColors.textFaint
                selected -> WizardColors.ctaContent
                else -> WizardColors.text
            },
        )
    }
}

/** Texto de apoyo bajo un control, en gris cálido. */
@Composable
internal fun EntrenoCaption(text: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    Text(
        text = text,
        style = WizardTypography.cardSubtitle,
        color = if (danger) WizardColors.danger else WizardColors.textMuted,
        modifier = modifier.fillMaxWidth(),
    )
}
