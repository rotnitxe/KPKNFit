package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.SetupNutritionPreparationResult
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import com.example.kpkn.screens.onboarding.design.WizardBlock
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Cuerpo de los pasos del bloque NUTRITION.
 *
 * Contrato con la raíz del wizard: la cabecera (pregunta, subtítulo, progreso y
 * CTA) la pinta `SetupStepScreen`; aquí solo se dibuja el control del paso,
 * siempre desde el catálogo `SetupStepDefinitions` (valor estable + etiqueta) y
 * con **como máximo dos entradas relacionadas visibles** por pantalla.
 *
 * Cada interacción escribe dos veces y en este orden: primero `updateStep`
 * (los campos tipados que el reductor no toca: macros manuales, peso meta,
 * contexto de historia y filas de pesaje) y después el setter de paso
 * (`setStepChoice`/`setStepChoices`/`setStepText`/`setStepNumber`), que guarda
 * la respuesta con su procedencia **y proyecta al dominio** en
 * `SetupStepAnswers.withStep*`: esa proyección va después y por eso es la que
 * manda cuando ambas tocan el mismo campo. Ninguna función mueve el cursor:
 * solo el CTA del Host confirma y avanza un paso.
 *
 * Ninguna cifra se fabrica: sin objetivo no hay meta, sin gasto estimado no hay
 * 0 y el modo «solo registro» no genera plan.
 */
@Composable
fun SetupNutritionStepContent(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    when (step) {
        SetupStepId.NUTRITION_START -> NutritionStartContent(state, vm)
        SetupStepId.NUTRITION_ELIGIBILITY -> NutritionEligibilityContent(state, vm)
        SetupStepId.NUTRITION_DIRECTION -> NutritionDirectionContent(state, vm)
        SetupStepId.NUTRITION_RHYTHM -> NutritionRhythmContent(state, vm)
        SetupStepId.NUTRITION_TARGET -> NutritionTargetContent(state, vm)
        SetupStepId.NUTRITION_HISTORY_CONTEXT -> NutritionHistoryContextContent(state, vm)
        SetupStepId.NUTRITION_ACTIVITY -> NutritionActivityContent(state, vm)
        SetupStepId.NUTRITION_MANUAL_CALORIES -> NutritionManualCaloriesContent(state, vm)
        SetupStepId.NUTRITION_MANUAL_CARBS_FAT -> NutritionManualCarbsFatContent(state, vm)
        SetupStepId.NUTRITION_DISTRIBUTION -> NutritionDistributionContent(state, vm)
        SetupStepId.NUTRITION_WEIGH_INS -> NutritionWeighInsContent(state, vm)
        SetupStepId.NUTRITION_RESULT -> NutritionResultContent(state, vm)
        // MILESTONE_NUTRITION lo pinta la raíz (hito del bloque) y NUTRITION_SEX
        // es legacy-only, fuera de la ruta productiva: aquí no se dibuja nada.
        else -> Unit
    }
}

// ─── Escritura sobre el borrador ────────────────────────────────────────────

private fun SetupWizardDraft.withNutritionDraft(
    change: (NutritionWizardDraft) -> NutritionWizardDraft,
): SetupWizardDraft {
    val base = nutritionDraft ?: NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId)
    return copy(nutritionDraft = change(base))
}

// ─── NUTRITION_START ────────────────────────────────────────────────────────

@Composable
private fun NutritionStartContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_START
    val mode = state.draft.nutritionDraft?.configurationMode
    val selected = state.draft.setupSelectionOf(step, mode?.name?.lowercase())
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        subtitles = mapOf(
            "automatic" to "Automático: calculo calorías y macros con tus datos.",
            "self_defined" to "Objetivos propios: los valores los pones tú.",
            "tracking_only" to "Solo registro: sin objetivos ni plan nutricional.",
        ),
        onSelect = { value ->
            setupEnumValueOrNull<NutritionConfigurationMode>(value)?.let { configurationMode ->
                vm.updateStep(step) { draft ->
                    draft.withNutritionDraft { it.copy(mode = "create", configurationMode = configurationMode) }
                }
            }
            vm.setStepChoice(step, value)
        },
    )
}

// ─── NUTRITION_ELIGIBILITY ──────────────────────────────────────────────────

