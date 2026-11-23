package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.kpkn.data.models.LoopMode
import com.example.kpkn.data.models.LoopModulationScope
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.LoopWaveStep
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
    val mode: LoopMode = LoopMode.RECURRING_WEEK,
    val waveSteps: List<LoopWaveStep> = emptyList(),
    val modulationScope: LoopModulationScope = LoopModulationScope.APRE_EXERCISES,
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
    val canAddLoops: Boolean = true,
    val apreExerciseNames: List<String> = emptyList(),
)

/** Lo que la hoja pide sobre un ciclo ya existente. [DELETE] se confirma antes de aplicarse. */
internal enum class ProgramCycleAction { POSTPONE_NEXT, CANCEL, REACTIVATE, DELETE }

/** Lo que devuelve el editor: el ciclo nuevo o cambiado. [loopId] es null cuando el ciclo es nuevo. */
internal data class ProgramCycleDraft(
    val loopId: String?,
    val title: String,
    val type: LoopType,
    val cadence: Int,
    val mode: LoopMode = LoopMode.RECURRING_WEEK,
    val waveSteps: List<LoopWaveStep> = emptyList(),
    val modulationScope: LoopModulationScope = LoopModulationScope.APRE_EXERCISES,
)

/** Icono del lienzo (e-cycles) para cada tipo de ciclo: descarga = repetir, test = diana, competición = bandera, otro = repetir. */
internal fun loopTypeIcon(type: LoopType): ImageVector = when (type) {
    LoopType.DELOAD -> KpknLineIcons.Repeat
    LoopType.ONE_RM_TEST -> KpknLineIcons.Target
    LoopType.COMPETITION -> KpknLineIcons.Flag
    LoopType.CUSTOM -> KpknLineIcons.Repeat
}

/** «cada 4 loops», o «cada loop» cuando la cadencia es 1. */
internal fun cycleCadenceLabel(cadence: Int): String =
    if (SpanishPlurals.isSingular(cadence)) "cada loop" else "cada $cadence loops"

/** «2 loops activos · vuelta 5». Sin vuelta en curso (0), la segunda parte no aparece. */
internal fun cycleSummaryLine(activeCount: Int, currentCycle: Int): String {
    val head = if (activeCount == 0) {
        "Sin loops activos"
    } else {
        numberWithUnit(SpanishPlurals.withNoun(activeCount, "loop activo", "loops activos"))
    }
    return if (currentCycle > 0) "$head · vuelta $currentCycle" else head
}

/** «Hay 2 eventos antiguos. Pásalos a loops para usar el sistema nuevo.» Con uno, en singular. */
internal fun legacyEventsLine(count: Int): String =
    if (SpanishPlurals.isSingular(count)) {
        "Hay 1 evento antiguo. Pásalo a loops para usar el sistema nuevo."
    } else {
        "Hay $count eventos antiguos. Pásalos a loops para usar el sistema nuevo."
    }

/** El otro loop que ya usa esa cadencia, o null si no hay choque. Una cadencia de 0 (campo vacío) nunca choca. */
internal fun cycleCadenceConflict(existing: List<ProgramCycleLoop>, editingId: String?, cadence: Int): ProgramCycleLoop? {
    if (cadence <= 0) return null
    return existing.firstOrNull { loop ->
        loop.id != editingId &&
            loop.mode != LoopMode.PROGRESSIVE_WAVE &&
            loop.cadence.coerceAtLeast(1) == cadence
    }
}

/** «Conflicto: Descarga ya usa cada 4 loops. Cambia la cadencia para evitar que dos loops compitan por la misma semana.» */
internal fun cycleConflictLine(conflict: ProgramCycleLoop, cadence: Int): String =
    "Conflicto: ${conflict.title} ya usa ${cycleCadenceLabel(cadence)}. Cambia la cadencia para evitar que dos loops compitan por la misma semana."

/** Las plantillas de loops con su icono. Qué loops crean vive en `CyclesSection`, que es quien los añade al programa. */
internal val programCycleTemplates: List<ProgramCycleTemplate> = listOf(
    ProgramCycleTemplate(
        id = "custom",
        name = "Personalizado",
        description = "Define el nombre, el tipo y la cadencia.",
        icon = KpknLineIcons.Edit,
    ),
    ProgramCycleTemplate(
        id = "deload-4",
        name = "Descarga",
        description = "Prepara una semana con menos volumen y esfuerzo.",
        icon = KpknLineIcons.Moon,
    ),
    ProgramCycleTemplate(
        id = "1rm-8",
        name = "Test 1RM",
        description = "Elige los movimientos que vas a probar.",
        icon = KpknLineIcons.Flag,
    ),
    ProgramCycleTemplate(
        id = "deload-1rm",
        name = "Descarga + Test 1RM",
        description = "Descarga cada 4 loops y test cada 8.",
        icon = KpknLineIcons.Repeat,
    ),
    ProgramCycleTemplate(
        id = "wave:onda-apre-10-6-3",
        name = "Onda APRE 10-6-3",
        description = "Acumulación APRE 10 -> transición APRE 6 -> pico APRE 3.",
        icon = KpknLineIcons.Flame,
    ),
    ProgramCycleTemplate(
        id = "wave:progresion-rir-3-0",
        name = "Progresión RIR 3->0",
        description = "Baja el RIR objetivo semana a semana hasta el pico.",
        icon = KpknLineIcons.Target,
    ),
    ProgramCycleTemplate(
        id = "wave:rampa-volumen-descarga",
        name = "Rampa + descarga",
        description = "Suma +1 serie por bloque y descarga automática al final.",
        icon = KpknLineIcons.Repeat,
    ),
    ProgramCycleTemplate(
        id = "wave:ondulacion-reps-12-6",
        name = "Ondulación de reps 12 → 6",
        description = "Reduce repeticiones progresivamente de 12 a 6 semana a semana.",
        icon = KpknLineIcons.Repeat,
    ),
)

/** Filtra las plantillas de loops según los ejercicios APRE presentes en el programa. */
internal fun programCycleTemplatesFor(apreExerciseNames: List<String>): List<ProgramCycleTemplate> {
    if (apreExerciseNames.isEmpty()) {
        return programCycleTemplates.filterNot { it.id == "wave:onda-apre-10-6-3" }
    }
    val targetsLabel = apreExerciseNames.distinct().joinToString(", ")
    return programCycleTemplates.map { template ->
        if (template.id == "wave:onda-apre-10-6-3") {
            template.copy(description = "Afecta solo a: $targetsLabel · ${template.description}")
        } else {
            template
        }
    }
}

/** Nombre del tipo de ciclo en el editor (el de `LoopEngine`, sin cambios). */
internal fun cycleTypeLabel(type: LoopType): String = LoopEngine.getLoopTypeLabel(type)
