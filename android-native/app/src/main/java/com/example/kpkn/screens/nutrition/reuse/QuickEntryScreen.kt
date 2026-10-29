package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.QuickEntry
import com.example.kpkn.domain.nutrition.QuickEntryField
import com.example.kpkn.domain.nutrition.QuickEntryForm
import com.example.kpkn.domain.nutrition.QuickEntryValidation
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerFieldBox
import com.example.kpkn.screens.nutrition.design.logger.LoggerPill
import com.example.kpkn.screens.nutrition.design.logger.LoggerSheetGroup
import com.example.kpkn.screens.nutrition.design.logger.LoggerSheetTitle
import com.example.kpkn.screens.nutrition.design.logger.LoggerStepper
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import java.util.UUID

/** Lo que suman o restan las kcal con − y + (`.stepper` del lienzo s-quick). */
private const val KCAL_STEP = 10.0

/** `.field div`: Inter 15 con números tabulares. */
private val FieldValueStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "'tnum' 1",
)

/**
 * El contenido de la hoja «Entrada rápida» (lienzo s-quick), sin estado: título y frase, las kcal en el stepper (−/+ de 10; tocar la
 * cifra deja escribir con el teclado numérico), «Macros (opcional)» en tres campos, el nombre opcional y «Añadir a la comida». La comida
 * la decide el registro que la abre. Los errores solo se enseñan tras pulsar ([showErrors]); el aviso de kcal que no cuadran se enseña
 * siempre que exista, como línea pequeña, y nunca bloquea.
 */
@Composable
fun QuickEntryContent(
    form: QuickEntryForm,
    validation: QuickEntryValidation,
    showErrors: Boolean,
    onChange: (QuickEntryForm) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun errorOf(field: QuickEntryField): String? = if (showErrors) validation.errors[field] else null
    val kcalNow = QuickEntry.parseNumber(form.kcal) ?: 0.0
    Column(
        modifier
            .fillMaxWidth()
            .testTag("quick-entry-root")
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
    ) {
        LoggerSheetTitle(title = "Entrada rápida", sub = "Para cuando solo sabes las calorías.")
        LoggerStepper(
            value = form.kcal,
            unit = "kcal",
            spokenName = "calorías",
            onMinus = { onChange(form.copy(kcal = kcalText((kcalNow - KCAL_STEP).coerceAtLeast(0.0)))) },
            onPlus = { onChange(form.copy(kcal = kcalText((kcalNow + KCAL_STEP).coerceAtMost(QuickEntry.MAX_KCAL)))) },
            minusEnabled = kcalNow > 0.0,
            valueContent = {
                KcalField(value = form.kcal, onValueChange = { onChange(form.copy(kcal = it)) }, modifier = Modifier.testTag("quick-entry-kcal"))
            },
        )
        errorOf(QuickEntryField.KCAL)?.let { ErrorLine(it) }

        LoggerSheetGroup("Macros (opcional)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroBox("Proteína", form.protein, { onChange(form.copy(protein = it)) }, "quick-entry-protein", Modifier.weight(1f))
            MacroBox("Carbos", form.carbs, { onChange(form.copy(carbs = it)) }, "quick-entry-carbs", Modifier.weight(1f))
            MacroBox("Grasa", form.fats, { onChange(form.copy(fats = it)) }, "quick-entry-fats", Modifier.weight(1f))
        }
        listOf(
            QuickEntryField.PROTEIN to "Proteína",
            QuickEntryField.CARBS to "Carbos",
            QuickEntryField.FATS to "Grasa",
        ).forEach { (field, label) -> errorOf(field)?.let { ErrorLine("$label: $it") } }

        LoggerFieldBox(label = "Nombre (opcional)", modifier = Modifier.padding(top = 8.dp).testTag("quick-entry-name")) {
            BasicTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nombre, opcional" },
                singleLine = true,
                textStyle = FieldValueStyle.copy(color = LoggerColors.ink),
                cursorBrush = SolidColor(LoggerColors.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                decorationBox = { inner ->
                    Box {
                        if (form.name.isEmpty()) Text("Menú del día", style = FieldValueStyle, color = LoggerColors.faint)
                        inner()
                    }
                },
            )
        }
        errorOf(QuickEntryField.NAME)?.let { ErrorLine(it) }

        validation.warning?.let {
            Text(
                text = it,
                style = LoggerText.cap,
                color = LoggerColors.ene,
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 10.dp).testTag("quick-entry-warning"),
            )
        }

        LoggerPill(
            text = "Añadir a la comida",
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("quick-entry-submit"),
        )
    }
}

