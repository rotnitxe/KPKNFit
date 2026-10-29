package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.PORTION_MULTIPLIERS
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.domain.nutrition.FoodPreparation
import com.example.kpkn.domain.nutrition.FoodPreparations
import com.example.kpkn.domain.nutrition.OilAmount
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.LocalKpknSheetDismiss
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Arreglos v1 · «Ajustar un alimento» (lienzo `npSheetInner`, escenas a-sheet, a-horno, a-cocido, a-aporta): hoja plana (#141414) con
 * la cabecera, «Cantidad» (stepper y Gramos | Raciones), «Tamaño» en cuatro tramos, «Cómo lo preparaste» con la fila «Aceite»,
 * «Aporta» y la huella. Las letras de 11-12 px del lienzo suben a 13 sp (mínimo de la app); las cifras se encogen si no caben.
 */

/** `.sheet h3` dentro de `.np-sh-h`: Syne Bold 19. */
private val TitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 19.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.4).sp,
)

/** `.np-step b`: la cantidad en Inter SemiBold 26 tabular, −0,3. */
private val AmountStyle = LoggerText.number(26f).copy(letterSpacing = (-0.3).sp)

/** `.np-step small`: la unidad en Inter Medium 14. */
private val AmountUnitStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp)

/** `.np-seg button`: Inter Medium 13. */
private val SegmentStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)

/** `.np-seg button small`: 11,5 px en el lienzo, 13 sp en la app. */
private val SegmentSmallStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp)

/** `.np-prep button`: Inter Medium 13,5. */
private val PrepStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp)

/** `.np-oil-h` y `.np-oil-none`: 12,5 px en el lienzo, 13 sp en la app. */
private val OilStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp)

/** `.np-k span`: «kcal» en Inter Medium 13. */
private val KcalUnitStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)

/** `.np-row .tx strong`: Inter SemiBold 14. */
private val RowTitleStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)

/** El nombre de cada tamaño en la hoja de ajuste. */
fun loggerSizeLabel(preset: PortionPreset): String = when (preset) {
    PortionPreset.SMALL -> "Pequeña"
    PortionPreset.MEDIUM -> "Mediana"
    PortionPreset.LARGE -> "Grande"
    PortionPreset.EXTRA -> "Extra"
}

/** Un tramo de «Tamaño»: el tamaño y sus gramos reales. */
data class LoggerSizeOption(val preset: PortionPreset, val grams: Double)

/** Los cuatro tramos de «Tamaño» a partir de la cantidad de referencia [anchorGrams] (Mediana), con los multiplicadores de la app. */
fun loggerSizeOptions(anchorGrams: Double): List<LoggerSizeOption> =
    if (!anchorGrams.isFinite() || anchorGrams <= 0.0) emptyList()
    else PortionPreset.entries.map { LoggerSizeOption(it, anchorGrams * (PORTION_MULTIPLIERS[it] ?: 1.0)) }

/** El tramo que coincide con [grams] (al medio gramo); null si la cantidad no es la de ningún tramo. */
fun loggerSelectedSize(sizes: List<LoggerSizeOption>, grams: Double): PortionPreset? =
    sizes.firstOrNull { abs(it.grams - grams) < 0.5 }?.preset

/** «Coincidencia alta · base KPKN»: cuánto encaja lo elegido con lo escrito y de dónde salen sus datos. */
fun loggerMatchLine(confidence: Double?, estimated: Boolean, source: String): String {
    val level = when {
        estimated -> "Estimación"
        confidence == null || confidence >= 0.8 -> "Coincidencia alta"
        confidence >= 0.5 -> "Coincidencia media"
        else -> "Coincidencia baja"
    }
    return "$level · $source"
}

/** Lo escrito en la cifra de «Cantidad» («1,5», «75.5»): null si no es una cantidad mayor que cero. */
fun loggerParseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

/**
 * Las unidades de una receta registrada: por raciones («ración / raciones», una ración por unidad, sin pasar por gramos) si se registró
 * así, o por gramos si se pesó.
 */
fun loggerRecipeUnits(loggedUnit: String): List<LoggerUnit> =
    if (loggedUnit.equals("g", ignoreCase = true) || loggedUnit.equals("ml", ignoreCase = true)) {
        listOf(LoggerUnit(LoggerUnitKind.GRAMS, "gramo", "gramos", 1.0))
    } else {
        listOf(LoggerUnit(LoggerUnitKind.SERVINGS, "ración", "raciones", 1.0))
    }

