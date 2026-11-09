package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.importer.ProgramImportParser
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/** Controles visuales compartidos del importador; no contienen lógica de edición de sesiones. */
internal fun Modifier.importGlassPanel(compact: Boolean = false): Modifier = this
    .clip(RoundedCornerShape(20.dp))
    .background(ImportModuleFill)
    .border(1.dp, ImportModuleBorder, RoundedCornerShape(20.dp))
    .then(if (compact) Modifier.padding(horizontal = 14.dp, vertical = 10.dp) else Modifier.padding(16.dp))

/** Los tonos del tablero G3: el módulo #111, su filete y el elemento tonal #1A1A1A de los controles. */
private val ImportModuleFill = Color(0xFF111111)
private val ImportModuleBorder = Color.White.copy(alpha = 0.10f)
internal val ImportControlFill = Color(0xFF1A1A1A)
private val ImportControlShape = RoundedCornerShape(14.dp)
private val ImportPillShape = RoundedCornerShape(24.dp)

/**
 * Repeticiones: «Fijas» (un selector de 1 a 100) o «Rango» (dos selectores, «de» y «a»). En «Rango», si «de» sube por encima de
 * «a», «a» sube con él; si «a» baja por debajo de «de», «de» baja con él. [onChange] recibe el modo y las dos repeticiones nuevas;
 * [tagPrefix] da las marcas de prueba (`<prefijo>-reps-min`…). Lo usan los controles de prescripción del importador.
 */
@Composable
internal fun ImportRepsEditor(
    rangeMode: Boolean,
    repsMin: Int,
    repsMax: Int,
    onChange: (rangeMode: Boolean, repsMin: Int, repsMax: Int) -> Unit,
    tagPrefix: String,
) {
    ImportRepsMode(rangeMode = rangeMode, repsMin = repsMin, repsMax = repsMax, onChange = onChange, tagPrefix = tagPrefix)
    ImportRepsSteppers(rangeMode = rangeMode, repsMin = repsMin, repsMax = repsMax, onChange = onChange, tagPrefix = tagPrefix)
}

/** El conmutador «Fijas | Rango» de las repeticiones. Al pasar a «Fijas», las dos repeticiones quedan en la de «de». */
@Composable
internal fun ImportRepsMode(
    rangeMode: Boolean,
    repsMin: Int,
    repsMax: Int,
    onChange: (rangeMode: Boolean, repsMin: Int, repsMax: Int) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    ImportSegmented(
        options = listOf(false, true),
        selected = rangeMode,
        label = { range -> if (range) "Rango" else "Fijas" },
        onSelected = { range -> onChange(range, repsMin, if (range) repsMax else repsMin) },
        testTag = "$tagPrefix-reps-mode",
        modifier = modifier,
    )
}

/** Los selectores de repeticiones: uno en «Fijas»; «de» y «a» en «Rango», con el empuje entre ellos. */
@Composable
internal fun ImportRepsSteppers(
    rangeMode: Boolean,
    repsMin: Int,
    repsMax: Int,
    onChange: (rangeMode: Boolean, repsMin: Int, repsMax: Int) -> Unit,
    tagPrefix: String,
) {
    if (rangeMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ImportStepper(
                field = "repeticiones mínimas",
                valueText = "$repsMin",
                prefix = "de",
                canDecrease = repsMin > 1,
                canIncrease = repsMin < ProgramImportParser.MAX_REPS,
                onDecrease = { onChange(true, repsMin - 1, repsMax) },
                onIncrease = {
                    // «de» por encima de «a» empuja «a».
                    val low = repsMin + 1
                    onChange(true, low, maxOf(repsMax, low))
                },
                testTag = "$tagPrefix-reps-min",
                modifier = Modifier.weight(1f),
            )
            ImportStepper(
                field = "repeticiones máximas",
                valueText = "$repsMax",
                prefix = "a",
                canDecrease = repsMax > 1,
                canIncrease = repsMax < ProgramImportParser.MAX_REPS,
                onDecrease = {
                    // «a» por debajo de «de» arrastra «de».
                    val high = repsMax - 1
                    onChange(true, minOf(repsMin, high), high)
                },
                onIncrease = { onChange(true, repsMin, repsMax + 1) },
                testTag = "$tagPrefix-reps-max",
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        ImportStepper(
            field = "repeticiones",
            valueText = "$repsMin",
            canDecrease = repsMin > 1,
            canIncrease = repsMin < ProgramImportParser.MAX_REPS,
            onDecrease = { onChange(false, repsMin - 1, repsMin - 1) },
            onIncrease = { onChange(false, repsMin + 1, repsMin + 1) },
            testTag = "$tagPrefix-reps",
        )
    }
}

/** Un bloque del panel: su etiqueta pequeña en mayúsculas y su control. */
@Composable
internal fun ImportSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label.uppercase(), style = WizardTypography.eyebrow, color = WizardColors.textMuted, maxLines = 1)
        content()
    }
}

