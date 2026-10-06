package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.bodyFatForSliderPos
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
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.WizardWeightRule
import kotlin.math.roundToInt

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
        SetupStepId.NAME, SetupStepId.AGE -> SetupAliasAndAge(state = state, vm = vm)
        SetupStepId.HEIGHT -> SetupAnthropometryPair(state = state, vm = vm)
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
private fun SetupAliasAndAge(
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    var text by rememberSaveable(state.draft.draftId, state.draft.commitId) {
        mutableStateOf(state.draft.inputTexts[SetupStepId.NAME.name] ?: state.draft.name)
    }
    val fontSize = (32f - text.length * 0.35f).coerceIn(20f, 28f).sp
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    NumberedPrompt("1. Alias", "¿Cómo quieres que te llamemos?")
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val next = raw.take(32)
            text = next
            vm.setStepText(SetupStepId.NAME, next)
        },
        textStyle = WizardTypography.question.copy(
            color = WizardColors.text,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(WizardColors.text),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup-name"),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (text.isEmpty()) {
                    Text(
                        text = "Pon tu alias",
                        color = WizardColors.textFaint,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
    NumberedPrompt("2. Fecha de nacimiento", null)
    BirthDateField(state = state, vm = vm)
    GenderPrompt()
    GenderSymbolRow(
        selected = state.draft.selectedValues(SetupStepId.EQUATION_SEX).firstOrNull(),
        onSelect = { value -> vm.setStepChoice(SetupStepId.EQUATION_SEX, value) },
    )
    }
}

private const val GENDER_SUBTITLE =
    "Conocer tu género nos permitirá calcular el gasto energético que te corresponde. Esto es muy importante para calcular tus calorías recomendadas para tu plan de nutrición. Si no tienes una opción que te identifique, elige la que más se apegue a tu contexto hormonal."

private enum class GenderMark { FEMALE, MALE, TRANS_MALE, TRANS_FEMALE }

private data class GenderChoice(val value: String, val label: String, val mark: GenderMark)

private val genderChoices = listOf(
    GenderChoice("female", "Mujer", GenderMark.FEMALE),
    GenderChoice("male", "Hombre", GenderMark.MALE),
    GenderChoice("trans_male", "Hombre trans", GenderMark.TRANS_MALE),
    GenderChoice("trans_female", "Mujer trans", GenderMark.TRANS_FEMALE),
)

