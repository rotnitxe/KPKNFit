package com.example.kpkn.screens.onboarding

import androidx.compose.runtime.Composable
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.KpknModule
import com.example.kpkn.screens.onboarding.design.OverlayStage
import com.example.kpkn.screens.onboarding.design.OverlayStageState
import com.example.kpkn.screens.onboarding.design.WizardBlock

/**
 * Contenido y textos de las páginas del wizard dentro de la **página larga**.
 *
 * Reglas de esta capa (solo dibujo y textos; la orquestación vive en `SetupStepGraph`,
 * `SetupWizardModels` y el ViewModel):
 * - El catálogo [SetupStepDefinitions] es la única fuente de título, subtítulo, opciones y
 *   control. No se lee ninguna pregunta de WizChat y no se llama a `answerChoice`/`answerMulti`.
 * - La sección (etiqueta, título, subtítulo) la dibuja el Host con `WizardPageItem`; el
 *   contenido por bloque ([SetupBasicsStepContent], `SetupTrainingStepContent`,
 *   `SetupNutritionStepContent`, `SetupRingsStepContent`, [SetupReviewStep]) solo pinta el
 *   control y nunca repite el título.
 * - Esta capa nunca confirma un paso ni avanza por su cuenta: el check es del Host.
 * - Cada sección activa expone `setup-step-<ID>` como testTag y cada fila-resumen
 *   `setup-summary-<ID>`; el check (`setup-continue`) lo publica el Host.
 */

/**
 * Pasos que se ven como páginas. Alias y edad comparten la página del alias, así que la edad no es una
 * vista propia cuando el alias está en la ruta. Los hitos entre bloques tampoco son páginas: se ven como
 * el overlay de «bloque completado» ([milestoneStages]) encima de la última página del bloque.
 */
internal fun wizardPresentationSteps(route: List<SetupStepId>): List<SetupStepId> {
    var steps = route.filterNot(SetupStepGraph::isMilestone)
    if (SetupStepId.NAME in steps) steps = steps.filterNot { it == SetupStepId.AGE }
    if (SetupStepId.HEIGHT in steps) steps = steps.filterNot { it == SetupStepId.WEIGHT }
    return steps
}

/** Bloque del grafo → bloque visual del sistema de diseño. */
fun SetupWizardBlock.toWizardBlock(): WizardBlock = when (this) {
    SetupWizardBlock.BASICS -> WizardBlock.BASICS
    SetupWizardBlock.TRAINING -> WizardBlock.TRAINING
    SetupWizardBlock.NUTRITION -> WizardBlock.NUTRITION
    SetupWizardBlock.RINGS -> WizardBlock.RINGS
    SetupWizardBlock.REVIEW -> WizardBlock.REVIEW
}

/** Título de cabecera por bloque; la copia vive en el catálogo, no aquí. */
fun SetupWizardBlock.headerTitle(): String = SetupStepDefinitions.blockTitle(this)

/** Contenido de una página: el control del paso. Sin título: lo pinta la sección. */
@Composable
internal fun SetupStepContent(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    SetupStepBody(step = step, state = state, vm = vm)
}

/** Título y subtítulo de la sección de una página. Las páginas fusionadas tienen su propio texto. */
internal data class WizardPageCopy(val title: String, val subtitle: String?)

/**
 * Título y subtítulo de la página [page]. El paso PLAN cambia con el perfil de objetivo ([goalProfile]): con una
 * disciplina se elige entre varios programas; con un perfil general (o sin perfil) se muestra el programa a medida. Y
 * con el programa aplazado ([programDeferred], «Lo haré más adelante») dice que todavía no hay programa: el título de un
 * programa que no se va a crear sería mentira (COPY · «PLAN (aplazado)»).
 */
