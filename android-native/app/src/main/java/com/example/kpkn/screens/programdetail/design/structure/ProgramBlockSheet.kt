package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Hoja del bloque (P3b): «Nuevo bloque» o «Editar bloque». Nombre, descripción opcional, objetivo (chips; se puede dejar sin elegir) y
 * esquema de progresión (una fila de radio por valor). Al editar, debajo va la vista previa de la progresión que da el motor. «Guardar»
 * entrega los cuatro datos a la vez; sin nombre no se guarda. El borrado no está aquí: va en el menú del bloque.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramBlockSheet(
    block: Block?,
    suggestedName: String,
    preview: String?,
    onSave: (name: String, description: String?, goal: BlockGoal?, scheme: BlockProgressionScheme?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(block?.id) { mutableStateOf(block?.name ?: suggestedName) }
    var description by remember(block?.id) { mutableStateOf(block?.description ?: "") }
    var goal by remember(block?.id) { mutableStateOf(block?.goal) }
    var scheme by remember(block?.id) { mutableStateOf(block?.progressionScheme) }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (block == null) "Nuevo bloque" else "Editar bloque",
                style = KpknType.title,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(16.dp))
            KpknTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre",
                placeholder = "Por ejemplo, Acumulación 1",
                singleLine = true,
                error = if (name.isBlank()) "Pon un nombre al bloque" else null,
            )
            Spacer(Modifier.height(14.dp))
            KpknTextField(
                value = description,
                onValueChange = { description = it },
                label = "Descripción",
                placeholder = "Opcional",
                singleLine = false,
                minLines = 2,
            )
            Spacer(Modifier.height(18.dp))
            ProgramSectionLabel(text = "Objetivo")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BLOCK_GOAL_CHOICES.forEach { option ->
                    KpknChip(
                        text = blockGoalLabel(option),
                        selected = goal == option,
                        onClick = { goal = if (goal == option) null else option },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ProgramSectionLabel(text = "Progresión")
            Spacer(Modifier.height(4.dp))
            BLOCK_SCHEME_CHOICES.forEach { option ->
                KpknRadioRow(
                    selected = scheme == option,
                    title = blockProgressionLabel(option),
                    onClick = { scheme = option },
                )
            }
            if (preview != null) {
                Spacer(Modifier.height(12.dp))
                ProgramSectionLabel(text = "Al superar el bloque")
                Spacer(Modifier.height(4.dp))
                Text(text = preview, style = KpknType.caption, color = WizardColors.textMuted)
            }
            Spacer(Modifier.height(20.dp))
            KpknPrimaryPill(
                text = "Guardar",
                onClick = { onSave(name.trim(), description.trim().ifBlank { null }, goal, scheme) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
