package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.kpkn.data.models.DurationType
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopStatus
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.domain.training.LoopProjection
import com.example.kpkn.domain.training.LoopPresetEngine
import com.example.kpkn.domain.training.LoopPresetPreview
import com.example.kpkn.screens.programdetail.design.structure.LoopPresetPreviewSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleAction
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleDraft
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleLoop
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleWeek
import com.example.kpkn.screens.programdetail.design.structure.ProgramCyclesModel
import com.example.kpkn.screens.programdetail.design.structure.ProgramCyclesSheet

/** Una semana de ciclo con las piezas que necesita la hoja y el motor para posponerla o cancelarla. */
private data class LoopActionOccurrence(
    val loop: Loop,
    val projection: LoopProjection,
    val blockId: String,
    val weekId: String,
    val weekLabel: String,
    val dayLabel: String,
    val existingSession: Session?,
    val originCycle: Int? = null,
) {
    val countdownLabel: String
        get() = LoopEngine.formatLoopCountdown(projection.daysUntil)
}

/**
 * La hoja «Ciclos» (P3c, sustituye a `LoopsView`): calcula lo que pinta con el motor y traduce cada acción de la hoja a la función del
 * motor de siempre. La lógica no cambia: crear, editar, eliminar, posponer, cancelar, reactivar, migrar eventos y añadir plantillas.
 */
@Composable
internal fun CyclesSection(
    program: Program,
    equipmentInventory: EquipmentInventory = EquipmentInventory(),
    onUpdateProgram: (Program) -> Unit,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var presetPreview by remember(program.id) { mutableStateOf<LoopPresetPreview?>(null) }
    val currentCycle = LoopEngine.getCurrentCycle(program)
    val legacyEvents = remember(program) { program.events.filter { it.repeatEveryXCycles != null } }
    val hasLegacy = legacyEvents.isNotEmpty() && program.loops.isEmpty()
    val normalizedProgram = remember(program) {
        if (program.loops.isNotEmpty()) program else LoopEngine.migrateEventsToLoops(program)
    }
    val actionableOccurrences = remember(normalizedProgram, currentCycle) {
        buildLoopOccurrences(normalizedProgram, currentCycle)
    }
    val loopCollisions = remember(normalizedProgram, currentCycle) {
        LoopEngine.detectLoopCollisions(
            LoopEngine.projectLoops(
                normalizedProgram,
                fromCycle = currentCycle.coerceAtLeast(0),
                lookAheadCycles = 24,
            ),
        )
    }
    val model = remember(program, currentCycle, legacyEvents, hasLegacy, actionableOccurrences, loopCollisions) {
        ProgramCyclesModel(
            currentCycle = currentCycle,
            loops = programCycleLoops(program),
            weeks = actionableOccurrences.map { occurrence ->
                ProgramCycleWeek(
                    occurrenceId = program.loopOccurrences
                        .firstOrNull {
                            it.loopId == occurrence.loop.id && it.scheduledCycle == occurrence.projection.cycle
                        }?.id
                        ?: "occ_${occurrence.loop.id}_${occurrence.projection.cycle}",
                    loopTitle = occurrence.loop.title,
                    loopType = occurrence.loop.type,
                    weekName = occurrence.weekLabel,
                    cycle = occurrence.projection.cycle,
                    originCycle = occurrence.originCycle,
                    dayLabel = occurrence.dayLabel,
                    countdown = occurrence.countdownLabel,
                    blockId = occurrence.blockId,
                    weekId = occurrence.weekId,
                )
            },
            legacyEventCount = if (hasLegacy) legacyEvents.size else 0,
            collisionLines = loopCollisions.entries.sortedBy { it.key }.take(4).map { (cycle, items) ->
                "C$cycle: ${items.joinToString(" + ") { it.loop.title.ifBlank { LoopEngine.getLoopTypeLabel(it.loop.type) } }}"
            },
            canAddLoops = canAddLoops(program),
        )
    }

    ProgramCyclesSheet(
        model = model,
        onDismiss = onDismiss,
        onSaveLoop = { draft: ProgramCycleDraft -> onUpdateProgram(saveCycleDraft(program, draft)) },
        onLoopAction = { loopId: String, action: ProgramCycleAction -> onUpdateProgram(applyCycleAction(program, loopId, action)) },
        onAddTemplate = { templateId: String ->
            if (canAddLoops(program)) presetPreview = LoopPresetEngine.preview(program, templateId, equipmentInventory)
        },
        onMigrateLegacy = { onUpdateProgram(LoopEngine.migrateEventsToLoops(program)) },
        onPostponeWeek = { occurrenceId: String -> onUpdateProgram(LoopEngine.postponeOccurrence(program, occurrenceId)) },
        onCancelWeek = { occurrenceId: String -> onUpdateProgram(LoopEngine.cancelOccurrence(program, occurrenceId)) },
        onViewWeek = onFocusWeek,
    )
    presetPreview?.let { preview ->
        LoopPresetPreviewSheet(
            preview = preview,
            onDismiss = { presetPreview = null },
            onConfirm = { movementIds, attemptKg ->
                val latestProgram = program
                val preparedLoops = runCatching {
                    LoopPresetEngine.materialize(latestProgram, preview.presetId, movementIds, attemptKg, equipmentInventory)
                }.getOrNull()
                if (preparedLoops != null) {
                    val updated = preparedLoops.fold(latestProgram) { current, loop -> LoopEngine.upsertLoop(current, loop) }
                    onUpdateProgram(updated)
                    presetPreview = null
                }
            },
        )
    }
}