internal fun wizardPageCopy(
    page: SetupStepId,
    goalProfile: TrainingGoalProfile? = null,
    programDeferred: Boolean = false,
): WizardPageCopy {
    val definition = SetupStepDefinitions.of(page)
    return when (page) {
        SetupStepId.NAME -> WizardPageCopy("Empecemos por ti", "Tu alias y tu fecha de nacimiento.")
        SetupStepId.HEIGHT -> WizardPageCopy("¿Cuánto mides y pesas?", "Desliza cada regla hasta tu medida.")
        // La figura y la regla se explican solas: este paso no lleva subtítulo.
        SetupStepId.BODY_FAT -> WizardPageCopy(definition?.title ?: page.name, null)
        SetupStepId.PLAN ->
            when {
                programDeferred -> WizardPageCopy(PLAN_DEFERRED_TITLE, PLAN_DEFERRED_SUBTITLE)
                goalProfile?.isSpecific == true ->
                    WizardPageCopy("Elige tu programa", "Elige el que más te guste. Podrás modificarlo después.")
                else -> WizardPageCopy(definition?.title ?: page.name, definition?.subtitle)
            }
        else -> WizardPageCopy(definition?.title ?: page.name, definition?.subtitle)
    }
}

/** PLAN con el programa aplazado: no hay programa todavía. La revisión final dice lo mismo ([DEFER_PROGRAM_REVIEW_VALUE]). */
internal const val PLAN_DEFERRED_TITLE = "Sin programa por ahora"
internal const val PLAN_DEFERRED_SUBTITLE = "$DEFER_PROGRAM_REVIEW_VALUE."

/** Página del wizard que muestra el paso del cursor: la edad vive en la del alias y el peso en la de la altura. */
internal fun wizardPageOf(step: SetupStepId): SetupStepId = when (step) {
    SetupStepId.AGE -> SetupStepId.NAME
    SetupStepId.WEIGHT -> SetupStepId.HEIGHT
    else -> step
}

/**
 * Página que ocupa la pantalla cuando el cursor está en [step]. Un hito no es una página: mientras su overlay
 * está abierto la página sigue siendo la última del bloque, así volver atrás (o terminar el overlay) no mueve
 * la página larga y el deslizado hacia el bloque siguiente ocurre DESPUÉS, al continuar.
 */
internal fun wizardCurrentPage(step: SetupStepId, route: List<SetupStepId>): SetupStepId {
    if (!SetupStepGraph.isMilestone(step) || step !in route) return wizardPageOf(step)
    val lastQuestion = route.takeWhile { it != step }.lastOrNull { !SetupStepGraph.isMilestone(it) }
    return lastQuestion?.let(::wizardPageOf) ?: wizardPageOf(step)
}

/** Posición de una página dentro de su bloque. */
internal data class WizardBlockPosition(val block: SetupWizardBlock, val number: Int, val total: Int)

internal fun wizardBlockPosition(page: SetupStepId, pages: List<SetupStepId>): WizardBlockPosition {
    val block = SetupStepGraph.blockOf(page)
    val inBlock = pages.filter { SetupStepGraph.blockOf(it) == block }
    val index = inBlock.indexOf(page)
    return WizardBlockPosition(block, number = if (index < 0) inBlock.size else index + 1, total = inBlock.size)
}

/** Etiqueta pequeña sobre el título de la sección: «Paso 2 de 5 · Datos básicos». */
internal fun wizardEyebrow(page: SetupStepId, pages: List<SetupStepId>): String = when {
    SetupStepGraph.blockOf(page) == SetupWizardBlock.REVIEW -> "Último paso"
    else -> {
        val position = wizardBlockPosition(page, pages)
        "Paso ${position.number} de ${position.total} · ${SetupStepDefinitions.blockTitle(position.block)}"
    }
}

/** Texto del centro de la cabecera: el bloque y cuánto llevas, sin repetir la pregunta. */
internal fun wizardHeaderLabel(page: SetupStepId, pages: List<SetupStepId>): String {
    val position = wizardBlockPosition(page, pages)
    val name = SetupStepDefinitions.blockTitle(position.block)
    return when {
        position.block == SetupWizardBlock.REVIEW -> name
        else -> "$name · ${position.number}/${position.total}"
    }
}

/**
 * Parte confirmada de cada bloque del recorrido, en orden: 0..1. [confirmedCount] es cuántas de las primeras
 * páginas ya están confirmadas: el índice de la página actual, o ese más uno mientras el overlay de un hito
 * está abierto (la última pregunta del bloque ya se confirmó).
 */
