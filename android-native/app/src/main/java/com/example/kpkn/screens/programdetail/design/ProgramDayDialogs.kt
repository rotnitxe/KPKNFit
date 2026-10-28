package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.kpkn.domain.training.StartDaySessionMode
import com.example.kpkn.domain.training.StartDayTemporalScope
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknMonthCalendar
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate

/*
 * Diálogos de la pestaña Semana (P2), sobre KpknAlertDialog y con las piezas de la app. Casi sin estado: cada uno recibe lo que muestra y
 * llama a sus lambdas; la hoja de la fecha guarda solo la fecha que se está eligiendo. Los textos del aviso de calendario salen de
 * `ProgramScheduleCopy.kt`; las pruebas buscan los textos.
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

/**
 * Aviso de calendario antes de empezar una sesión fuera de su fecha: [title] dice el caso y [message] lo explica. Se puede empezar igualmente.
 */
@Composable
internal fun ProgramOffScheduleDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(title) },
        text = { Text(text = message, style = KpknType.body, color = WizardColors.textMuted) },
        confirmButton = { KpknCompactPill(text = "Empezar igualmente", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}

/**
 * «Fecha del <día>» (sustituye al campo de texto AAAA-MM-DD): el calendario de mes para anclar este día de la semana a otra fecha. «Guardar»
 * devuelve la fecha elegida; «Quitar la fecha», si ya hay una, devuelve null. La hoja no guarda nada por sí sola.
 */
@Composable
internal fun ProgramDayDateSheet(
    title: String,
    selected: LocalDate?,
    today: LocalDate,
    onSave: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(selected) { mutableStateOf(selected) }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(text = title, style = KpknType.title, color = WizardColors.text)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Para turnos irregulares: ancla este día a otra fecha.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            KpknMonthCalendar(
                selected = picked,
                onSelect = { picked = it },
                today = today,
            )
            Spacer(Modifier.height(18.dp))
            KpknPrimaryPill(
                text = "Guardar",
                onClick = { onSave(picked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = picked != null,
            )
            if (selected != null) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    KpknTextAction(text = "Quitar la fecha", onClick = { onSave(null) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
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
        confirmButton = { KpknDangerPill(text = "Eliminar", onClick = onConfirm) },
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
