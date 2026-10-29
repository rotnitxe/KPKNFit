package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.util.Locale
import kotlin.math.roundToInt

/** Lo más que deja escribir la ficha: 10 kg. */
private const val MAX_GRAMS = 10_000.0

/**
 * La ficha de un alimento (lienzo s-detail), que se abre al tocar un resultado: el origen («KPKN · verificado»), el nombre, «Cuánto» con
 * −/+ en la unidad elegida (tocar la cifra deja escribirla), las unidades en chips, el resumen de calorías y macros a esa cantidad, el
 * hueco de la huella de vitaminas y minerales ([footprint], lo pinta otro paquete) y el pie con el favorito y «Añadir al desayuno».
 *
 * Arranca en [initialGrams] o, si no hay, en la ración que añade el «+» de la fila. Con [onEdit] aparece un ⋯ junto al origen que abre
 * una hoja con [editLabel] (S-D1); al elegirla se llama a [onEdit] y luego a [onDismiss]. [mealLabel] llega en minúscula («desayuno»).
 */
@Composable
fun LoggerFoodDetailSheet(
    food: FoodItem,
    query: String,
    mealLabel: String,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAdd: (grams: Double) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    editLabel: String = "Editar alimento propio",
    footprint: (@Composable (grams: Double) -> Unit)? = null,
    initialGrams: Double? = null,
) {
    val units = remember(food, query) { loggerUnits(food.name, food, query) }
    val volume = remember(food) { food.unit.trim().lowercase() in setOf("ml", "l") }
    var unitIndex by remember(food, query) { mutableIntStateOf(0) }
    var grams by remember(food, query) { mutableDoubleStateOf(initialGrams ?: loggerSearchPortion(food, query).grams) }
    var editing by remember(food, query) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val unit = units[unitIndex.coerceIn(0, units.lastIndex)]
    val scaled = remember(food, grams) { scaleFoodByPortion(food, amountGrams = grams) }
    val value = loggerUnitValue(grams, unit)
    val verified = remember(food) { NutrientBasis.isVerified(food) }

    val editContent: @Composable () -> Unit = {
        DetailAmountField(
            initial = loggerAmountText(value),
            unitLabel = detailUnitLabel(unit, value, volume),
            onType = { text ->
                val typed = text.replace(',', '.').trim().toDoubleOrNull()
                if (typed != null && typed > 0.0) {
                    grams = (typed * unit.gramsPerUnit).coerceIn(loggerMinGrams(unit), MAX_GRAMS)
                }
            },
            onFinish = { editing = false },
        )
    }

    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val origin = loggerSearchOrigin(food)
                Text(
                    text = (if (verified) "$origin · verificado" else origin).uppercase(Locale("es")),
                    style = LoggerText.eyebrow,
                    color = LoggerColors.muted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (onEdit != null) {
                    LoggerIconButton(icon = KpknLineIcons.More, contentDescription = "Más opciones", onClick = { menuOpen = true })
                }
            }
            LoggerSheetTitle(title = food.name, modifier = Modifier.padding(top = 4.dp))
            LoggerSheetGroup("Cuánto")
            LoggerStepper(
                value = loggerFormatValue(value, unit),
                unit = detailUnitLabel(unit, value, volume),
                spokenName = unit.plural,
                minusEnabled = grams > loggerMinGrams(unit) + 1e-6,
                onMinus = {
                    editing = false
                    grams = loggerStepGrams(grams, unit, -1)
                },
                onPlus = {
                    editing = false
                    grams = loggerStepGrams(grams, unit, 1).coerceAtMost(MAX_GRAMS)
                },
                onValueClick = if (editing) null else ({ editing = true }),
                valueContent = if (editing) editContent else null,
            )
            // Las chips tienen 48 dp de zona táctil; el margen de 10 dp del lienzo se descuenta de ella.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                units.forEachIndexed { index, option ->
                    LoggerChip(
                        text = option.chipLabel,
                        selected = index == unitIndex,
                        onClick = {
                            unitIndex = index
                            editing = false
                        },
                        height = 34.dp,
                    )
                }
            }
            LoggerSummaryCard(
                kcal = scaled.calories.roundToInt(),
                protein = scaled.protein,
                carbs = scaled.carbs,
                fats = scaled.fats,
                modifier = Modifier.padding(top = 14.dp),
            )
            footprint?.invoke(grams)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoggerPill(
                    text = "",
                    onClick = onToggleFavorite,
                    tone = LoggerPillTone.SECONDARY,
                    icon = if (favorite) LoggerIcons.StarFilled else LoggerIcons.Star,
                    contentColor = if (favorite) LoggerColors.ene else LoggerColors.muted,
                    contentDescription = if (favorite) "Quitar de favoritos" else "Añadir a favoritos",
                    modifier = Modifier.width(58.dp),
                )
                LoggerPill(text = "Añadir al $mealLabel", onClick = { onAdd(grams) }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (menuOpen && onEdit != null) {
        KpknSheet(onDismissRequest = { menuOpen = false }, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 30.dp)) {
                LoggerMenuRow(
                    icon = KpknLineIcons.Edit,
                    title = editLabel,
                    divider = false,
                    onClick = {
                        menuOpen = false
                        onEdit()
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** La unidad que acompaña a la cifra del stepper: «envase · 125 g», «raciones · 250 g», «g». */
private fun detailUnitLabel(unit: LoggerUnit, value: Double, volume: Boolean): String {
    if (unit.kind == LoggerUnitKind.GRAMS) return if (volume) "ml" else "g"
    val mass = "${unit.gramsPerUnit.roundToInt()} ${if (volume) "ml" else "g"}"
    return "${loggerUnitNoun(value, unit)} · $mass"
}

/**
 * La cifra del stepper mientras se escribe: Syne 30 con el teclado numérico y la unidad al lado. Cada cifra válida cambia la cantidad al
 * instante ([onType]), así que «Añadir» nunca deja nada sin aplicar; «Hecho» del teclado o perder el foco cierran el campo ([onFinish]).
 */
@Composable
private fun DetailAmountField(initial: String, unitLabel: String, onType: (String) -> Unit, onFinish: () -> Unit) {
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(verticalAlignment = Alignment.Bottom) {
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onType(it.text)
            },
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .widthIn(min = 28.dp)
                .focusRequester(focus)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true else if (hadFocus) onFinish()
                }
                .semantics { contentDescription = "Cantidad" },
            singleLine = true,
            textStyle = LoggerText.figure(30, FontWeight.ExtraBold).copy(color = LoggerColors.ink, textAlign = TextAlign.End),
            cursorBrush = SolidColor(LoggerColors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onFinish() }),
        )
        Text(
            text = unitLabel,
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp),
            color = LoggerColors.muted,
            modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
            maxLines = 1,
        )
    }
}
