package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.droppedSelectionNotice
import com.example.kpkn.screens.onboarding.performNoticeEffect

/** Una acción de texto de un aviso de los pasos del programa: su etiqueta, su marca de prueba y qué hace. */
internal data class EntrenoPlanNoticeAction(val label: String, val tag: String, val onClick: () -> Unit)

/**
 * Una acción de texto de los pasos del programa («Otra versión», «Lo haré más adelante», «Reintentar»…) ALINEADA con el texto
 * del paso: el relleno lateral de 12 dp de Material la sangraba respecto a los párrafos de arriba. Conserva el objetivo
 * táctil de 48 dp de alto.
 */
@Composable
internal fun EntrenoTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = WizardColors.text,
    style: TextStyle = WizardTypography.cardTitle,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
        Text(label, color = color, style = style)
    }
}

/**
 * Entreno v2 · un aviso de los pasos PLAN y WEEK_LAYOUT sobre la página negra, sin caja ni borde (BRIEF_COMUN · Diseño):
 * una línea de texto (en el rojo suave del sistema si es un fallo) y debajo sus acciones como texto. Lo usan el fallo
 * del barrido y de la vista previa («Reintentar»), la selección caída con su reparación de un toque y el reparto
 * rechazado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntrenoPlanNotice(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = true,
    actions: List<EntrenoPlanNoticeAction> = emptyList(),
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = text,
            style = WizardTypography.bodySmall,
            color = if (error) WizardColors.danger else WizardColors.textMuted,
        )
        if (actions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    EntrenoTextAction(label = action.label, onClick = action.onClick, modifier = Modifier.testTag(action.tag))
                }
            }
        }
    }
}

/** Una carga en curso dicha en una línea, sin caja (debajo del overlay o en la página que se asoma). */
@Composable
internal fun EntrenoPlanLoadingLine(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier.fillMaxWidth(), style = WizardTypography.bodySmall, color = WizardColors.textMuted)
}

/**
 * El plan que la persona tenía elegido (o el que trajo de la biblioteca) ya no encaja con sus respuestas: el mismo
 * texto y las mismas reparaciones de un toque del aviso clásico (`droppedSelectionNotice` + `performNoticeEffect`),
 * sin caja. «Ver otras opciones» lo cierra (las alternativas están justo debajo). Sin selección caída no pinta nada.
 */
@Composable
internal fun EntrenoDroppedSelectionNotice(state: SetupWizardState, vm: SetupWizardViewModel) {
    val selected = state.draft.selectedCatalogId
    val dropped = state.droppedSelection?.takeIf { selected == null || selected == it.planId } ?: return
    var closed by rememberSaveable(dropped.planId) { mutableStateOf(false) }
    if (closed) return
    val notice = droppedSelectionNotice(dropped, state.draft)
    EntrenoPlanNotice(
        text = notice.text,
        modifier = Modifier.testTag(DROPPED_NOTICE_TAG),
        actions = notice.buttons.mapIndexed { index, button ->
            EntrenoPlanNoticeAction(label = button.label, tag = "$DROPPED_NOTICE_TAG-$index") {
                performNoticeEffect(button.effect, vm) { closed = true }
            }
        },
    )
}

/** Marca de prueba del aviso de la selección caída (sus acciones: `-0` la principal, `-1` la secundaria). */
internal const val DROPPED_NOTICE_TAG = "setup-plan-dropped"
