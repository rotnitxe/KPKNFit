package com.example.kpkn.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.bodyFatForSliderPos
import com.example.kpkn.domain.nutrition.physiqueSliderPositionForBodyFat
import com.example.kpkn.domain.onboarding.SetupControlKind
import com.example.kpkn.domain.onboarding.SetupEquationSexValues
import com.example.kpkn.domain.onboarding.SetupOptionDefinition
import com.example.kpkn.domain.onboarding.SetupStepDefinition
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.design.WizardBodyFatPicker
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardAnthropometryLayout
import com.example.kpkn.screens.onboarding.design.WizardGenderGlyph
import com.example.kpkn.screens.onboarding.design.WizardGenderMark
import com.example.kpkn.screens.onboarding.design.WizardHeightRule
import com.example.kpkn.screens.onboarding.design.WizardHeightUnit
import com.example.kpkn.screens.onboarding.design.WizardHeightUnitToggle
import com.example.kpkn.screens.onboarding.design.WizardHeightWheel
import com.example.kpkn.screens.onboarding.design.currentAnthropometryLayout
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardMassUnitToggle
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.WizardWeightRule
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

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
        SetupStepId.BODY_FAT -> SetupBodyFatControl(step = step, state = state, vm = vm)
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
 *
 * Explica la razón científica (el gasto de energía depende sobre todo de la masa libre de grasa y del
 * entorno hormonal, no de cómo se identifica la persona) y deja claro que las opciones solo sirven
 * para elegir la ecuación más adecuada.
 */
private const val GENDER_WHY_TEXT =
    "El gasto de energía de tu cuerpo depende sobre todo de tu masa libre de grasa (músculo, huesos y órganos) y de tu entorno hormonal, no de cómo te identificas. " +
        "Por eso lo preguntamos: para elegir la ecuación que mejor se ajusta a tu cuerpo y calcular unas calorías más acertadas para tu plan de nutrición. " +
        "Las opciones solo sirven para eso; ninguna te define, así que elige la que más se acerque a tu caso. " +
        "Si no lo tienes claro, toca «No lo sé» y cuéntanos qué hormonas predominan en tu cuerpo."

private data class GenderChoice(val value: String, val label: String, val mark: WizardGenderMark)

private val genderChoices = listOf(
    GenderChoice("female", "Mujer", WizardGenderMark.FEMALE),
    GenderChoice("male", "Hombre", WizardGenderMark.MALE),
    GenderChoice("trans_male", "Hombre trans", WizardGenderMark.TRANS_MALE),
    GenderChoice("trans_female", "Mujer trans", WizardGenderMark.TRANS_FEMALE),
)

/** Zona del glifo y de su resplandor: es también lo que se toca, así que supera con holgura los 48 dp. */
private val GENDER_GLYPH_BOX = 60.dp

/** Lado del lienzo de TODOS los glifos: el mismo para los cuatro, así miden igual y quedan centrados. */
private val GENDER_GLYPH_SIZE = 40.dp

/** Color del resplandor: azul para lo masculino, violeta para lo femenino (también en las opciones trans). */
private fun WizardGenderMark.glowColor(): Color = when (this) {
    WizardGenderMark.MALE, WizardGenderMark.TRANS_MALE -> WizardColors.genderMasculine
    WizardGenderMark.FEMALE, WizardGenderMark.TRANS_FEMALE -> WizardColors.genderFeminine
}

/**
 * Las cuatro opciones de género en una fila. Sin círculos ni tarjetas: el glifo va solo, los cuatro del
 * mismo tamaño y centrados sobre su etiqueta. Cada opción es un botón de radio (`Role.RadioButton` +
 * `selected`) y mide bastante más de 48 dp.
 *
 * Al pulsar sale un pequeño resplandor (azul masculino, violeta femenino) que se asienta en uno más tenue
 * mientras la opción está elegida. El estado no depende del matiz del resplandor: la elegida también pasa
 * de gris a blanco en el glifo y en la etiqueta, y se anuncia como seleccionada.
 *
 * Con una escala de fuente grande («Hombre trans» ya no cabe en un cuarto del ancho) las opciones pasan a
 * dos filas de dos, para que ninguna etiqueta se parta.
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
                // Todas las celdas miden lo mismo (la etiqueta reserva siempre dos líneas): glifos alineados arriba.
                verticalAlignment = Alignment.Top,
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Pulsado: el resplandor sube rápido; al soltar baja despacio hasta el nivel de reposo (más tenue si está elegida).
    val glow = animateFloatAsState(
        targetValue = when {
            pressed -> GENDER_GLOW_PRESSED
            active -> GENDER_GLOW_SELECTED
            else -> 0f
        },
        animationSpec = tween(durationMillis = if (pressed) 90 else 380),
        label = "gender-glow",
    )
    val press = animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "gender-press",
    )
    val glowColor = choice.mark.glowColor()
    Column(
        modifier = modifier
            .selectable(
                selected = active,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(GENDER_GLYPH_BOX)
                .drawBehind { drawGenderGlow(glowColor, glow.value) },
            contentAlignment = Alignment.Center,
        ) {
            WizardGenderGlyph(
                mark = choice.mark,
                color = if (active) WizardColors.text else WizardColors.textFaint,
                modifier = Modifier
                    .size(GENDER_GLYPH_SIZE)
                    .graphicsLayer {
                        scaleX = press.value
                        scaleY = press.value
                    },
            )
        }
        Text(
            text = choice.label,
            style = WizardTypography.note,
            color = if (active) WizardColors.text else WizardColors.textMuted,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
    }
}

/** Intensidad del resplandor mientras se mantiene pulsada la opción. */
private const val GENDER_GLOW_PRESSED = 0.75f