/**
 * Conmutador de opciones de una sola escala para el panel: pista tonal #1A1A1A de 48 dp de alto, cada opción con su objetivo
 * táctil de 48 dp y la pastilla activa en tinta de marca. Es `Role.RadioButton` con `selected`, como `WizardUnitToggle`, pero sin
 * su escala de medida, que cortaba «Sin carga» en tres opciones. [weights] reparte el ancho entre las opciones (iguales si no se da).
 */
@Composable
internal fun <T> ImportSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    weights: List<Float>? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WizardSpacing.touchTarget)
            .clip(ImportPillShape)
            .background(ImportControlFill)
            .testTag(testTag),
    ) {
        options.forEachIndexed { position, option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(weights?.getOrNull(position) ?: 1f)
                    .fillMaxHeight()
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelected(option) })
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(ImportPillShape)
                        .background(if (active) WizardColors.cta else Color.Transparent)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = WizardTypography.cardSubtitle.copy(fontSize = 12.sp),
                        color = if (active) WizardColors.ctaContent else WizardColors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Selector «−  4  +»: un módulo tonal de 48 dp con dos botones de 48 dp (descripciones «Menos series», «Más series») y el valor en
 * medio, en una sola línea; [prefix] («de», «a») y [suffix] («series») van como textos aparte, para que el valor sea el único texto
 * de su nodo. Se desactiva en los extremos y el valor se anuncia como estado.
 */
@Composable
internal fun ImportStepper(
    field: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WizardSpacing.touchTarget)
            .clip(ImportControlShape)
            .background(ImportControlFill)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(symbol = "−", description = "Menos $field", enabled = canDecrease, onClick = onDecrease)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            prefix?.let { Text(it, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted, maxLines = 1) }
            Text(
                valueText,
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.semantics {
                    contentDescription = field
                    stateDescription = valueText
                },
            )
            suffix?.let { Text(it, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted, maxLines = 1) }
        }
        StepperButton(symbol = "+", description = "Más $field", enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit, small: Boolean = false) {
    Box(
        modifier = Modifier
            .size(WizardSpacing.touchTarget)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = if (small) WizardTypography.cardSubtitle else WizardTypography.controlLabel,
            color = if (enabled) WizardColors.text else WizardColors.textFaint,
        )
    }
}

/** El botón principal del panel: el CTA del wizard (tinta sobre negro), apagado si no se puede guardar. Con [compact], de 48 dp. */
@Composable
internal fun ImportCta(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    trailingArrow: Boolean = false,
) {
    Box(
        modifier = modifier
            .heightIn(min = if (compact) WizardSpacing.touchTarget else WizardSpacing.ctaHeight)
            .clip(if (compact) ImportPillShape else WizardShapes.cta)
            .background(if (enabled) WizardColors.cta else WizardColors.ctaDisabled)
            .testTag(testTag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = if (compact) WizardTypography.cardTitle else WizardTypography.cta,
                color = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
            )
            if (trailingArrow) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    KpknLineIcons.ChevronRight,
                    contentDescription = null,
                    tint = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** «Cambiar» del ejercicio: una píldora con filete de 36 dp de alto dentro de su objetivo de 48. */
@Composable
internal fun ImportOutlineButton(label: String, onClick: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = WizardTypography.cardSubtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = WizardColors.text, maxLines = 1)
        }
    }
}
