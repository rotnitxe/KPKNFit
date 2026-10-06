package com.example.kpkn.screens.onboarding

import androidx.compose.runtime.Composable
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.screens.onboarding.design.WizardBlock
import com.example.kpkn.screens.onboarding.design.WizardMilestoneItem
import com.example.kpkn.screens.onboarding.design.WizardMilestoneState
import com.example.kpkn.screens.onboarding.design.WizardMilestones

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
 * Pasos que se ven como páginas. Alias y edad comparten la página del alias,
 * así que la edad no es una vista propia cuando el alias está en la ruta.
 */
internal fun wizardPresentationSteps(route: List<SetupStepId>): List<SetupStepId> {
    var steps = route
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

/** Contenido de una página: el hito o el control del paso. Sin título: lo pinta la sección. */
@Composable
internal fun SetupStepContent(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    if (SetupStepGraph.isMilestone(step)) {
        MilestoneContent(step = step, state = state)
    } else {
        SetupStepBody(step = step, state = state, vm = vm)
    }
}

/** Título y subtítulo de la sección de una página. Las páginas fusionadas tienen su propio texto. */
internal data class WizardPageCopy(val title: String, val subtitle: String?)

internal fun wizardPageCopy(page: SetupStepId, route: List<SetupStepId>): WizardPageCopy {
    if (SetupStepGraph.isMilestone(page)) {
        return WizardPageCopy(
            title = milestoneSectionTitle(SetupStepGraph.blockOf(page)),
            subtitle = milestoneHeroSubtitle(page, route),
        )
    }
    val definition = SetupStepDefinitions.of(page)
    return when (page) {
        SetupStepId.NAME -> WizardPageCopy("Empecemos por ti", "Tu alias y tu fecha de nacimiento.")
        SetupStepId.HEIGHT -> WizardPageCopy("¿Cuánto mides y pesas?", "Desliza cada regla hasta tu medida.")
        // La figura y la regla se explican solas: este paso no lleva subtítulo.
        SetupStepId.BODY_FAT -> WizardPageCopy(definition?.title ?: page.name, null)
        else -> WizardPageCopy(definition?.title ?: page.name, definition?.subtitle)
    }
}

/** Título de la sección de un hito: lo que acaba de quedar listo. */
internal fun milestoneSectionTitle(block: SetupWizardBlock): String = when (block) {
    SetupWizardBlock.BASICS -> "Datos básicos listos"
    SetupWizardBlock.TRAINING -> "Entreno listo"
    SetupWizardBlock.NUTRITION -> "Nutrición lista"
    SetupWizardBlock.RINGS -> "Rings listos"
    SetupWizardBlock.REVIEW -> "Revisión lista"
}

/** Página del wizard que muestra el paso del cursor: la edad vive en la del alias y el peso en la de la altura. */
internal fun wizardPageOf(step: SetupStepId): SetupStepId = when (step) {
    SetupStepId.AGE -> SetupStepId.NAME
    SetupStepId.WEIGHT -> SetupStepId.HEIGHT
    else -> step
}

/** Posición de una página dentro de su bloque, contando solo las páginas de pregunta (no los hitos). */
internal data class WizardBlockPosition(val block: SetupWizardBlock, val number: Int, val total: Int)

internal fun wizardBlockPosition(page: SetupStepId, pages: List<SetupStepId>): WizardBlockPosition {
    val block = SetupStepGraph.blockOf(page)
    val inBlock = pages.filter { SetupStepGraph.blockOf(it) == block && !SetupStepGraph.isMilestone(it) }
    val index = inBlock.indexOf(page)
    return WizardBlockPosition(block, number = if (index < 0) inBlock.size else index + 1, total = inBlock.size)
}

/** Etiqueta pequeña sobre el título de la sección: «Paso 2 de 5 · Datos básicos». */
internal fun wizardEyebrow(page: SetupStepId, pages: List<SetupStepId>): String = when {
    SetupStepGraph.isMilestone(page) -> "Bloque completado"
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
        SetupStepGraph.isMilestone(page) -> "$name · listo"
        position.block == SetupWizardBlock.REVIEW -> name
        else -> "$name · ${position.number}/${position.total}"
    }
}

/**
 * Parte confirmada de cada bloque del recorrido, en orden: 0..1. Un bloque sin preguntas
 * propias (solo hito) cuenta como completo.
 */