@Composable
private fun NutritionEligibilityContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_ELIGIBILITY
    val nutrition = state.draft.nutritionDraft
    val fallback = buildSet {
        if (nutrition?.eligibilityUnknown == true) add("unknown")
        if (nutrition?.pregnant == true) add("pregnancy")
        if (nutrition?.lactating == true) add("lactation")
        if (nutrition?.medicalRestriction == true) add("medical_restriction")
    }
    val selected = state.draft.setupSelectionOf(step, *fallback.toTypedArray())
    SetupBodyMultiChoiceCards(
        step = step,
        selected = selected,
        subtitles = mapOf(
            "none" to "Ninguna de estas situaciones aplica a mí.",
            "unknown" to "Prefiero no responder; sin cadena de ecuación no calculamos el gasto.",
        ),
        // El toggle se resuelve dentro del mutex del VM sobre el último
        // borrador: la proyección tipada de `withStepChoices` escribe aquí
        // (eligibilityUnknown/pregnant/lactating/medicalRestriction) y
        // `validateStep(ELIGIBILITY)` lee `selectedValues`, no la UI.
        onSelect = { value -> vm.toggleStepChoice(step, value) },
    )
}

// ─── NUTRITION_DIRECTION ────────────────────────────────────────────────────

@Composable
private fun NutritionDirectionContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_DIRECTION
    val direction = state.draft.nutritionDraft?.direction?.takeIf { it != PlanDirection.PROFESSIONAL }
    val selected = state.draft.setupSelectionOf(step, direction?.name?.lowercase())
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value ->
            setupEnumValueOrNull<PlanDirection>(value)?.let { resolved ->
                vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(direction = resolved) } }
            }
            vm.setStepChoice(step, value)
        },
    )
    SetupBodySkipAction(step = step, vm = vm, label = "Sin dirección por ahora", visible = selected.isEmpty())
}

// ─── NUTRITION_RHYTHM ───────────────────────────────────────────────────────

@Composable
private fun NutritionRhythmContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_RHYTHM
    val pace = state.draft.nutritionDraft?.pacePreset
    val selected = state.draft.setupSelectionOf(
        step,
        when (pace) {
            WizardPacePreset.SLOW -> "slow"
            WizardPacePreset.MEDIUM -> "medium"
            WizardPacePreset.FAST -> "fast"
            null -> null
        },
    )
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        subtitles = mapOf(
            "slow" to "Cambio más largo en el tiempo.",
            "medium" to "Ritmo moderado y sostenible.",
            "fast" to "Cambio más rápido; vigilamos las señales.",
        ),
        onSelect = { value ->
            setupEnumValueOrNull<WizardPacePreset>(value)?.let { resolved ->
                vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(pacePreset = resolved) } }
            }
            vm.setStepChoice(step, value)
        },
    )
}

// ─── NUTRITION_TARGET ───────────────────────────────────────────────────────

@Composable
private fun NutritionTargetContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_TARGET
    val domainText = state.draft.nutritionDraft?.targetWeightText.orEmpty()
    // `inputTexts` se lee siempre con `step.name`; conserva el valor crudo.
    val initial = domainText.ifBlank { state.draft.inputTexts[step.name].orEmpty() }
    SetupBodyTextField(
        label = "Peso objetivo (kg)",
        initial = initial,
        keyboardType = KeyboardType.Decimal,
        helper = "Opcional. Déjalo vacío si no quieres fijar una meta de peso.",
        onValueChange = { raw ->
            val parsed = parseLocalizedNumber(raw)?.takeIf { it.isFinite() && it in 20.0..500.0 }
            vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(targetWeightText = raw.trim()) } }
            vm.setStepText(step, raw)
            vm.setStepNumber(step, parsed)
        },
    )
    SetupBodySkipAction(step = step, vm = vm, label = "No fijar peso objetivo", visible = initial.isBlank())
}

// ─── NUTRITION_HISTORY_CONTEXT ──────────────────────────────────────────────

/** Tendencia declarada: contexto del plan, nunca un registro de pesaje. */
private val trendOptions = listOf(
    "rising" to "Subiendo",
    "stable" to "Estable",
    "falling" to "Bajando",
)

