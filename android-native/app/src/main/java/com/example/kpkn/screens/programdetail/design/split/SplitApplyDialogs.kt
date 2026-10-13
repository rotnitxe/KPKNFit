package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.design.KpknType

/**
 * Confirmación antes de reemplazar sesiones (P5): el mismo diálogo KPKN para la hoja de un reparto y para la de varios. «Cancelar» va
 * a la izquierda y la confirmación, a la derecha, como en el resto de diálogos KPKN.
 */
@Composable
internal fun SplitConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = KpknType.title, color = WizardColors.text) },
        text = { Text(text = text, style = KpknType.body, color = WizardColors.textMuted) },
        confirmButton = { KpknAlertConfirmButton(text = confirmLabel, onClick = onConfirm) },
        dismissButton = { KpknAlertDismissButton(text = "Cancelar", onClick = onDismiss) },
    )
}

/** Error al aplicar: el texto que devuelve el motor, o uno genérico si no trae. Se cierra con «Entendido». */
@Composable
internal fun SplitErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "No se pudo aplicar el reparto", style = KpknType.title, color = WizardColors.text) },
        text = { Text(text = message, style = KpknType.body, color = WizardColors.textMuted) },
        confirmButton = { KpknAlertConfirmButton(text = "Entendido", onClick = onDismiss) },
    )
}
