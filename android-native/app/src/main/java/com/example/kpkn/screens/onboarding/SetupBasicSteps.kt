package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.onboarding.SetupControlKind
import com.example.kpkn.domain.onboarding.SetupOptionDefinition
import com.example.kpkn.domain.onboarding.SetupStepDefinition
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.design.WizardBodyFatSource
import com.example.kpkn.screens.onboarding.design.WizardBodyFatValue
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardAnthropometryLayout
import com.example.kpkn.screens.onboarding.design.WizardHeightRule
import com.example.kpkn.screens.onboarding.design.WizardHeightUnit
import com.example.kpkn.screens.onboarding.design.WizardHeightUnitToggle
import com.example.kpkn.screens.onboarding.design.WizardHeightWheel
import com.example.kpkn.screens.onboarding.design.currentAnthropometryLayout
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardMassUnitToggle
import com.example.kpkn.screens.onboarding.design.WizardPhysiqueSelector
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.WizardWeightRule

/**
 * Bloque 1 «Datos básicos»: nombre, edad, altura, peso, sexo de cálculo y
 * grasa corporal actual, más el paso de hito (la raíz lo resuelve antes de aquí).
 *
 * Reglas del bloque:
 * - Un control por pregunta, como máximo dos entradas relacionadas en pantalla
 *   (unidad + rueda/regla, fuente + valor).
 * - Los campos escriben **solo** por los setters del contrato
 *   (`setStepText`, `setStepNumber`, `setStepChoice(s)`, `updateStep`,
 *   `skipStep`); aquí no se confirma ni se avanza: eso lo decide el CTA del Host.
 * - La posición inicial de la rueda, la regla o el slider **no** es respuesta:
 *   solo la emite una interacción real o la confirmación explícita del propio
 *   control. No se declara ningún valor en montaje ni se añade un segundo CTA.
 * - «Omitir» aparece únicamente si `definition.allowSkip` lo declara.
 * - La figura de la grasa (hombre/mujer) es una referencia visual: nunca
 *   escribe `equationSex` ni plantea una pregunta de identidad.
 * - La edad admite tecleo numérico crudo: el intermedio («1» de «19») se
 *   conserva en el campo mientras el valor canónico sigue sin declararse.
 */
@Composable
fun SetupBasicsStepContent(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    val definition = SetupStepDefinitions.of(step) ?: return
    when (step) {
        SetupStepId.NAME -> SetupNameField(step = step, state = state, vm = vm, definition = definition)
        SetupStepId.AGE -> SetupAgeField(step = step, state = state, vm = vm, definition = definition)
        SetupStepId.HEIGHT -> when (currentAnthropometryLayout()) {
            WizardAnthropometryLayout.Combined -> SetupAnthropometryPair(state = state, vm = vm)
            WizardAnthropometryLayout.Separate -> SetupHeightControl(step = step, state = state, vm = vm)
            WizardAnthropometryLayout.Pending -> Unit
        }
        SetupStepId.WEIGHT -> SetupWeightControl(step = step, state = state, vm = vm)
        SetupStepId.EQUATION_SEX -> SetupEquationSexControl(step = step, state = state, vm = vm, definition = definition)
        SetupStepId.BODY_FAT -> SetupBodyFatControl(step = step, state = state, vm = vm, definition = definition)
        // Pasos legacy que solo se leen (identidad de género): se pintan desde el
        // catálogo, nunca desde una pregunta de WizChat.
        else -> SetupCatalogStep(step = step, state = state, vm = vm, definition = definition)
    }
}

// ─── Nombre y edad ───────────────────────────────────────────────────────────

@Composable
private fun SetupNameField(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    definition: SetupStepDefinition,
) {
    var text by rememberSaveable(state.draft.draftId, state.draft.commitId, step) {
        mutableStateOf(state.draft.inputTexts[step.name] ?: state.draft.name)
    }
    SetupFormTextField(
        value = text,
        onValueChange = { raw -> text = raw; vm.setStepText(step, raw) },
        label = "Nombre",
        testTag = "setup-name",
    )
    if (definition.allowSkip) SetupFormSkipAction(onSkip = { vm.skipStep(step) })
}

/** Edad con tecleo crudo: el intermedio se conserva en el campo Y en `inputTexts`. */
@Composable
private fun SetupAgeField(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    definition: SetupStepDefinition,
) {
    var raw by rememberSaveable(state.draft.draftId, state.draft.commitId, step) {
        mutableStateOf(state.draft.inputTexts[step.name] ?: state.draft.ageYears?.toString().orEmpty())
    }
    SetupFormTextField(
        value = raw,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(3)
            raw = digits
            vm.setStepText(step, digits)
            vm.setStepNumber(step, digits.toIntOrNull()?.toDouble())
        },
        label = definition.unit?.let { "Edad ($it)" } ?: "Edad",
        keyboardType = KeyboardType.Number,
    )
    SetupFormCaption("Entre 13 y 100 años.")
}

// ─── Altura y peso ───────────────────────────────────────────────────────────