internal fun wizardBlockProgress(
    pages: List<SetupStepId>,
    confirmedCount: Int,
): List<Pair<SetupWizardBlock, Float>> =
    pages.map(SetupStepGraph::blockOf).distinct().map { block ->
        val questions = pages.withIndex().filter { (_, page) -> SetupStepGraph.blockOf(page) == block }
        val fraction = questions.count { (index, _) -> index < confirmedCount }.toFloat() / questions.size
        block to fraction
    }

/** Animación del overlay del hito del bloque [this]; la revisión no tiene hito. */
internal fun SetupWizardBlock.toKpknModule(): KpknModule? = when (this) {
    SetupWizardBlock.BASICS -> KpknModule.BASICOS
    SetupWizardBlock.TRAINING -> KpknModule.ENTRENO
    SetupWizardBlock.NUTRITION -> KpknModule.NUTRICION
    SetupWizardBlock.RINGS -> KpknModule.RINGS
    SetupWizardBlock.REVIEW -> null
}

/** Los bloques de la ruta efectiva [route], en el orden en que se recorren y sin repetir. */
internal fun milestoneBlocks(route: List<SetupStepId>): List<SetupWizardBlock> =
    route.map(SetupStepGraph::blockOf).distinct()

/** Nombre corto de una etapa en la fila de etapas del overlay: caben cinco en una fila. */
internal fun milestoneStageLabel(block: SetupWizardBlock): String = when (block) {
    SetupWizardBlock.BASICS -> "Básicos"
    SetupWizardBlock.TRAINING -> "Entreno"
    SetupWizardBlock.NUTRITION -> "Nutrición"
    SetupWizardBlock.RINGS -> "Rings"
    SetupWizardBlock.REVIEW -> "Revisión"
}

/**
 * Etapas del recorrido [route] vistas desde el hito [milestone]: el bloque que acaba de cerrarse
 * ([OverlayStageState.JUST_DONE]), los que ya estaban completos ([completed]), el siguiente por completar
 * ([OverlayStageState.NEXT]) y los demás pendientes. Las etapas salen de la RUTA efectiva del borrador, no de
 * una lista fija: un asistente solo de entreno (desde la biblioteca) no habla de Nutrición ni de Rings.
 */
internal fun milestoneStages(
    milestone: SetupStepId,
    route: List<SetupStepId>,
    completed: Set<SetupWizardBlock>,
): List<OverlayStage> {
    val blocks = milestoneBlocks(route)
    val currentIndex = blocks.indexOf(SetupStepGraph.blockOf(milestone))
    val nextIndex = blocks.indices.firstOrNull { it > currentIndex && blocks[it] !in completed }
    return blocks.mapIndexed { index, block ->
        val state = when {
            index == currentIndex -> OverlayStageState.JUST_DONE
            block in completed -> OverlayStageState.DONE
            index == nextIndex -> OverlayStageState.NEXT
            else -> OverlayStageState.PENDING
        }
        OverlayStage(milestoneStageLabel(block), state)
    }
}

/**
 * Etapas de la pantalla de arranque: ninguna completada (sin color de «completado»); la primera señalada como
 * por dónde se empieza.
 */
internal fun introStages(route: List<SetupStepId>): List<OverlayStage> =
    milestoneBlocks(route).mapIndexed { index, block ->
        OverlayStage(milestoneStageLabel(block), if (index == 0) OverlayStageState.NEXT else OverlayStageState.PENDING)
    }

/**
 * Contenido por bloque. Cada rama delega en el dueño de su bloque con la misma
 * firma `(step, state, vm)`: ninguno monta andamiaje ni cabecera propios.
 */
@Composable
private fun SetupStepBody(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    when (SetupStepGraph.blockOf(step)) {
        SetupWizardBlock.BASICS -> SetupBasicsStepContent(step = step, state = state, vm = vm)
        SetupWizardBlock.TRAINING -> SetupTrainingStepContent(step = step, state = state, vm = vm)
        SetupWizardBlock.NUTRITION -> SetupNutritionStepContent(step = step, state = state, vm = vm)
        SetupWizardBlock.RINGS -> SetupRingsStepContent(step = step, state = state, vm = vm)
        SetupWizardBlock.REVIEW -> SetupReviewStep(step = step, state = state, vm = vm)
    }
}