/** Las kcal como texto del campo: sin decimales si son enteras y vacío en 0 (el error «Escribe las kcal.» lo recuerda tras pulsar). */
private fun kcalText(value: Double): String = when {
    value <= 0.0 -> ""
    value == value.toLong().toDouble() -> value.toLong().toString()
    else -> value.toString()
}

/** La cifra del stepper: Syne 30 que se escribe con el teclado numérico, con «0» tenue si está vacía y «kcal» a su lado. */
@Composable
private fun KcalField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    Row(
        modifier.fillMaxWidth().tapToFocus(focus),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .focusRequester(focus)
                .width(IntrinsicSize.Min)
                .widthIn(min = 28.dp)
                .semantics { contentDescription = "Calorías" },
            singleLine = true,
            textStyle = LoggerText.figure(30, FontWeight.ExtraBold).copy(color = LoggerColors.ink, textAlign = TextAlign.End),
            cursorBrush = SolidColor(LoggerColors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text("0", style = LoggerText.figure(30, FontWeight.ExtraBold), color = LoggerColors.faint, textAlign = TextAlign.End)
                    }
                    inner()
                }
            },
        )
        Text(
            text = "kcal",
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp),
            color = LoggerColors.muted,
            modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
            maxLines = 1,
        )
    }
}

/** Un campo de macro del lienzo: etiqueta encima y el valor con «g» (vacío se ve «—»). [tag] lo lleva la caja para que las pruebas lo encuentren. */
@Composable
private fun MacroBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LoggerFieldBox(label = label, modifier = modifier.testTag(tag).tapToFocus(focus)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .focusRequester(focus)
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 14.dp)
                    .semantics { contentDescription = "$label, en gramos" },
                singleLine = true,
                textStyle = FieldValueStyle.copy(color = LoggerColors.ink),
                cursorBrush = SolidColor(LoggerColors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text("—", style = FieldValueStyle, color = LoggerColors.faint)
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) Text(" g", style = FieldValueStyle, color = LoggerColors.muted, maxLines = 1)
        }
    }
}

/** Tocar cualquier punto de la caja pone el cursor en su campo (el texto solo ocupa lo que mide). Sin semántica de clic: el campo sigue siendo el nodo editable. */
private fun Modifier.tapToFocus(focus: FocusRequester): Modifier =
    pointerInput(focus) { detectTapGestures { focus.requestFocus() } }

/** Un error bajo su campo: 13 sp en rojo, sin caja. */
@Composable
private fun ErrorLine(text: String) {
    Text(text = text, style = LoggerText.cap, color = LoggerColors.danger, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
}

/**
 * La entrada rápida con su estado, como hoja sobre el registro de comidas. «Añadir a la comida» entrega el alimento ya marcado como
 * entrada rápida y la comida de la que se abrió ([initialMeal]); lo guarda el flujo normal del registro, con todo lo demás de la comida.
 * [initialForm] y [showErrorsInitially] son solo para la vista previa de depuración.
 */
@Composable
fun QuickEntryOverlay(
    initialMeal: MealType,
    onAdd: (LoggedFood, MealType) -> Unit,
    onDismiss: () -> Unit,
    initialForm: QuickEntryForm = QuickEntryForm(),
    showErrorsInitially: Boolean = false,
) {
    // El id del alimento nace al abrir: pulsar «Añadir a la comida» dos veces entrega el mismo alimento, no dos.
    val foodId = remember { "quick_${UUID.randomUUID()}" }
    var form by remember { mutableStateOf(initialForm) }
    var showErrors by remember { mutableStateOf(showErrorsInitially) }
    val validation = remember(form) { QuickEntry.validate(form, foodId) }
    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        QuickEntryContent(
            form = form,
            validation = validation,
            showErrors = showErrors,
            onChange = { form = it },
            onSubmit = {
                showErrors = true
                val food = validation.food
                if (food != null && validation.isValid) onAdd(food, initialMeal)
            },
            modifier = Modifier.imePadding(),
        )
    }
}