/**
 * Altura con rueda vertical de cinco filas. La unidad vive en el borrador
 * (`draft.heightUnit`) y persiste entre sesiones; alternarla solo reexpresa el
 * mismo valor canónico (cm), sin acumular error de conversión.
 *
 * El toggle de unidad lo pinta la cabecera fija del paso (altura/peso): aquí
 * solo queda la rueda, que el scaffold centra en el espacio restante.
 */
@Composable
private fun SetupHeightControl(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    WizardHeightWheel(
        unit = state.draft.heightUnit.toWizardHeightUnit(),
        cm = state.draft.heightCm?.toInt(),
        onValueChange = { cm -> vm.setStepNumber(step, cm.toDouble()) },
    )
}

/**
 * Altura y peso en la misma pantalla, cada uno con su regla horizontal y su
 * unidad. Un solo CTA (el del scaffold). Arrastrar no avanza y la posición
 * inicial de cada regla no se escribe hasta que el usuario la mueve o la confirma.
 */
@Composable
private fun SetupAnthropometryPair(state: SetupWizardState, vm: SetupWizardViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Altura", style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
        WizardHeightUnitToggle(
            selected = state.draft.heightUnit.toWizardHeightUnit(),
            onSelected = { target ->
                vm.updateStep(SetupStepId.HEIGHT) { draft -> draft.copy(heightUnit = target.wizardHeightCode) }
            },
        )
        WizardHeightRule(
            unit = state.draft.heightUnit.toWizardHeightUnit(),
            cm = state.draft.heightCm?.toInt(),
            onValueChange = { cm -> vm.setStepNumber(SetupStepId.HEIGHT, cm.toDouble()) },
        )
        Text(text = "Peso", style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
        WizardMassUnitToggle(
            selected = if (state.draft.weightUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG,
            onSelected = { target -> vm.setWeightUnit(target.code) },
        )
        WizardWeightRule(
            unit = if (state.draft.weightUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG,
            valueKg = state.draft.weightKg,
            onValueChange = { kg -> vm.setStepNumber(SetupStepId.WEIGHT, kg) },
        )
    }
}

/** Peso con regla horizontal y cursor verde; el canónico siempre es kg. */
@Composable
private fun SetupWeightControl(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    WizardWeightRule(
        unit = if (state.draft.weightUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG,
        valueKg = state.draft.weightKg,
        onValueChange = { kg -> vm.setStepNumber(step, kg) },
    )
}

/**
 * Toggle de unidad de los pasos de control centrado (altura/peso). Se emite en
 * la cabecera fija, justo bajo la pregunta, para que la rueda o la regla se
 * centren en el espacio que queda debajo.
 */
@Composable
fun SetupStepUnitToggle(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    when (step) {
        SetupStepId.HEIGHT -> {
            val unit = state.draft.heightUnit.toWizardHeightUnit()
            WizardHeightUnitToggle(
                selected = unit,
                onSelected = { target ->
                    vm.updateStep(step) { draft -> draft.copy(heightUnit = target.wizardHeightCode) }
                },
            )
        }

        SetupStepId.WEIGHT -> {
            val unit = if (state.draft.weightUnit == "lb") WizardMassUnit.LB else WizardMassUnit.KG
            WizardMassUnitToggle(
                selected = unit,
                onSelected = { target -> vm.setWeightUnit(target.code) },
            )
        }

        else -> Unit
    }
}

// ─── Sexo de cálculo ─────────────────────────────────────────────────────────

/**
 * Sexo de cálculo: dos valores del catálogo más «No lo sé», que solo puede
 * continuar cuando la nutrición se prepara a mano (no hay ecuación que
 * completar). No se pregunta identidad de género en ningún caso.
 */
@Composable
private fun SetupEquationSexControl(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    definition: SetupStepDefinition,
) {
    val draft = state.draft
    val selected = draft.selectedValues(step).firstOrNull()
        ?: when (draft.nutritionDraft?.equationSex) {
            EerSex.FEMALE -> "female"
            EerSex.MALE -> "male"
            null -> null
        }
    val options = if (definition.options.any { it.value == EQUATION_SEX_UNKNOWN }) {
        definition.options
    } else {
        definition.options + SetupOptionDefinition(EQUATION_SEX_UNKNOWN, "No lo sé")
    }
    SetupFormChoiceCards(
        options = options,
        isSelected = { it == selected },
        onOptionClick = { value -> vm.setStepChoice(step, value) },
    )
    SetupFormCaption("Si traes tus propios números de nutrición, puedes dejarlo sin determinar.")
}

private const val EQUATION_SEX_UNKNOWN = "unknown"

// ─── Grasa corporal actual ───────────────────────────────────────────────────

/**
 * Grasa actual en una sola pantalla: la figura y el slider aparecen de entrada.
 * La medición exacta es un campo opcional. Mover el slider estima; escribir
 * el porcentaje la sustituye. No hace falta elegir antes entre tres tarjetas.
 * La figura no toca `equationSex`.
 */
@Composable
private fun SetupBodyFatControl(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    definition: SetupStepDefinition,
) {
    var showExact by rememberSaveable(state.draft.draftId, step) {
        mutableStateOf(state.draft.bodyFatSource == SetupBodyFatSource.MEASURED ||
            !state.draft.inputTexts[step.name].isNullOrBlank())
    }
    SetupVisualBodyFat(step = step, state = state, vm = vm, measuredOpen = showExact)
    Text(
        text = if (showExact) "Ocultar medición exacta" else "Tengo una medición exacta",
        style = WizardTypography.cardSubtitle,
        color = WizardColors.text,
        modifier = Modifier
            .clip(WizardShapes.pill)
            .clickable { showExact = !showExact }
            .semantics { role = Role.Button }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
    if (showExact) SetupMeasuredBodyFatField(step = step, state = state, vm = vm)
    if (definition.allowSkip) SetupFormSkipAction(onSkip = { vm.skipStep(step) })
}

/**
 * Medido: el texto crudo se persiste **tal cual se escribió** (`setStepText`,
 * sin filtrar: un valor inválido o incompleto se ve y lo señala la
 * validación), y el canónico sale del parseo (`setStepNumber`) sin recortar
 * rangos aquí — el parser y el rango los controla el reducer de M1.
 */
@Composable
private fun SetupMeasuredBodyFatField(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    var raw by rememberSaveable(state.draft.draftId, state.draft.commitId, step) {
        mutableStateOf(state.draft.inputTexts[step.name] ?: state.draft.bodyFatPercent?.let(::formatPercent).orEmpty())
    }
    SetupFormTextField(
        value = raw,
        onValueChange = { input ->
            raw = input
            if (input.isNotBlank()) vm.setStepChoice(step, SetupBodyFatSource.MEASURED.name)
            vm.setStepText(step, input)
            vm.setStepNumber(step, input.replace(',', '.').toDoubleOrNull())
        },
        label = "Grasa corporal actual (%)",
        keyboardType = KeyboardType.Decimal,
    )
    SetupFormCaption("Entre 3 % y 60 %.")
}

/**
 * Visual: valor real del slider (`setStepNumber`) + fuente VISUAL
 * (`setStepChoice`). Figura y posición se persisten en el borrador a través de
 * los callbacks controlados del selector (`model`/`sliderPosition`), sin tocar
 * la ejecución ni `equationSex`.
 */
@Composable
private fun SetupVisualBodyFat(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    measuredOpen: Boolean,
) {
    val measuredActive = measuredOpen && !state.draft.inputTexts[step.name].isNullOrBlank()
    WizardPhysiqueSelector(
        candidate = state.draft.bodyFatPercent?.let {
            WizardBodyFatValue(percent = it, source = WizardBodyFatSource.VISUAL_ESTIMATE)
        },
        onCandidateChange = { value ->
            if (measuredActive) return@WizardPhysiqueSelector
            vm.setStepChoice(step, SetupBodyFatSource.VISUAL_ESTIMATE.name)
            vm.setStepNumber(step, value.percent)
        },
        model = state.draft.physiqueModel,
        sliderPosition = state.draft.physiqueSliderPosition,
        onModelChange = { model ->
            vm.updateStep(step) { draft -> draft.copy(physiqueModel = model) }
        },
        onSliderPositionChange = { position ->
            vm.updateStep(step) { draft -> draft.copy(physiqueSliderPosition = position) }
        },
    )
}

private fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value).replace('.', ',')

// ─── Pasos legacy solo lectura ───────────────────────────────────────────────

/**
 * Pasos fuera de la ruta productiva (identidad de género, sexo legacy): se
 * pintan con las opciones del catálogo y el mismo contrato de selección; nunca
 * se lee `WizChatGraph` ni se llama a `answerChoice`/`answerMulti`.
 */
@Composable
private fun SetupCatalogStep(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
    definition: SetupStepDefinition,
) {
    val selected = state.draft.selectedValues(step)
    if (definition.control == SetupControlKind.MULTI_CHOICE) {
        SetupFormChoiceCards(
            options = definition.options,
            isSelected = { it in selected },
            // El toggle se resuelve en el VM sobre el último borrador (mismo
            // contrato que las demás multitarjetas): el eco de la UI no cuenta.
            onOptionClick = { value -> vm.toggleStepChoice(step, value) },
        )
    } else {
        SetupFormChoiceCards(
            options = definition.options,
            isSelected = { it in selected },
            onOptionClick = { value -> vm.setStepChoice(step, value) },
        )
    }
    if (definition.allowSkip) SetupFormSkipAction(onSkip = { vm.skipStep(step) })
}

// ─── Unidades de altura ──────────────────────────────────────────────────────

/** `draft.heightUnit` es texto persistido (`cm`/`ft`); se toleran alias reales. */
private fun String.toWizardHeightUnit(): WizardHeightUnit = when (this.lowercase()) {
    "ft", "ft_in", "ftin", "in", "imperial" -> WizardHeightUnit.FT_IN
    else -> WizardHeightUnit.CM
}

private val WizardHeightUnit.wizardHeightCode: String
    get() = if (this == WizardHeightUnit.FT_IN) "ft" else "cm"
