package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.bodyFatForSliderPos
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
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
import java.util.Locale
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
 * - Cada paso es una sección de la página larga: el Host pinta la etiqueta, el
 *   título y el subtítulo, así que aquí no se repiten. Tipografía solo con roles
 *   de [WizardTypography] (mínimo 13 sp); las únicas excepciones son el glifo
 *   ♀/♂, que es un icono, y el tamaño del alias, que baja de 36 a 24 sp partiendo
 *   de `controlValue`.
 * - El género se pregunta una sola vez, en su propio paso (EQUATION_SEX); la
 *   primera página solo pide alias y fecha de nacimiento.
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

// ─── Alias y fecha de nacimiento ─────────────────────────────────────────────

/**
 * Primera página (NAME): «Alias» y «Fecha de nacimiento», cada uno con su
 * etiqueta (`controlLabel`) alineada a la izquierda y el campo a todo el ancho;
 * bajo la fecha, «Tienes N años». El título de la sección lo pinta el Host y el
 * género NO se pregunta aquí: tiene su propio paso (EQUATION_SEX).
 *
 * Conserva las marcas de prueba (`setup-name`, `setup-birth-date`), las
 * escrituras al ViewModel y la validación del CTA tal como estaban.
 */
@Composable
private fun SetupAliasAndAge(
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.sectionGap),
    ) {
        SetupLabeledField(label = "Alias") { AliasField(state = state, vm = vm) }
        SetupLabeledField(label = "Fecha de nacimiento") { BirthDateField(state = state, vm = vm) }
    }
}

/** Etiqueta (`controlLabel`, a la izquierda) sobre un campo a todo el ancho. */
@Composable
private fun SetupLabeledField(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = WizardTypography.controlLabel, color = WizardColors.text)
        content()
    }
}

/**
 * Marco de un campo de la página larga: tarjeta con borde fino que pasa a borde
 * blanco grueso con el foco. Vive dentro del `decorationBox` del campo para que
 * todo el marco (no solo el texto) reciba el toque y abra el teclado.
 */
@Composable
private fun SetupFieldFrame(
    focused: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETUP_FIELD_MIN_HEIGHT)
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(
                width = if (focused) WizardColors.selectedBorderWidth else WizardColors.unselectedBorderWidth,
                color = if (focused) WizardColors.selectedBorder else WizardColors.cardBorder,
                shape = WizardShapes.card,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}

/** Alto mínimo del marco de un campo: por encima del objetivo táctil de 48 dp y con aire para 36 sp. */
private val SETUP_FIELD_MIN_HEIGHT = 64.dp

/**
 * Alias: `controlValue` (36 sp) mientras es corto y hasta 24 sp con alias
 * largos. Vacío muestra «Pon tu alias» con el mismo estilo en `textFaint`.
 */
@Composable
private fun AliasField(
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    var text by rememberSaveable(state.draft.draftId, state.draft.commitId) {
        mutableStateOf(state.draft.inputTexts[SetupStepId.NAME.name] ?: state.draft.name)
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Excepción permitida a «sin tamaños propios»: el tamaño baja con la longitud,
    // pero siempre partiendo del rol `controlValue`.
    val style = WizardTypography.controlValue.copy(
        color = WizardColors.text,
        fontSize = aliasFontSizeSp(text.length).sp,
    )
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val next = raw.take(ALIAS_MAX_LENGTH)
            text = next
            vm.setStepText(SetupStepId.NAME, next)
        },
        textStyle = style,
        cursorBrush = SolidColor(WizardColors.text),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup-name"),
        decorationBox = { inner ->
            SetupFieldFrame(focused = focused) {
                if (text.isEmpty()) {
                    Text(text = "Pon tu alias", style = style, color = WizardColors.textFaint, maxLines = 1)
                }
                inner()
            }
        },
    )
}

/** Longitud máxima del alias (la misma que valida `SetupWizardValidation`). */
private const val ALIAS_MAX_LENGTH = 32

/** Tamaño del alias en sp: 36 mientras es corto, medio sp menos por carácter y nunca por debajo de 24. */
private fun aliasFontSizeSp(length: Int): Float =
    (ALIAS_FONT_MAX_SP - length * ALIAS_FONT_SHRINK_SP_PER_CHAR).coerceIn(ALIAS_FONT_MIN_SP, ALIAS_FONT_MAX_SP)

private const val ALIAS_FONT_MAX_SP = 36f
private const val ALIAS_FONT_MIN_SP = 24f
private const val ALIAS_FONT_SHRINK_SP_PER_CHAR = 0.5f

