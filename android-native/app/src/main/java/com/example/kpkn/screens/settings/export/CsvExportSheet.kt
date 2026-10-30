package com.example.kpkn.screens.settings.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.export.CsvExportDomain
import com.example.kpkn.screens.settings.AjButton
import com.example.kpkn.screens.settings.AjColors
import com.example.kpkn.screens.settings.AjIcons
import com.example.kpkn.screens.settings.AjOption
import com.example.kpkn.screens.settings.AjSheet
import com.example.kpkn.screens.settings.AjSheetSub
import com.example.kpkn.screens.settings.AjSheetTitle
import com.example.kpkn.screens.settings.AjType

/** Hoja «Exportar datos» de Ajustes (aj-csv): la hoja gris plana de la app con el cuerpo en filas con casilla y filete. */
@Composable
fun CsvExportSheet(state: CsvExportUiState, onExport: (Set<CsvExportDomain>) -> Unit, onDismiss: () -> Unit) {
    var names by rememberSaveable { mutableStateOf(listOf(CsvExportDomain.MEALS.name)) }
    val selected = names.map { CsvExportDomain.valueOf(it) }.toSet()
    AjSheet(onDismiss, tall = true, dismissible = !state.busy) {
        CsvExportContent(selected, { domain -> names = (if (domain in selected) selected - domain else selected + domain).map { it.name } },
            state.busy, state.error, null, { onExport(selected) }, onDismiss, Modifier.weight(1f))
    }
}

/**
 * Contenido de la hoja: título con su cierre de 44 dp, cuerpo con scroll (explicación, una fila con casilla por dominio y el aviso de ZIP),
 * el error sobre el botón y «Exportar», desactivado sin selección y mientras trabaja.
 */
@Composable
fun CsvExportContent(selected: Set<CsvExportDomain>, onToggle: (CsvExportDomain) -> Unit, busy: Boolean,
    error: String?, hasData: Boolean?, onExport: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { AjSheetTitle("Exportar datos") }
            if (!busy) {
                Box(
                    Modifier.size(44.dp).semantics { contentDescription = "Cerrar exportación CSV"; role = Role.Button }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(AjIcons.Close, contentDescription = null, tint = AjColors.Muted, modifier = Modifier.size(20.dp)) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AjSheetSub("Elige los datos que quieres llevar a una hoja de cálculo. Después elegirás dónde guardarlos.")
            if (hasData == false) AjSheetSub("No hay registros; se exportarán archivos con sus cabeceras.")
            Column {
                CsvExportDomain.entries.forEachIndexed { i, domain ->
                    AjOption(
                        domain.label, domain in selected, { onToggle(domain) }, check = true, enabled = !busy, divider = i > 0, tag = "csv-domain-${domain.name}",
                        sub = when (domain) {
                            CsvExportDomain.MEALS -> "Alimentos registrados, hora y estado del día"
                            CsvExportDomain.BODY -> "Mediciones y unidades de Ajustes"
                            CsvExportDomain.WORKOUTS -> "Sesiones, series, repeticiones y notas"
                            CsvExportDomain.LIBRARY -> "Ejercicios, alimentos y recetas propios"
                        },
                    )
                }
            }
            Text(
                "Una selección produce un CSV; varias se agrupan en ZIP. Mi biblioteca incluye dos CSV y siempre se entrega en ZIP.",
                style = AjType.note,
            )
        }
        if (error != null) Text(error, style = AjType.error)
        AjButton(
            if (busy) "Preparando…" else "Exportar", onExport, fill = true, busy = busy,
            enabled = selected.isNotEmpty() && !busy, tag = "csv-export",
        )
    }
}
