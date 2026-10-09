package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanTuningContext
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.domain.nutrition.PlanWarning
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.nutrition.automaticPlanValues
import com.example.kpkn.domain.nutrition.goalDateSpanText
import com.example.kpkn.domain.nutrition.goalWeeklyChangeKg
import com.example.kpkn.domain.nutrition.goalWeeksRange
import com.example.kpkn.domain.nutrition.goalWeightWindow
import com.example.kpkn.domain.nutrition.nutritionGoalActive
import com.example.kpkn.domain.nutrition.nutritionTargetKgOf
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.nutrition.presetSeedValues
import com.example.kpkn.domain.nutrition.recommendedPlanValues
import com.example.kpkn.domain.onboarding.SetupNutritionPreparationResult
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardNutritionDay
import com.example.kpkn.screens.onboarding.design.WizardNutritionGoal
import com.example.kpkn.screens.onboarding.design.WizardNutritionLiveState
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanCallbacks
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanModel
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanPanel
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.WizardWeightRule
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

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

/** Qué significa cada opción de la dirección (sin cifras: lo dice el motor en el resultado). */
internal val nutritionDirectionSubtitles: Map<String, String> = mapOf(
    "deficit" to "Comes un poco menos de lo que gastas para bajar grasa sin perder músculo.",
    "maintenance" to "Comes lo que gastas para sostener tu peso mientras entrenas.",
    "surplus" to "Comes un poco más de lo que gastas para ganar músculo poco a poco.",
)

@Composable
private fun NutritionDirectionContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val step = SetupStepId.NUTRITION_DIRECTION
    val direction = state.draft.nutritionDraft?.direction?.takeIf { it != PlanDirection.PROFESSIONAL }
    val selected = state.draft.setupSelectionOf(step, direction?.name?.lowercase())
    SetupBodyChoiceCards(
        step = step,
        selected = selected,
        subtitles = nutritionDirectionSubtitles,
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
    val nutrition = state.draft.nutritionDraft
    val unit = wizardMassUnitOf(state.draft)
    val currentKg = state.draft.weightKg
    if (currentKg != null) {
        NutritionTargetControl(
            unit = unit,
            currentKg = currentKg,
            direction = nutrition?.direction,
            pace = nutrition?.pacePreset ?: WizardPacePreset.MEDIUM,
            committedKg = nutritionTargetKgOf(nutrition),
            onCommit = { kg -> commitNutritionTarget(vm, step, unit, kg) },
        )
    } else {
        SetupBodyHint(text = "Primero confirma tu peso actual: desde él medimos el objetivo.")
    }
    // `inputTexts` se lee siempre con `step.name`: un texto crudo también cuenta como respuesta ya dada.
    val stored = nutrition?.targetWeightText.orEmpty().ifBlank { state.draft.inputTexts[step.name].orEmpty() }
    SetupBodySkipAction(step = step, vm = vm, label = "No fijar peso objetivo", visible = stored.isBlank())
}

/** Escribe el peso objetivo en el borrador nutricional, en [unit] (la del alta), y lo declara como respuesta del paso. */
private fun commitNutritionTarget(vm: SetupWizardViewModel, step: SetupStepId, unit: WizardMassUnit, kg: Double) {
    val display = WizardWeightScale.snap(WizardWeightScale.toDisplay(kg, unit))
    val text = WizardWeightScale.format(display)
    vm.updateStep(step) { draft -> draft.withNutritionDraft { it.copy(targetWeightText = text, weightUnit = unit.code) } }
    vm.setStepText(step, text)
    vm.setStepNumber(step, display)
}

/**
 * Peso objetivo con la regla de peso de Datos básicos ([WizardWeightRule]), la diferencia con el peso actual y la
 * estimación con el ritmo elegido. Arrastrar solo propone un peso; tocar la cifra lo fija ([onCommit], en kg). Cada
 * propuesta respeta el límite de la dirección ([goalWeightWindow]), que la regla recibe como mínimo y máximo: no se
 * puede arrastrar más allá, así lo que se ve es lo que se guarda.
 */