@Composable
private fun NumberedPrompt(indexLabel: String, caption: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(indexLabel, color = WizardColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (caption != null) {
            Text(
                caption,
                color = WizardColors.textMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GenderPrompt() {
    var info by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("3. Género", color = WizardColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("Elige tu género", color = WizardColors.textMuted, fontSize = 14.sp)
        Text(
            "(i)",
            color = WizardColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 6.dp)
                .clickable { info = true },
        )
    }
    if (info) {
        androidx.compose.ui.window.Popup(onDismissRequest = { info = false }) {
            Text(
                GENDER_SUBTITLE,
                color = WizardColors.text,
                style = WizardTypography.bodySmall,
                modifier = Modifier
                    .padding(24.dp)
                    .width(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun BirthDateField(state: SetupWizardState, vm: SetupWizardViewModel) {
    var digits by rememberSaveable(state.draft.draftId) {
        mutableStateOf(state.draft.inputTexts["birthDate"].orEmpty().filter(Char::isDigit).take(8))
    }
    val age = ageYearsFromBirthDigits(digits)
    BasicTextField(
        value = digits,
        onValueChange = { raw ->
            val next = raw.filter(Char::isDigit).take(8)
            digits = next
            val years = ageYearsFromBirthDigits(next)
            vm.updateStep(SetupStepId.AGE) { draft ->
                draft.copy(
                    inputTexts = draft.inputTexts + ("birthDate" to next),
                    ageYears = years,
                )
            }
        },
        visualTransformation = BirthDateVisualTransformation,
        textStyle = WizardTypography.question.copy(
            color = WizardColors.text,
            fontSize = 36.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(WizardColors.text),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("setup-birth-date"),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (digits.isEmpty()) {
                    Text(
                        "DD/MM/AAAA",
                        color = WizardColors.textFaint,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
    if (age != null) {
        Text(
            text = "Tienes $age años",
            color = WizardColors.text,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
internal fun GenderSymbolRow(selected: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        genderChoices.forEach { choice ->
            val active = choice.value == selected
            Column(
                Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(choice.value) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    choice.label,
                    color = WizardColors.textMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 13.sp,
                )
                GenderMarkIcon(choice.mark, active, Modifier.padding(top = 2.dp).size(54.dp))
            }
        }
    }
}

@Composable
private fun GenderMarkIcon(mark: GenderMark, active: Boolean, modifier: Modifier = Modifier) {
    val color = when (mark) {
        GenderMark.FEMALE -> if (active) WizardColors.text else WizardColors.textFaint
        GenderMark.MALE -> if (active) WizardColors.text else WizardColors.textFaint
        GenderMark.TRANS_MALE -> Color(0xFF3D8BFF)
        GenderMark.TRANS_FEMALE -> Color(0xFFFF6BA8)
    }
    when (mark) {
        GenderMark.FEMALE -> Text("♀", color = color, fontSize = 46.sp, fontWeight = FontWeight.Bold, modifier = modifier, textAlign = TextAlign.Center)
        GenderMark.MALE -> Text("♂", color = color, fontSize = 46.sp, fontWeight = FontWeight.Bold, modifier = modifier, textAlign = TextAlign.Center)
        GenderMark.TRANS_MALE, GenderMark.TRANS_FEMALE -> TransGlyph(color, modifier)
    }
}

@Composable
private fun TransGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.085f
        val center = Offset(s * 0.50f, s * 0.46f)
        val radius = s * 0.20f
        drawCircle(color = color, radius = radius, center = center, style = Stroke(stroke, cap = StrokeCap.Square))
        val stemTop = center.y + radius
        val stemBottom = s * 0.92f
        drawLine(color, Offset(center.x, stemTop), Offset(center.x, stemBottom), stroke, StrokeCap.Square)
        val barY = stemTop + (stemBottom - stemTop) * 0.42f
        drawLine(color, Offset(center.x - s * 0.13f, barY), Offset(center.x + s * 0.13f, barY), stroke, StrokeCap.Square)
        fun arrow(angle: Float) {
            val dir = Offset(kotlin.math.cos(angle), kotlin.math.sin(angle))
            val start = center + dir * radius
            val end = start + dir * (s * 0.28f)
            drawLine(color, start, end, stroke, StrokeCap.Square)
            val side = Offset(-dir.y, dir.x)
            val head = s * 0.11f
            drawLine(color, end, end - dir * head + side * head, stroke, StrokeCap.Square)
            drawLine(color, end, end - dir * head - side * head, stroke, StrokeCap.Square)
        }
        arrow(-0.62f)
        arrow(-2.52f)
    }
}

@Composable
private fun MeasureHeading(
    title: String,
    metricSelected: Boolean,
    metric: String,
    imperial: String,
    onMetric: () -> Unit,
    onImperial: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = WizardColors.text, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.10f)).padding(3.dp),
        ) {
            UnitChip(metric, metricSelected, onMetric)
            UnitChip(imperial, !metricSelected, onImperial)
        }
    }
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.Black else WizardColors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private object BirthDateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(8)
        val out = buildString {
            digits.forEachIndexed { index, char ->
                if (index == 2 || index == 4) append('/')
                append(char)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val extra = when {
                    offset <= 2 -> 0
                    offset <= 4 -> 1
                    else -> 2
                }
                return (offset + extra).coerceAtMost(out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val cut = when {
                    offset <= 2 -> 0
                    offset <= 5 -> 1
                    else -> 2
                }
                return (offset - cut).coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

private fun formatBirthDigits(digits: String): String = buildString {
    digits.forEachIndexed { index, char ->
        if (index == 2 || index == 4) append('/')
        append(char)
    }
}

private fun ageYearsFromBirthDigits(digits: String): Int? {
    if (digits.length != 8) return null
    val day = digits.substring(0, 2).toIntOrNull() ?: return null
    val month = digits.substring(2, 4).toIntOrNull() ?: return null
    val year = digits.substring(4, 8).toIntOrNull() ?: return null
    val birth = runCatching { java.time.LocalDate.of(year, month, day) }.getOrNull() ?: return null
    val today = java.time.LocalDate.now()
    if (birth.isAfter(today)) return null
    var age = today.year - birth.year
    if (today.monthValue < birth.monthValue ||
        (today.monthValue == birth.monthValue && today.dayOfMonth < birth.dayOfMonth)
    ) {
        age -= 1
    }
    return age.takeIf { it in 0..120 }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeasureHeading(
            title = "Altura",
            metricSelected = state.draft.heightUnit.toWizardHeightUnit() == WizardHeightUnit.CM,
            metric = "cm",
            imperial = "ft",
            onMetric = {
                vm.updateStep(SetupStepId.HEIGHT) { draft -> draft.copy(heightUnit = WizardHeightUnit.CM.wizardHeightCode) }
            },
            onImperial = {
                vm.updateStep(SetupStepId.HEIGHT) { draft -> draft.copy(heightUnit = WizardHeightUnit.FT_IN.wizardHeightCode) }
            },
        )
        WizardHeightRule(
            unit = state.draft.heightUnit.toWizardHeightUnit(),
            cm = state.draft.heightCm?.toInt(),
            onValueChange = { cm -> vm.setStepNumber(SetupStepId.HEIGHT, cm.toDouble()) },
        )
        MeasureHeading(
            title = "Peso",
            metricSelected = state.draft.weightUnit != "lb",
            metric = "kg",
            imperial = "lb",
            onMetric = { vm.setWeightUnit("kg") },
            onImperial = { vm.setWeightUnit("lb") },
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
    Text(GENDER_SUBTITLE, color = WizardColors.textMuted, style = WizardTypography.bodySmall)
    GenderSymbolRow(selected = selected, onSelect = { value -> vm.setStepChoice(step, value) })
    if (options.any { it.value == EQUATION_SEX_UNKNOWN }) {
        SetupFormChoiceCards(
            options = listOf(SetupOptionDefinition(EQUATION_SEX_UNKNOWN, "No lo sé")),
            isSelected = { it == selected },
            onOptionClick = { value -> vm.setStepChoice(step, value) },
        )
    }
}

private const val EQUATION_SEX_UNKNOWN = "unknown"

// ─── Grasa corporal actual ───────────────────────────────────────────────────

/**
 * Grasa actual en una sola pantalla: la figura y el slider aparecen de entrada.
 * La medición exacta es un campo opcional. Mover el slider estima; escribir
 * el porcentaje la sustituye. No hace falta elegir antes entre tres tarjetas.
 * La figura no toca `equationSex`.
 *
 * La figura de arranque (≈25 %) NO es una respuesta: Continuar queda bloqueado
 * hasta una acción explícita (mover o tocar la figura, escribir una medición u
 * «Omitir este paso»), salvo que Ajustes ya tenga una grasa declarada. El estado
 * se ve siempre en pantalla («Sin dato todavía», «Omitido»…).
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
    SetupVisualBodyFat(
        step = step,
        state = state,
        vm = vm,
        measuredOpen = showExact,
        bodyFatState = state.draft.bodyFatState(),
    )
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
    if (definition.allowSkip) {
        SetupFormSkipAction(onSkip = {
            // Omitir limpia lo elegido: el campo de medición no puede seguir
            // mostrando un texto que el borrador ya descartó.
            showExact = false
            vm.skipStep(step)
        })
    }
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
    bodyFatState: SetupBodyFatState,
) {
    val measuredActive = measuredOpen && !state.draft.inputTexts[step.name].isNullOrBlank()
    // Omitido: el selector compartido sigue mostrando «≈ N % de grasa corporal» (su texto
    // no cambia); se atenúa para que no parezca un dato declarado. Sigue siendo tocable:
    // moverlo vuelve a declarar una estimación.
    Box(modifier = Modifier.alpha(bodyFatSelectorAlpha(bodyFatState))) {
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
    // El texto del selector compartido no cambia (lo esperan pruebas instrumentadas):
    // el estado real del dato y la línea tocable «Usar ≈ N %» viven aquí, fuera de él.
    val figurePercent = bodyFatForSliderPos(state.draft.physiqueSliderPosition)
    SetupBodyFatStatus(
        statusText = bodyFatStatusText(state.draft),
        hint = bodyFatStatusHint(bodyFatState),
        figurePercent = figurePercent.takeIf { bodyFatState.offersFigureValue },
        onUseFigure = {
            vm.setStepChoice(step, SetupBodyFatSource.VISUAL_ESTIMATE.name)
            vm.setStepNumber(step, figurePercent)
        },
    )
}

/**
 * Estado del dato bajo la figura y, mientras no haya una estimación de ESTA
 * pantalla, la línea tocable «Usar ≈ N %» para aceptar lo que muestra la
 * figura. Stateless: el texto lo decide quien llama.
 */
@Composable
private fun SetupBodyFatStatus(
    statusText: String,
    hint: String?,
    figurePercent: Double?,
    onUseFigure: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = statusText,
            style = WizardTypography.cardTitle,
            color = WizardColors.text,
            modifier = Modifier.testTag(BODY_FAT_STATUS_TAG),
        )
        if (hint != null) SetupFormCaption(hint)
        if (figurePercent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = WizardSpacing.touchTarget)
                    .clip(WizardShapes.pill)
                    .border(1.dp, WizardColors.selectedBorder, WizardShapes.pill)
                    .clickable(role = Role.Button, onClick = onUseFigure)
                    .testTag(BODY_FAT_USE_FIGURE_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Usar ≈ ${figurePercent.roundToInt()} %",
                    style = WizardTypography.cardTitle,
                    color = WizardColors.text,
                )
            }
        }
    }
}

/** Opacidad del selector de figura: atenuado («sin usar») mientras el paso está omitido. */
internal fun bodyFatSelectorAlpha(state: SetupBodyFatState): Float =
    if (state == SetupBodyFatState.SKIPPED) BODY_FAT_SKIPPED_SELECTOR_ALPHA else 1f

internal const val BODY_FAT_SKIPPED_SELECTOR_ALPHA = 0.35f

/** Marca de prueba del estado del dato de grasa corporal. */
internal const val BODY_FAT_STATUS_TAG = "setup-bodyfat-status"

/** Marca de prueba de la línea tocable «Usar ≈ N %». */
internal const val BODY_FAT_USE_FIGURE_TAG = "setup-bodyfat-use"

/** Solo se ofrece «Usar ≈ N %» mientras esta pantalla aún no tiene una estimación o medición propia. */
internal val SetupBodyFatState.offersFigureValue: Boolean
    get() = this == SetupBodyFatState.PENDING ||
        this == SetupBodyFatState.ON_FILE ||
        this == SetupBodyFatState.SKIPPED

/** Línea de estado del dato: siempre dice si hay dato, cuál y si se omitió. */
internal fun bodyFatStatusText(draft: SetupWizardDraft): String = when (draft.bodyFatState()) {
    SetupBodyFatState.PENDING -> "Sin dato todavía"
    SetupBodyFatState.ON_FILE -> (draft.bodyFatPercent ?: draft.importedBodyFatPercent)
        ?.let { "Dato guardado antes: ≈ ${formatPercent(it)} %" }
        ?: "Dato guardado antes"
    SetupBodyFatState.VISUAL -> draft.bodyFatPercent
        ?.let { "Estimación visual guardada: ≈ ${it.roundToInt()} %" }
        ?: "Estimación visual guardada"
    SetupBodyFatState.MEASURED -> draft.bodyFatPercent
        ?.let { "Medición guardada: ${formatPercent(it)} %" }
        ?: "Falta el porcentaje de tu medición"
    SetupBodyFatState.SKIPPED -> "Omitido"
}

/** Ayuda breve bajo el estado; null cuando el dato ya está claro y no hace falta explicar nada. */
internal fun bodyFatStatusHint(state: SetupBodyFatState): String? = when (state) {
    SetupBodyFatState.PENDING -> BODY_FAT_PENDING_MESSAGE
    SetupBodyFatState.ON_FILE -> "Si continúas, se conserva. Mueve la figura o escribe una medición para cambiarlo."
    SetupBodyFatState.SKIPPED -> "No se guardará tu grasa corporal. Mueve la figura si cambias de idea."
    SetupBodyFatState.VISUAL, SetupBodyFatState.MEASURED -> null
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
