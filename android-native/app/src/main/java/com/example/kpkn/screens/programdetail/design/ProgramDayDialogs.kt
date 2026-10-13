package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.StartDaySessionMode
import com.example.kpkn.domain.training.StartDayTemporalScope
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/*
 * Diálogos de la pestaña Semana (P2), sobre KpknAlertDialog y con las piezas de la app. Sin estado: cada uno recibe lo que muestra y
 * llama a sus lambdas. Los textos son los de siempre: las pruebas los buscan.
 */

/** «Cambiar inicio de semana»: el alcance (todas las semanas o desde esta) y qué pasa con las sesiones. */
@Composable
internal fun ProgramStartDayDialog(
    dayName: String,
    scope: StartDayTemporalScope,
    sessionMode: StartDaySessionMode,
    onScopeChange: (StartDayTemporalScope) -> Unit,
    onSessionModeChange: (StartDaySessionMode) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("Cambiar inicio de semana") },
        text = {
            Column {
                Text(text = "$dayName será el nuevo primer día visual del programa.", style = KpknType.body, color = WizardColors.textMuted)
                Spacer(Modifier.height(14.dp))
                DialogEyebrow("Alcance de sesiones")
                KpknRadioRow(
                    selected = scope == StartDayTemporalScope.ALL_WEEKS,
                    title = "Todas",
                    onClick = { onScopeChange(StartDayTemporalScope.ALL_WEEKS) },
                )
                KpknRadioRow(
                    selected = scope == StartDayTemporalScope.FROM_SELECTED_WEEK,
                    title = "Desde esta",
                    onClick = { onScopeChange(StartDayTemporalScope.FROM_SELECTED_WEEK) },
                )
                Spacer(Modifier.height(14.dp))
                DialogEyebrow("Qué hacer con las sesiones")
                KpknRadioRow(
                    selected = sessionMode == StartDaySessionMode.KEEP_DAYS,
                    title = "Mantener días actuales",
                    onClick = { onSessionModeChange(StartDaySessionMode.KEEP_DAYS) },
                )
                KpknRadioRow(
                    selected = sessionMode == StartDaySessionMode.KEEP_SPLIT_ORDER,
                    title = "Mantener orden del split",
                    onClick = { onSessionModeChange(StartDaySessionMode.KEEP_SPLIT_ORDER) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (sessionMode == StartDaySessionMode.KEEP_DAYS) {
                        "Solo cambiará el orden visual de la semana. Las sesiones seguirán en sus días actuales."
                    } else {
                        "Las sesiones se moverán para conservar su posición dentro de la semana."
                    },
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
        },
        confirmButton = { KpknCompactPill(text = "Confirmar", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}

/** Aviso de calendario antes de entrenar fuera de su día: se puede continuar igualmente. */
@Composable
internal fun ProgramOffScheduleDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("Aviso de calendario") },
        text = { Text(text = message, style = KpknType.body, color = WizardColors.textMuted) },
        confirmButton = { KpknCompactPill(text = "Continuar", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}

/**
 * «Fecha de entrenamiento»: la fecha ISO de este día de la semana. Con el calendario de mes (`KpknMonthCalendar`) llegará después; por
 * ahora es el campo de texto de siempre.
 */
@Composable
internal fun ProgramDayDateDialog(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("Fecha de entrenamiento") },
        text = {
            Column {
                Text(
                    text = "Fecha AAAA-MM-DD de este día de la semana. Así puedes anclar turnos irregulares.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                KpknTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = "Fecha",
                    placeholder = "AAAA-MM-DD",
                    singleLine = true,
                )
            }
        },
        confirmButton = { KpknCompactPill(text = "Guardar", onClick = onSave) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}

/** «Restaurar sesión»: la sesión vuelve a lo que dice el plan. Solo se restaura esa ocurrencia. */
@Composable
internal fun ProgramRestoreSessionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("Restaurar sesión") },
        text = {
            Text(
                text = "Se perderá la edición de esta sesión. Solo se restaurará esta ocurrencia; las sesiones vecinas y el historial de entrenamiento se conservarán.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
        },
        confirmButton = { KpknCompactPill(text = "Restaurar desde el plan", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}

/** «Confirmación de borrado» de una sesión: el borrado no se deshace. */
@Composable
internal fun ProgramDeleteSessionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("Confirmar eliminación") },
        text = { Text(text = "¿Confirmas que eliminas esta sesión?", style = KpknType.body, color = WizardColors.textMuted) },
        confirmButton = { KpknCompactPill(text = "Eliminar", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}

/** Título de un diálogo de la app: Syne Bold en tinta. */
@Composable
private fun DialogTitle(text: String) {
    Text(text = text, style = KpknType.title, color = WizardColors.text)
}

/** Rótulo de un bloque dentro de un diálogo: el eyebrow en `textMuted`. */
@Composable
private fun DialogEyebrow(text: String) {
    Text(text = text.uppercase(java.util.Locale("es")), style = KpknType.eyebrow, color = WizardColors.textMuted)
    Spacer(Modifier.height(6.dp))
}