/** Lo que la hoja de ajuste necesita de un alimento: todo ya calculado por el registro. */
data class LoggerFoodSheetState(
    val name: String,
    val icon: ImageVector,
    val matchLine: String,
    val grams: Double,
    val units: List<LoggerUnit>,
    val kcal: Int,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
    /** Los tramos de «Tamaño»; vacío los oculta (recetas). */
    val sizes: List<LoggerSizeOption> = emptyList(),
    /** Las chips de «Cómo lo preparaste», en su orden; vacío oculta el bloque. */
    val preparations: List<FoodPreparation> = emptyList(),
    val preparation: FoodPreparation? = null,
    /** La opción marcada en «Aceite»; null, ninguna. */
    val oil: OilAmount? = null,
    /** Las kcal del aceite de la cabecera de la fila («+222 kcal»). */
    val oilKcal: Int = 0,
    val excluded: Boolean = false,
    val volume: Boolean = false,
)

/**
 * Ajustar un alimento (Arreglos v1, `npSheetInner`): hoja plana con la cabecera (icono, nombre, «Coincidencia alta · base KPKN» y
 * «Cambiar» → [onChange]); «Cantidad» con − y + y «Gramos | Raciones»; «Tamaño» en cuatro tramos con sus gramos; «Cómo lo
 * preparaste» (una sola elección) con la fila «Aceite» o la línea que dice que no lleva; «Aporta» (las cifras se corrigen al
 * tocarlas); la huella ([footprint], la pinta el paquete de nutrientes); «Excluir de la comida», la papelera y «Listo».
 */
