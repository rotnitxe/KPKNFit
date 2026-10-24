package com.example.kpkn.screens.nutrition.daystate

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NutritionDayStateSheet(
    state: NutritionDayState,
    onSelect: (DayMark) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
) {
    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        NutritionDayStateOptions(state, onSelect, Modifier.verticalScroll(rememberScrollState()))
    }
}

/** Contenido sin estado, compartido por la hoja real, el banco visual y la vista previa. */
@Composable
fun NutritionDayStateOptions(state: NutritionDayState, onSelect: (DayMark) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Estado del día", style = KpknType.title, color = WizardColors.text)
        Spacer(Modifier.height(8.dp))
        val dateLabel = state.date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")))
        Text("$dateLabel · Tú decides qué días usar para estimar tu gasto.", style = KpknType.caption, color = WizardColors.textMuted)
        Spacer(Modifier.height(16.dp))
        DayStateOption("Día completo", "Confirma que registraste todo para estimar tu gasto.", DayMark.COMPLETE_CONFIRMED, state, state.hasMeals, onSelect)
        DayStateOption("Registro parcial", "Excluye este día de la estimación sin borrar comidas.", DayMark.PARTIAL, state, state.hasMeals, onSelect)
        if (!state.hasMeals) {
            DayStateOption("Ayuno (0 kcal)", "Confirma un día sin comida; cuenta como 0\u00A0kcal en la estimación.", DayMark.FASTING, state, true, onSelect)
        }
        DayStateOption("Quitar marca", "Deja el día sin confirmar y permite volver a revisarlo.", DayMark.NONE, state, state.mark != DayMark.NONE, onSelect)
        if (!state.hasMeals) {
            Spacer(Modifier.height(8.dp))
            Text("Para marcar completo o parcial, registra primero una comida.", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

@Composable
private fun DayStateOption(
    title: String, explanation: String, mark: DayMark, state: NutritionDayState,
    available: Boolean, onSelect: (DayMark) -> Unit,
) {
    val enabled = available && state.editable && !state.saving
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("nutrition-day-option-${mark.name}")
            .selectable(
                selected = mark != DayMark.NONE && state.mark == mark, enabled = enabled,
                role = if (mark == DayMark.NONE) Role.Button else Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onSelect(mark) },
            ).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KpknType.bodyStrong, color = if (enabled) WizardColors.text else WizardColors.textFaint)
            Spacer(Modifier.height(4.dp))
            Text(explanation, style = KpknType.caption, color = WizardColors.textMuted)
        }
        if (mark != DayMark.NONE && state.mark == mark) Icon(KpknLineIcons.Check, null, tint = SymbolPalette.energia, modifier = Modifier.size(20.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}
