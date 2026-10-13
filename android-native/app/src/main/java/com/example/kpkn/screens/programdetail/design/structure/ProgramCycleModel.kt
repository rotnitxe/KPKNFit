package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.icons.KpknLineIcons

/*
 * Modelo de la hoja «Ciclos» (P3c, sustituye a `LoopsView`): lo que la hoja pinta, ya calculado desde el programa. Sin Compose salvo el
 * icono de cada tipo. Las cuentas y los textos con número son funciones puras, para fijarlas con pruebas de JVM.
 */

/** Un ciclo del programa. [cadence] es «cada N ciclos»; [cancelled] lo deja fuera de las semanas próximas y lo marca en la lista. */
internal data class ProgramCycleLoop(
    val id: String,
    val title: String,
    val type: LoopType,
    val cadence: Int,
    val cancelled: Boolean,
)

/**
 * Una semana de ciclo próxima. [occurrenceId] es la clave que usa el motor para posponer o cancelar esa semana. [originCycle] solo se
 * pone si la semana se ha movido desde otro ciclo. [countdown] llega ya formateado («3 días», «2 sem»).
 */
internal data class ProgramCycleWeek(
    val occurrenceId: String,
    val loopTitle: String,
    val loopType: LoopType,
    val weekName: String,
    val cycle: Int,
    val originCycle: Int?,
    val dayLabel: String,
    val countdown: String,
    val blockId: String,
    val weekId: String,
)

/** Una plantilla de ciclos: nombre, lo que hace y el icono KPKN que la representa (sin emojis). */
internal data class ProgramCycleTemplate(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
)

/** Lo que pinta la hoja de Ciclos. [legacyEventCount] es 0 cuando no hay eventos antiguos por migrar. */
internal data class ProgramCyclesModel(
    val currentCycle: Int,
    val loops: List<ProgramCycleLoop>,
    val weeks: List<ProgramCycleWeek>,
    val legacyEventCount: Int,
    val collisionLines: List<String>,
)

/** Lo que la hoja pide sobre un ciclo ya existente. [DELETE] se confirma antes de aplicarse. */
internal enum class ProgramCycleAction { POSTPONE_NEXT, CANCEL, REACTIVATE, DELETE }

/** Lo que devuelve el editor: el ciclo nuevo o cambiado. [loopId] es null cuando el ciclo es nuevo. */
internal data class ProgramCycleDraft(
    val loopId: String?,
    val title: String,
    val type: LoopType,
    val cadence: Int,
)

/** Icono KPKN de cada tipo de ciclo (sustituye a los emojis de `LoopEngine`): descarga = luna, test = bandera, evento = calendario, otro = repetición. */
internal fun loopTypeIcon(type: LoopType): ImageVector = when (type) {
    LoopType.DELOAD -> KpknLineIcons.Moon
    LoopType.ONE_RM_TEST -> KpknLineIcons.Flag
    LoopType.COMPETITION -> KpknLineIcons.Calendar
    LoopType.CUSTOM -> KpknLineIcons.Repeat
}

/** «cada 4 ciclos», o «cada ciclo» cuando la cadencia es 1. */
internal fun cycleCadenceLabel(cadence: Int): String =
    if (SpanishPlurals.isSingular(cadence)) "cada ciclo" else "cada $cadence ciclos"

/** «2 ciclos activos · vas por el ciclo 5». Sin ciclo en curso (0), la segunda parte no aparece. */
internal fun cycleSummaryLine(activeCount: Int, currentCycle: Int): String {
    val head = if (activeCount == 0) {
        "Sin ciclos activos"
    } else {
        numberWithUnit(SpanishPlurals.withNoun(activeCount, "ciclo activo", "ciclos activos"))
    }
    return if (currentCycle > 0) "$head · vas por el ciclo $currentCycle" else head
}

/** «Hay 2 eventos antiguos. Pásalos a ciclos para usar el sistema nuevo.» Con uno, en singular. */
internal fun legacyEventsLine(count: Int): String =
    if (SpanishPlurals.isSingular(count)) {
        "Hay 1 evento antiguo. Pásalo a ciclos para usar el sistema nuevo."
    } else {
        "Hay $count eventos antiguos. Pásalos a ciclos para usar el sistema nuevo."
    }

/** El otro ciclo que ya usa esa cadencia, o null si no hay choque. Una cadencia de 0 (campo vacío) nunca choca. */
internal fun cycleCadenceConflict(existing: List<ProgramCycleLoop>, editingId: String?, cadence: Int): ProgramCycleLoop? {
    if (cadence <= 0) return null
    return existing.firstOrNull { loop -> loop.id != editingId && loop.cadence.coerceAtLeast(1) == cadence }
}

/** «Conflicto: Descarga ya usa cada 4 ciclos. Cambia la cadencia para evitar que dos ciclos compitan por la misma semana.» */
internal fun cycleConflictLine(conflict: ProgramCycleLoop, cadence: Int): String =
    "Conflicto: ${conflict.title} ya usa ${cycleCadenceLabel(cadence)}. Cambia la cadencia para evitar que dos ciclos compitan por la misma semana."

/** Las cuatro plantillas de ciclos con su icono. Qué ciclos crean vive en `CyclesSection`, que es quien los añade al programa. */
internal val programCycleTemplates: List<ProgramCycleTemplate> = listOf(
    ProgramCycleTemplate(
        id = "deload-4",
        name = "Descarga cada 4",
        description = "Semana de descarga automática cada 4 ciclos.",
        icon = KpknLineIcons.Moon,
    ),
    ProgramCycleTemplate(
        id = "1rm-8",
        name = "Test 1RM cada 8",
        description = "Semana de pruebas de fuerza cada 8 ciclos.",
        icon = KpknLineIcons.Flag,
    ),
    ProgramCycleTemplate(
        id = "deload-1rm",
        name = "Descarga + Test 1RM",
        description = "Descarga cada 4 y Test 1RM cada 8 ciclos.",
        icon = KpknLineIcons.Repeat,
    ),
    ProgramCycleTemplate(
        id = "competition-12",
        name = "Competición cada 12",
        description = "Competición cada 12 ciclos con descarga cada 4.",
        icon = KpknLineIcons.Calendar,
    ),
)

/** Nombre del tipo de ciclo en el editor (el de `LoopEngine`, sin cambios). */
internal fun cycleTypeLabel(type: LoopType): String = LoopEngine.getLoopTypeLabel(type)