@Composable
fun LoggerFoodSheet(
    state: LoggerFoodSheetState,
    onGramsChange: (Double) -> Unit,
    onSizeChange: (LoggerSizeOption) -> Unit,
    onPreparationChange: (FoodPreparation) -> Unit,
    onOilChange: (OilAmount) -> Unit,
    onToggleExcluded: () -> Unit,
    onMacroChange: (LoggerMacro, Double) -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    footprint: (@Composable () -> Unit)? = null,
) {
    var unitIndex by remember(state.name) { mutableStateOf(0) }
    val unit = state.units.getOrNull(unitIndex) ?: state.units.firstOrNull() ?: LoggerUnit(LoggerUnitKind.GRAMS, "gramo", "gramos", 1.0)
    val massUnit = if (state.volume) "ml" else "g"
    KpknSheet(
        onDismissRequest = onDismiss,
        backdropScrim = Color.Black.copy(alpha = 0.67f),
        surfaceColor = LoggerColors.sheet,
    ) {
        val close = LocalKpknSheetDismiss.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            // `.np-sh-h`: icono en caja de 40, nombre y procedencia, «Cambiar».
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(LoggerColors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(state.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = LoggerColors.ink)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.name,
                        style = TitleStyle,
                        color = LoggerColors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Text(text = state.matchLine, style = LoggerText.cap, color = LoggerColors.faint)
                }
                LoggerAct(text = "Cambiar", onClick = onChange, modifier = Modifier.padding(horizontal = 4.dp))
            }

            LoggerPageGroup("Cantidad")
            val value = loggerUnitValue(state.grams, unit)
            val unitLabel = if (unit.kind == LoggerUnitKind.GRAMS) massUnit else loggerUnitNoun(value, unit)
            // Tocar la cifra la deja escribir (con decimales: «1,5» raciones o «75,5» g), como las cifras de «Aporta».
            var typing by remember(state.name, unitIndex) { mutableStateOf(false) }
            var typed by remember(state.name, unitIndex, typing) {
                mutableStateOf(loggerFormatValue(value, unit).replace("½", ",5").let { if (it.startsWith(",")) "0$it" else it })
            }
            val commitTyped = {
                loggerParseAmount(typed)?.let { amount -> onGramsChange(amount * unit.gramsPerUnit) }
                typing = false
            }
            LoggerAmountStepper(
                value = loggerFormatValue(value, unit),
                unit = unitLabel,
                minusEnabled = state.grams > loggerMinGrams(unit) + 1e-6,
                onMinus = { onGramsChange(loggerStepGrams(state.grams, unit, -1)) },
                onPlus = { onGramsChange(loggerStepGrams(state.grams, unit, +1)) },
                onValueClick = { typing = true },
                editor = if (typing) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = typed,
                                onValueChange = { text -> typed = text.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                                singleLine = true,
                                textStyle = AmountStyle.copy(color = LoggerColors.ink, textAlign = TextAlign.Center),
                                cursorBrush = SolidColor(LoggerColors.ink),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commitTyped() }),
                                modifier = Modifier.width(110.dp).semantics { contentDescription = "Cantidad en $unitLabel" },
                            )
                            Text(text = unitLabel, style = AmountUnitStyle, color = LoggerColors.faint, modifier = Modifier.padding(start = 5.dp))
                        }
                    }
                } else null,
            )
            if (state.units.size > 1) {
                LoggerSegmented(
                    labels = state.units.map { it.chipLabel },
                    selected = state.units.indexOf(unit),
                    soft = true,
                    onSelect = { unitIndex = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.sizes.isNotEmpty()) {
                LoggerPageGroup("Tamaño")
                val selectedSize = loggerSelectedSize(state.sizes, state.grams)
                LoggerSegmented(
                    labels = state.sizes.map { loggerSizeLabel(it.preset) },
                    smalls = state.sizes.map { "${it.grams.roundToInt()} $massUnit" },
                    selected = state.sizes.indexOfFirst { it.preset == selectedSize }.takeIf { it >= 0 },
                    soft = false,
                    onSelect = { index -> onSizeChange(state.sizes[index]) },
                )
            }

            if (state.preparations.isNotEmpty()) {
                LoggerPageGroup("Cómo lo preparaste")
                LoggerPreparationChips(state.preparations, state.preparation, onPreparationChange)
                val oilOptions = FoodPreparations.oilOptions(state.preparation)
                if (oilOptions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Aceite", style = OilStyle, color = LoggerColors.faint, modifier = Modifier.weight(1f))
                        LoggerFitText(
                            text = FoodPreparations.oilCaption(state.oilKcal),
                            style = LoggerText.number(13f, FontWeight.Medium),
                            color = LoggerColors.muted,
                            minSize = 11.sp,
                        )
                    }
                    LoggerSegmented(
                        labels = oilOptions.map { it.label },
                        selected = oilOptions.indexOf(state.oil).takeIf { it >= 0 },
                        soft = true,
                        onSelect = { index -> onOilChange(oilOptions[index]) },
                    )
                } else {
                    FoodPreparations.noOilLine(state.preparation)?.let { line ->
                        Text(
                            text = line,
                            style = OilStyle,
                            color = LoggerColors.faint,
                            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 12.dp),
                        )
                    }
                }
            }

            LoggerSheetSeparator()
            LoggerContribution(state = state, massUnit = massUnit, onMacroChange = onMacroChange)

            footprint?.let { slot ->
                LoggerSheetSeparator()
                Column(Modifier.fillMaxWidth().padding(top = 14.dp)) { slot() }
            }

            // `.np-row`: excluir sin quitar.
            Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(LoggerColors.hair))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onToggleExcluded)
                        .padding(horizontal = 2.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = if (state.excluded) "Incluir en la comida" else "Excluir de la comida",
                        style = RowTitleStyle,
                        color = LoggerColors.ink,
                    )
                    Text(
                        text = if (state.excluded) "Ahora no cuenta en el total" else "Queda en la lista sin contar en el total",
                        style = LoggerText.cap,
                        color = LoggerColors.faint,
                    )
                }
            }
            // `.np-foot`: la papelera (52, filete blanco al 11 %, rojo) y «Listo».
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.11f), CircleShape)
                        .clickable(role = Role.Button, onClick = onRemove)
                        .semantics { contentDescription = "Quitar ${state.name}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KpknLineIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp), tint = LoggerColors.danger)
                }
                LoggerPill(text = "Listo", onClick = close, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** `.np-sep`: filete de 1 dp entre bloques (20 dp arriba, 4 abajo). */
@Composable
private fun LoggerSheetSeparator() {
    Box(
        Modifier
            .padding(top = 20.dp, bottom = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(LoggerColors.hair),
    )
}

/**
 * `.np-step`: caja superficie 2, radio 24, 6 dp de relleno; − y + redondos de 44 dp en superficie 3; la cifra en Inter SemiBold 26 con
 * su unidad al lado en Inter Medium 14 gris tenue. Tocar la cifra la deja escribir ([editor]).
 */
@Composable
private fun LoggerAmountStepper(
    value: String,
    unit: String,
    minusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueClick: () -> Unit,
    editor: (@Composable () -> Unit)?,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LoggerColors.surface2, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoggerRoundStep(KpknLineIcons.Minus, "Menos $unit", minusEnabled, onMinus)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .clickable(role = Role.Button, onClickLabel = "Escribir la cantidad", onClick = onValueClick),
            contentAlignment = Alignment.Center,
        ) {
            if (editor != null) {
                editor()
            } else {
                Row {
                    LoggerFitText(
                        text = value,
                        style = AmountStyle,
                        color = LoggerColors.ink,
                        minSize = 16.sp,
                        modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                    )
                    if (unit.isNotEmpty()) {
                        Text(
                            text = unit,
                            style = AmountUnitStyle,
                            color = LoggerColors.faint,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 5.dp).alignByBaseline(),
                        )
                    }
                }
            }
        }
        LoggerRoundStep(KpknLineIcons.Plus, "Más $unit", true, onPlus)
    }
}

