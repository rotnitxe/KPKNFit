package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknTextAction

/**
 * Los dos accesos discretos del registro de comidas (N4), bajo las pestañas: «Plantillas» (aplica una guardada como borrador) y
 * «Entrada rápida» (kcal y macros sin buscar un alimento). Acciones de texto en `textMuted`; con letra grande pasan a la línea
 * siguiente en vez de cortarse. Sin estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReuseLoggerEntries(
    onTemplates: () -> Unit,
    onQuickEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        KpknTextAction(
            text = "Plantillas",
            onClick = onTemplates,
            modifier = Modifier.testTag("logger-templates"),
            leadingIcon = KpknLineIcons.Layers,
            color = WizardColors.textMuted,
        )
        KpknTextAction(
            text = "Entrada rápida",
            onClick = onQuickEntry,
            modifier = Modifier.testTag("logger-quick-entry"),
            leadingIcon = KpknLineIcons.Plus,
            color = WizardColors.textMuted,
        )
    }
}
