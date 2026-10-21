package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknType

/**
 * Confirmación de descarte (N3b): «¿Descartar esta comida?» o, al editar, «¿Descartar los cambios?». «Descartar» es la acción
 * destructiva, en `danger`; «Seguir editando» es la de blanco y no toca nada.
 */
@Composable
fun LoggerDiscardDialog(
    isEditing: Boolean,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onKeepEditing,
        title = {
            Text(
                text = if (isEditing) "¿Descartar los cambios?" else "¿Descartar esta comida?",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        },
        text = {
            Text(
                text = if (isEditing) {
                    "La comida seguirá como estaba guardada."
                } else {
                    "El borrador y sus correcciones no se guardarán ni se usarán para aprender hábitos."
                },
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
        },
        dismissButton = { LoggerDangerAction(text = "Descartar", onClick = onDiscard) },
        confirmButton = { KpknAlertConfirmButton(text = "Seguir editando", onClick = onKeepEditing) },
    )
}
