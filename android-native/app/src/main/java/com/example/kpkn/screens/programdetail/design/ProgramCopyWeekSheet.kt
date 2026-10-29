package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.WeekWithMeta
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.WeekCopyConflict

/** «Dónde» de m-paste: solo la semana donde se pegó, todo su bloque o las semanas que se elijan. */
internal enum class PasteScope { ONE_WEEK, WHOLE_BLOCK, CHOOSE }

/** Las semanas de destino de cada opción de «Dónde», sin la semana de origen. */
internal fun pasteTargets(
    scope: PasteScope,
    sourceWeekId: String,
    targetWeekId: String,
    blockWeekIds: List<String>,
    chosen: Set<String>,
): Set<String> = when (scope) {
    PasteScope.ONE_WEEK -> setOf(targetWeekId)
    PasteScope.WHOLE_BLOCK -> blockWeekIds.toSet()
    PasteScope.CHOOSE -> chosen
} - sourceWeekId

/**
 * «Pegar la semana N» (lienzo v6, m-paste): el origen es lo copiado y el destino, la semana donde se pulsó «Pegar». «Dónde» en chips («Solo
 * la semana 4», «Todo el bloque 2», «Elegir semanas», que abre los chips de cada semana), «Ya tienen sesiones» con una fila por semana con
 * conflicto (sus días y el chip «Reemplazar») y «Pegar». La lógica de copia es la de siempre.
 *
 * [weeks] son todas las semanas del programa en orden (su número es su posición + 1); [blockWeekIds], las del bloque de la semana destino y
 * [blockNumber], el número de ese bloque (null en un programa sin bloques). [sourceSub] es «5 sesiones · Acumulación».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramCopyWeekSheet(
    weeks: List<WeekWithMeta>,
    sourceWeekId: String,
    targetWeekId: String,
    blockWeekIds: List<String>,
    blockNumber: Int?,
    sourceSub: String?,
    conflictsFor: (String, Set<String>) -> List<WeekCopyConflict>,
    onCopy: (String, Set<String>, Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val numberOf: (String) -> Int? = { id -> weeks.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) }
    val weekLabel: (String) -> String = { id -> numberOf(id)?.let { "Semana $it" } ?: weeks.firstOrNull { it.id == id }?.name.orEmpty() }
    var scope by remember(sourceWeekId, targetWeekId) { mutableStateOf(PasteScope.ONE_WEEK) }
    var chosen by remember(sourceWeekId, targetWeekId) { mutableStateOf(setOf(targetWeekId)) }
    var replaceWeekIds by remember(sourceWeekId, targetWeekId) { mutableStateOf(emptySet<String>()) }
    val targets = pasteTargets(scope, sourceWeekId, targetWeekId, blockWeekIds, chosen)
    val conflicts = remember(sourceWeekId, targets, weeks) { conflictsFor(sourceWeekId, targets) }
    val sourceHasSessions = weeks.firstOrNull { it.id == sourceWeekId }?.sessions?.isNotEmpty() == true
    val showBlock = blockNumber != null && (blockWeekIds.toSet() - sourceWeekId).size > 1

    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = "Pegar la ${weekLabel(sourceWeekId).replaceFirstChar { it.lowercase() }}", sub = sourceSub)
        LienzoGroup("Dónde")
        val options = buildList {
            add(PasteScope.ONE_WEEK to "Solo la ${weekLabel(targetWeekId).replaceFirstChar { it.lowercase() }}")
            if (showBlock) add(PasteScope.WHOLE_BLOCK to "Todo el bloque $blockNumber")
            add(PasteScope.CHOOSE to "Elegir semanas")
        }
        LienzoChipsWrap(
            options = options.map { it.second },
            selected = { options[it].first == scope },
            onSelect = {
                scope = options[it].first
                replaceWeekIds = emptySet()
            },
            tagOf = { "paste_scope_${options[it].first.name}" },
        )
        if (scope == PasteScope.CHOOSE) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                weeks.filter { it.id != sourceWeekId }.forEach { week ->
                    LienzoChip(
                        text = weekLabel(week.id),
                        selected = week.id in chosen,
                        onClick = {
                            chosen = if (week.id in chosen) chosen - week.id else chosen + week.id
                            replaceWeekIds = replaceWeekIds - week.id
                        },
                        tag = "paste_week_${week.id}",
                    )
                }
            }
        }
        if (conflicts.isNotEmpty()) {
            LienzoGroup("Ya tienen sesiones")
            conflicts.forEach { conflict ->
                val replace = conflict.weekId in replaceWeekIds
                LienzoRatioRow(
                    label = numberOf(conflict.weekId)?.let { "Semana $it" } ?: conflict.weekName,
                    labelStyle = LienzoRatioStyle.copy(fontWeight = FontWeight.SemiBold),
                    sub = conflict.dayLabels.joinToString(", ") { it.lowercase() }.replaceFirstChar { it.uppercase() }.ifBlank { null },
                    trailing = {
                        LienzoChip(
                            text = "Reemplazar",
                            selected = replace,
                            onClick = { replaceWeekIds = if (replace) replaceWeekIds - conflict.weekId else replaceWeekIds + conflict.weekId },
                            tag = "paste_replace_${conflict.weekId}",
                        )
                    },
                )
            }
        }
        if (!sourceHasSessions) {
            Text("La semana copiada no tiene sesiones.", style = LienzoCapStyle, color = WizardColors.danger, modifier = Modifier.padding(top = 10.dp))
        }
        ProgramLienzoPill(
            text = "Pegar",
            onClick = {
                onCopy(sourceWeekId, targets, replaceWeekIds intersect targets)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            enabled = sourceHasSessions && targets.isNotEmpty(),
            tag = "paste_confirm",
        )
    }
}