/** Los ciclos de [program] como los pinta el editor: nombre, tipo, cadencia y si están cancelados. */
internal fun programCycleLoops(program: Program): List<ProgramCycleLoop> {
    val cancelled = (program.loopState?.cancelled ?: emptyList()).toSet()
    return program.loops.map { loop ->
        ProgramCycleLoop(
            id = loop.id,
            title = loop.title,
            type = loop.type,
            cadence = loop.repeatEveryXLoops,
            cancelled = loop.id in cancelled,
        )
    }
}

/** Guarda lo que devuelve el editor de un ciclo: lo crea si es nuevo o le cambia nombre, tipo y cadencia. Pura. */
internal fun saveCycleDraft(program: Program, draft: ProgramCycleDraft): Program {
    if (draft.loopId == null && !canAddLoops(program)) return program
    if (draft.type == LoopType.COMPETITION) return program
    if (program.loops.any { it.id != draft.loopId && it.repeatEveryXLoops.coerceAtLeast(1) == draft.cadence.coerceAtLeast(1) }) return program
    val base = draft.loopId?.let { id -> program.loops.firstOrNull { it.id == id } }
        ?: Loop(
            id = "loop_${System.nanoTime()}",
            title = "",
            type = LoopType.CUSTOM,
            repeatEveryXLoops = 4,
            durationType = DurationType.WEEK,
        )
    val loop = base.copy(title = draft.title, type = draft.type, repeatEveryXLoops = draft.cadence.coerceAtLeast(1))
    return LoopEngine.upsertLoop(program, loop)
}

/** La misma regla que muestra «Añadir loop»: solo en una repetición simple entrenable y sin competición planificada. */
internal fun canAddLoops(program: Program): Boolean {
    return LoopEngine.canAddNewLoop(program)
}

/** Aplica una acción del editor de un ciclo con la función del motor de siempre. Pura. */
internal fun applyCycleAction(program: Program, loopId: String, action: ProgramCycleAction): Program = when (action) {
    ProgramCycleAction.POSTPONE_NEXT -> LoopEngine.postponeNextOccurrence(program, loopId)
    ProgramCycleAction.CANCEL -> LoopEngine.cancelLoop(program, loopId)
    ProgramCycleAction.REACTIVATE -> LoopEngine.reactivateLoop(program, loopId)
    ProgramCycleAction.DELETE -> LoopEngine.deleteLoop(program, loopId)
}