@Composable
private fun NutritionHistoryContextContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_HISTORY_CONTEXT
    val draft = state.draft
    val trend = draft.weightTrend
    val maximum = draft.previousMaximumWeightKg
    val selectedTrend = draft.setupSelectionOf(step, trend)
    val maximumText = draft.inputTexts[step.name]?.takeIf { it.isNotBlank() }
        ?: maximum?.let(::formatWeight).orEmpty()

    Text(
        text = "Tendencia reciente",
        style = WizardTypography.cardTitle,
        color = WizardColors.text,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
    ) {
        trendOptions.forEach { (value, label) ->
            WizardChoiceCard(
                title = label,
                selected = value in selectedTrend,
                onClick = {
                    vm.updateStep(step) { current -> current.copy(weightTrend = value) }
                    vm.setStepChoice(step, value)
                },
            )
        }
    }

    Text(
        text = "Máximo anterior",
        style = WizardTypography.cardTitle,
        color = WizardColors.text,
        modifier = Modifier.padding(top = WizardSpacing.titleGap, bottom = 4.dp),
    )
    SetupBodyTextField(
        label = "Máximo anterior (kg)",
        initial = maximumText,
        keyboardType = KeyboardType.Decimal,
        helper = "Contexto del plan: no se convierte en un pesaje histórico.",
        onValueChange = { raw ->
            val parsed = parseLocalizedNumber(raw)?.takeIf { it.isFinite() && it in 20.0..500.0 }
            vm.updateStep(step) { current -> current.copy(previousMaximumWeightKg = parsed) }
            vm.setStepText(step, raw)
            vm.setStepNumber(step, parsed)
        },
    )
    SetupBodySkipAction(
        step = step,
        vm = vm,
        label = "Sin contexto de peso",
        visible = selectedTrend.isEmpty() && maximum == null,
    )
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// ─── NUTRITION_ACTIVITY ─────────────────────────────────────────────────────

@Composable
private fun NutritionActivityContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_ACTIVITY
    val activity = state.draft.nutritionDraft?.activity
    val selected = state.draft.setupSelectionOf(step, activity?.name)
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        onSelect = { value ->
            setupEnumValueOrNull<EerActivity>(value)?.let { resolved ->
                vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(activity = resolved) } }
            }
            vm.setStepChoice(step, value)
        },
    )
    SetupBodyHint(
        text = "Es tu actividad diaria fuera del entrenamiento: el gasto de las sesiones se calcula aparte con tu calendario.",
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ─── NUTRITION_MANUAL_CALORIES / NUTRITION_MANUAL_CARBS_FAT ────────────────

@Composable
private fun NutritionManualCaloriesContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_MANUAL_CALORIES
    val nutrition = state.draft.nutritionDraft
    SetupBodyHint(text = "Tus valores, no una estimación: dejar un campo vacío es válido.")
    SetupBodyTextField(
        label = "Calorías (kcal)",
        initial = nutrition?.manualCalorieTargetText.orEmpty(),
        keyboardType = KeyboardType.Number,
        onValueChange = { raw ->
            vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(manualCalorieTargetText = raw.trim()) } }
            vm.setStepText(step, raw.trim())
            vm.setStepNumber(step, parseLocalizedNumber(raw)?.takeIf { it.isFinite() && it > 0.0 })
        },
    )
    SetupBodyTextField(
        label = "Proteína (g)",
        initial = nutrition?.manualProteinText.orEmpty(),
        keyboardType = KeyboardType.Number,
        onValueChange = { raw ->
            vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(manualProteinText = raw.trim()) } }
        },
    )
}

@Composable
private fun NutritionManualCarbsFatContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_MANUAL_CARBS_FAT
    val nutrition = state.draft.nutritionDraft
    SetupBodyHint(text = "Tus valores, no una estimación: dejar un campo vacío es válido.")
    SetupBodyTextField(
        label = "Hidratos (g)",
        initial = nutrition?.manualCarbsText.orEmpty(),
        keyboardType = KeyboardType.Number,
        onValueChange = { raw ->
            vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(manualCarbsText = raw.trim()) } }
            vm.setStepText(step, raw.trim())
            vm.setStepNumber(step, parseLocalizedNumber(raw)?.takeIf { it.isFinite() && it >= 0.0 })
        },
    )
    SetupBodyTextField(
        label = "Grasas (g)",
        initial = nutrition?.manualFatText.orEmpty(),
        keyboardType = KeyboardType.Number,
        onValueChange = { raw ->
            vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(manualFatText = raw.trim()) } }
        },
    )
}

// ─── NUTRITION_DISTRIBUTION ─────────────────────────────────────────────────

