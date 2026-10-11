package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Acción de texto del importador: alineada a la izquierda (sin el ancho mínimo de un botón de texto, que sangraba los textos
 * cortos) y con 48 dp de alto táctil. Mismo estilo que las acciones del wizard; desactivada se ve atenuada.
 */
@Composable
internal fun ImportTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = WizardColors.text,
) {
    Box(
        modifier = modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = WizardTypography.cardTitle, color = if (enabled) color else WizardColors.textFaint)
    }
}
