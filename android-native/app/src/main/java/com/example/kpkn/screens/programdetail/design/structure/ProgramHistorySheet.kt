package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.ProgramSnapshot
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.formatDayMonth
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Cuántas copias se enseñan: las más recientes, como siempre. */
internal const val PROGRAM_COPIES_SHOWN = 10

/** Abreviatura de cada día ISO (1 = lunes), con mayúscula: «Dom 26 oct». */
private val WEEKDAY_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** Las copias que se enseñan, de la más nueva a la más antigua. */
internal fun latestProgramCopies(snapshots: List<ProgramSnapshot>, limit: Int = PROGRAM_COPIES_SHOWN): List<ProgramSnapshot> =
    snapshots.sortedByDescending { snapshot -> snapshot.savedAtMs }.take(limit)

/**
 * Cuándo se guardó una copia, como en e-history: «Hoy 08:12» si es de hoy, «Dom 26 oct» si es de la última semana y «1 oct» si es más
 * antigua (con el año si no es el de [today]). Pura.
 */
internal fun programCopyStamp(savedAtMs: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(savedAtMs).atZone(zone)
    val day = at.toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0L -> "Hoy ${at.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        daysAgo < 7L -> "${WEEKDAY_SHORT[day.dayOfWeek.value - 1]} ${formatDayMonth(day)}"
        day.year == today.year -> formatDayMonth(day)
        else -> "${formatDayMonth(day)} ${day.year}"
    }
}

/**
 * La línea de «Versiones guardadas» en «Del programa»: «Última: hoy 08:12, antes de aplicar la propuesta». Sin copias, que aún no hay.
 * Pura.
 */
internal fun programLatestCopyLine(snapshots: List<ProgramSnapshot>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val latest = latestProgramCopies(snapshots, limit = 1).firstOrNull() ?: return "Aún no hay versiones guardadas"
    val stamp = programCopyStamp(latest.savedAtMs, today, zone).replaceFirstChar { it.lowercase() }
    val reason = latest.reason.trim().replaceFirstChar { it.lowercase() }
    return if (reason.isBlank()) "Última: $stamp" else "Última: $stamp, $reason"
}

/**
 * e-history, hoja «Versiones guardadas»: el título y su explicación, una fila por copia (cuándo y por qué, con «Restaurar») y la píldora
 * «Guardar una copia como programa nuevo». «Restaurar» pide confirmación antes de llamar a [onRestore] con el id de la copia.
 */
@Composable
internal fun ProgramHistorySheet(
    snapshots: List<ProgramSnapshot>,
    today: LocalDate,
    onRestore: (snapshotId: String) -> Unit,
    onSaveCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        ProgramHistoryContent(snapshots = snapshots, today = today, onRestore = onRestore, onSaveCopy = onSaveCopy)
    }
}

/** El contenido de la hoja sin la hoja: la cabecera, las filas, la píldora y la confirmación de restaurar. */
@Composable
internal fun ProgramHistoryContent(
    snapshots: List<ProgramSnapshot>,
    today: LocalDate,
    onRestore: (snapshotId: String) -> Unit,
    onSaveCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copies = remember(snapshots) { latestProgramCopies(snapshots) }
    var pending by remember { mutableStateOf<ProgramSnapshot?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(StructureSheetPadding),
    ) {
        StructureSheetHeader(
            title = "Versiones guardadas",
            subtitle = "KPKN guarda una copia antes de cada cambio grande. Restaurar no borra tus registros.",
        )
        ProgramHistoryRows(copies = copies, today = today, onRequestRestore = { copy -> pending = copy })
        StructureSecondarySmallPill(
            text = "Guardar una copia como programa nuevo",
            onClick = onSaveCopy,
            icon = StructureIcons.Copy,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
    pending?.let { copy ->
        KpknAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(text = "¿Restaurar esta versión?", style = KpknType.title, color = WizardColors.text) },
            text = {
                Text(
                    text = "El programa vuelve a como estaba: ${programCopyStamp(copy.savedAtMs, today).replaceFirstChar { it.lowercase() }}. " +
                        "Tus registros no se borran y la versión actual se guarda antes de restaurar.",
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
 * Las filas de las copias (`.ratio`, 9 dp arriba y abajo, filete `--div` arriba): cuándo en Inter 600 y el motivo en `.cap`, con
 * «Restaurar» (`.act`) a la derecha. Tocar «Restaurar» no restaura nada: pide la confirmación por [onRequestRestore].
 */
@Composable
internal fun ProgramHistoryRows(
    copies: List<ProgramSnapshot>,
    today: LocalDate,
    onRequestRestore: (ProgramSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        copies.forEach { copy ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(StructureDivider))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = programCopyStamp(copy.savedAtMs, today),
                        style = StructureActionStyle.copy(fontWeight = FontWeight.SemiBold).tabular(),
                        color = StructureInk,
                    )
                    StructureCaption(text = copy.reason)
                }
                StructureAction(text = "Restaurar", onClick = { onRequestRestore(copy) })
            }
        }
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
