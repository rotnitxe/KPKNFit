package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.roundToInt

private val KpiValueStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 18.sp, letterSpacing = (-0.4).sp)
private val SizeStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp)
private val DropStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)

/** El nombre de cada tamaño en la hoja de ajuste (decisión F-D1, bloque `sizes` del lienzo). */
fun loggerSizeLabel(preset: PortionPreset): String = when (preset) {
    PortionPreset.SMALL -> "Pequeña"
    PortionPreset.MEDIUM -> "Mediana"
    PortionPreset.LARGE -> "Grande"
    PortionPreset.EXTRA -> "Extra"
}

/** Diámetro del círculo de cada tamaño (22, 30, 38 y 46 px en el lienzo). */
private fun sizeDiameter(preset: PortionPreset): Int = when (preset) {
    PortionPreset.SMALL -> 22
    PortionPreset.MEDIUM -> 30
    PortionPreset.LARGE -> 38
    PortionPreset.EXTRA -> 46
}

/** «Coincidencia alta · base KPKN» (f-edit): cuánto encaja lo elegido con lo escrito y de dónde salen sus datos. */
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

/** Cómo se cocinó (f-edit, `drop3`): con aceite, sin grasa o con mantequilla. */
enum class LoggerCooking(val label: String) { OIL("Con aceite"), NO_FAT("Sin grasa"), BUTTER("Con mantequilla") }

/** Una variante del alimento (decisión F-D2: las chips N50 viven en la hoja), con su grupo y si es la elegida. */
data class LoggerVariantChip(val group: String, val id: String, val label: String, val selected: Boolean)

/** Lo que la hoja de ajuste necesita de un alimento: todo ya calculado por la hoja del registro. */
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
    val portion: PortionPreset?,
    val cooking: LoggerCooking?,
    val variants: List<LoggerVariantChip> = emptyList(),
    val excluded: Boolean = false,
    val volume: Boolean = false,
)

/**
 * Ajustar un alimento (lienzo f-edit), en una hoja: icono de 48 dp, nombre, «Coincidencia alta · base KPKN» y «Cambiar» ([onChange]);
 * «Cantidad» con −/+ en la unidad natural y las chips de unidad; los tamaños (F-D1); «Cómo lo cocinaste» si cocinar cambia algo;
 * las variantes y «Excluir» (F-D2); «Aporta» con cuatro cifras que se corrigen al tocarlas; la huella ([footprint], la pinta otro
 * paquete); la papelera roja y «Listo».
 */