@Composable
private fun NutritionDistributionContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_DISTRIBUTION
    val distribution = state.draft.nutritionDraft?.weeklyDistribution
    val selected = state.draft.setupSelectionOf(
        step,
        when (distribution) {
            NutritionWeeklyDistributionMode.UNIFORM -> "uniform"
            NutritionWeeklyDistributionMode.VARIABLE -> "variable"
            null -> null
        },
    )
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        subtitles = mapOf(
            "uniform" to "El mismo objetivo todos los días.",
            "variable" to "Más el día de sesión y menos de descanso.",
        ),
        onSelect = { value ->
            setupEnumValueOrNull<NutritionWeeklyDistributionMode>(value)?.let { resolved ->
                vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(weeklyDistribution = resolved) } }
            }
            vm.setStepChoice(step, value)
        },
    )
    SetupBodyHint(
        text = "Los dos repartos mantienen el mismo presupuesto semanal: solo cambia cómo se reparte entre los días.",
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ─── NUTRITION_WEIGH_INS ────────────────────────────────────────────────────

@Composable
private fun NutritionWeighInsContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_WEIGH_INS
    val rows = state.draft.historicalWeighIns.sortedByDescending { it.dateIso }
    var editing: SetupWeighIn? by remember { mutableStateOf(null) }
    var creating by remember { mutableStateOf(false) }
    val target = editing

    if (rows.isEmpty()) {
        SetupBodyHint(text = "Solo datos reales con fecha: la tendencia y el máximo se declaran en el paso anterior.")
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
    ) {
        rows.forEach { row ->
            WeighInRow(
                row = row,
                onEdit = { editing = row },
                onDelete = {
                    vm.updateStep(step) { draft ->
                        draft.copy(historicalWeighIns = draft.historicalWeighIns.filterNot { it.id == row.id })
                    }
                },
            )
        }
    }
    TextButton(
        onClick = { creating = true },
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WizardSpacing.touchTarget),
    ) {
        Text("Añadir pesaje", style = WizardTypography.cardSubtitle, color = WizardColors.text)
    }
    SetupBodySkipAction(
        step = step,
        vm = vm,
        label = "No añadir pesajes",
        visible = rows.isEmpty() && !creating && target == null,
    )

    if (creating || target != null) {
        WeighInDialog(
            initial = target,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { saved ->
                vm.updateStep(step) { draft ->
                    val current = draft.historicalWeighIns
                    val next = if (target == null) current + saved
                    else current.map { if (it.id == target.id) saved else it }
                    draft.copy(historicalWeighIns = next.sortedByDescending { it.dateIso })
                }
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun WeighInRow(row: SetupWeighIn, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = WizardColors.cardFill,
        shape = WizardShapes.card,
        border = BorderStroke(WizardColors.unselectedBorderWidth, WizardColors.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(row.dateIso, style = WizardTypography.cardTitle, color = WizardColors.text)
                Text("${formatWeight(row.weightKg)} kg", style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
            }
            TextButton(onClick = onEdit) { Text("Editar", color = WizardColors.text) }
            TextButton(onClick = onDelete) { Text("Eliminar", color = WizardColors.danger) }
        }
    }
}

@Composable
private fun WeighInDialog(
    initial: SetupWeighIn?,
    onDismiss: () -> Unit,
    onSave: (SetupWeighIn) -> Unit,
) {
    var date by remember { mutableStateOf(initial?.dateIso.orEmpty()) }
    var weight by remember { mutableStateOf(initial?.let { formatWeight(it.weightKg) }.orEmpty()) }
    val today = remember { LocalDate.now() }
    val parsedDate = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }
    val parsedWeight = remember(weight) { parseLocalizedNumber(weight)?.takeIf { it.isFinite() && it in 20.0..500.0 } }
    val dateError = when {
        date.isBlank() -> "Escribe la fecha"
        parsedDate == null -> "Usa el formato AAAA-MM-DD"
        parsedDate.isAfter(today) -> "La fecha no puede ser futura"
        else -> null
    }
    val weightError = when {
        weight.isBlank() -> "Escribe el peso"
        parsedWeight == null -> "Peso entre 20 y 500 kg"
        else -> null
    }
    val valid = parsedDate != null && !parsedDate.isAfter(today) && parsedWeight != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Añadir pesaje" else "Editar pesaje",
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupFormTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = "Fecha (AAAA-MM-DD)",
                    keyboardType = KeyboardType.Ascii,
                )
                SetupFormTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = "Peso (kg)",
                    keyboardType = KeyboardType.Decimal,
                )
                if (date.isNotBlank() && dateError != null) {
                    Text(dateError, style = WizardTypography.caption, color = WizardColors.danger)
                }
                if (weight.isNotBlank() && weightError != null) {
                    Text(weightError, style = WizardTypography.caption, color = WizardColors.danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val dateIso = parsedDate?.toString() ?: return@TextButton
                    val weightKg = parsedWeight ?: return@TextButton
                    onSave(
                        SetupWeighIn(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            dateIso = dateIso,
                            weightKg = weightKg,
                        ),
                    )
                },
            ) { Text("Guardar", color = WizardColors.text) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = WizardColors.textMuted) }
        },
    )
}

