package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/**
 * Acción de texto en `danger` (como `KpknTextAction`, pero en rojo) para borrar desde una hoja o un diálogo: zona táctil de 48 dp. La usa
 * la hoja de la semana (fuera de Estructura); las hojas de Estructura usan `StructureDangerAction`, la del lienzo.
 */
@Composable
internal fun ProgramDangerAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = KpknType.action, color = WizardColors.danger)
    }
}
