package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepDefinition
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.screens.onboarding.design.WizardAnthropometryLayout
import com.example.kpkn.screens.onboarding.design.WizardBlock
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.currentAnthropometryLayout
import com.example.kpkn.screens.onboarding.design.WizardMilestoneItem
import com.example.kpkn.screens.onboarding.design.WizardMilestoneState
import com.example.kpkn.screens.onboarding.design.WizardMilestones
import com.example.kpkn.screens.onboarding.design.WizardScaffold
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Pantalla tradicional del wizard: **un paso por pantalla**, sin conversación.
 *
 * Reglas de esta capa (solo dibujo; la orquestación vive en `SetupStepGraph`,
 * `SetupWizardModels` y el ViewModel):
 * - El catálogo [SetupStepDefinitions] es la única fuente de título, subtítulo,
 *   opciones y control. No se lee ninguna pregunta de WizChat y no se llama a
 *   `answerChoice`/`answerMulti`.
 * - La firma de [SetupStepScreen] la fija el Host: los CTA (Continuar / Activar)
 *   siguen siendo suyos y esta capa nunca confirma un paso ni avanza por su cuenta.
 * - El progreso se calcula sobre la **ruta efectiva**
 *   (`SetupStepGraph.stepIds(draft.stepContext())`) filtrando por bloque y el
 *   paso actual (`state.currentStep`): nunca sobre el orden del enum, porque
 *   las ramas condicionales insertan o quitan pasos.
 * - Raíz única de andamiaje: `WizardScaffold` se instancia una sola vez aquí, con
 *   la pregunta grande y su subtítulo una vez. El contenido por bloque
 *   ([SetupBasicsStepContent], `SetupTrainingStepContent`,
 *   `SetupNutritionStepContent`, `SetupRingsStepContent`, [SetupReviewStep]) no
 *   vuelve a montar un scaffold ni repite la cabecera.
 * - Cada pantalla expone `setup-step-<ID>` como testTag de contenedor; el CTA
 *   (`setup-continue`) lo publica el dueño del scaffold.
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

