package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.WeekWithMeta
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.WeekCopyConflict
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * «Copiar semana» (P2): la semana origen con filas de radio, los destinos con casillas (con «Bloque actual» y «Una semana» como atajos) y,
 * si un destino ya tiene sesiones, la casilla para reemplazarlas. «Copiar» solo se activa con una origen que tenga sesiones y al menos un
 * destino. Las reglas son las de siempre; cambia el contenedor: una hoja de vidrio en vez de un diálogo de Material.
 */
@Composable
internal fun ProgramCopyWeekSheet(
    weeks: List<WeekWithMeta>,
    currentBlockWeekIds: Set<String>,
    selectedWeekId: String?,
    conflictsFor: (String, Set<String>) -> List<WeekCopyConflict>,
    onCopy: (String, Set<String>, Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var sourceWeekId by remember(weeks, selectedWeekId) {
        mutableStateOf(selectedWeekId ?: weeks.firstOrNull()?.id.orEmpty())
    }
    var targetWeekIds by remember(weeks, sourceWeekId) {
        mutableStateOf(weeks.filter { it.id != sourceWeekId }.take(1).map { it.id }.toSet())
    }
    var replaceWeekIds by remember { mutableStateOf(emptySet<String>()) }
    val conflicts = remember(sourceWeekId, targetWeekIds, weeks) { conflictsFor(sourceWeekId, targetWeekIds) }
    val sourceHasSessions = weeks.firstOrNull { it.id == sourceWeekId }?.sessions?.isNotEmpty() == true

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(text = "Copiar sesiones de semana", style = KpknType.title, color = WizardColors.text)
            Spacer(Modifier.height(16.dp))
            SheetEyebrow("Semana origen")
            weeks.forEach { week ->
                KpknRadioRow(
                    selected = sourceWeekId == week.id,
                    title = week.name,
                    subtitle = listOfNotNull(week.dateRangeLabel, "${week.sessions.size} sesiones").joinToString(" · "),
                    onClick = {
                        sourceWeekId = week.id
                        targetWeekIds = targetWeekIds - week.id
                        replaceWeekIds = emptySet()
                    },
                )
            }
            Spacer(Modifier.height(18.dp))
            SheetEyebrow("Destino")
            Row {
                KpknTextAction(
                    text = "Bloque actual",
                    onClick = {
                        targetWeekIds = weeks.filter { it.id != sourceWeekId && it.id in currentBlockWeekIds }.map { it.id }.toSet()
                        replaceWeekIds = emptySet()
                    },
                )
                Spacer(Modifier.width(16.dp))
                KpknTextAction(
                    text = "Una semana",
                    onClick = {
                        targetWeekIds = weeks.filter { it.id != sourceWeekId }.take(1).map { it.id }.toSet()
                        replaceWeekIds = emptySet()
                    },
                )
            }
            weeks.filter { it.id != sourceWeekId }.forEach { week ->
                KpknCheckRow(
                    checked = week.id in targetWeekIds,
                    title = week.name,
                    subtitle = week.dateRangeLabel ?: "${week.sessions.size} sesiones",
                    onClick = {
                        targetWeekIds = if (week.id in targetWeekIds) targetWeekIds - week.id else targetWeekIds + week.id
                        replaceWeekIds = replaceWeekIds - week.id
                    },
                )
            }
            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SheetEyebrow("Conflictos por semana")
                conflicts.forEach { conflict ->
                    Text(text = conflict.weekName, style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(
                        text = "Ya tiene sesiones en: ${conflict.dayLabels.joinToString(", ").ifBlank { "días asignados" }}",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    KpknCheckRow(
                        checked = conflict.weekId in replaceWeekIds,
                        title = "Reemplazar en esta semana",
                        onClick = {
                            replaceWeekIds = if (conflict.weekId in replaceWeekIds) {
                                replaceWeekIds - conflict.weekId
                            } else {
                                replaceWeekIds + conflict.weekId
                            }
                        },
                    )
                }
            }
            if (!sourceHasSessions) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "La semana origen no tiene sesiones para copiar.",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
            }
            Spacer(Modifier.height(18.dp))
            KpknPrimaryPill(
                text = "Copiar",
                onClick = {
                    onCopy(sourceWeekId, targetWeekIds, replaceWeekIds)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = sourceHasSessions && targetWeekIds.isNotEmpty(),
            )
            Spacer(Modifier.height(4.dp))
            KpknTextAction(text = "Cancelar", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Rótulo de un bloque de la hoja: el eyebrow en `textMuted`. */
@Composable
private fun SheetEyebrow(text: String) {
    Text(text = text.uppercase(java.util.Locale("es")), style = KpknType.eyebrow, color = WizardColors.textMuted)
    Spacer(Modifier.height(6.dp))
}