/** Intensidad del resplandor, ya asentado, de la opción elegida. */
private const val GENDER_GLOW_SELECTED = 0.32f

/** Resplandor suave y pequeño: un degradado radial que nace del centro del glifo y se apaga antes del borde de la zona. */
private fun DrawScope.drawGenderGlow(color: Color, alpha: Float) {
    if (alpha <= 0.01f) return
    val radius = size.minDimension * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.5f to color.copy(alpha = alpha * 0.4f),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
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
 * Género → base de la ecuación de energía: cuatro glifos y una tarjeta «No lo sé». La energía se calcula
 * con fórmulas científicas en todo momento, y esas fórmulas dependen de la masa libre de grasa y del
 * entorno hormonal, no de la identidad. Por eso «No lo sé» no deja el paso sin resolver: en vez de
 * volver a preguntar «hombre o mujer», despliega justo debajo de su tarjeta la consulta del contexto
 * hormonal («¿Qué hormonas predominan en tu cuerpo?») con tres respuestas que eligen la ecuación
 * (estrógenos → femenina, andrógenos → masculina, equilibrio → promedio de ambas). Solo con una de
 * ellas el paso continúa (ver `SetupWizardValidation`).
 *
 * - Pulsar «No lo sé» guarda `unknown` y abre el panel; elegir una respuesta reemplaza `unknown` en la
 *   selección única; elegir un glifo reemplaza lo anterior y cierra el panel.
 * - «No lo sé» se ve seleccionada mientras la selección sea `unknown` o una respuesta hormonal. Volver
 *   a pulsarla estando abierta no hace nada: no borra la respuesta hormonal ya elegida.
 * - Escribe solo por `setStepChoice`; no se pregunta ni se guarda una identidad aparte.
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
    // `selectedValues` ya devuelve la proyección tipada cuando el borrador se rehidrató sin selección.
    val selected = state.draft.selectedValues(step).firstOrNull()
    val hormonalOpen = SetupEquationSexValues.opensHormonalPanel(selected)
    val unknownOption = definition.option(SetupEquationSexValues.UNKNOWN)
        ?: SetupOptionDefinition(SetupEquationSexValues.UNKNOWN, "No lo sé")
    val hormonalOptions = SetupEquationSexValues.HORMONAL.mapNotNull { value -> definition.option(value) }
    var whyOpen by rememberSaveable { mutableStateOf(false) }
    GenderWhyButton(onClick = { whyOpen = true })
    GenderSymbolRow(selected = selected, onSelect = { value -> vm.setStepChoice(step, value) })
    // Sin separación entre la tarjeta y el panel: el aire de arriba del panel va dentro de lo que se
    // anima, así el hueco crece y se cierra con él en vez de aparecer de golpe.
    Column(modifier = Modifier.fillMaxWidth()) {
        SetupFormChoiceCards(
            options = listOf(unknownOption),
            isSelected = { hormonalOpen },
            // Abierta (con «No lo sé» o con una respuesta hormonal) no hay nada que cambiar.
            onOptionClick = { value -> if (!hormonalOpen) vm.setStepChoice(step, value) },
        )
        GenderHormonesPanel(
            visible = hormonalOpen,
            options = hormonalOptions,
            selected = selected,
            onSelect = { value -> vm.setStepChoice(step, value) },
        )
    }
    if (whyOpen) GenderWhyDialog(onDismiss = { whyOpen = false })
}

/** Pregunta del panel hormonal. */
private const val GENDER_HORMONES_QUESTION = "¿Qué hormonas predominan en tu cuerpo?"