internal fun wizardBlockProgress(
    pages: List<SetupStepId>,
    currentIndex: Int,
): List<Pair<SetupWizardBlock, Float>> =
    pages.map(SetupStepGraph::blockOf).distinct().map { block ->
        val questions = pages.withIndex().filter { (_, page) ->
            SetupStepGraph.blockOf(page) == block && !SetupStepGraph.isMilestone(page)
        }
        val fraction = if (questions.isEmpty()) 1f else questions.count { (index, _) -> index < currentIndex }.toFloat() / questions.size
        block to fraction
    }

/**
 * Hitos entre bloques: etapas numeradas bajo el título de la sección. Solo la etapa actual
 * muestra su párrafo y solo un bloque aparece como completado cuando su hito
 * quedó **confirmado** (`completedBlocks`), nunca por haber visitado el paso.
 */
@Composable
private fun MilestoneContent(step: SetupStepId, state: SetupWizardState) {
    val currentBlock = SetupStepGraph.blockOf(step)
    val completed = state.completedBlocks
    // H8/H15: las etapas y lo que falta salen de la RUTA efectiva del borrador, no de una lista fija: un asistente
    // solo de entreno (TRAINING_ONLY, desde la biblioteca) no habla de Nutrición ni de Rings.
    val route = SetupStepGraph.stepIds(state.draft.stepContext())
    val items = milestoneBlocks(route).map { block ->
        WizardMilestoneItem(
            block = block.toWizardBlock(),
            title = milestoneBlockTitle(block),
            body = milestoneBody(block, route),
            state = when {
                block in completed -> WizardMilestoneState.DONE
                block == currentBlock -> WizardMilestoneState.CURRENT
                else -> WizardMilestoneState.PENDING
            },
        )
    }
    // El título y el subtítulo del hito los pinta la sección; aquí solo las etapas.
    WizardMilestones(items = items)
}

/** Los bloques de la ruta efectiva [route], en el orden en que se recorren y sin repetir. */
internal fun milestoneBlocks(route: List<SetupStepId>): List<SetupWizardBlock> =
    route.map(SetupStepGraph::blockOf).distinct()

/** Nombre de cada etapa en la lista de etapas del hito. */
internal fun milestoneBlockTitle(block: SetupWizardBlock): String = when (block) {
    SetupWizardBlock.BASICS -> "Datos básicos"
    SetupWizardBlock.TRAINING -> "Entreno"
    SetupWizardBlock.NUTRITION -> "Nutrición"
    SetupWizardBlock.RINGS -> "Rings"
    SetupWizardBlock.REVIEW -> "Revisión y activación"
}

/**
 * Qué queda por recorrer tras el hito [step], según la ruta efectiva [route]: los bloques que vienen después del suyo y
 * la revisión final («Faltan Entreno, Nutrición, Rings y la revisión final.»; con una ruta de solo entreno «Faltan
 * Entreno y la revisión final.» y, al cerrar Entreno, «Solo queda la revisión final.»). Un paso que no es un hito del
 * recorrido da la frase general.
 */
internal fun milestoneHeroSubtitle(step: SetupStepId, route: List<SetupStepId>): String {
    if (!SetupStepGraph.isMilestone(step) || step !in route) {
        return "Los bloques de tu ruta y la revisión final para activar tu plan."
    }
    val current = SetupStepGraph.blockOf(step)
    val remaining = milestoneBlocks(route)
        .dropWhile { block -> block != current }
        .drop(1)
        .filter { block -> block != SetupWizardBlock.REVIEW }
    return if (remaining.isEmpty()) {
        "Solo queda la revisión final."
    } else {
        "Faltan ${remaining.joinToString(", ") { block -> milestoneBlockTitle(block) }} y la revisión final."
    }
}

/**
 * Párrafo de cada etapa. La revisión dice qué se activa según la ruta: el programa y el plan nutricional juntos
 * solo si la ruta trae el bloque de Nutrición; con una ruta de solo entreno se activa únicamente el programa.
 */
internal fun milestoneBody(block: SetupWizardBlock, route: List<SetupStepId>): String = when (block) {
    SetupWizardBlock.BASICS -> "Edad, sexo usado por la ecuación, altura, peso y grasa corporal actual."
    SetupWizardBlock.TRAINING -> "Ruta, equipo, calendario, prioridades de orden y calentamientos."
    SetupWizardBlock.NUTRITION -> "Presupuesto energético, macros y reparto semanal según el gasto previsto."
    SetupWizardBlock.RINGS -> "Entrenamiento reciente, sensaciones y molestias para calibrar la recuperación."
    SetupWizardBlock.REVIEW ->
        if (SetupWizardBlock.NUTRITION in milestoneBlocks(route)) {
            "Se activa el programa y el plan nutricional juntos, al final del alta."
        } else {
            "Se activa tu programa al final, con lo que hayas respondido."
        }
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