// ─── NUTRITION_RESULT ───────────────────────────────────────────────────────

private val macroDatePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

@Composable
private fun NutritionResultContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val preparation = state.nutritionPreparation
    val trackingOnly = state.draft.nutritionDraft?.configurationMode == NutritionConfigurationMode.TRACKING_ONLY

    when {
        trackingOnly -> SetupBodyInfoPanel(
            title = "Solo registro de comidas",
            body = "No hay objetivos ni plan: guardamos lo que comes y nada más.",
        )

        preparation == null -> SetupBodyHint(text = "Preparando tus referencias con tus respuestas…")
        else -> NutritionPreparationSummary(preparation, state)
    }

    NutritionBlockedRemedies(state = state, vm = vm)
    NutritionResultEditActions(state, vm)
}

/**
 * Ruta remedial cuando la ecuación EER no aplica con los datos del usuario
 * (`BLOCKED_EQUATION`): cambiar a **objetivos propios** o a **solo registro**
 * sin exigir la ruta profesional y sin avance automático.
 *
 * El cambio usa el setter del paso (`setStepChoice(NUTRITION_START, …)`), que
 * solo escribe la respuesta y `configurationMode`; después `editStep` lleva
 * explícitamente al paso de inicio, donde la elección se confirma con el único
 * CTA del Host y la ruta condicional se reevalúa (siguiente paso =
 * dirección/cadenas manuales o directo al resultado). Conserva edad, sexo de
 * cálculo, respuestas previas y la meta opcional: no se fabrica ningún dato.
 */
@Composable
private fun NutritionBlockedRemedies(state: SetupWizardState, vm: SetupWizardViewModel) {
    val preparation = state.nutritionPreparation ?: return
    if (preparation.status != NutritionPlanPreparationStatus.BLOCKED_EQUATION) return
    val mode = state.draft.nutritionDraft?.configurationMode

    SetupBodyHint(
        text = "El cálculo automático no puede aplicarse con tus datos todavía. " +
            "Puedes cambiar de enfoque sin perder tus respuestas:",
        modifier = Modifier.padding(top = 4.dp),
    )
    if (mode != NutritionConfigurationMode.SELF_DEFINED) {
        SetupBodyEditAction(
            label = "Objetivos propios: yo fijo calorías y macros",
            onClick = { vm.editWithNutritionMode(SetupStepId.NUTRITION_START, "self_defined") },
        )
    }
    if (mode != NutritionConfigurationMode.TRACKING_ONLY) {
        SetupBodyEditAction(
            label = "Solo registrar comidas, sin objetivos",
            onClick = { vm.editWithNutritionMode(SetupStepId.NUTRITION_START, "tracking_only") },
        )
    }
    SetupBodyCaption(
        text = "Al cambiar de enfoque conservas tu edad, tu sexo de cálculo y tus respuestas; " +
            "el paso de inicio se confirma con Continuar y no se calcula nada por ti.",
    )
}

/**
 * Cambia el modo de nutrición con el setter del paso (sin avanzar, sin
 * confirmar) y lleva al usuario al paso de inicio para validarlo con el único
 * CTA. La selección queda visible y editable en ese paso.
 */
private fun SetupWizardViewModel.editWithNutritionMode(step: SetupStepId, stableValue: String) {
    setStepChoice(step, stableValue)
    editStep(step)
}

