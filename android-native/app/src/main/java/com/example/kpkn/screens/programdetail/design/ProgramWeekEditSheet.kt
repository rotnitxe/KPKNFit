package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * «Nombre y nota de la semana» (la fila del ⋯ de la semana, lienzo v6 m-week): el nombre y la nota en campos `.field`, con «Guardar». El
 * nombre es obligatorio; sin nombre, «Guardar» queda apagado. Con [onDelete] (la hoja de Estructura) lleva «Eliminar semana» en `danger`.
 * Sin estado: el borrador vive en la pantalla.
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
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = "Nombre y nota de la semana")
        LienzoTextField(
            label = "Nombre",
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Nombre de la semana",
            error = name.isBlank(),
            tag = "week_edit_name",
        )
        LienzoTextField(
            label = "Nota",
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Para qué es esta semana (opcional)",
            singleLine = false,
            tag = "week_edit_note",
        )
        ProgramLienzoPill(
            text = "Guardar",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = name.isNotBlank(),
        )
        if (onDelete != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                ProgramDangerAction(text = "Eliminar semana", onClick = onDelete)
            }
        }
    }
}