/**
 * Las próximas semanas de ciclo: una por cada semana de ciclo del programa, con la siguiente ocurrencia de su ciclo. Es la misma lógica que
 * tenía `LoopsView`, sin cambios.
 */
private fun buildLoopOccurrences(program: Program, currentCycle: Int): List<LoopActionOccurrence> {
    val baseBlock = program.macrocycles.firstOrNull()?.blocks?.firstOrNull() ?: return emptyList()
    val loopWeeks = baseBlock.mesocycles.flatMap { it.weeks }.filter { it.isLoopWeek && it.loopId != null }
    if (loopWeeks.isEmpty()) return emptyList()

    val synced = if (program.loopOccurrences.isEmpty()) {
        LoopEngine.syncOccurrences(program)
    } else {
        program
    }
    val occurrencesByLoop = synced.loopOccurrences
        .filter {
            it.status != LoopStatus.CANCELLED &&
                it.status != LoopStatus.COMPLETED &&
                it.status != LoopStatus.POSTPONED
        }
        .groupBy { it.loopId }
    val projections = LoopEngine.projectLoops(program, fromCycle = currentCycle.coerceAtLeast(0), lookAheadCycles = 24)
    val projectionByLoopId = projections.groupBy { it.loop.id }

    return loopWeeks.mapNotNull { targetWeek ->
        val loop = program.loops.firstOrNull { it.id == targetWeek.loopId } ?: return@mapNotNull null
        val nextOcc = occurrencesByLoop[loop.id]?.minByOrNull { it.scheduledCycle }
        val projection = projectionByLoopId[loop.id]?.firstOrNull()
            ?: LoopProjection(
                loop = loop,
                cycle = nextOcc?.scheduledCycle ?: currentCycle.coerceAtLeast(1),
                isPostponed = (nextOcc?.originCycle ?: nextOcc?.scheduledCycle) != nextOcc?.scheduledCycle,
                isCancelled = nextOcc?.status == LoopStatus.CANCELLED ||
                    loop.id in (program.loopState?.cancelled ?: emptyList()),
                daysUntil = 0,
                weekInCycle = LoopEngine.getCycleLength(program),
            )
        val preferredDay = preferredLoopDay(loop, program.startDay ?: 1)
        val existingSession = targetWeek.sessions.firstOrNull { it.dayOfWeek == preferredDay }
        LoopActionOccurrence(
            loop = loop,
            projection = projection.copy(
                cycle = nextOcc?.scheduledCycle ?: projection.cycle,
                isPostponed = nextOcc?.let { it.originCycle != it.scheduledCycle } == true || projection.isPostponed,
                isCancelled = nextOcc?.status == LoopStatus.CANCELLED || projection.isCancelled,
            ),
            blockId = baseBlock.id,
            weekId = nextOcc?.weekInstanceId ?: targetWeek.id,
            weekLabel = targetWeek.name,
            dayLabel = dayLabel(preferredDay),
            existingSession = existingSession,
            originCycle = nextOcc?.originCycle,
        )
    }
}

private fun preferredLoopDay(loop: Loop, startDay: Int): Int {
    val explicitDay = loop.dayOfWeek?.takeIf { it in 1..7 }
    if (explicitDay != null) return explicitDay
    return when (loop.type) {
        LoopType.COMPETITION -> ((startDay - 1 + 5) % 7) + 1
        LoopType.ONE_RM_TEST -> ((startDay - 1 + 2) % 7) + 1
        LoopType.DELOAD -> startDay.coerceIn(1, 7)
        LoopType.CUSTOM -> startDay.coerceIn(1, 7)
    }
}

private fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Día $dayOfWeek"
}
