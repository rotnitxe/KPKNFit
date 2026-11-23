package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.screens.onboarding.design.MacroRingFractions
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMacroRings
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.MacroColors

/** Diámetro de los anillos del editor: el mismo que usa el wizard. */
private val RingsSize = 112.dp

/** Aire entre los anillos y la cifra, como en la nutrición de Home. */
private val RingsGap = 16.dp

/** Número de muestra para medir si la cifra de cinco dígitos cabe junto a los anillos (como Home). */
private const val FigureSampleKcal = 12345

/**
 * Módulo «Calorías y macros». Arriba, en vivo: los anillos (112 dp) y la cifra de la base con su línea de macros, con la misma caída a
 * apilado que Home. Debajo, la base editable: las calorías y los tres macros con su color y su unidad dentro del campo; el reparto de
 * energía; un aviso solo si los macros se alejan de las calorías más de un 2 %, y la meta de hoy si ya está fijada. Sin estado.
 */
@Composable
fun EditorEnergyModule(
    base: NutritionEditorBase?,
    eerKcal: Double?,
    todayGoalFixedKcal: Int?,
    onCalories: (Int) -> Unit,
    onProtein: (Double) -> Unit,
    onCarbs: (Double) -> Unit,
    onFat: (Double) -> Unit,
    modifier: Modifier = Modifier,
    weightKg: Double? = null,
    onResetRecommendation: (() -> Unit)? = null,
) {
    var caloriesInput by remember { mutableStateOf(base?.caloriesKcal?.toString().orEmpty()) }
    var caloriesFocused by remember { mutableStateOf(false) }
    var caloriesHadFocus by remember { mutableStateOf(false) }
    var caloriesUserTyped by remember { mutableStateOf(false) }
    LaunchedEffect(base?.caloriesKcal, caloriesFocused) {
        if (!caloriesFocused || !caloriesUserTyped) {
            caloriesInput = base?.caloriesKcal?.toString().orEmpty()
        }
    }

    val percents = editorEnergyPercents(base)
    KpknModule(modifier = modifier) {
        KpknModuleHeader(
            label = "Calorías y macros",
            action = onResetRecommendation?.let {
                {
                    KpknTextAction(
                        text = "Restablecer recomendación",
                        onClick = {
                            caloriesUserTyped = false
                            it()
                        },
                    )
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        EditorEnergyHero(base = base, eerKcal = eerKcal)
        Spacer(Modifier.height(16.dp))
        EditorNumberField(
            value = caloriesInput,
            onValueChange = { text ->
                caloriesUserTyped = true
                caloriesInput = text
                editorCaloriesFromText(text)?.let(onCalories)
            },
            label = "Calorías objetivo",
            unit = "kcal",
            placeholder = "—",
            keyboard = KeyboardType.Number,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (focus.hasFocus) {
                        caloriesHadFocus = true
                        caloriesFocused = true
                    } else {
                        caloriesFocused = false
                        if (caloriesHadFocus) {
                            val parsed = editorCaloriesFromText(caloriesInput)
                            if (parsed != null) {
                                if (caloriesUserTyped && parsed != base?.caloriesKcal) {
                                    onCalories(parsed)
                                }
                            } else {
                                caloriesInput = base?.caloriesKcal?.toString().orEmpty()
                            }
                            caloriesUserTyped = false
                        }
                    }
                },
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorMacroCard(
                label = "Proteína",
                color = MacroColors.protein,
                value = base?.proteinG,
                percent = percents?.first,
                gramsPerKg = weightKg?.takeIf { it > 0 }?.let { w -> base?.proteinG?.let { it / w } },
                onValue = onProtein,
            )
            EditorMacroCard(
                label = "Hidratos",
                color = MacroColors.carbs,
                value = base?.carbsG,
                percent = percents?.second,
                gramsPerKg = weightKg?.takeIf { it > 0 }?.let { w -> base?.carbsG?.let { it / w } },
                onValue = onCarbs,
            )
            EditorMacroCard(
                label = "Grasas",
                color = MacroColors.fat,
                value = base?.fatG,
                percent = percents?.third,
                gramsPerKg = weightKg?.takeIf { it > 0 }?.let { w -> base?.fatG?.let { it / w } },
                onValue = onFat,
            )
        }
        percents?.let { p ->
            Spacer(Modifier.height(12.dp))
            EditorShareBar(p)
            Spacer(Modifier.height(6.dp))
            EditorCaption(editorEnergyLegend(p), numeric = true)
        }
        // Solo si los macros se alejan de las calorías más de un 2 %: si no, el aviso sobra.
        base?.let { editorMacroGapLine(it) }?.let { line ->
            Spacer(Modifier.height(12.dp))
            EditorCaption(line)
        }
        // Editar el MISMO plan nunca cambia la meta de hoy: queda fijada y el cambio vale desde mañana.
        todayGoalFixedKcal?.let { kcal ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = KpknLineIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SymbolPalette.energia,
                )
                Spacer(Modifier.width(8.dp))
                EditorCaption(editorTodayGoalLine(kcal), modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Anillos y cifra de la base. Los anillos son la lectura en vivo: el exterior, las kcal frente al mantenimiento; los interiores, la parte
 * de la energía de cada macro. Con la cifra y su línea de macros a la derecha; si la cifra no cabe junto a los anillos, pasa a todo el ancho.
 */
@Composable
private fun EditorEnergyHero(base: NutritionEditorBase?, eerKcal: Double?) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val reduced = wizardReducedMotion()
    val fractions = editorRingFractions(base, eerKcal)
    val description = editorRingsDescription(base)
    val macros = editorMacrosLine(base)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val ringsAndGapPx = with(density) { (RingsSize + RingsGap).roundToPx() }
        val sideBySide = remember(constraints.maxWidth, density, measurer) {
            figureFits(measurer, density, availablePx = constraints.maxWidth - ringsAndGapPx)
        }
        if (sideBySide) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EditorRings(base = base, fractions = fractions, description = description, reduced = reduced)
                Spacer(Modifier.width(RingsGap))
                Column(modifier = Modifier.weight(1f)) {
                    EditorFigure(base = base, style = KpknType.metricL)
                    Spacer(Modifier.height(4.dp))
                    EditorCaption(macros, numeric = true)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                EditorFigure(base = base, style = KpknType.metricXL)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditorRings(base = base, fractions = fractions, description = description, reduced = reduced)
                    Spacer(Modifier.width(RingsGap))
                    EditorCaption(macros, numeric = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EditorRings(
    base: NutritionEditorBase?,
    fractions: MacroRingFractions,
    description: String,
    reduced: Boolean,
) {
    // La cifra va a la derecha, no dentro del anillo (como en Home): por eso `showCenter` es falso.
    WizardMacroRings(
        kcal = base?.caloriesKcal ?: 0,
        fractions = fractions,
        caption = "kcal",
        description = description,
        appeared = true,
        reducedMotion = reduced,
        modifier = Modifier.size(RingsSize),
        showCenter = false,
    )
}

@Composable
private fun EditorFigure(base: NutritionEditorBase?, style: TextStyle) {
    KpknMetric(
        value = base?.let { formatKcalEs(it.caloriesKcal) } ?: "—",
        unit = if (base == null) null else "kcal",
        style = style,
    )
}

/**
 * Un macro: un punto de su color y su nombre arriba, y el campo numérico debajo con la unidad («g») dentro. El nombre va en una sola línea,
 * y el punto y el campo quedan alineados en la misma columna en cualquier escala de letra.
 */
@Composable
private fun EditorMacroCard(
    label: String,
    color: Color,
    value: Int?,
    percent: Int?,
    gramsPerKg: Double?,
    onValue: (Double) -> Unit,
) {
    var inputText by remember { mutableStateOf(value?.toString().orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var userTyped by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused || !userTyped) {
            inputText = value?.toString().orEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KpknSurface.inset)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = KpknType.bodyStrong, color = WizardColors.text)
            }
            val meta = listOfNotNull(
                percent?.let { "$it %" },
                gramsPerKg?.let { String.format(java.util.Locale.getDefault(), "%.1f g/kg", it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpknTextAction(
                text = "−5 g",
                onClick = {
                    val next = ((value ?: 0) - 5).coerceAtLeast(0)
                    userTyped = false
                    inputText = next.toString()
                    onValue(next.toDouble())
                },
            )
            EditorNumberField(
                value = inputText,
                onValueChange = { text ->
                    userTyped = true
                    inputText = text
                    parseLocalizedNumber(text)?.takeIf { it >= 0 }?.let(onValue)
                },
                unit = "g",
                placeholder = "—",
                keyboard = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focus ->
                        if (focus.hasFocus) {
                            hadFocus = true
                            focused = true
                        } else {
                            focused = false
                            if (hadFocus) {
                                val parsed = parseLocalizedNumber(inputText)?.takeIf { it >= 0 }
                                if (parsed != null) {
                                    if (userTyped && parsed.toInt() != value) {
                                        onValue(parsed)
                                    }
                                } else {
                                    inputText = value?.toString().orEmpty()
                                }
                                userTyped = false
                            }
                        }
                    },
            )
            KpknTextAction(
                text = "+5 g",
                onClick = {
                    val next = ((value ?: 0) + 5).coerceAtLeast(0)
                    userTyped = false
                    inputText = next.toString()
                    onValue(next.toDouble())
                },
            )
        }
    }
}

/** Barra apilada de 8 dp con el reparto de energía de P, H y G, en los colores de los macros. Es decorativa: la lee la leyenda. */
@Composable
private fun EditorShareBar(percents: Triple<Int, Int, Int>) {
    val (protein, carbs, fat) = percents
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(KpknShape.pill)
            .background(KpknSurface.inset)
            .clearAndSetSemantics { },
    ) {
        if (protein > 0) Box(Modifier.weight(protein.toFloat()).fillMaxHeight().background(MacroColors.protein))
        if (carbs > 0) Box(Modifier.weight(carbs.toFloat()).fillMaxHeight().background(MacroColors.carbs))
        if (fat > 0) Box(Modifier.weight(fat.toFloat()).fillMaxHeight().background(MacroColors.fat))
    }
}

/** ¿Cabe la cifra junto a los anillos? La misma medida que Home: una cifra de cinco dígitos en `metricL` más su unidad. */
private fun figureFits(measurer: TextMeasurer, density: Density, availablePx: Int): Boolean {
    val value = measurer.measure(text = formatKcalEs(FigureSampleKcal), style = KpknType.metricL, maxLines = 1).size.width
    val unit = measurer.measure(text = "kcal", style = KpknType.unit, maxLines = 1).size.width
    val gapPx = with(density) { 3.dp.roundToPx() }
    return value + gapPx + unit <= availablePx
}
