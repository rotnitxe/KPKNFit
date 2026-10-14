package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Hoja del mesociclo (P3b): «Nuevo mesociclo» o «Editar mesociclo». Nombre y objetivo (chips de [MESOCYCLE_GOAL_CHOICES]; siempre hay uno
 * elegido). Al editar y si el bloque tiene más de un mesociclo, «Eliminar mesociclo» en `danger`, a la izquierda; «Guardar», a la derecha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramMesoSheet(
    meso: Mesocycle?,
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (meso == null) "Nuevo mesociclo" else "Editar mesociclo",
                style = KpknType.title,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(16.dp))
            KpknTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre",
                placeholder = "Por ejemplo, Mesociclo 2",
                singleLine = true,
                error = if (name.isBlank()) "Pon un nombre al mesociclo" else null,
            )
            Spacer(Modifier.height(18.dp))
            ProgramSectionLabel(text = "Objetivo")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MESOCYCLE_GOAL_CHOICES.forEach { option ->
                    KpknChip(
                        text = mesocycleGoalLabel(option),
                        selected = goal == option,
                        onClick = { goal = option },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            KpknPrimaryPill(
                text = "Guardar",
                onClick = { onSave(name.trim(), goal) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            )
            if (meso != null && canDelete) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    ProgramDangerAction(text = "Eliminar mesociclo", onClick = onDelete)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
