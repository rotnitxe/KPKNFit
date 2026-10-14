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
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Las frases de «Para qué sirven los ciclos» (las de siempre, con «ciclos» en lugar de «loops»). */
private val CYCLE_INFO_LINES = listOf(
    "Un ciclo no es el evento: es el tramo completo de ciclos que llevan a ese evento.",
    "Las semanas S1, S2, etc. son espejo en cada ciclo; si editas S1, cambia S1 en todos los ciclos.",
    "El evento se muestra como E1, E2… y es una semana real donde puedes crear sesiones.",
    "Ejemplos: 5 ciclos hasta E1, descarga cada 4 ciclos o competición cada 12 ciclos.",
)

/** Qué ciclo se edita en la hoja: null = uno nuevo. */
internal class ProgramCycleEditing(val loop: ProgramCycleLoop?)

/**
 * Hoja «Ciclos» (P3c, sustituye a `LoopsView` y a su hoja de siempre): la envoltura de [ProgramCyclesContent] en una `KpknSheet`.
 */
@Composable
internal fun ProgramCyclesSheet(
    model: ProgramCyclesModel,
    onDismiss: () -> Unit,
    onSaveLoop: (ProgramCycleDraft) -> Unit,
    onLoopAction: (loopId: String, action: ProgramCycleAction) -> Unit,
    onAddTemplate: (templateId: String) -> Unit,
    onMigrateLegacy: () -> Unit,
    onPostponeWeek: (occurrenceId: String) -> Unit,
    onCancelWeek: (occurrenceId: String) -> Unit,
    onViewWeek: (blockId: String, weekId: String) -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        ProgramCyclesContent(
            model = model,
            onSaveLoop = onSaveLoop,
            onLoopAction = onLoopAction,
            onAddTemplate = onAddTemplate,
            onMigrateLegacy = onMigrateLegacy,
            onPostponeWeek = onPostponeWeek,
            onCancelWeek = onCancelWeek,
            onViewWeek = onViewWeek,
        )
    }
}

/**
 * El contenido de la hoja de Ciclos, sin la hoja: el resumen y la explicación, los avisos (eventos antiguos y choques), los ciclos, las
 * semanas de ciclo próximas con sus acciones de hoy y las cuatro plantillas. Crear y editar pasan por el editor anidado; borrar, por una
 * confirmación. Toda acción llega como lambda: el contenido no toca el programa.
 */
@Composable
internal fun ProgramCyclesContent(
    model: ProgramCyclesModel,
    onSaveLoop: (ProgramCycleDraft) -> Unit,
    onLoopAction: (loopId: String, action: ProgramCycleAction) -> Unit,
    onAddTemplate: (templateId: String) -> Unit,
    onMigrateLegacy: () -> Unit,
    onPostponeWeek: (occurrenceId: String) -> Unit,
    onCancelWeek: (occurrenceId: String) -> Unit,
    onViewWeek: (blockId: String, weekId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProgramCycleEditing?>(null) }
    var pendingDelete by remember { mutableStateOf<ProgramCycleLoop?>(null) }
    val activeCount = model.loops.count { !it.cancelled }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Ciclos", style = KpknType.title, color = WizardColors.text)
                Text(
                    text = cycleSummaryLine(activeCount, model.currentCycle),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            KpknIconButton(
                icon = KpknLineIcons.Info,
                contentDescription = "Para qué sirven los ciclos",
                onClick = { showInfo = true },
            )
        }
        Spacer(Modifier.height(16.dp))
        if (model.legacyEventCount > 0) {
            ProgramCycleLegacyNotice(count = model.legacyEventCount, onMigrate = onMigrateLegacy)
            Spacer(Modifier.height(12.dp))
        }
        if (model.collisionLines.isNotEmpty()) {
            ProgramCycleCollisionNotice(lines = model.collisionLines)
            Spacer(Modifier.height(12.dp))
        }
        KpknModule {
            KpknModuleHeader(
                label = "Tus ciclos",
                action = {
                    KpknTextAction(
                        text = "Nuevo ciclo",
                        onClick = { editing = ProgramCycleEditing(null) },
                        icon = KpknLineIcons.Plus,
                    )
                },
            )
            Spacer(Modifier.height(4.dp))
            if (model.loops.isEmpty()) {
                Text(
                    text = "Aún no tienes ciclos. Añade uno o empieza con una plantilla.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            } else {
                model.loops.forEachIndexed { index, loop ->
                    if (index > 0) ProgramRowDivider()
                    ProgramCycleRow(loop = loop, onClick = { editing = ProgramCycleEditing(loop) })
                }
            }
        }
        if (model.weeks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            KpknModule {
                KpknModuleHeader(label = "Semanas de ciclo")
                model.weeks.forEachIndexed { index, week ->
                    if (index > 0) ProgramRowDivider()
                    ProgramCycleWeekRow(
                        week = week,
                        onView = { onViewWeek(week.blockId, week.weekId) },
                        onPostpone = { onPostponeWeek(week.occurrenceId) },
                        onCancel = { onCancelWeek(week.occurrenceId) },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        ProgramSectionLabel(text = "Plantillas de ciclo")
        Spacer(Modifier.height(8.dp))
        ProgramCycleTemplatesModule(templates = programCycleTemplates, onAdd = onAddTemplate)
    }

    editing?.let { target ->
        ProgramCycleEditorSheet(
            loop = target.loop,
            existing = model.loops,
            onSave = { draft ->
                onSaveLoop(draft)
                editing = null
            },
            onAction = { action ->
                val loop = target.loop
                if (loop != null) {
                    if (action == ProgramCycleAction.DELETE) {
                        pendingDelete = loop
                    } else {
                        onLoopAction(loop.id, action)
                        editing = null
                    }
                }
            },
            onDismiss = { editing = null },
        )
    }

    if (showInfo) {
        KpknAlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(text = "Para qué sirven los ciclos", style = KpknType.title, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CYCLE_INFO_LINES.forEach { line ->
                        Text(text = line, style = KpknType.body, color = WizardColors.textMuted)
                    }
                }
            },
            confirmButton = { KpknPrimaryPill(text = "Entendido", onClick = { showInfo = false }) },
        )
    }

    pendingDelete?.let { loop ->
        KpknAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = "¿Eliminar este ciclo?", style = KpknType.title, color = WizardColors.text) },
            text = {
                Text(
                    text = "Sus semanas de ciclo dejan de programarse.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                KpknDangerPill(
                    text = "Eliminar",
                    onClick = {
                        onLoopAction(loop.id, ProgramCycleAction.DELETE)
                        pendingDelete = null
                        editing = null
                    },
                )
            },
            dismissButton = { KpknTextAction(text = "Cancelar", onClick = { pendingDelete = null }) },
        )
    }
}
