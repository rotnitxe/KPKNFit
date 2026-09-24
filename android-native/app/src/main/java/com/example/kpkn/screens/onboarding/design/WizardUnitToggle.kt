package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Selector de unidad de las referencias: píldora oscura con la opción activa en
 * una pastilla blanca de texto oscuro.
 *
 * Alternar unidad no cambia el valor canónico: solo reexpresa lo mismo en otra
 * unidad, sin acumular errores de conversión.
 */
@Composable
fun <T> WizardUnitToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(WizardShapes.pill)
            .background(WizardColors.cardFill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(WizardShapes.pill)
                    .background(if (active) WizardColors.cta else androidx.compose.ui.graphics.Color.Transparent)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = active
                    }
                    .clickable { onSelected(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = WizardTypography.cardSubtitle,
                    color = if (active) WizardColors.ctaContent else WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun WizardMassUnitToggle(
    selected: WizardMassUnit,
    onSelected: (WizardMassUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    WizardUnitToggle(
        options = WizardMassUnit.entries.toList(),
        selected = selected,
        label = { it.code.uppercase() },
        onSelected = onSelected,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun WizardHeightUnitToggle(
    selected: WizardHeightUnit,
    onSelected: (WizardHeightUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    WizardUnitToggle(
        options = WizardHeightUnit.entries.toList(),
        selected = selected,
        label = { it.label },
        onSelected = onSelected,
        modifier = modifier.fillMaxWidth(),
    )
}
