package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.QuickEntry
import com.example.kpkn.domain.nutrition.QuickEntryField
import com.example.kpkn.domain.nutrition.QuickEntryForm
import com.example.kpkn.domain.nutrition.QuickEntryValidation
import com.example.kpkn.screens.nutrition.scan.FullScreenOverlay
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.util.UUID

/**
 * La entrada rápida (N4), sin estado: kcal obligatorias, proteína, carbohidratos y grasas opcionales, nombre opcional y la comida. Los
 * errores solo se enseñan tras pulsar «Agregar» ([showErrors]); el aviso de kcal que no cuadran se enseña siempre que exista y nunca
 * bloquea.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickEntryContent(
    form: QuickEntryForm,
    validation: QuickEntryValidation,
    showErrors: Boolean,
    mealType: MealType,
    onChange: (QuickEntryForm) -> Unit,
    onMealChange: (MealType) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun errorOf(field: QuickEntryField): String? = if (showErrors) validation.errors[field] else null
    Column(modifier.background(KpknSurface.canvas).testTag("quick-entry-root")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknIconButton(KpknLineIcons.Close, "Cerrar sin agregar", onClose, Modifier.testTag("quick-entry-close"))
            Spacer(Modifier.width(8.dp))
            Text(
                "Entrada rápida",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Para cuando ya sabes las kcal y no quieres buscar un alimento. Cuenta en los totales del día; sus micronutrientes quedan sin dato.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Energía")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpknTextField(
                        value = form.kcal,
                        onValueChange = { onChange(form.copy(kcal = it)) },
                        modifier = Modifier.testTag("quick-entry-kcal"),
                        label = "Energía (obligatoria)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        error = errorOf(QuickEntryField.KCAL),
                        trailing = { Text("kcal", style = KpknType.unit, color = WizardColors.textMuted) },
                    )
                    KpknTextField(
                        value = form.name,
                        onValueChange = { onChange(form.copy(name = it)) },
                        modifier = Modifier.testTag("quick-entry-name"),
                        label = "Nombre (opcional)",
                        placeholder = MealReuse.QUICK_ENTRY_NAME,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        error = errorOf(QuickEntryField.NAME),
                    )
                }
            }
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Macros (opcionales)")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        MacroField("Proteína", form.protein, { onChange(form.copy(protein = it)) }, errorOf(QuickEntryField.PROTEIN), "quick-entry-protein", Modifier.weight(1f))
                        MacroField("Carbohidratos", form.carbs, { onChange(form.copy(carbs = it)) }, errorOf(QuickEntryField.CARBS), "quick-entry-carbs", Modifier.weight(1f))
                    }
                    MacroField("Grasas", form.fats, { onChange(form.copy(fats = it)) }, errorOf(QuickEntryField.FATS), "quick-entry-fats", imeLast = true)
                }
            }
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Comida")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlots.mealOrder.forEach { meal ->
                        KpknChip(
                            text = MealSlots.mealLabel(meal),
                            selected = meal == mealType,
                            onClick = { onMealChange(meal) },
                            modifier = Modifier.testTag("quick-entry-meal-${meal.name}"),
                        )
                    }
                }
            }
            validation.warning?.let {
                KpknModule(Modifier.fillMaxWidth().testTag("quick-entry-warning"), accent = SymbolPalette.energia) {
                    Text(it, style = KpknType.bodyStrong, color = WizardColors.text)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            KpknPrimaryPill(
                text = "Agregar a la comida",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                tag = "quick-entry-submit",
                icon = KpknLineIcons.Plus,
            )
        }
    }
}

@Composable
private fun MacroField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier,
    imeLast: Boolean = false,
) {
    KpknTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(tag),
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (imeLast) ImeAction.Done else ImeAction.Next),
        error = error,
        trailing = { Text("g", style = KpknType.unit, color = WizardColors.textMuted) },
    )
}

/**
 * La entrada rápida con su estado, a pantalla completa sobre el registro de comidas. «Agregar a la comida» entrega el alimento ya
 * marcado como entrada rápida y la comida elegida ([onAdd]); lo guarda el flujo normal del registro, con todo lo demás de la comida.
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
    // El id del alimento nace al abrir: pulsar «Agregar» dos veces entrega el mismo alimento, no dos.
    val foodId = remember { "quick_${UUID.randomUUID()}" }
    var form by remember { mutableStateOf(initialForm) }
    var meal by remember { mutableStateOf(initialMeal) }
    var showErrors by remember { mutableStateOf(showErrorsInitially) }
    val validation = remember(form) { QuickEntry.validate(form, foodId) }
    FullScreenOverlay(onBack = onDismiss) {
        QuickEntryContent(
            form = form,
            validation = validation,
            showErrors = showErrors,
            mealType = meal,
            onChange = { form = it },
            onMealChange = { meal = it },
            onSubmit = {
                showErrors = true
                val food = validation.food
                if (food != null && validation.isValid) onAdd(food, meal)
            },
            onClose = onDismiss,
        )
    }
}