@Composable
fun LoggerFoodSheet(
    state: LoggerFoodSheetState,
    onGramsChange: (Double) -> Unit,
    onPortionChange: (PortionPreset) -> Unit,
    onCookingChange: (LoggerCooking) -> Unit,
    onVariant: (String) -> Unit,
    onToggleExcluded: () -> Unit,
    onMacroChange: (LoggerMacro, Double) -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    footprint: (@Composable () -> Unit)? = null,
) {
    var unitIndex by remember(state.name) { mutableStateOf(0) }
    val unit = state.units.getOrNull(unitIndex) ?: state.units.firstOrNull() ?: LoggerUnit(LoggerUnitKind.GRAMS, "gramo", "gramos", 1.0)
    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(state.icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = LoggerColors.ink)
                }
                Column(Modifier.weight(1f)) {
                    Text(text = state.name, style = LoggerText.sheetTitle, color = LoggerColors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = state.matchLine, style = LoggerText.cap, color = LoggerColors.faint)
                }
                LoggerAct(text = "Cambiar", onClick = onChange)
            }

            LoggerSheetGroup("Cantidad")
            val value = loggerUnitValue(state.grams, unit)
            val unitLabel = if (unit.kind == LoggerUnitKind.GRAMS) (if (state.volume) "ml" else "g") else loggerUnitNoun(value, unit)
            // Tocar la cifra la deja escribir (la cantidad exacta, con decimales: «1,5» raciones o «75,5» g), como las cifras de «Aporta».
            var typing by remember(state.name, unitIndex) { mutableStateOf(false) }
            var typed by remember(state.name, unitIndex, typing) { mutableStateOf(loggerFormatValue(value, unit).replace("½", ",5").let { if (it.startsWith(",")) "0$it" else it }) }
            val commitTyped = {
                loggerParseAmount(typed)?.let { amount -> onGramsChange(amount * unit.gramsPerUnit) }
                typing = false
            }
            LoggerStepper(
                value = loggerFormatValue(value, unit),
                unit = unitLabel,
                onMinus = { onGramsChange(loggerStepGrams(state.grams, unit, -1)) },
                onPlus = { onGramsChange(loggerStepGrams(state.grams, unit, +1)) },
                minusEnabled = state.grams > loggerMinGrams(unit) + 1e-6,
                onValueClick = { typing = true },
                valueContent = if (typing) {
                    {
                        Row(verticalAlignment = Alignment.Bottom) {
                            BasicTextField(
                                value = typed,
                                onValueChange = { text -> typed = text.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                                singleLine = true,
                                textStyle = LoggerText.figure(30, FontWeight.ExtraBold).copy(color = LoggerColors.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                                cursorBrush = SolidColor(LoggerColors.ink),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commitTyped() }),
                                modifier = Modifier.width(110.dp).semantics { contentDescription = "Cantidad en $unitLabel" },
                            )
                            Text(text = unitLabel, style = LoggerText.cap, color = LoggerColors.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        }
                    }
                } else null,
            )
            if (state.units.size > 1) {
                Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.units.forEachIndexed { index, option ->
                        LoggerChip(text = option.chipLabel, selected = index == unitIndex, onClick = { unitIndex = index }, height = 34.dp)
                    }
                }
            }
            state.portion?.let { selected ->
                Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    PortionPreset.entries.forEach { preset ->
                        val on = preset == selected
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(role = Role.RadioButton) { onPortionChange(preset) }
                                .semantics { this.selected = on },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val d = sizeDiameter(preset).dp
                            Box(
                                Modifier
                                    .size(d)
                                    .clip(CircleShape)
                                    .background(if (on) LoggerColors.ink else Color.Transparent, CircleShape)
                                    .border(1.5.dp, if (on) Color.Transparent else LoggerColors.ink.copy(alpha = 0.333f), CircleShape),
                            )
                            Text(
                                text = loggerSizeLabel(preset),
                                style = if (on) SizeStyle.copy(fontWeight = FontWeight.SemiBold) else SizeStyle,
                                color = if (on) LoggerColors.ink else LoggerColors.faint,
                            )
                        }
                    }
                }
            }

            state.cooking?.let { cooking ->
                LoggerSheetGroup("Cómo lo cocinaste")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        LoggerCooking.OIL to LoggerIcons.Pan,
                        LoggerCooking.NO_FAT to LoggerIcons.Raw,
                        LoggerCooking.BUTTER to LoggerIcons.Drop,
                    ).forEach { (option, icon) ->
                        val on = option == cooking
                        val shape = RoundedCornerShape(18.dp)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .background(Color.White.copy(alpha = 0.031f), shape)
                                .border(if (on) 2.dp else 1.dp, if (on) LoggerColors.ink else Color.White.copy(alpha = 0.07f), shape)
                                .clickable(role = Role.RadioButton) { onCookingChange(option) }
                                .semantics { this.selected = on }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = LoggerColors.ink)
                            Text(text = option.label, style = DropStyle, color = LoggerColors.ink, maxLines = 1)
                        }
                    }
                }
            }

            if (state.variants.isNotEmpty()) {
                LoggerSheetGroup("Variante")
                state.variants.groupBy { it.group }.values.forEach { group ->
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        group.forEach { chip ->
                            LoggerChip(text = chip.label, selected = chip.selected, onClick = { if (!chip.selected) onVariant(chip.id) })
                        }
                    }
                }
            }

            LoggerSheetGroup("Aporta")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LoggerKpiField(state.kcal.toDouble(), "kcal", "kcal", Modifier.weight(1f)) { onMacroChange(LoggerMacro.CALORIES, it) }
                LoggerKpiField(state.protein, "g", "prot.", Modifier.weight(1f)) { onMacroChange(LoggerMacro.PROTEIN, it) }
                LoggerKpiField(state.carbs, "g", "carb.", Modifier.weight(1f)) { onMacroChange(LoggerMacro.CARBS, it) }
                LoggerKpiField(state.fats, "g", "grasa", Modifier.weight(1f)) { onMacroChange(LoggerMacro.FATS, it) }
            }
            Text(
                text = "Toca una cifra para corregirla a mano.",
                style = LoggerText.cap,
                color = LoggerColors.faint,
                modifier = Modifier.padding(top = 8.dp),
            )
            footprint?.invoke()
            LoggerMenuRow(
                icon = if (state.excluded) KpknLineIcons.Plus else KpknLineIcons.Minus,
                title = if (state.excluded) "Incluir en la comida" else "Excluir de la comida",
                subtitle = if (state.excluded) "Ahora no cuenta en el total" else "Queda en la lista sin contar en el total",
                onClick = onToggleExcluded,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoggerPill(
                    text = "",
                    onClick = onRemove,
                    tone = LoggerPillTone.SECONDARY,
                    icon = KpknLineIcons.Trash,
                    contentColor = LoggerColors.danger,
                    contentDescription = "Quitar ${state.name}",
                )
                LoggerPill(text = "Listo", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Una cifra de «Aporta» (`.kpi`): valor en Syne 16 y su nombre debajo. Al tocarla se escribe con el teclado numérico; se aplica al
 * confirmar o al salir del campo.
 */
@Composable
private fun LoggerKpiField(value: Double, unit: String, label: String, modifier: Modifier = Modifier, onCommit: (Double) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value.roundToInt().toString()) }
    val shown = if (unit == "kcal") value.roundToInt().toString() else "${value.roundToInt()} g"
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f), shape)
            .clickable(role = Role.Button) { editing = true }
            .semantics { contentDescription = "$label: $shown. Corregir" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { text -> draft = text.filter { it.isDigit() || it == ',' || it == '.' }.take(5) },
                singleLine = true,
                textStyle = KpiValueStyle.copy(color = LoggerColors.ink),
                cursorBrush = SolidColor(LoggerColors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    draft.replace(',', '.').toDoubleOrNull()?.let { onCommit(it.coerceAtLeast(0.0)) }
                    editing = false
                }),
            )
        } else {
            Text(text = shown, style = KpiValueStyle, color = LoggerColors.ink, maxLines = 1)
        }
        Text(text = label, style = LoggerText.cap, color = LoggerColors.faint, maxLines = 1)
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