@Composable
private fun LoggerRoundStep(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(CircleShape)
            .background(LoggerColors.surface3, CircleShape)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = LoggerColors.ink)
    }
}

/**
 * `.np-seg`: pista superficie 2, radio 18, 4 dp de relleno y 3 entre tramos; cada tramo de 38 dp como mínimo, radio 14, con su
 * [smalls] debajo si lo hay. Elegido: tinta con texto negro; con [soft] (`.np-seg.soft`), superficie 3 con texto tinta. Sin [selected],
 * ninguno marcado.
 */
@Composable
private fun LoggerSegmented(
    labels: List<String>,
    selected: Int?,
    soft: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    smalls: List<String>? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LoggerColors.surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            val fill = when {
                !on -> Color.Transparent
                soft -> LoggerColors.surface3
                else -> LoggerColors.ink
            }
            val ink = when {
                !on -> LoggerColors.muted
                soft -> LoggerColors.ink
                else -> LoggerColors.onInk
            }
            val smallInk = if (on && !soft) LoggerColors.onInk.copy(alpha = 0.73f) else LoggerColors.faint
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(fill)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoggerFitText(
                    text = label,
                    style = SegmentStyle.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = ink,
                    minSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
                smalls?.getOrNull(index)?.let { small ->
                    LoggerFitText(
                        text = small,
                        style = SegmentSmallStyle.copy(fontFeatureSettings = "'lnum' 1, 'tnum' 1"),
                        color = smallInk,
                        minSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** `.np-prep`: chips de texto de 38 dp (superficie 2; elegida, tinta con texto negro), 6 dp entre ellas, sin iconos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoggerPreparationChips(
    preparations: List<FoodPreparation>,
    selected: FoodPreparation?,
    onSelect: (FoodPreparation) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        preparations.forEach { preparation ->
            val on = preparation == selected
            val shape = RoundedCornerShape(19.dp)
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(shape)
                    .background(if (on) LoggerColors.ink else LoggerColors.surface2, shape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { if (!on) onSelect(preparation) })
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = preparation.label,
                    style = PrepStyle.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) LoggerColors.onInk else LoggerColors.muted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * «Aporta» (`.np-mod` en la hoja): «APORTA» con los gramos a la derecha; las kcal en Syne 30 con «kcal»; la barra apilada de 8 dp en
 * los tres tonos de tinta; las tres columnas de macros. Tocar cualquier cifra la deja corregir a mano.
 */
@Composable
private fun LoggerContribution(state: LoggerFoodSheetState, massUnit: String, onMacroChange: (LoggerMacro, Double) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Aporta".uppercase(java.util.Locale.forLanguageTag("es")),
                style = LoggerText.eyebrow,
                color = LoggerColors.muted,
                modifier = Modifier.weight(1f),
            )
            Text(text = "${state.grams.roundToInt()} $massUnit", style = LoggerText.cap, color = LoggerColors.faint, maxLines = 1)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            LoggerEditableFigure(
                shown = loggerThousands(state.kcal),
                draftOf = state.kcal.toString(),
                style = LoggerText.total,
                minSize = 20.sp,
                description = "kcal: ${state.kcal}. Corregir",
                onCommit = { onMacroChange(LoggerMacro.CALORIES, it) },
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            )
            Text(
                text = "kcal",
                style = KcalUnitStyle,
                color = LoggerColors.faint,
                modifier = Modifier.padding(start = 6.dp).alignByBaseline(),
            )
        }
        LoggerMacroSplitBar(
            protein = state.protein,
            carbs = state.carbs,
            fats = state.fats,
            height = 8.dp,
            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(LoggerMacro.PROTEIN, state.protein, "Proteína" to LoggerColors.macroP),
                Triple(LoggerMacro.CARBS, state.carbs, "Carbos" to LoggerColors.macroC),
                Triple(LoggerMacro.FATS, state.fats, "Grasa" to LoggerColors.macroF),
            ).forEach { (macro, grams, named) ->
                Column(modifier = Modifier.weight(1f)) {
                    LoggerEditableFigure(
                        shown = "${grams.roundToInt()} g",
                        draftOf = grams.roundToInt().toString(),
                        style = LoggerText.number(16f),
                        minSize = 12.sp,
                        description = "${named.first}: ${grams.roundToInt()} g. Corregir",
                        onCommit = { onMacroChange(macro, it) },
                    )
                    Row(modifier = Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(named.second))
                        Text(
                            text = named.first,
                            style = LoggerText.cap,
                            color = LoggerColors.faint,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = "Toca una cifra para corregirla a mano.",
            style = LoggerText.cap,
            color = LoggerColors.faint,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Una cifra de «Aporta» que se encoge si no cabe y que, al tocarla, se escribe con el teclado numérico (se aplica con «Listo»). */
@Composable
private fun LoggerEditableFigure(
    shown: String,
    draftOf: String,
    style: TextStyle,
    minSize: androidx.compose.ui.unit.TextUnit,
    description: String,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(draftOf, editing) { mutableStateOf(draftOf) }
    Box(
        modifier = modifier
            .clickable(role = Role.Button) { editing = true }
            .semantics { contentDescription = description },
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { text -> draft = text.filter { it.isDigit() || it == ',' || it == '.' }.take(5) },
                singleLine = true,
                textStyle = style.copy(color = LoggerColors.ink),
                cursorBrush = SolidColor(LoggerColors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    draft.replace(',', '.').toDoubleOrNull()?.let { onCommit(it.coerceAtLeast(0.0)) }
                    editing = false
                }),
                modifier = Modifier.width(110.dp),
            )
        } else {
            LoggerFitText(text = shown, style = style, color = LoggerColors.ink, minSize = minSize)
        }
    }
}

/** Un candidato de la duda de identidad (f-identity): nombre, «2 rebanadas · 158 kcal» y si es «Muy probable» o «Posible». */
data class LoggerCandidate(val id: String, val name: String, val caption: String, val likely: Boolean)

/**
 * «¿Cuál se parece más?» (lienzo f-identity): «Escribiste «tostadas»», los candidatos con su radio, su ración y cuánto encajan, «Buscar
 * otro» (abre un campo dentro de la hoja; los resultados llegan en [searchResults]) y «Usar este».
 */
@Composable
fun LoggerIdentitySheet(
    typed: String,
    candidates: List<LoggerCandidate>,
    onUse: (String) -> Unit,
    onDismiss: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<LoggerCandidate>?,
    initiallySearching: Boolean = false,
) {
    var selected by remember(candidates) { mutableStateOf(candidates.firstOrNull()?.id) }
    var searching by remember { mutableStateOf(initiallySearching || candidates.isEmpty()) }
    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            Text(
                text = "Escribiste «$typed»".uppercase(java.util.Locale.forLanguageTag("es")),
                style = LoggerText.eyebrow,
                color = LoggerColors.faint,
            )
            LoggerSheetTitle(title = "¿Cuál se parece más?", modifier = Modifier.padding(top = 6.dp))
            val shown = if (searching && searchResults != null) searchResults else candidates
            shown.forEachIndexed { index, candidate ->
                if (index > 0) LoggerRowDivider()
                LoggerCandidateRow(candidate, candidate.id == selected) { selected = candidate.id }
            }
            if (searching) {
                LoggerSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onScan = {},
                    placeholder = "Busca otro alimento",
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                LoggerAct(
                    text = "Buscar otro",
                    onClick = { searching = true },
                    leadingIcon = KpknLineIcons.Search,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            LoggerPill(
                text = "Usar este",
                onClick = { selected?.let(onUse) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun LoggerCandidateRow(candidate: LoggerCandidate, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { selected = on }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (on) LoggerColors.ink else Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Box(Modifier.size(9.dp).clip(CircleShape).background(LoggerColors.ink))
        }
        Column(Modifier.weight(1f)) {
            Text(text = candidate.name, style = LoggerText.rowTitle.copy(fontSize = 14.sp), color = LoggerColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = candidate.caption, style = LoggerText.cap, color = LoggerColors.faint, maxLines = 1)
        }
        val (fg, bg) = if (candidate.likely) LoggerColors.done to LoggerColors.done.copy(alpha = 0.1f) else LoggerColors.ene to LoggerColors.ene.copy(alpha = 0.1f)
        Text(
            text = if (candidate.likely) "Muy probable" else "Posible",
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp),
            color = fg,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
