package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatGraph
import com.example.kpkn.screens.onboarding.design.WizardBlock
import com.example.kpkn.screens.onboarding.design.WizardBodyFatSource
import com.example.kpkn.screens.onboarding.design.WizardBodyFatValue
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardHeightUnit
import com.example.kpkn.screens.onboarding.design.WizardHeightUnitToggle
import com.example.kpkn.screens.onboarding.design.WizardHeightWheel
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardMassUnitToggle
import com.example.kpkn.screens.onboarding.design.WizardMilestoneItem
import com.example.kpkn.screens.onboarding.design.WizardMilestoneState
import com.example.kpkn.screens.onboarding.design.WizardMilestones
import com.example.kpkn.screens.onboarding.design.WizardPhysiqueSelector
import com.example.kpkn.screens.onboarding.design.WizardScaffold
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.WizardWeightRule

/**
 * Pantallas tradicionales del wizard.
 *
 * Sustituyen la vista conversacional de WizChat por **una pantalla por paso**,
 * como exige el plan: pregunta grande, control principal y CTA blanco fijo. La
 * orquestación (grafo, validación, persistencia, atrás sin pérdida, diálogos de
 * salida y activación conjunta) vive en `SetupStepGraph`, `SetupWizardModels` y
 * el ViewModel; aquí solo se dibuja.
 *
 * Sin burbujas, dock de respuestas, indicador de escritura ni avatar.
 */

/** Bloque del grafo → bloque visual del sistema de diseño. */
fun SetupWizardBlock.toWizardBlock(): WizardBlock = when (this) {
    SetupWizardBlock.BASICS -> WizardBlock.BASICS
    SetupWizardBlock.TRAINING -> WizardBlock.TRAINING
    SetupWizardBlock.NUTRITION -> WizardBlock.NUTRITION
    SetupWizardBlock.RINGS -> WizardBlock.RINGS
    SetupWizardBlock.REVIEW -> WizardBlock.REVIEW
}

/** Título de cabecera por bloque, como en las referencias. */
fun SetupWizardBlock.headerTitle(): String = when (this) {
    SetupWizardBlock.BASICS -> "Datos básicos"
    SetupWizardBlock.TRAINING -> "Entreno"
    SetupWizardBlock.NUTRITION -> "Nutrición"
    SetupWizardBlock.RINGS -> "Rings"
    SetupWizardBlock.REVIEW -> "Revisión"
}

private fun question(step: SetupStepId) = SetupStepGraph.questionForStep(step)?.let(WizChatGraph::question)

private fun questionTitle(step: SetupStepId): String = question(step)?.prompt ?: step.name

private fun questionKind(step: SetupStepId): WizChatAnswerKind =
    question(step)?.kind ?: WizChatAnswerKind.ACTION

private fun questionOptions(step: SetupStepId): List<String> = question(step)?.options.orEmpty()

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
    val blockSteps = SetupStepId.entries.filter { SetupStepGraph.blockOf(it) == block }
    val index = blockSteps.indexOf(step).coerceAtLeast(0)
    val progress = if (blockSteps.size <= 1) 1f else (index + 1f) / blockSteps.size

    WizardScaffold(
        block = block.toWizardBlock(),
        title = block.headerTitle(),
        progress = progress,
        onBack = onBack,
        onExit = onExit,
        ctaLabel = ctaLabel,
        ctaEnabled = ctaEnabled,
        onCta = onCta,
    ) {
        if (SetupStepGraph.isMilestone(step)) {
            MilestoneContent(step = step)
            return@WizardScaffold
        }

        Text(
            text = questionTitle(step),
            style = WizardTypography.question,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WizardSpacing.titleGap),
            verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
        ) {
            SetupStepBody(step = step, state = state, vm = vm)
        }
    }
}

@Composable
private fun MilestoneContent(step: SetupStepId) {
    val currentBlock = SetupStepGraph.blockOf(step)
    val items = listOf(
        WizardBlock.BASICS to "Datos básicos",
        WizardBlock.TRAINING to "Entreno",
        WizardBlock.NUTRITION to "Nutrición",
        WizardBlock.RINGS to "Rings",
        WizardBlock.REVIEW to "Revisión y activación",
    ).map { (block, title) ->
        WizardMilestoneItem(
            block = block,
            title = title,
            body = milestoneBody(block),
            state = when {
                block.ordinal < currentBlock.ordinal -> WizardMilestoneState.DONE
                block == currentBlock.toWizardBlock() -> WizardMilestoneState.CURRENT
                else -> WizardMilestoneState.PENDING
            },
        )
    }
    WizardMilestones(items = items)
}

private fun milestoneBody(block: WizardBlock): String = when (block) {
    WizardBlock.BASICS -> "Edad, sexo usado por la ecuación, altura, peso y grasa corporal actual."
    WizardBlock.TRAINING -> "Ruta, equipo, calendario, prioridades de orden y calentamientos."
    WizardBlock.NUTRITION -> "Presupuesto energético, macros y reparto semanal según el gasto previsto."
    WizardBlock.RINGS -> "Entrenamiento reciente, sensaciones y molestias para calibrar la recuperación."
    WizardBlock.REVIEW -> "Se activa el programa y el plan nutricional juntos, al final del alta."
}