@Composable
internal fun NutritionTargetControl(
    unit: WizardMassUnit,
    currentKg: Double,
    direction: PlanDirection?,
    pace: WizardPacePreset,
    committedKg: Double?,
    onCommit: (Double) -> Unit,
) {
    val window = remember(direction, currentKg) { goalWeightWindow(direction, currentKg) }
    // La posición de arranque es el objetivo guardado o el peso actual (en el límite si queda fuera): no es una
    // respuesta hasta que se fija.
    val ruleAnchorKg = remember(unit) { committedKg ?: currentKg }
    var proposalKg by remember(unit) { mutableStateOf<Double?>(null) }
    var pending by remember(unit) { mutableStateOf(false) }
    val shownKg = proposalKg ?: committedKg

    val estimate = shownKg?.let { value ->
        goalWeeklyChangeKg(direction, pace, currentKg)?.let { weekly -> goalWeeksRange(currentKg, value, weekly) }
    }?.let { goalWeeksText(it, pace) }
    val hint = when {
        pending -> "Toca la cifra para fijar este objetivo."
        shownKg == null -> window?.let {
            "Desliza la regla y toca la cifra para fijarlo: entre ${goalDisplayText(it.start, unit)} y " +
                "${goalDisplayText(it.endInclusive, unit)} ${unit.code}."
        }
        else -> null
    }

    Column(modifier = Modifier.fillMaxWidth().testTag("setup-nutrition-target")) {
        WizardWeightRule(
            unit = unit,
            valueKg = ruleAnchorKg,
            min = window?.start,
            max = window?.endInclusive,
            onValueChange = { kg ->
                // La regla no sale del límite; el coerce solo absorbe el error de coma flotante del borde.
                proposalKg = window?.let { kg.coerceIn(it) } ?: kg
                pending = true
            },
            onConfirm = {
                proposalKg?.let { kg ->
                    onCommit(kg)
                    pending = false
                }
            },
        )
        if (shownKg != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = goalDifferenceText(shownKg, currentKg, unit),
                style = WizardTypography.controlValue,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (estimate != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = estimate,
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Un peso en la unidad del alta, sin el código (p. ej. «79,5»). */
private fun goalDisplayText(kg: Double, unit: WizardMassUnit): String =
    WizardWeightScale.format(WizardWeightScale.snap(WizardWeightScale.toDisplay(kg, unit)))

/** La diferencia con el peso actual: «−8,5 kg desde tus 80 kg»; «Igual que tu peso actual» si no cambia. */
private fun goalDifferenceText(targetKg: Double, currentKg: Double, unit: WizardMassUnit): String {
    val delta = WizardWeightScale.snap(
        WizardWeightScale.toDisplay(targetKg, unit) - WizardWeightScale.toDisplay(currentKg, unit),
    )
    if (delta == 0.0) return "Igual que tu peso actual"
    val sign = if (delta < 0) "−" else "+"
    return "$sign${WizardWeightScale.format(abs(delta))} ${unit.code} desde tus " +
        WizardWeightScale.formatWithUnit(currentKg, unit)
}

/** «≈ 18–24 semanas a ritmo medio», con la estimación del motor. */
private fun goalWeeksText(range: IntRange, pace: WizardPacePreset): String {
    val count = if (range.first == range.last) "${range.first}" else "${range.first}–${range.last}"
    val word = if (range.last == 1) "semana" else "semanas"
    val rhythm = when (pace) {
        WizardPacePreset.SLOW -> "a ritmo suave"
        WizardPacePreset.MEDIUM -> "a ritmo medio"
        WizardPacePreset.FAST -> "a ritmo rápido"
    }
    return "≈ $count $word $rhythm"
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
    // El subtítulo del paso ya dice «tus valores reales»: aquí solo lo que añade.
    SetupBodyHint(text = "Dejar un campo vacío es válido.")
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
    // El subtítulo del paso ya dice «tus valores reales»: aquí solo lo que añade.
    SetupBodyHint(text = "Dejar un campo vacío es válido.")
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
    SetupBodyHint(
        text = "Hacen falta unas dos semanas de pesajes y comidas para el primer ajuste.",
        modifier = Modifier.padding(top = 4.dp),
    )
    SetupBodySkipAction(
        step = step,
        vm = vm,
        label = "No añadir pesajes",
        visible = rows.isEmpty() && !creating && target == null,
    )

    if (creating || target != null) {
        WeighInDialog(
            initial = target,
            existing = rows,
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
    existing: List<SetupWeighIn>,
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
        weighInDateTaken(existing, parsedDate.toString(), initial?.id) -> "Ya hay un pesaje ese día."
        else -> null
    }
    val weightError = when {
        weight.isBlank() -> "Escribe el peso"
        parsedWeight == null -> "Peso entre 20 y 500 kg"
        else -> null
    }
    val valid = parsedDate != null && !parsedDate.isAfter(today) && parsedWeight != null && dateError == null

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

/**
 * Tu plan de alimentación: anillos, ritmo y un deslizador por macro ([WizardNutritionPlanPanel]).
 *
 * Lo que se ve es exactamente lo que se activa: el panel trabaja sobre un plan «en vivo» (los anillos
 * responden al instante) y SOLO al soltar escribe en el borrador los cuatro números manuales (kcal y
 * gramos); la preparación real los recalcula y el activar usa esa misma preparación. Si el plan vuelve a
 * ser el que el motor recomienda por sí solo, los campos manuales se vacían y el plan sigue siendo
 * automático. Nada de «Editar …»: las filas de los pasos anteriores ya permiten volver.
 */
@Composable
private fun NutritionResultContent(state: SetupWizardState, vm: SetupWizardViewModel) {
    val preparation = state.nutritionPreparation
    val trackingOnly = state.draft.nutritionDraft?.configurationMode == NutritionConfigurationMode.TRACKING_ONLY

    when {
        trackingOnly -> SetupBodyHint(text = "Solo registro: sin objetivos ni plan.")
        preparation == null -> SetupBodyHint(text = "Preparando tu plan…")
        else -> NutritionPlanBody(state, vm, preparation)
    }

    NutritionBlockedRemedies(state = state, vm = vm)
}

/**
 * Ruta remedial cuando la ecuación EER no aplica con los datos del usuario
 * (`BLOCKED_EQUATION`): cambiar a **objetivos propios** o a **solo registro**
 * sin exigir la ruta profesional y sin avance automático. Los errores concretos ya los pinta
 * [NutritionPlanBody] en rojo; aquí solo una frase corta y los dos caminos.
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
        text = "No podemos calcularlo con tus datos.",
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

/** Errores reales de validación en rojo (una línea cada uno), el plan si lo hay y el aviso de reparto provisional. */
@Composable
private fun NutritionPlanBody(
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    preparation: SetupNutritionPreparationResult,
) {
    val errors = if (preparation.errors.isNotEmpty()) preparation.errors else state.nutritionErrors
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

    when {
        errors.isNotEmpty() -> Unit
        preparation.plan == null -> SetupBodyHint(text = "Todavía no hay un plan que mostrar.")
        else -> NutritionPlanEditor(state, vm, preparation)
    }

    if (preparation.distributionStatus == NutritionDistributionStatus.PROVISIONAL_UNIFORM) {
        SetupBodyCaption(
            text = "Reparto uniforme provisional: falta estimar el gasto de alguna sesión.",
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Cuánto se espera a que el borrador confirme una escritura antes de volver a lo confirmado. */
private const val PLAN_WRITE_TIMEOUT_MS = 2_000L

@Composable
private fun NutritionPlanEditor(
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    preparation: SetupNutritionPreparationResult,
) {
    val step = SetupStepId.NUTRITION_RESULT
    val nutrition = state.draft.nutritionDraft
    val mode = nutrition?.configurationMode ?: NutritionConfigurationMode.AUTOMATIC
    val active = state.currentStep == step

    // Objetivos propios: no hay recomendación a la que volver; «restablecer» devuelve lo que había al abrir.
    val plan = preparation.plan
    val planValues = plan?.let { PlanValues(it.calorieTarget, it.proteinGoal, it.carbGoal, it.fatGoal) }
    var arrival by remember { mutableStateOf<PlanValues?>(null) }
    LaunchedEffect(planValues) { if (arrival == null) arrival = planValues }

    val committed = remember(preparation, nutrition, arrival, state.draft.weightKg) {
        nutritionPlanTuningOf(state, arrival)
    } ?: return

    val session = remember(state.draft.draftId) { WizardNutritionLiveState(committed) }
    LaunchedEffect(committed) { session.adopt(committed) }
    // Una escritura que el borrador no confirma (se descartó) no deja la pantalla enseñando otra cosa.
    val latestCommitted by rememberUpdatedState(committed)
    LaunchedEffect(session.pending) {
        if (session.pending != null) {
            delay(PLAN_WRITE_TIMEOUT_MS)
            session.expirePending(latestCommitted)
        }
    }

    val write: (PlanTuning) -> Unit = remember(vm, mode) {
        { tuning ->
            val clear = mode == NutritionConfigurationMode.AUTOMATIC &&
                tuning.values == automaticPlanValues(tuning.context)
            vm.updateStep(step) { draft -> draft.withNutritionDraft { it.withPlanValues(tuning.values, clear) } }
        }
    }

    // El motor no lee el ritmo del paso anterior: si difiere del que aplica por sí solo, se siembra al abrir
    // para que lo que se ve y lo que se activa sean lo mismo.
    val seed = remember(committed, nutrition) { nutritionPresetSeed(nutrition, committed) }
    LaunchedEffect(active, seed) {
        if (active && seed != null) {
            vm.updateStep(step) { draft -> draft.withNutritionDraft { it.withPlanValues(seed, clearManual = false) } }
        }
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val days = remember(preparation.days, nutrition?.weeklyDistribution) {
        nutritionStripDays(preparation, nutrition?.weeklyDistribution, LocalDate.now())
    }
    LaunchedEffect(days.size) { if (selectedDay != null && selectedDay !in days.indices) selectedDay = null }

    val callbacks = remember(session, write) {
        WizardNutritionPlanCallbacks(
            onProtein = { grams -> selectedDay = null; session.edit { it.withProtein(grams) } },
            onCarbs = { grams -> selectedDay = null; session.edit { it.withCarbs(grams) } },
            onFat = { grams -> selectedDay = null; session.edit { it.withFat(grams) } },
            onPaceRate = { rate -> selectedDay = null; session.edit { it.withPaceRate(rate) } },
            onPacePreset = { preset -> selectedDay = null; session.commitNow { it.withPreset(preset) }?.let(write) },
            onChangeFinished = { session.finishDrag()?.let(write) },
            onReset = { selectedDay = null; session.commitNow { it.reset() }?.let(write) },
            onSelectDay = { index -> selectedDay = index },
        )
    }

    WizardNutritionPlanPanel(
        model = WizardNutritionPlanModel(
            tuning = session.live,
            days = days,
            selectedDay = selectedDay,
            editable = true,
            active = active,
            dragging = session.dragging,
            resetLabel = if (mode == NutritionConfigurationMode.AUTOMATIC) {
                "Restablecer a la recomendación"
            } else {
                "Restablecer a lo que traías"
            },
            goal = nutritionGoalOf(state),
        ),
        callbacks = callbacks,
    )
}

// ─── Lógica pura del resultado (también la usa el ViewModel y las pruebas) ─────────────────────────

/**
 * Escribe un plan en el borrador. Con [clearManual] vacía los cuatro campos manuales (el plan vuelve a ser el
 * automático del motor, con su procedencia); si no, los fija: el borrador los traduce a una base manual
 * ([com.example.kpkn.domain.nutrition.editorDraftOf]) y la preparación real devuelve exactamente esos números.
 */
internal fun NutritionWizardDraft.withPlanValues(values: PlanValues, clearManual: Boolean): NutritionWizardDraft =
    if (clearManual) {
        copy(manualCalorieTargetText = "", manualProteinText = "", manualCarbsText = "", manualFatText = "")
    } else {
        copy(
            manualCalorieTargetText = values.kcal.toString(),
            manualProteinText = values.proteinG.toString(),
            manualCarbsText = values.carbsG.toString(),
            manualFatText = values.fatG.toString(),
        )
    }

/**
 * Datos del plan que no cambian al afinar. El peso es el que usó el motor (su foto de entradas) para que los
 * g/kg y el ritmo cuadren con el plan que se ve; el sexo es el de la ecuación.
 */
internal fun nutritionTuningContextOf(
    state: SetupWizardState,
    preparation: SetupNutritionPreparationResult,
    direction: PlanDirection?,
): PlanTuningContext {
    val recommendation = preparation.recommendation
    val engineWeight = recommendation?.snapshot?.inputs?.get("weightKg")?.toDoubleOrNull()
    return PlanTuningContext(
        weightKg = engineWeight ?: state.draft.weightKg,
        eerKcal = recommendation?.eerKcal,
        direction = direction,
        sex = state.draft.nutritionDraft?.equationSex,
    )
}

/**
 * El plan afinable del estado, o null si no hay (solo registro, errores, sin preparación o pauta
 * profesional, que no se afina aquí). [arrival] es lo que había al abrir: la base de «restablecer» en
 * objetivos propios (sin recomendación a la que volver).
 */
internal fun nutritionPlanTuningOf(state: SetupWizardState, arrival: PlanValues? = null): PlanTuning? {
    val nutrition = state.draft.nutritionDraft ?: return null
    if (nutrition.configurationMode == NutritionConfigurationMode.TRACKING_ONLY) return null
    val preparation = state.nutritionPreparation ?: return null
    val plan = preparation.plan ?: return null
    val direction = plan.direction ?: nutrition.direction
    if (direction == PlanDirection.PROFESSIONAL) return null
    val context = nutritionTuningContextOf(state, preparation, direction)
    val values = PlanValues(plan.calorieTarget, plan.proteinGoal, plan.carbGoal, plan.fatGoal)
    val baseline = when (nutrition.configurationMode) {
        NutritionConfigurationMode.AUTOMATIC -> recommendedPlanValues(context, nutrition.pacePreset)
        else -> null
    } ?: arrival ?: values
    return PlanTuning(context, baseline, values)
}

/**
 * Valores con los que sembrar el borrador al abrir el resultado: solo en automático, sin ediciones y cuando
 * el ritmo elegido en el paso anterior no es el que el motor aplica por sí solo.
 */
internal fun nutritionPresetSeed(nutrition: NutritionWizardDraft?, committed: PlanTuning): PlanValues? {
    if (nutrition == null || nutrition.configurationMode != NutritionConfigurationMode.AUTOMATIC) return null
    val untouched = nutrition.manualCalorieTargetText.isBlank() && nutrition.manualProteinText.isBlank() &&
        nutrition.manualCarbsText.isBlank() && nutrition.manualFatText.isBlank()
    if (!untouched) return null
    return presetSeedValues(committed.context, nutrition.pacePreset)
}

/** Franja de días: solo con reparto variable y objetivos distintos entre los días; 7 como mucho. */
internal fun nutritionStripDays(
    preparation: SetupNutritionPreparationResult,
    distribution: NutritionWeeklyDistributionMode?,
    today: LocalDate,
): List<WizardNutritionDay> {
    if (distribution != NutritionWeeklyDistributionMode.VARIABLE) return emptyList()
    val days = preparation.days.sortedBy { it.date }.take(7)
    if (days.size < 2 || days.map { it.calorieTargetKcal }.distinct().size < 2) return emptyList()
    return days.map { day ->
        WizardNutritionDay(
            date = day.date,
            kcal = day.calorieTargetKcal,
            proteinG = day.proteinG,
            carbsG = day.carbsG,
            fatG = day.fatG,
            isToday = day.date == today,
        )
    }
}

/** Texto de «Continuar» rechazado por un plan con calorías peligrosamente bajas. */
internal const val NUTRITION_LOW_CALORIES_MESSAGE = "Sube las calorías para poder continuar"

/** Texto de «Continuar» rechazado por un ritmo de pérdida extremo. */
internal const val NUTRITION_EXTREME_PACE_MESSAGE = "Elige un ritmo más suave para poder continuar"

/**
 * Puerta de «Continuar» en el resultado de nutrición: con un plan que tiene `hardStop` (calorías por debajo del
 * umbral duro o una pérdida extrema, los mismos criterios que [com.example.kpkn.domain.nutrition.buildNutritionRiskFlags])
 * el paso no avanza. Función pura para probarla sin montar el ViewModel; sin plan afinable (solo registro,
 * errores, pauta profesional) no añade nada.
 */
internal fun nutritionResultGate(state: SetupWizardState, step: SetupStepId): Map<String, String> {
    if (step != SetupStepId.NUTRITION_RESULT) return emptyMap()
    val tuning = nutritionPlanTuningOf(state) ?: return emptyMap()
    if (!tuning.hardStop) return emptyMap()
    val message = if (PlanWarning.LOW_CALORIES_HARD in tuning.warnings) {
        NUTRITION_LOW_CALORIES_MESSAGE
    } else {
        NUTRITION_EXTREME_PACE_MESSAGE
    }
    return mapOf("nutritionPlan" to message)
}

// ─── Lógica pura del peso objetivo, los pesajes y el hito ─────────────────────────────────────

/** Unidad del peso del alta (la del borrador): la regla y el texto del objetivo van en ella. */
internal fun wizardMassUnitOf(draft: SetupWizardDraft): WizardMassUnit =
    if (draft.weightUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG

/** Ya hay otro pesaje con esa fecha: no se admiten dos el mismo día (el que se edita no cuenta). */
internal fun weighInDateTaken(existing: List<SetupWeighIn>, dateIso: String, editingId: String?): Boolean =
    existing.any { it.dateIso == dateIso && it.id != editingId }

/** Qué celebración cierra el bloque de nutrición: la de la meta o la de siempre. */
internal enum class NutritionMilestoneVariant { STANDARD, GOAL }

/** Con meta de peso (Definir o Volumen) el hito de nutrición es «Objetivo establecido»; si no, el de siempre. */
internal fun nutritionMilestoneVariant(draft: SetupWizardDraft): NutritionMilestoneVariant {
    val nutrition = draft.nutritionDraft ?: return NutritionMilestoneVariant.STANDARD
    if (nutrition.configurationMode == NutritionConfigurationMode.TRACKING_ONLY) return NutritionMilestoneVariant.STANDARD
    return if (nutritionGoalActive(nutrition.direction, nutritionTargetKgOf(nutrition))) {
        NutritionMilestoneVariant.GOAL
    } else {
        NutritionMilestoneVariant.STANDARD
    }
}

/** La meta del plan («Hasta tu objetivo»): el peso de hoy y el objetivo guardado; null si falta uno de los dos. */
internal fun nutritionGoalOf(state: SetupWizardState): WizardNutritionGoal? {
    val targetKg = nutritionTargetKgOf(state.draft.nutritionDraft) ?: return null
    val currentKg = state.draft.weightKg ?: return null
    return WizardNutritionGoal(currentKg = currentKg, targetKg = targetKg, unit = wizardMassUnitOf(state.draft))
}

/** «Fecha estimada: entre marzo y abril de 2027» del hito con meta, con el ritmo que se activa; null si no hay fecha. */
internal fun nutritionGoalNoteOf(state: SetupWizardState): String? {
    val goal = nutritionGoalOf(state) ?: return null
    val weeklyKg = nutritionPlanTuningOf(state)?.weeklyChangeKg ?: return null
    val span = goalDateSpanText(goal.currentKg, goal.targetKg, weeklyKg) ?: return null
    return "Fecha estimada: $span"
}
