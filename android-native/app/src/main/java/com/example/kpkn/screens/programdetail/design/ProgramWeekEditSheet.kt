package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.structure.ProgramDangerAction
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Hoja «Editar semana» (P2): nombre y descripción de la semana, con «Guardar». El nombre es obligatorio; sin nombre, «Guardar» queda
 * apagado. Con [onDelete] (P3b, la hoja de Estructura) lleva «Eliminar semana» en `danger` a la izquierda. Sin estado: el borrador vive
 * en la pantalla.
 */
@Composable
internal fun ProgramWeekEditSheet(
    name: String,
    description: String,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(text = "Editar semana", style = KpknType.title, color = WizardColors.text)
            Spacer(Modifier.height(16.dp))
            KpknTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Nombre",
                placeholder = "Nombre de la semana",
                singleLine = true,
                error = if (name.isBlank()) "Pon un nombre a la semana" else null,
            )
            Spacer(Modifier.height(14.dp))
            KpknTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = "Descripción",
                placeholder = "Para qué es esta semana (opcional)",
                singleLine = false,
                minLines = 2,
            )
            Spacer(Modifier.height(20.dp))
            if (onDelete != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ProgramDangerAction(text = "Eliminar semana", onClick = onDelete)
                    Spacer(Modifier.weight(1f))
                    KpknPrimaryPill(text = "Guardar", onClick = onSave, enabled = name.isNotBlank())
                }
            } else {
                KpknPrimaryPill(
                    text = "Guardar",
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank(),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