/** Control principal de cada paso. */
@Composable
private fun SetupStepBody(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    when (step) {
        SetupStepId.NAME -> NameStep(state = state, vm = vm)
        SetupStepId.AGE -> NumberStep(
            label = "Edad",
            value = state.draft.ageYears?.toString().orEmpty(),
            keyboardType = KeyboardType.Number,
            onChange = { vm.setAge(it.toIntOrNull()) },
        )

        SetupStepId.WEIGHT -> WeightStep(state = state, vm = vm)
        SetupStepId.HEIGHT -> HeightStep(state = state, vm = vm)
        SetupStepId.PLAN, SetupStepId.TRAINING_REVIEW -> PlanStep(state = state, vm = vm)
        SetupStepId.REVIEW_ACTIVATE -> ReviewContent(state = state)
        else -> GenericStep(step = step, state = state, vm = vm)
    }
}

// ─── Controles tipados ──────────────────────────────────────────────────────

@Composable
private fun NameStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    OutlinedTextField(
        value = state.draft.name,
        onValueChange = vm::setName,
        singleLine = true,
        label = { Text("Nombre") },
        colors = wizardFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberStep(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = wizardFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun wizardFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = WizardColors.cardFill,
    unfocusedContainerColor = WizardColors.cardFill,
    focusedTextColor = WizardColors.text,
    unfocusedTextColor = WizardColors.text,
    focusedLabelColor = WizardColors.textMuted,
    unfocusedLabelColor = WizardColors.textMuted,
    cursorColor = WizardColors.ruleCursor,
    focusedBorderColor = WizardColors.selectedBorder,
    unfocusedBorderColor = WizardColors.cardBorder,
)

/** Peso con regla horizontal. La posición inicial no es respuesta. */
@Composable
private fun WeightStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val unit = if (state.draft.weightUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG
    Text(
        text = "Desliza la regla y confirma tu peso. Puedes alternar unidad sin cambiar el valor real.",
        style = WizardTypography.bodySmall,
        color = WizardColors.textMuted,
    )
    WizardMassUnitToggle(
        selected = unit,
        onSelected = { vm.setWeightUnit(it.code) },
    )
    WizardWeightRule(
        unit = unit,
        valueKg = state.draft.weightKg,
        onValueChange = { kg -> vm.setWeightKg(kg) },
    )
}

/** Altura con rueda vertical. La posición inicial no es respuesta. */
@Composable
private fun HeightStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    var unit by remember { mutableStateOf(WizardHeightUnit.CM) }
    Text(
        text = "Desliza la rueda para declarar tu altura. Se usa en las ecuaciones de gasto energético.",
        style = WizardTypography.bodySmall,
        color = WizardColors.textMuted,
    )
    WizardHeightUnitToggle(selected = unit, onSelected = { unit = it })
    WizardHeightWheel(
        unit = unit,
        cm = state.draft.heightCm?.toInt(),
        onValueChange = { cm -> vm.setHeightCm(cm.toDouble()) },
    )
}

/** Selección simple o múltiple sobre tarjetas. */
@Composable
private fun GenericStep(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    val questionId = SetupStepGraph.questionForStep(step)
    val options = questionOptions(step)
    val selected = state.selectedLabelsFor(step)

    when (questionKind(step)) {
        WizChatAnswerKind.MULTI_CHOICE -> options.forEach { option ->
            val checked = option in selected
            WizardChoiceCard(
                title = option,
                selected = checked,
                onClick = {
                    questionId?.let {
                        vm.answerMulti(it, (if (checked) selected - option else selected + option).toList())
                    }
                },
            )
        }

        WizChatAnswerKind.CHOICE -> options.forEach { option ->
            WizardChoiceCard(
                title = option,
                selected = selected.firstOrNull() == option,
                onClick = { questionId?.let { vm.answerChoice(it, option) } },
            )
        }

        WizChatAnswerKind.NUMBER, WizChatAnswerKind.TEXT -> Text(
            text = "Introduce el valor con el control del paso.",
            style = WizardTypography.caption,
            color = WizardColors.textFaint,
        )

        WizChatAnswerKind.ACTION, WizChatAnswerKind.TOGGLE -> Unit
    }
}

/** Candidatos de plan y revisión de entreno. */
@Composable
private fun PlanStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val candidates = state.planCandidates.ifEmpty { state.availablePlanCandidates }
    if (candidates.isEmpty()) {
        Text(
            text = "Preparando las opciones compatibles con tus respuestas…",
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
        )
        return
    }
    candidates.forEach { candidate ->
        WizardChoiceCard(
            title = candidate.title,
            subtitle = candidate.subtitle ?: candidate.description,
            selected = state.draft.selectedCatalogId == candidate.id,
            onClick = { vm.selectPlan(candidate.id) },
        )
    }
}

/** Revisión final: cuatro bloques, ya revisados. */
@Composable
private fun ReviewContent(state: SetupWizardState) {
    Text(
        text = "Revisa tu configuración. La activación del programa y del plan ocurre al final, de forma conjunta.",
        style = WizardTypography.bodySmall,
        color = WizardColors.textMuted,
    )
    listOf(
        WizardBlock.BASICS to "Datos básicos",
        WizardBlock.TRAINING to "Entreno",
        WizardBlock.NUTRITION to "Nutrición",
        WizardBlock.RINGS to "Rings",
    ).forEach { (block, title) ->
        WizardChoiceCard(
            title = title,
            subtitle = "Tocar para revisar",
            selected = false,
            onClick = { },
        )
    }
}

/** Etiquetas respondidas de un paso; nunca fabrica una respuesta. */
private fun SetupWizardState.selectedLabelsFor(step: SetupStepId): Set<String> {
    val questionId = SetupStepGraph.questionForStep(step) ?: return emptySet()
    val record = draft.wizChat.acceptedAnswers.lastOrNull { it.questionId == questionId } ?: return emptySet()
    return buildSet {
        record.textValue?.takeIf { it.isNotBlank() }?.let { add(it) }
        record.values.filter { it.isNotBlank() }.forEach { add(it) }
    }
}
