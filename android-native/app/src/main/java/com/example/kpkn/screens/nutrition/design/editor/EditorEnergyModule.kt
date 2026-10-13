package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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
 * apilado que Home. Debajo, la base editable: las calorías, los tres macros con su color, el reparto de energía y las lecturas de la base
 * revisada. Si la meta de hoy ya está fijada por este plan, lo dice. Sin estado.
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
) {
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Calorías y macros")
        Spacer(Modifier.height(16.dp))
        EditorEnergyHero(base = base, eerKcal = eerKcal)
        Spacer(Modifier.height(16.dp))
        EditorNumberField(
            value = base?.caloriesKcal?.toString().orEmpty(),
            onValueChange = { text -> editorCaloriesFromText(text)?.let(onCalories) },
            label = "Calorías",
            unit = "kcal",
            placeholder = "—",
            keyboard = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorMacroField(
                label = "Proteína (g)",
                color = MacroColors.protein,
                value = base?.proteinG,
                onValue = onProtein,
                modifier = Modifier.weight(1f),
            )
            EditorMacroField(
                label = "Hidratos (g)",
                color = MacroColors.carbs,
                value = base?.carbsG,
                onValue = onCarbs,
                modifier = Modifier.weight(1f),
            )
            EditorMacroField(
                label = "Grasas (g)",
                color = MacroColors.fat,
                value = base?.fatG,
                onValue = onFat,
                modifier = Modifier.weight(1f),
            )
        }
        editorEnergyPercents(base)?.let { percents ->
            Spacer(Modifier.height(12.dp))
            EditorShareBar(percents)
            Spacer(Modifier.height(6.dp))
            EditorCaption(editorEnergyLegend(percents), numeric = true)
        }
        base?.let { revised ->
            Spacer(Modifier.height(12.dp))
            EditorBaseRevised(revised)
            editorAtwaterLine(revised)?.let { line ->
                Spacer(Modifier.height(4.dp))
                EditorCaption(line)
            }
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

/** Un macro: punto de su color y etiqueta arriba, y el campo numérico debajo. La etiqueta va en la misma columna que su campo. */
@Composable
private fun EditorMacroField(
    label: String,
    color: Color,
    value: Int?,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Dos líneas de alto para todas: así los tres campos quedan a la misma altura aunque una etiqueta se parta (130 %).
        Row(
            modifier = Modifier.heightIn(min = 36.dp).padding(start = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EditorNumberField(
            value = value?.toString().orEmpty(),
            onValueChange = { text -> parseLocalizedNumber(text)?.takeIf { it >= 0 }?.let(onValue) },
            placeholder = "—",
            keyboard = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * La base revisada en dos líneas: la frase en la primera y las cifras en la segunda, para que ninguna cifra quede partida de su unidad.
 * La semántica lleva la frase entera, tal como la lee la prueba instrumentada y el lector de pantalla.
 */
@Composable
private fun EditorBaseRevised(base: NutritionEditorBase) {
    Column(modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(editorBaseRevisedLine(base)) }) {
        EditorCaption(EDITOR_BASE_REVISED_LEAD)
        EditorCaption(editorBaseRevisedFigures(base), numeric = true)
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
