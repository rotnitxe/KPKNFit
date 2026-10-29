package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.ui.components.KpknSheet

/**
 * Hoja del mesociclo, con el patrón de e-block (el lienzo no la dibuja): el nombre como título (o «Nuevo mesociclo»), [subtitle] con el
 * bloque, el campo «Nombre», «Objetivo» con sus chips (siempre hay uno elegido), «Guardar» y, si se puede, «Eliminar mesociclo» en
 * `--danger`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramMesoSheet(
    meso: Mesocycle?,
    subtitle: String?,
    suggestedName: String,
    canDelete: Boolean,
    onSave: (name: String, goal: MesocycleGoal) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(meso?.id) { mutableStateOf(meso?.name ?: suggestedName) }
    var goal by remember(meso?.id) { mutableStateOf(meso?.goal ?: MesocycleGoal.ACCUMULATION) }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding),
        ) {
            StructureSheetHeader(title = meso?.name?.takeIf { it.isNotBlank() } ?: "Nuevo mesociclo", subtitle = subtitle)
            StructureTextField(label = "Nombre", value = name, onValueChange = { name = it }, placeholder = "Por ejemplo, Mesociclo 2")
            StructureSheetGroup(text = "Objetivo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MESOCYCLE_GOAL_CHOICES.forEach { option ->
                    StructureChip(text = mesocycleGoalLabel(option), selected = goal == option, onClick = { goal = option })
                }
            }
            StructurePrimaryPill(
                text = "Guardar",
                onClick = { onSave(name.trim(), goal) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            if (meso != null && canDelete) {
                StructureDangerAction(text = "Eliminar mesociclo", onClick = onDelete)
            }
        }
    }
}
