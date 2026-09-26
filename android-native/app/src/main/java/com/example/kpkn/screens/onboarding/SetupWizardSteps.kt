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
    val centerControl = step == SetupStepId.HEIGHT || step == SetupStepId.WEIGHT
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
                CombinedMeasureQuestion()
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
    val ctaReady = ctaEnabled &&
        (step != SetupStepId.HEIGHT || anthropometryForCta != WizardAnthropometryLayout.Pending) &&
        (!combinedCta || state.draft.weightKg != null)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("setup-step-${step.name}"),
    ) {
        WizardScaffold(
            block = block.toWizardBlock(),
            title = block.headerTitle(),
            progress = progress,
            onBack = onBack,
            onExit = onExit,
            ctaLabel = ctaLabel,
            ctaEnabled = ctaReady,
            onCta = {
                if (combinedCta) vm.submitAnthropometryPair() else onCta()
            },
            centerControl = centerControl,
            header = stepHeader,
        ) {
            if (SetupStepGraph.isMilestone(step)) {
                MilestoneContent(step = step, state = state)
                return@WizardScaffold
            }
            if (!centerControl) {
                StepQuestion(step = step, definition = SetupStepDefinitions.of(step))
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
    Text(
        text = "Desliza cada regla. La posición inicial no es una respuesta.",
        style = WizardTypography.bodySmall,
        color = WizardColors.textMuted,
        modifier = Modifier.padding(top = 8.dp),
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
    val items = listOf(
        SetupWizardBlock.BASICS to "Datos básicos",
        SetupWizardBlock.TRAINING to "Entreno",
        SetupWizardBlock.NUTRITION to "Nutrición",
        SetupWizardBlock.RINGS to "Rings",
        SetupWizardBlock.REVIEW to "Revisión y activación",
    ).map { (block, title) ->
        WizardMilestoneItem(
            block = block.toWizardBlock(),
            title = title,
            body = milestoneBody(block),
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
        heroSubtitle = milestoneHeroSubtitle(step),
    )
}

/**
 * Héroe de la transición, como `Workouts/p1`: titular corto en mayúsculas y
 * subtítulo real, **sin** repetir el nombre del bloque que ya encabeza la
 * lista de etapas.
 */
private const val MILESTONE_HERO_TITLE = "UN PASO MÁS"

/** Qué queda por recorrer: los cuatro bloques de la ruta más la revisión. */
private fun milestoneHeroSubtitle(step: SetupStepId): String = when (step) {
    SetupStepId.MILESTONE_BASICS -> "Faltan Entreno, Nutrición, Rings y la revisión final."
    SetupStepId.MILESTONE_TRAINING -> "Faltan Nutrición, Rings y la revisión final."
    SetupStepId.MILESTONE_NUTRITION -> "Faltan Rings y la revisión final."
    SetupStepId.MILESTONE_RINGS -> "Solo queda la revisión final."
    else -> "Cuatro bloques y la revisión final para activar tu plan."
}

private fun milestoneBody(block: SetupWizardBlock): String = when (block) {
    SetupWizardBlock.BASICS -> "Edad, sexo usado por la ecuación, altura, peso y grasa corporal actual."
    SetupWizardBlock.TRAINING -> "Ruta, equipo, calendario, prioridades de orden y calentamientos."
    SetupWizardBlock.NUTRITION -> "Presupuesto energético, macros y reparto semanal según el gasto previsto."
    SetupWizardBlock.RINGS -> "Entrenamiento reciente, sensaciones y molestias para calibrar la recuperación."
    SetupWizardBlock.REVIEW -> "Se activa el programa y el plan nutricional juntos, al final del alta."
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
