package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknDangerPill
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
 * Hoja «Ciclos» («Añadir ciclo» de e-cycles): la envoltura de [ProgramCyclesContent] en una `KpknSheet`.
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
 * El contenido de la hoja de Ciclos, sin la hoja, con las piezas del lienzo (e-cycles): el título y el resumen, los avisos (eventos
 * antiguos y choques), «Tus ciclos» con «Nuevo ciclo», las semanas de ciclo próximas con sus acciones de hoy, las cuatro plantillas y
 * «Para qué sirven los ciclos». Crear y editar pasan por el editor anidado; borrar, por una
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
            .padding(StructureSheetPadding),
    ) {
        StructureSheetHeader(title = "Ciclos", subtitle = cycleSummaryLine(activeCount, model.currentCycle))
        if (model.legacyEventCount > 0) {
            ProgramCycleLegacyNotice(count = model.legacyEventCount, onMigrate = onMigrateLegacy, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (model.collisionLines.isNotEmpty()) {
            ProgramCycleCollisionNotice(lines = model.collisionLines, modifier = Modifier.padding(bottom = 12.dp))
        }
        StructureSheetGroup(text = "Tus ciclos")
        model.loops.forEachIndexed { index, loop ->
            ProgramCycleRow(loop = loop, divider = index > 0, onClick = { editing = ProgramCycleEditing(loop) })
        }
        StructureMenuRow(
            icon = KpknLineIcons.Plus,
            title = "Nuevo ciclo",
            detail = if (model.loops.isEmpty()) "Aún no tienes ciclos. Añade uno o empieza con una plantilla." else null,
            divider = model.loops.isNotEmpty(),
            onClick = { editing = ProgramCycleEditing(null) },
        )
        if (model.weeks.isNotEmpty()) {
            StructureSheetGroup(text = "Semanas de ciclo")
            model.weeks.forEachIndexed { index, week ->
                ProgramCycleWeekRow(
                    week = week,
                    divider = index > 0,
                    onView = { onViewWeek(week.blockId, week.weekId) },
                    onPostpone = { onPostponeWeek(week.occurrenceId) },
                    onCancel = { onCancelWeek(week.occurrenceId) },
                )
            }
        }
        StructureSheetGroup(text = "Plantillas de ciclo")
        ProgramCycleTemplatesModule(templates = programCycleTemplates, onAdd = onAddTemplate, modifier = Modifier.padding(top = 4.dp))
        StructureAction(
            text = "Para qué sirven los ciclos",
            onClick = { showInfo = true },
            leadingIcon = KpknLineIcons.Info,
            faint = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
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
        ProgramCycleDeleteDialog(
            onConfirm = {
                onLoopAction(loop.id, ProgramCycleAction.DELETE)
                pendingDelete = null
                editing = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** La confirmación de borrar un ciclo: sus semanas de ciclo dejan de programarse. */
@Composable
internal fun ProgramCycleDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "¿Eliminar este ciclo?", style = KpknType.title, color = WizardColors.text) },
        text = { Text(text = "Sus semanas de ciclo dejan de programarse.", style = KpknType.body, color = WizardColors.textMuted) },
        confirmButton = { KpknDangerPill(text = "Eliminar", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}