@Composable
private fun NutritionPreparationSummary(
    preparation: SetupNutritionPreparationResult,
    state: SetupWizardState,
) {
    val errors = if (preparation.errors.isNotEmpty()) preparation.errors else state.nutritionErrors
    val plan: NutritionPlan? = preparation.plan ?: state.nutritionPlanPreview

    if (errors.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            errors.values.forEach { message ->
                Text(message, style = WizardTypography.bodySmall, color = WizardColors.danger)
            }
        }
    }

    val days = preparation.days.take(7)
    when {
        errors.isNotEmpty() -> Unit
        days.isEmpty() -> SetupBodyHint(text = "Todavía no hay objetivos por fecha.")
        else -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
        ) {
            plan?.let {
                SetupBodyCaption(
                    text = "Referencia diaria: ${it.calorieTarget} kcal · ${it.proteinGoal} g proteína · " +
                        "${it.carbGoal} g hidratos · ${it.fatGoal} g grasas",
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                days.forEach { day ->
                    NutritionDayColumn(day = day, plan = plan, modifier = Modifier.weight(1f))
                }
            }
            MacroLegend()
        }
    }

    if (errors.isEmpty()) {
        SetupBodyHint(text = preparation.status.summary(), modifier = Modifier.padding(top = 4.dp))
    }
    if (preparation.distributionStatus == NutritionDistributionStatus.PROVISIONAL_UNIFORM) {
        SetupBodyCaption(
            text = "Reparto uniforme provisional: alguna sesión del periodo todavía no tiene gasto estimado " +
                "y ese dato no se sustituye por 0.",
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun NutritionPlanPreparationStatus.summary(): String = when (this) {
    NutritionPlanPreparationStatus.READY -> "Referencias listas para activar."
    NutritionPlanPreparationStatus.BLOCKED_EQUATION -> "Faltan datos válidos para calcular tu gasto energético."
    NutritionPlanPreparationStatus.SELF_DEFINED_MANUAL -> "Revisa tus valores: alguno no es válido todavía."
    NutritionPlanPreparationStatus.TRACKING_ONLY -> "Solo registro: sin objetivos que calcular."
}

@Composable
private fun NutritionDayColumn(
    day: NutritionEditorDayTarget,
    plan: NutritionPlan?,
    modifier: Modifier = Modifier,
) {
    val description = "${day.date.format(macroDatePattern)}: ${day.calorieTargetKcal} kcal, " +
        "${day.proteinG} gramos de proteína, ${day.carbsG} gramos de hidratos, ${day.fatG} gramos de grasas"
    Column(
        modifier = modifier.semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = day.date.format(macroDatePattern),
            style = WizardTypography.caption.copy(fontSize = 10.sp),
            color = WizardColors.textFaint,
        )
        Text(
            text = day.calorieTargetKcal.toString(),
            style = WizardTypography.header,
            color = WizardColors.text,
        )
        MacroBar(grams = day.proteinG, base = plan?.proteinGoal, color = WizardBlock.NUTRITION.accent)
        MacroBar(grams = day.carbsG, base = plan?.carbGoal, color = WizardColors.info)
        MacroBar(grams = day.fatG, base = plan?.fatGoal, color = WizardBlock.RINGS.accent)
    }
}

/**
 * Barra de un macro contra la referencia diaria del plan. Sin referencia no se
 * pinta barra (nada de escalas inventadas): solo la cifra exacta en gramos.
 */
@Composable
private fun MacroBar(grams: Int, base: Int?, color: Color) {
    val reference = base?.takeIf { it > 0 }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (reference != null) {
            val fraction = (grams.toFloat() / reference).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(WizardColors.progressTrack, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(5.dp)
                        .background(color, RoundedCornerShape(3.dp)),
                )
            }
        }
        Text(
            text = "$grams g",
            style = WizardTypography.caption.copy(fontSize = 10.sp),
            color = WizardColors.textMuted,
        )
    }
}

@Composable
private fun MacroLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot("Proteína", WizardBlock.NUTRITION.accent, Modifier.weight(1f))
        LegendDot("Hidratos", WizardColors.info, Modifier.weight(1f))
        LegendDot("Grasas", WizardBlock.RINGS.accent, Modifier.weight(1f))
    }
}

@Composable
private fun LegendDot(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Text(label, style = WizardTypography.caption.copy(fontSize = 10.sp), color = WizardColors.textMuted)
    }
}

/** Acciones de edición del resultado: vuelven al paso sin borrar respuestas. */
@Composable
private fun NutritionResultEditActions(state: SetupWizardState, vm: SetupWizardViewModel) {
    val route = SetupStepGraph.stepIds(state.draft.stepContext())
    val targets = listOf(
        SetupStepId.NUTRITION_START to "Editar modo de nutrición",
        SetupStepId.NUTRITION_DIRECTION to "Editar dirección",
        SetupStepId.NUTRITION_ACTIVITY to "Editar actividad",
        SetupStepId.NUTRITION_MANUAL_CALORIES to "Editar calorías y proteína",
        SetupStepId.NUTRITION_DISTRIBUTION to "Editar reparto semanal",
        SetupStepId.NUTRITION_WEIGH_INS to "Editar pesajes",
    ).filter { (step, _) -> step in route }
    if (targets.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        targets.forEach { (step, label) ->
            SetupBodyEditAction(label = label, onClick = { vm.editStep(step) })
        }
    }
}
