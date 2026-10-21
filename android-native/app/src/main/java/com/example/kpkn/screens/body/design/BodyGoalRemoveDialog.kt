package com.example.kpkn.screens.body.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Confirmación de «Quitar objetivo» (BODY_V1 §2.5): quita los objetivos propios y deja los pesajes. Sin estado.
 */
@Composable
fun BodyGoalRemoveDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "¿Quitar el objetivo?", style = KpknType.title, color = WizardColors.text)
        },
        text = {
            Text(
                text = "Dejarás de ver la meta y su fecha de inicio. Tus pesajes no se borran.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
        },
        confirmButton = {
            BodyDangerAction(text = "Quitar", onClick = onConfirm)
        },
        dismissButton = {
            KpknTextAction(text = "Cancelar", onClick = onDismiss)
        },
    )
}
