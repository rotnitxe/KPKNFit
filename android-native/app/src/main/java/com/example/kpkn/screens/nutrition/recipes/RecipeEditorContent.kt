package com.example.kpkn.screens.nutrition.recipes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.screens.nutrition.customfood.CustomFoodValidator
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.*
import java.util.Locale

@Composable
fun RecipeEditorContent(
    draft: RecipeDraft,
    preview: FoodItem?,
    error: String?,
    saving: Boolean,
    query: String,
    results: List<FoodItem>,
    onChange: (RecipeDraft) -> Unit,
    onQuery: (String) -> Unit,
    onAddIngredient: (FoodItem, Double) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showValidationErrors: Boolean = false,
) {
    var summaryScope by rememberSaveable { mutableIntStateOf(0) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var attemptedSave by rememberSaveable { mutableStateOf(false) }
    var pendingFood by rememberSaveable(stateSaver = recipeFoodSaver) { mutableStateOf<FoodItem?>(null) }
    var pendingAmount by rememberSaveable { mutableStateOf("") }
    val servings = draft.servings.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val totals = remember(preview) { preview?.recipe?.let { runCatching { RecipeCalculator.calculate(it) }.getOrNull() } }
    val validation = remember(draft) { draft.validationErrors() }
    val revealErrors = showValidationErrors || attemptedSave
    val nameError = if (revealErrors || draft.name.trim().length > 80) validation.nameError else null
    val servingsError = if (revealErrors || draft.servings.isNotBlank()) validation.servingsError else null
    val finalWeightError = validation.finalWeightError
    val ingredientsEmptyError = if (revealErrors) validation.ingredientsEmptyError else null

    BackHandler(enabled = pickerOpen && pendingFood == null) { pickerOpen = false }

    if (pickerOpen) {
        Column(modifier.background(KpknSurface.canvas).testTag("recipe-ingredient-picker")) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                KpknIconButton(KpknLineIcons.Close, "Volver al editor", { pickerOpen = false }, modifier = Modifier.testTag("recipe-picker-back"))
                Text("Añadir ingrediente", style = KpknType.title, color = WizardColors.text)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KpknTextField(
                    query,
                    onQuery,
                    label = "Buscar ingrediente en el catálogo",
                    modifier = Modifier.testTag("recipe-search"),
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (query.isBlank()) {
                    Text(
                        "Escribe un nombre para buscar en todo el catálogo o tus alimentos.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                } else if (results.isEmpty()) {
                    Text(
                        "Sin resultados para «$query».",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.testTag("recipe-search-empty"),
                    )
                } else {
                    results.forEach { food ->
                        key(food.id) {
                            RecipeSearchResultRow(
                                food = food,
                                onSelect = {
                                    pendingFood = food
                                    pendingAmount = if (food.recipe != null) "1" else "100"
                                },
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier.background(KpknSurface.canvas).testTag("recipe-editor")) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                KpknIconButton(KpknLineIcons.Close, "Cerrar receta", onClose)
                Text("Receta propia", style = KpknType.title, color = WizardColors.text)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KpknModule {
                    KpknTextField(
                        draft.name,
                        { onChange(draft.copy(name = it)) },
                        label = "Nombre",
                        error = nameError,
                        modifier = Modifier.testTag("recipe-name"),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpknTextAction("− 1", {
                            onChange(draft.copy(servings = (servings - 1).coerceAtLeast(1).toString()))
                        }, color = WizardColors.textMuted)
                        KpknTextField(
                            draft.servings,
                            { onChange(draft.copy(servings = it)) },
                            label = "Raciones",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("recipe-servings"),
                        )
                        KpknTextAction("+ 1", {
                            onChange(draft.copy(servings = (servings + 1).toString()))
                        }, color = WizardColors.textMuted)
                    }
                    servingsError?.let {
                        Text(
                            it,
                            style = KpknType.caption,
                            color = WizardColors.danger,
                            modifier = Modifier.padding(top = 4.dp).testTag("recipe-servings-error"),
                        )
                    }
                }

                totals?.let { calculated ->
                    KpknModule {
                        KpknModuleHeader("Resumen")
                        KpknSegmentedControl(
                            options = listOf("Por ración", "Lote"),
                            selectedIndex = summaryScope,
                            onSelect = { summaryScope = it },
                            size = KpknSegmentedSize.Compact,
                        )
                        Spacer(Modifier.height(10.dp))
                        val shown = if (summaryScope == 0) calculated.perServing else calculated.batch
                        RecipeNumbers(
                            if (summaryScope == 0) "Por ración" else "Lote",
                            shown.calories, shown.protein, shown.carbs, shown.fats,
                        )
                        calculated.warning?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
                    }
                }

                KpknModule {
                    KpknModuleHeader("Ingredientes")
                    draft.ingredients.forEach { ingredient ->
                        key(ingredient.id) {
                            val snapshot = ingredient.ingredient.snapshot
                            val amount = CustomFoodValidator.parseNumber(ingredient.amount)
                            val contribution = amount?.let { snapshot.calories * it / snapshot.amount } ?: 0.0
                            val rowError = if (revealErrors || ingredient.amount.isNotBlank()) {
                                validation.ingredientAmountErrors[ingredient.id]
                            } else {
                                null
                            }
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("recipe-ingredient-row-${ingredient.id}")) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(snapshot.foodName, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 1)
                                        Text(
                                            "${String.format(Locale.forLanguageTag("es"), "%.0f", contribution)} kcal · ${snapshot.unit}",
                                            style = KpknType.caption, color = WizardColors.textMuted,
                                        )
                                    }
                                    KpknTextField(
                                        ingredient.amount,
                                        { value ->
                                            onChange(
                                                draft.copy(
                                                    ingredients = draft.ingredients.map { item ->
                                                        if (item.id == ingredient.id) item.copy(amount = value) else item
                                                    },
                                                ),
                                            )
                                        },
                                        label = "Cantidad (${snapshot.unit})",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(104.dp).testTag("recipe-ingredient-${ingredient.id}"),
                                    )
                                    KpknTextAction(
                                        "Quitar",
                                        { onChange(draft.copy(ingredients = draft.ingredients.filterNot { it.id == ingredient.id })) },
                                        color = WizardColors.textMuted,
                                        modifier = Modifier.testTag("recipe-ingredient-remove-${ingredient.id}"),
                                    )
                                }
                                rowError?.let {
                                    Text(
                                        it,
                                        style = KpknType.caption,
                                        color = WizardColors.danger,
                                        modifier = Modifier.padding(top = 4.dp).testTag("recipe-ingredient-error-${ingredient.id}"),
                                    )
                                }
                            }
                        }
                    }
                    ingredientsEmptyError?.let {
                        Text(
                            it,
                            style = KpknType.caption,
                            color = WizardColors.danger,
                            modifier = Modifier.padding(vertical = 4.dp).testTag("recipe-ingredients-error"),
                        )
                    }
                    KpknTextAction(
                        "Añadir ingrediente",
                        onClick = { pickerOpen = true },
                        leadingIcon = KpknLineIcons.Plus,
                        modifier = Modifier.testTag("recipe-add-ingredient"),
                    )
                    if (results.isNotEmpty()) {
                        results.forEach { food ->
                            key(food.id) {
                                RecipeSearchResultRow(
                                    food = food,
                                    onSelect = {
                                        pendingFood = food
                                        pendingAmount = if (food.recipe != null) "1" else "100"
                                    },
                                )
                            }
                        }
                    }
                }

                KpknModule {
                    KpknModuleHeader("Detalles del lote")
                    KpknTextField(
                        draft.finalWeight,
                        { onChange(draft.copy(finalWeight = it)) },
                        label = "Peso final cocinado (g, opcional)",
                        error = finalWeightError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("recipe-final-weight"),
                    )
                    Text(
                        "El agua cambia el peso, no las kcal del lote. Sin peso final podrás registrar por raciones.",
                        style = KpknType.caption, color = WizardColors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    KpknTextField(draft.notes, { onChange(draft.copy(notes = it)) }, label = "Notas (opcional)")
                }
            }
            error?.let {
                Text(
                    it,
                    style = KpknType.body,
                    color = WizardColors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("recipe-error"),
                )
            }
            KpknPrimaryPill(
                if (saving) "Guardando…" else "Guardar receta",
                {
                    attemptedSave = true
                    onSave()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = !saving,
                tag = "recipe-save",
            )
        }
    }
    pendingFood?.let { food ->
        val amount = CustomFoodValidator.parseNumber(pendingAmount)
        val unitLabel = if (food.recipe != null) "Raciones" else "Gramos"
        KpknAlertDialog(
            onDismissRequest = { pendingFood = null },
            title = { Text("Añadir ingrediente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${food.name} · $unitLabel", style = KpknType.bodyStrong, color = WizardColors.text)
                    KpknTextField(
                        pendingAmount,
                        { pendingAmount = it },
                        label = unitLabel,
                        error = if (amount == null) "Indica una cantidad mayor que cero." else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("recipe-amount-confirm"),
                    )
                }
            },
            confirmButton = {
                KpknPrimaryPill(
                    "Añadir",
                    {
                        if (amount != null && amount > 0.0) {
                            onAddIngredient(food, amount)
                            pendingFood = null
                            pickerOpen = false
                            onQuery("")
                        }
                    },
                    enabled = amount != null && amount > 0.0,
                    tag = "recipe-amount-confirm-add",
                )
            },
            dismissButton = { KpknTextAction("Cancelar", { pendingFood = null }, color = WizardColors.textMuted) },
        )
    }
}

@Composable
private fun RecipeSearchResultRow(food: FoodItem, onSelect: () -> Unit) {
    val portionLabel = if (food.recipe != null) "1 ración" else "100 g"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("recipe-search-result-${food.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.name, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 1)
            Text(
                "${food.calories.toInt()} kcal · $portionLabel",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        KpknTextAction("Elegir", onSelect, leadingIcon = KpknLineIcons.Plus)
    }
}

@Composable
internal fun RecipeNumbers(label: String, kcal: Double, protein: Double, carbs: Double, fats: Double) {
    Text(
        "$label · ${String.format(Locale.forLanguageTag("es"), "%.0f", kcal)} kcal",
        style = KpknType.bodyStrong,
        color = WizardColors.text,
    )
    Text(
        String.format(Locale.forLanguageTag("es"), "P %.1f g · H %.1f g · G %.1f g", protein, carbs, fats),
        style = KpknType.caption,
        color = WizardColors.textMuted,
    )
}