/** Pantalla completa de un paso. */
@Composable
fun SetupStepScreen(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    onBack: (() -> Unit)?,
    onExit: () -> Unit,
    ctaLabel: String = "Continuar",
    ctaEnabled: Boolean = true,
    onCta: () -> Unit,
    showCta: Boolean = true,
    embedded: Boolean = false,
) {
    val block = SetupStepGraph.blockOf(step)
    val route = SetupStepGraph.stepIds(state.draft.stepContext())
    val blockSteps = route.filter { SetupStepGraph.blockOf(it) == block }
    val index = blockSteps.indexOf(step)
    val progress = if (blockSteps.size <= 1 || index < 0) 1f else (index + 1f) / blockSteps.size

    // Altura/peso: la pregunta y el toggle de unidad suben a la cabecera fija
    // y el control se centra en el espacio restante real de la viewport. El
    // resto de pasos conserva el layout clásico con scroll (API opcional,
    // por defecto sin cambios).
    val centerControl = step == SetupStepId.WEIGHT
    var anthropometryForCta by remember(step) { mutableStateOf(WizardAnthropometryLayout.Pending) }
    var stepHeader: (@Composable () -> Unit)? = null
    if (centerControl) {
        stepHeader = {
            val layout = if (step == SetupStepId.HEIGHT) {
                currentAnthropometryLayout()
            } else {
                WizardAnthropometryLayout.Separate
            }
            SideEffect { anthropometryForCta = layout }
            if (layout == WizardAnthropometryLayout.Combined) {
                Unit
            } else {
                StepQuestion(step = step, definition = SetupStepDefinitions.of(step))
                if (layout != WizardAnthropometryLayout.Pending) {
                    SetupStepUnitToggle(step = step, state = state, vm = vm)
                }
            }
        }
    }
    val combinedCta = step == SetupStepId.HEIGHT &&
        anthropometryForCta == WizardAnthropometryLayout.Combined
    val aliasPage = step == SetupStepId.NAME
    val ageYears = state.draft.ageYears
    val aliasReady = !aliasPage || (
        state.draft.name.isNotBlank() && ageYears != null && ageYears in 13..100
        )
    val ctaReady = ctaEnabled && aliasReady &&
        (step != SetupStepId.HEIGHT || anthropometryForCta != WizardAnthropometryLayout.Pending) &&
        (!combinedCta || state.draft.weightKg != null)
    val presentation = wizardPresentationSteps(route)
    val here = presentation.indexOf(step)
    val nextTitle = presentation.getOrNull(here + 1)?.let { SetupStepDefinitions.of(it)?.title }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("setup-step-${step.name}"),
    ) {
        WizardScaffold(
            block = block.toWizardBlock(),
            title = when {
                step == SetupStepId.HEIGHT -> "¿Cuánto mides y pesas?"
                else -> SetupStepDefinitions.of(step)?.title ?: block.headerTitle()
            },
            progress = progress,
            onBack = onBack,
            onExit = onExit,
            ctaLabel = ctaLabel,
            ctaEnabled = ctaReady,
            showCta = showCta,
            showHeader = showCta && !embedded,
            embedded = embedded,
            nextPeekTitle = if (embedded) null else nextTitle,
            onCta = {
                when {
                    combinedCta -> vm.submitAnthropometryPair()
                    aliasPage -> vm.submitAliasAgePair()
                    else -> onCta()
                }
            },
            centerControl = centerControl,
            header = stepHeader,
        ) {
            if (SetupStepGraph.isMilestone(step)) {
                MilestoneContent(step = step, state = state)
                return@WizardScaffold
            }
            if (!centerControl && step != SetupStepId.NAME) {
                val subtitle = if (step == SetupStepId.BODY_FAT) BODY_FAT_SUBTITLE else SetupStepDefinitions.of(step)?.subtitle
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = WizardTypography.bodySmall,
                        color = WizardColors.textMuted,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // En los pasos centrados la cabecera ya trajo su hueco.
                    .padding(top = if (centerControl) 0.dp else WizardSpacing.titleGap),
                verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
            ) {
                SetupStepBody(step = step, state = state, vm = vm)
            }
        }
    }
}

/** Pregunta única cuando altura y peso caben juntos en la viewport. */
@Composable
private fun CombinedMeasureQuestion() {
    Text(
        text = "¿Cuánto mides y pesas?",
        style = WizardTypography.question,
        color = WizardColors.text,
        modifier = Modifier.semantics { heading() },
    )
}

/** Pregunta grande + subtítulo, renderizados una sola vez por la raíz. */
@Composable
private fun StepQuestion(step: SetupStepId, definition: SetupStepDefinition?) {
    Text(
        text = definition?.title ?: step.name,
        style = WizardTypography.question,
        color = WizardColors.text,
        modifier = Modifier.semantics { heading() },
    )
    // Subtítulo de catálogo para todos los pasos; para BODY_FAT la raíz lo
    // recorta a 1–2 líneas: el selector de grasa aporta su propio copy y la
    // pantalla tiene que mantener figura, slider y valor dentro del viewport.
    val subtitle = if (step == SetupStepId.BODY_FAT) BODY_FAT_SUBTITLE else definition?.subtitle
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Subtítulo corto del paso de grasa corporal (máx. 2 líneas). */
private const val BODY_FAT_SUBTITLE = "Mueve la figura. Si tienes el dato medido, escríbelo debajo."

/**
 * Hitos entre bloques: héroe grande más etapas numeradas. Solo la etapa actual
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
    WizardMilestones(
        items = items,
        heroTitle = MILESTONE_HERO_TITLE,
        heroSubtitle = milestoneHeroSubtitle(step, route),
    )
}

/**
 * Héroe de la transición, como `Workouts/p1`: titular corto en mayúsculas y
 * subtítulo real, **sin** repetir el nombre del bloque que ya encabeza la
 * lista de etapas.
 */
private const val MILESTONE_HERO_TITLE = "UN PASO MÁS"

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
