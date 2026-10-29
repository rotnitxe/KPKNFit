package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.StartDaySessionMode
import com.example.kpkn.domain.training.StartDayTemporalScope
import com.example.kpkn.ui.design.KpknMonthCalendar
import java.time.LocalDate
import java.util.Locale

/*
 * Hojas de la pestaña Semana con la piel del lienzo v6 (m-startday, m-offdate, m-date y las confirmaciones cortas de la sesión). Casi sin
 * estado: cada una recibe lo que muestra y llama a sus lambdas; la hoja de la fecha guarda solo la fecha que se está eligiendo. Los textos
 * del aviso de calendario salen de `ProgramScheduleCopy.kt`.
 */

/**
 * «La semana empezará el miércoles» (m-startday): qué semanas (todas o desde esta) y qué pasa con las sesiones, con el efecto en una línea
 * bajo cada opción, y «Confirmar».
 */
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
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(
            title = "La semana empezará el ${dayName.lowercase(Locale.forLanguageTag("es"))}",
            sub = "Cambia el orden en que ves los días.",
        )
        LienzoGroup("Qué semanas")
        LienzoOptionRow(
            label = "Todas",
            selected = scope == StartDayTemporalScope.ALL_WEEKS,
            onClick = { onScopeChange(StartDayTemporalScope.ALL_WEEKS) },
            divider = false,
        )
        LienzoOptionRow(
            label = "Desde esta semana",
            selected = scope == StartDayTemporalScope.FROM_SELECTED_WEEK,
            onClick = { onScopeChange(StartDayTemporalScope.FROM_SELECTED_WEEK) },
        )
        LienzoGroup("Las sesiones")
        LienzoOptionRow(
            label = "Se quedan en su día",
            sub = "Solo cambia el orden visual",
            selected = sessionMode == StartDaySessionMode.KEEP_DAYS,
            onClick = { onSessionModeChange(StartDaySessionMode.KEEP_DAYS) },
            divider = false,
        )
        LienzoOptionRow(
            label = "Conservan su orden del reparto",
            sub = "Se mueven para mantener su posición en la semana",
            selected = sessionMode == StartDaySessionMode.KEEP_SPLIT_ORDER,
            onClick = { onSessionModeChange(StartDaySessionMode.KEEP_SPLIT_ORDER) },
        )
        ProgramLienzoPill(text = "Confirmar", onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/**
 * Aviso de calendario antes de empezar una sesión fuera de su fecha (m-offdate): [title] dice el caso y [message] la consecuencia. «Empezar
 * igualmente» o «Cancelar».
 */
@Composable
internal fun ProgramOffScheduleDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ProgramConfirmSheet(title = title, sub = message, confirmLabel = "Empezar igualmente", onConfirm = onConfirm, onDismiss = onDismiss)
}

/**
 * «Fecha del <día>» (m-date): el calendario de mes para anclar este día de la semana a otra fecha. «Guardar» devuelve la fecha elegida;
 * «Quitar la fecha», si ya hay una, devuelve null. La hoja no guarda nada por sí sola.
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
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = title, sub = "Para turnos irregulares: ancla este día a otra fecha.")
        KpknMonthCalendar(
            selected = picked,
            onSelect = { picked = it },
            today = today,
        )
        ProgramLienzoPill(
            text = "Guardar",
            onClick = { onSave(picked) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            enabled = picked != null,
        )
        if (selected != null) {
            LienzoTextAction(text = "Quitar la fecha", onClick = { onSave(null) }, centered = true)
        }
    }
}

/** «Restaurar desde el plan» (H-D1, patrón m-offdate): la sesión vuelve a lo que dice el plan y se pierden los cambios de esa sesión. */
@Composable
internal fun ProgramRestoreSessionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ProgramConfirmSheet(
        title = "Restaurar desde el plan",
        sub = "Se pierden los cambios que hiciste en esta sesión.",
        confirmLabel = "Restaurar",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmTag = "restore_session_confirm",
    )
}

/** «Eliminar sesión» (m-session: «Eliminar» sigue pidiendo confirmación), con el patrón corto de m-offdate. */
@Composable
internal fun ProgramDeleteSessionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ProgramConfirmSheet(
        title = "Eliminar sesión",
        sub = "Se quita de esta semana. Tus registros de entrenamiento se conservan.",
        confirmLabel = "Eliminar",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmTag = "delete_session_confirm",
    )
}
