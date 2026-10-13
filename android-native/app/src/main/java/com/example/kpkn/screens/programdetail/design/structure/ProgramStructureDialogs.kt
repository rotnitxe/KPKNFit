package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Confirmación de borrado de un bloque, un mesociclo o una semana (P3b), con los textos de siempre: [text] dice qué se pierde. «Eliminar»
 * va en `danger` y es la acción destructiva; «Cancelar» cierra sin cambios.
 */
@Composable
internal fun ProgramDeleteDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Eliminar", style = KpknType.title, color = WizardColors.text) },
        text = { Text(text = text, style = KpknType.body, color = WizardColors.textMuted) },
        confirmButton = { ProgramDangerAction(text = "Eliminar", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}

/**
 * «¿Convertir en programa por bloques?» (P3b): un programa simple pasa a avanzado al añadir un segundo bloque. «Convertir» es la acción
 * principal; el texto dice que se terminan los ciclos (no «loops»).
 */
@Composable
internal fun ProgramConvertBlocksDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "¿Convertir en programa por bloques?",
                style = KpknType.title,
                color = WizardColors.text,
            )
        },
        text = {
            Text(
                text = "Agregar un segundo bloque hace que este programa deje de ser simple. " +
                    "La estructura pasa a ser lineal, se terminan los ciclos y los eventos cíclicos dejan de ser compatibles.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
        },
        confirmButton = { KpknCompactPill(text = "Convertir", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}
