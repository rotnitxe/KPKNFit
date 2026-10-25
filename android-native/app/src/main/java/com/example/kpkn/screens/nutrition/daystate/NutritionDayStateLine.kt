package com.example.kpkn.screens.nutrition.daystate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

/** Línea discreta bajo los totales; la revisión siempre requiere una decisión explícita. */
@Composable
fun NutritionDayStateLine(
    state: NutritionDayState,
    onOpen: () -> Unit,
    onAcceptSuggestion: () -> Unit,
    onRejectSuggestion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("nutrition-day-state")
                .clickable(
                    enabled = state.editable && !state.saving,
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button, onClick = onOpen,
                ).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(nutritionDayStateLabel(state), style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
            if (state.mark == DayMark.COMPLETE_CONFIRMED && !state.needsReview) {
                Icon(KpknLineIcons.Check, contentDescription = null, tint = SymbolPalette.energia, modifier = Modifier.size(18.dp))
            }
            if (state.editable) {
                Text(
                    when {
                        state.saving -> "Guardando…"
                        state.suggested -> "Revisar"
                        state.mark == DayMark.NONE -> "Marcar"
                        else -> "Cambiar"
                    },
                    style = KpknType.caption, color = WizardColors.textMuted,
                )
            }
        }
        if (state.needsReview) {
            Text("La marca ya no coincide con las comidas de este día.", style = KpknType.caption, color = WizardColors.textMuted)
        }
        if (state.suggested) {
            Text("Este día tiene menos registros de lo habitual. ¿Falta alguna comida?", style = KpknType.caption, color = WizardColors.textMuted)
            DayStateAction("Marcar como parcial", "nutrition-day-accept", !state.saving, onAcceptSuggestion)
            DayStateAction("Así es mi día", "nutrition-day-reject", !state.saving, onRejectSuggestion)
        }
    }
}

@Composable
private fun DayStateAction(text: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
    ) { Text(text, style = KpknType.action, color = SymbolPalette.energia) }
}
