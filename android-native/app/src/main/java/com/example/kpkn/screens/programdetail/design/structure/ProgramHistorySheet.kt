package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.ProgramSnapshot
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Cuántas copias se enseñan: las más recientes, como siempre. */
internal const val PROGRAM_COPIES_SHOWN = 10

/** Las copias que se enseñan, de la más nueva a la más antigua. */
internal fun latestProgramCopies(snapshots: List<ProgramSnapshot>, limit: Int = PROGRAM_COPIES_SHOWN): List<ProgramSnapshot> =
    snapshots.sortedByDescending { snapshot -> snapshot.savedAtMs }.take(limit)

/** «mar 6 oct · 10:42»: el día y la hora de una copia en la zona del móvil. */
internal fun programCopyStamp(savedAtMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(savedAtMs).atZone(zone)
    return "${dayMonthWeekdayLabel(at.toLocalDate())} · ${at.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

/** «3 copias guardadas. Restaurar sustituye el programa actual.»; sin copias, nada. */
internal fun programHistoryCaption(copies: Int): String? =
    if (copies == 0) {
        null
    } else {
        "${numberWithUnit(SpanishPlurals.withNoun(copies, "copia guardada", "copias guardadas"))}. " +
            "Restaurar sustituye el programa actual."
    }

/**
 * Hoja «Historial del programa» (P3c, sustituye al diálogo de texto): una fila por copia, con la fecha en `bodyStrong` y el motivo en
 * `caption`. «Restaurar» pide confirmación antes de llamar a [onRestore] con el id de la copia.
 */
@Composable
internal fun ProgramHistorySheet(
    snapshots: List<ProgramSnapshot>,
    onRestore: (snapshotId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        ProgramHistoryContent(snapshots = snapshots, onRestore = onRestore)
    }
}

/** El contenido del historial sin la hoja: las filas, el estado vacío y la confirmación de restaurar. */
@Composable
internal fun ProgramHistoryContent(
    snapshots: List<ProgramSnapshot>,
    onRestore: (snapshotId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copies = remember(snapshots) { latestProgramCopies(snapshots) }
    var pending by remember { mutableStateOf<ProgramSnapshot?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = "Historial del programa", style = KpknType.title, color = WizardColors.text)
        programHistoryCaption(copies.size)?.let { caption ->
            Spacer(Modifier.height(4.dp))
            Text(text = caption, style = KpknType.caption, color = WizardColors.textMuted)
        }
        Spacer(Modifier.height(16.dp))
        if (copies.isEmpty()) {
            KpknEmptyState(
                icon = KpknLineIcons.History,
                title = "Aún no hay copias",
                body = "Se guarda una cada vez que reemplazas el programa con una plantilla o un protocolo.",
            )
        } else {
            ProgramHistoryRows(copies = copies, onRequestRestore = { copy -> pending = copy })
        }
    }
    pending?.let { copy ->
        KpknAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(text = "¿Restaurar esta copia?", style = KpknType.title, color = WizardColors.text) },
            text = {
                Text(
                    text = "Sustituye el programa actual por la copia del ${programCopyStamp(copy.savedAtMs)}. " +
                        "Tu programa actual se guarda en el historial antes de restaurar.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                ProgramRestoreActions(
                    snapshotId = copy.id,
                    onRestore = { id ->
                        pending = null
                        onRestore(id)
                    },
                    onCancel = { pending = null },
                )
            },
        )
    }
}

/**
 * Las filas de las copias en un módulo, separadas por `divider`. Tocar «Restaurar» de una fila no restaura nada: pide la confirmación por
 * [onRequestRestore].
 */
@Composable
internal fun ProgramHistoryRows(
    copies: List<ProgramSnapshot>,
    onRequestRestore: (ProgramSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier) {
        copies.forEachIndexed { index, copy ->
            if (index > 0) ProgramRowDivider()
            ProgramHistoryRow(copy = copy, onRestore = { onRequestRestore(copy) })
        }
    }
}

@Composable
private fun ProgramHistoryRow(
    copy: ProgramSnapshot,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El margen separa el motivo, que puede ser largo, del botón «Restaurar» (antes se tocaban).
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = programCopyStamp(copy.savedAtMs),
                style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.text,
            )
            Text(
                text = copy.reason,
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        KpknTextAction(text = "Restaurar", onClick = onRestore)
    }
}

/**
 * Los dos botones de la confirmación de restaurar: «Cancelar», en texto, y «Restaurar», la principal. Llama a [onRestore] con el id de la
 * copia, y a [onCancel] si se arrepiente. Es la parte que va dentro del diálogo.
 */
@Composable
internal fun ProgramRestoreActions(
    snapshotId: String,
    onRestore: (snapshotId: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknTextAction(text = "Cancelar", onClick = onCancel)
        KpknPrimaryPill(text = "Restaurar", onClick = { onRestore(snapshotId) })
    }
}
