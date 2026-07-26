package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kpkn.data.models.Session
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.EditorMiniField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompetitionConfigSheet(
    session: Session,
    onDismiss: () -> Unit,
    onUpdateSession: ((Session) -> Session) -> Unit,
) {
    val details = session.competitionDetails ?: CompetitionDetails()
    KpknSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Configurar competición", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Datos del evento, pesaje y recordatorios. Mantenerlo separado evita que el editor de movimientos se sature.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "Fecha (YYYY-MM-DD)",
                    value = details.competitionDate.orEmpty(),
                    stateKey = "comp-sheet-date-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(competitionDate = input.ifBlank { null }) } }
                }
                EditorMiniField(
                    label = "Hora inicio",
                    value = details.startTime.orEmpty(),
                    stateKey = "comp-sheet-time-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(startTime = input.ifBlank { null }) } }
                }
            }
            EditorMiniField("Ubicación", details.location.orEmpty(), "comp-sheet-location-${session.id}") { input ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(location = input.ifBlank { null }) } }
            }
            EditorMiniField("Federación", details.federation.orEmpty(), "comp-sheet-fed-${session.id}") { input ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(federation = input.ifBlank { null }) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "Categoría",
                    value = details.category.orEmpty(),
                    stateKey = "comp-sheet-category-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(category = input.ifBlank { null }) } }
                }
                EditorMiniField(
                    label = "División",
                    value = details.division.orEmpty(),
                    stateKey = "comp-sheet-division-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(division = input.ifBlank { null }) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "Equipamiento",
                    value = details.equipment.orEmpty(),
                    stateKey = "comp-sheet-equipment-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(equipment = input.ifBlank { null }) } }
                }
                EditorMiniField(
                    label = "Peso objetivo",
                    value = formatEditableNumber(details.targetBodyweightKg),
                    keyboardType = KeyboardType.Decimal,
                    stateKey = "comp-sheet-target-bw-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(targetBodyweightKg = input.safeDoubleOrNull()) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "Pesaje fecha",
                    value = details.weighInDate.orEmpty(),
                    stateKey = "comp-sheet-weigh-date-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(weighInDate = input.ifBlank { null }) } }
                }
                EditorMiniField(
                    label = "Pesaje hora",
                    value = details.weighInTime.orEmpty(),
                    stateKey = "comp-sheet-weigh-time-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(weighInTime = input.ifBlank { null }) } }
                }
            }
            Text("Recordatorios", fontWeight = FontWeight.Bold)
            CompetitionConfigCheckRow("Una semana antes", details.reminderOneWeekEnabled) { checked ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(reminderOneWeekEnabled = checked) } }
            }
            CompetitionConfigCheckRow("48 horas antes", details.reminder48hEnabled) { checked ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(reminder48hEnabled = checked) } }
            }
            CompetitionConfigCheckRow("Al inicio del evento", details.reminderStartEnabled) { checked ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(reminderStartEnabled = checked) } }
            }
            EditorMiniField(
                label = "Estrategia / notas",
                value = details.strategyNotes.orEmpty(),
                stateKey = "comp-sheet-strategy-${session.id}",
            ) { input ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(strategyNotes = input.ifBlank { null }) } }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Listo")
            }
        }
    }
}

@Composable
internal fun CompetitionConfigCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
