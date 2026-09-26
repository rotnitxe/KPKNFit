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
 * Selector de unidad centrado con la opción activa en una pastilla blanca. Los
 * controles de altura/peso usan la escala de la referencia (90 % del ancho,
 * tipografía y altura mayores); los demás usos compactos conservan 75 %.
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
    measurementScale: Boolean = false,
) {
    val compact = options.size <= 2
    val controlWidth = when {
        !compact -> 1f
        measurementScale -> 0.90f
        else -> 0.75f
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth(controlWidth)
                .clip(WizardShapes.pill)
                .background(androidx.compose.ui.graphics.Color.Black)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = if (measurementScale) 50.dp else 40.dp)
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
                        style = if (measurementScale) WizardTypography.measureUnit else WizardTypography.cardSubtitle,
                        color = if (active) WizardColors.ctaContent else WizardColors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
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
        measurementScale = true,
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
        measurementScale = true,
    )
}
