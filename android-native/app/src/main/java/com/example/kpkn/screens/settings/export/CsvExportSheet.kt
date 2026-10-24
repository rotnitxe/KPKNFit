package com.example.kpkn.screens.settings.export

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.export.CsvExportDomain
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.*

@Composable
fun CsvExportSheet(state: CsvExportUiState, onExport: (Set<CsvExportDomain>) -> Unit, onDismiss: () -> Unit) {
    var names by rememberSaveable { mutableStateOf(listOf(CsvExportDomain.MEALS.name)) }
    val selected = names.map { CsvExportDomain.valueOf(it) }.toSet()
    KpknSheet(onDismissRequest = onDismiss, dismissible = !state.busy) {
        CsvExportContent(selected, { domain -> names = (if (domain in selected) selected - domain else selected + domain).map { it.name } },
            state.busy, state.error, null, { onExport(selected) }, onDismiss, Modifier.fillMaxHeight(0.85f))
    }
}

@Composable
fun CsvExportContent(selected: Set<CsvExportDomain>, onToggle: (CsvExportDomain) -> Unit, busy: Boolean,
    error: String?, hasData: Boolean?, onExport: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Exportar datos", style = KpknType.title, color = WizardColors.text, modifier = Modifier.weight(1f))
            if (!busy) KpknIconButton(KpknLineIcons.Close, "Cerrar exportación CSV", onDismiss)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Elige los datos que quieres llevar a una hoja de cálculo. Después elegirás dónde guardarlos.", style = KpknType.body, color = WizardColors.textMuted)
            if (hasData == false) Text("No hay registros; se exportarán archivos con sus cabeceras.", style = KpknType.body, color = WizardColors.textMuted)
            KpknModule(Modifier.fillMaxWidth()) {
                CsvExportDomain.entries.forEach { domain ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("csv-domain-${domain.name}")
                        .toggleable(domain in selected, enabled = !busy, role = Role.Checkbox, onValueChange = { onToggle(domain) })
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(domain.label, style = KpknType.bodyStrong, color = WizardColors.text)
                            Text(when (domain) {
                                CsvExportDomain.MEALS -> "Alimentos registrados, hora y estado del día"
                                CsvExportDomain.BODY -> "Mediciones y unidades de Ajustes"
                                CsvExportDomain.WORKOUTS -> "Sesiones, series, repeticiones y notas"
                                CsvExportDomain.LIBRARY -> "Ejercicios, alimentos y recetas propios"
                            }, style = KpknType.caption, color = WizardColors.textMuted)
                        }
                        Checkbox(domain in selected, onCheckedChange = null, enabled = !busy)
                    }
                }
            }
            Text("Una selección produce un CSV; varias se agrupan en ZIP. Mi biblioteca incluye dos CSV y siempre se entrega en ZIP.",
                style = KpknType.caption, color = WizardColors.textMuted)
            if (busy) Text("Preparando exportación…", style = KpknType.bodyStrong, color = WizardColors.text)
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, style = KpknType.body, color = WizardColors.danger) }
            KpknPrimaryPill(if (busy) "Preparando…" else "Exportar", onExport, enabled = selected.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth(), tag = "csv-export")
        }
    }
}