/** Marca de prueba del panel hormonal que despliega «No lo sé». */
internal const val GENDER_HORMONES_TAG = "setup-gender-hormones"

/** Duración del despliegue del panel hormonal: sobria, bastante más corta que el deslizado de página. */
private const val GENDER_PANEL_MILLIS = 300

/**
 * Consulta del contexto hormonal que abre «No lo sé»: una pregunta ([GENDER_HORMONES_QUESTION], con el
 * rol `controlLabel`) y tres tarjetas de elección con su subtítulo. Se despliega con altura y fundido
 * (sin rebote), y sin animación si la persona pidió reducir el movimiento. Cerrada no compone nada.
 */
@Composable
private fun GenderHormonesPanel(
    visible: Boolean,
    options: List<SetupOptionDefinition>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val reducedMotion = wizardReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            expandVertically(
                animationSpec = tween(GENDER_PANEL_MILLIS, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(GENDER_PANEL_MILLIS))
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            shrinkVertically(
                animationSpec = tween(GENDER_PANEL_MILLIS, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(GENDER_PANEL_MILLIS))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WizardSpacing.sectionGap)
                .testTag(GENDER_HORMONES_TAG),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = GENDER_HORMONES_QUESTION,
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
                modifier = Modifier.semantics { heading() },
            )
            SetupFormChoiceCards(
                options = options,
                isSelected = { value -> value == selected },
                onOptionClick = onSelect,
                modifier = Modifier.selectableGroup(),
            )
        }
    }
}

// ─── Grasa corporal actual ───────────────────────────────────────────────────

/**
 * Grasa actual: una figura grande con una regla vertical de porcentaje a su derecha ([WizardBodyFatPicker]).
 * Mover la regla (arrastrar o tocar un punto) DECLARA el valor: no hay nada más que pulsar y desde ese momento el
 * check queda habilitado. El paso es obligatorio (sin «omitir») y ya no tiene aviso, estado, enlace ni campo de
 * medición exacta: quien sabe su número se fija en la regla y quien no, se guía por la figura.
 *
 * La posición de arranque (≈25 %, o el dato de Ajustes si lo hay) NO es una respuesta hasta que se mueve la regla:
 * se pinta atenuada y no se guarda sola. Un dato ya guardado en Ajustes, o declarado, cuenta como declarado y la
 * regla arranca en él. La figura nunca toca `equationSex`: cambiar de figura solo cambia la figura.
 */
@Composable
private fun SetupBodyFatControl(
    step: SetupStepId,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    val draft = state.draft
    WizardBodyFatPicker(
        percent = draft.bodyFatRulerPercent(),
        model = draft.physiqueModel,
        declared = draft.bodyFatState().isDeclared,
        onPercentChange = { percent ->
            vm.updateStep(step) { current -> current.withBodyFatRulerValue(percent) }
        },
        onModelChange = { model ->
            // Cambiar de figura solo cambia la figura: ni el porcentaje ni `equationSex`.
            vm.updateStep(step) { current -> current.copy(physiqueModel = model) }
        },
    )
}

/**
 * Porcentaje con el que arranca y se pinta la regla: el declarado; si no, el dato creíble de Ajustes
 * ([SetupBodyFatState.ON_FILE]); y si no hay ninguno, el de la figura de arranque (la posición guardada, ≈25 % por
 * defecto). Solo los dos primeros son una respuesta ([SetupBodyFatState.isDeclared]); el tercero es una referencia
 * y la regla lo pinta atenuado hasta que se mueve.
 */
internal fun SetupWizardDraft.bodyFatRulerPercent(): Double =
    bodyFatPercent?.takeIf { it.isFinite() }
        ?: importedBodyFatPercent?.takeIf { bodyFatState() == SetupBodyFatState.ON_FILE }
        ?: bodyFatForSliderPos(physiqueSliderPosition)

/**
 * Declara la estimación visual que fija la regla, en UNA sola escritura: la fuente `VISUAL_ESTIMATE`, el
 * porcentaje entero (con su fecha real, solo si cambió) y la posición de la figura derivada de ese porcentaje.
 * Equivale a `setStepChoice` + `setStepNumber` + guardar la posición del slider, pero atómico y con un único guardado.
 */
internal fun SetupWizardDraft.withBodyFatRulerValue(
    percent: Int,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft {
    val value = percent.toDouble()
    return withStepChoice(SetupStepId.BODY_FAT, SetupBodyFatSource.VISUAL_ESTIMATE.name, nowEpochMs)
        .withStepNumber(SetupStepId.BODY_FAT, value, nowEpochMs)
        .copy(physiqueSliderPosition = physiqueSliderPositionForBodyFat(value))
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
