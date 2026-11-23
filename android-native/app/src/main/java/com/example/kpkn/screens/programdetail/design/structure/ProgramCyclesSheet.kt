package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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

/** Las frases de «Para qué sirven los loops». */
private val CYCLE_INFO_LINES = listOf(
    "Los Loops son una forma entretenida de dinamizar tus entrenos basados en una o más semanas.",
    "Podrás incluir eventos como semanas de descarga automáticas tras un número de repeticiones, o periodizar aspectos internos de tu rutina —como el RIR o RPE de tus ejercicios— conforme avanza.",
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
    var showInfo by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProgramCycleEditing?>(null) }
    var pendingDelete by remember { mutableStateOf<ProgramCycleLoop?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                onOpenEditor = { editing = ProgramCycleEditing(it) },
                onShowInfo = { showInfo = true },
            )
        }

        editing?.let { target ->
            ProgramCycleEditorSheet(
                loop = target.loop,
                existing = model.loops,
                apreExerciseNames = model.apreExerciseNames,
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
                title = { Text(text = "Para qué sirven los loops", style = KpknType.title, color = WizardColors.text) },
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
    onOpenEditor: ((ProgramCycleLoop?) -> Unit)? = null,
    onShowInfo: (() -> Unit)? = null,
) {
    var localShowInfo by remember { mutableStateOf(false) }
    var localEditing by remember { mutableStateOf<ProgramCycleEditing?>(null) }
    var localPendingDelete by remember { mutableStateOf<ProgramCycleLoop?>(null) }
    val activeCount = model.loops.count { !it.cancelled }
    val openEditor = onOpenEditor ?: { localEditing = ProgramCycleEditing(it) }
    val openInfo = onShowInfo ?: { localShowInfo = true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(StructureSheetPadding),
    ) {
        StructureSheetHeader(title = "Loops", subtitle = cycleSummaryLine(activeCount, model.currentCycle))
        if (model.legacyEventCount > 0) {
            ProgramCycleLegacyNotice(count = model.legacyEventCount, onMigrate = onMigrateLegacy, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (model.collisionLines.isNotEmpty()) {
            ProgramCycleCollisionNotice(lines = model.collisionLines, modifier = Modifier.padding(bottom = 12.dp))
        }
        StructureSheetGroup(text = "Tus loops")
        model.loops.forEachIndexed { index, loop ->
            ProgramCycleRow(loop = loop, divider = index > 0, onClick = { openEditor(loop) })
        }
        if (model.canAddLoops) {
            StructureMenuRow(
                icon = KpknLineIcons.Plus,
                title = "Nuevo loop",
                detail = if (model.loops.isEmpty()) "Aún no tienes loops. Añade uno o elige una plantilla." else null,
                divider = model.loops.isNotEmpty(),
                onClick = { openEditor(null) },
            )
        }
        if (model.weeks.isNotEmpty()) {
            StructureSheetGroup(text = "Semanas de loop")
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
        if (model.canAddLoops) {
            StructureSheetGroup(text = "Loops listos")
            ProgramCycleTemplatesModule(
                templates = programCycleTemplatesFor(model.apreExerciseNames),
                onAdd = { templateId ->
                    if (templateId == "custom") openEditor(null) else onAddTemplate(templateId)
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            StructureCaption(
                text = "Para añadir loops, usa una rutina de una sola semana base y sin una competición planificada.",
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        StructureAction(
            text = "Para qué sirven los loops",
            onClick = openInfo,
            leadingIcon = KpknLineIcons.Info,
            faint = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    localEditing?.let { target ->
        ProgramCycleEditorSheet(
            loop = target.loop,
            existing = model.loops,
            apreExerciseNames = model.apreExerciseNames,
            onSave = { draft ->
                onSaveLoop(draft)
                localEditing = null
            },
            onAction = { action ->
                val loop = target.loop
                if (loop != null) {
                    if (action == ProgramCycleAction.DELETE) {
                        localPendingDelete = loop
                    } else {
                        onLoopAction(loop.id, action)
                        localEditing = null
                    }
                }
            },
            onDismiss = { localEditing = null },
        )
    }

    if (localShowInfo) {
        KpknAlertDialog(
            onDismissRequest = { localShowInfo = false },
            title = { Text(text = "Para qué sirven los loops", style = KpknType.title, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CYCLE_INFO_LINES.forEach { line ->
                        Text(text = line, style = KpknType.body, color = WizardColors.textMuted)
                    }
                }
            },
            confirmButton = { KpknPrimaryPill(text = "Entendido", onClick = { localShowInfo = false }) },
        )
    }

    localPendingDelete?.let { loop ->
        ProgramCycleDeleteDialog(
            onConfirm = {
                onLoopAction(loop.id, ProgramCycleAction.DELETE)
                localPendingDelete = null
                localEditing = null
            },
            onDismiss = { localPendingDelete = null },
        )
    }
}

/** La confirmación de borrar un ciclo: sus semanas de ciclo dejan de programarse. */
@Composable
internal fun ProgramCycleDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "¿Eliminar este loop?", style = KpknType.title, color = WizardColors.text) },
        text = { Text(text = "Sus semanas de loop dejan de programarse.", style = KpknType.body, color = WizardColors.textMuted) },
        confirmButton = { KpknDangerPill(text = "Eliminar", onClick = onConfirm) },
        dismissButton = { KpknTextAction(text = "Cancelar", onClick = onDismiss) },
    )
}