@Composable
private fun BirthDateField(state: SetupWizardState, vm: SetupWizardViewModel) {
    var digits by rememberSaveable(state.draft.draftId) {
        mutableStateOf(state.draft.inputTexts["birthDate"].orEmpty().filter(Char::isDigit).take(8))
    }
    val age = ageYearsFromBirthDigits(digits)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = WizardTypography.controlValue.copy(color = WizardColors.text)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            textStyle = style,
            cursorBrush = SolidColor(WizardColors.text),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup-birth-date"),
            decorationBox = { inner ->
                SetupFieldFrame(focused = focused) {
                    if (digits.isEmpty()) {
                        Text(text = "DD/MM/AAAA", style = style, color = WizardColors.textFaint, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (age != null) {
            Text(
                text = if (age == 1) "Tienes 1 año" else "Tienes $age años",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
            )
        }
    }
}

// ─── Género (sexo de cálculo) ────────────────────────────────────────────────

/**
 * Por qué se pregunta el género. Es el párrafo largo que antes era el subtítulo
 * del paso: ahora vive detrás de «Por qué lo preguntamos» (diálogo) y el
 * subtítulo del catálogo se queda en una frase corta.
 */
private const val GENDER_WHY_TEXT =
    "Conocer tu género nos permitirá calcular el gasto energético que te corresponde. Esto es muy importante para calcular tus calorías recomendadas para tu plan de nutrición. Si no tienes una opción que te identifique, elige la que más se apegue a tu contexto hormonal."

private enum class GenderMark { FEMALE, MALE, TRANS_MALE, TRANS_FEMALE }

private data class GenderChoice(val value: String, val label: String, val mark: GenderMark)

private val genderChoices = listOf(
    GenderChoice("female", "Mujer", GenderMark.FEMALE),
    GenderChoice("male", "Hombre", GenderMark.MALE),
    GenderChoice("trans_male", "Hombre trans", GenderMark.TRANS_MALE),
    GenderChoice("trans_female", "Mujer trans", GenderMark.TRANS_FEMALE),
)

/** Aro de cada glifo: tamaño fijo, no crece con la escala de fuente. */
private val GENDER_DISC_SIZE = 56.dp

/** Tamaño del glifo ♀/♂ (icono): 46 dp, es decir 46 sp a escala 1; no sigue la escala de fuente. */
private val GENDER_GLYPH_SIZE = 46.dp

/** Lado del lienzo del glifo trans, de modo que su trazo ocupe lo mismo que ♀/♂ dentro del aro. */
private val GENDER_TRANS_GLYPH_SIZE = 40.dp

/**
 * Las cuatro opciones de género en una fila. Cada una es un botón de radio
 * (`Role.RadioButton` + `selected`) con etiqueta `note` y el glifo dentro de un
 * aro, y mide bastante más de 48 dp de alto. La elegida lleva aro blanco grueso
 * y glifo y etiqueta en `text`: el estado no depende solo del color.
 *
 * Con una escala de fuente grande («Hombre trans» ya no cabe en un cuarto del
 * ancho) las opciones pasan a dos filas de dos, para que ninguna etiqueta se parta.
 */
@Composable
internal fun GenderSymbolRow(selected: String?, onSelect: (String) -> Unit) {
    val perRow = if (LocalDensity.current.fontScale > GENDER_GRID_FONT_SCALE) 2 else genderChoices.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genderChoices.chunked(perRow).forEach { rowChoices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                // Con los glifos alineados abajo, una etiqueta de dos líneas no desplaza los aros.
                verticalAlignment = Alignment.Bottom,
            ) {
                rowChoices.forEach { choice ->
                    GenderOption(
                        choice = choice,
                        active = choice.value == selected,
                        onSelect = { onSelect(choice.value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Escala de fuente a partir de la cual las opciones de género pasan a dos filas de dos. */
private const val GENDER_GRID_FONT_SCALE = 1.25f

@Composable
private fun GenderOption(
    choice: GenderChoice,
    active: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(WizardShapes.card)
            .selectable(selected = active, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = choice.label,
            style = WizardTypography.note,
            color = if (active) WizardColors.text else WizardColors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        GenderMarkDisc(mark = choice.mark, active = active)
    }
}

/** Glifo dentro de su aro: fino y apagado en reposo, blanco y grueso cuando está elegido. */
@Composable
private fun GenderMarkDisc(mark: GenderMark, active: Boolean) {
    Box(
        modifier = Modifier
            .size(GENDER_DISC_SIZE)
            .background(WizardColors.cardFill, CircleShape)
            .border(
                width = if (active) WizardColors.selectedBorderWidth else WizardColors.unselectedBorderWidth,
                color = if (active) WizardColors.selectedBorder else WizardColors.cardBorder,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GenderMarkIcon(mark = mark, active = active)
    }
}

@Composable
private fun GenderMarkIcon(mark: GenderMark, active: Boolean, modifier: Modifier = Modifier) {
    // Un solo tratamiento para los cuatro glifos: apagado `textFaint`, elegido `text`.
    // Los dos glifos trans son idénticos: antes solo los distinguía el color (azul/rosa)
    // y ahora la etiqueta es lo que dice cuál es cuál.
    val color = if (active) WizardColors.text else WizardColors.textFaint
    when (mark) {
        GenderMark.FEMALE -> GenderSignGlyph("♀", color, modifier)
        GenderMark.MALE -> GenderSignGlyph("♂", color, modifier)
        GenderMark.TRANS_MALE, GenderMark.TRANS_FEMALE -> TransGlyph(color, modifier.size(GENDER_TRANS_GLYPH_SIZE))
    }
}

/** Glifo ♀/♂ como icono decorativo: la etiqueta de la opción ya dice qué es (TalkBack no lo repite). */
@Composable
private fun GenderSignGlyph(sign: String, color: Color, modifier: Modifier = Modifier) {
    // Excepción permitida a «sin tamaños propios»: es un icono, no texto; su tamaño no sigue la escala de fuente.
    val glyphSize = with(LocalDensity.current) { GENDER_GLYPH_SIZE.toSp() }
    Text(
        text = sign,
        style = WizardTypography.controlValue.copy(fontSize = glyphSize, lineHeight = TextUnit.Unspecified),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.clearAndSetSemantics { },
    )
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

/**
 * Botón «Por qué lo preguntamos»: abre el diálogo con la explicación larga.
 * Mide al menos 48 dp de alto y se anuncia como botón.
 */
@Composable
private fun GenderWhyButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .clip(WizardShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp)
            .testTag(GENDER_WHY_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = WizardColors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        Text(text = "Por qué lo preguntamos", style = WizardTypography.controlLabel, color = WizardColors.text)
    }
}

/** Marca de prueba del botón «Por qué lo preguntamos». */
internal const val GENDER_WHY_TAG = "setup-gender-why"

@Composable
private fun GenderWhyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Por qué lo preguntamos", style = WizardTypography.cardTitle, color = WizardColors.text) },
        text = { Text(GENDER_WHY_TEXT, style = WizardTypography.bodySmall, color = WizardColors.textMuted) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido", color = WizardColors.text) } },
    )
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

/** Etiqueta de la medida (`controlLabel`) con su selector de unidad a la derecha. */
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
        Text(title, style = WizardTypography.controlLabel, color = WizardColors.text)
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.10f)).padding(3.dp),
        ) {
            UnitChip(metric, metricSelected, onMetric)
            UnitChip(imperial, !metricSelected, onImperial)
        }
    }
}

/** Unidad del selector: al menos 48 × 48 dp y `controlLabel`; la elegida va en píldora blanca. */
@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = WizardSpacing.touchTarget)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = WizardTypography.controlLabel,
            color = if (selected) Color.Black else WizardColors.text,
        )
    }
}

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
 *
 * El subtítulo corto lo pinta el Host; el porqué largo vive detrás de
 * «Por qué lo preguntamos» (diálogo), no como párrafo sobre las opciones.
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
    var whyOpen by rememberSaveable { mutableStateOf(false) }
    GenderWhyButton(onClick = { whyOpen = true })
    GenderSymbolRow(selected = selected, onSelect = { value -> vm.setStepChoice(step, value) })
    if (options.any { it.value == EQUATION_SEX_UNKNOWN }) {
        SetupFormChoiceCards(
            options = listOf(SetupOptionDefinition(EQUATION_SEX_UNKNOWN, "No lo sé")),
            isSelected = { it == selected },
            onOptionClick = { value -> vm.setStepChoice(step, value) },
        )
    }
    if (whyOpen) GenderWhyDialog(onDismiss = { whyOpen = false })
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
    SetupExactMeasurementToggle(expanded = showExact, onToggle = { showExact = !showExact })
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
 * Enlace «Tengo una medición exacta» / «Ocultar medición exacta»: letra
 * `controlLabel`, anunciado como botón y de al menos 48 dp de alto.
 */
@Composable
private fun SetupExactMeasurementToggle(expanded: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .clip(WizardShapes.pill)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = if (expanded) "Ocultar medición exacta" else "Tengo una medición exacta",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
        )
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
        mutableStateOf(bodyFatFieldText(state.draft.inputTexts[step.name], state.draft.bodyFatPercent))
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

/**
 * Texto con el que se abre el campo de medición exacta. Manda lo guardado, pero nunca
 * un `Double` crudo («33.184518814086914», como dejaban las versiones anteriores al
 * mover la figura): con más de un decimal se recorta a uno y conserva el separador que
 * ya traía el texto (el parser acepta punto y coma). Sin texto guardado se parte del
 * porcentaje del borrador.
 */
internal fun bodyFatFieldText(stored: String?, percent: Double?): String {
    if (stored == null) return percent?.let(::formatPercent).orEmpty()
    val separatorIndex = stored.lastIndexOfAny(charArrayOf('.', ','))
    val decimals = if (separatorIndex < 0) 0 else stored.length - separatorIndex - 1
    if (decimals <= 1) return stored
    val parsed = parseLocalizedNumber(stored) ?: return stored
    val shortText = String.format(Locale.ROOT, "%.1f", parsed).removeSuffix(".0")
    return if (stored[separatorIndex] == ',') shortText.replace('.', ',') else shortText
}

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
