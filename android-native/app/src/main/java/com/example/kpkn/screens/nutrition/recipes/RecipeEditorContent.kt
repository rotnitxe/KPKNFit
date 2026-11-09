package com.example.kpkn.screens.nutrition.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
) {
    var summaryScope by rememberSaveable { mutableIntStateOf(0) }
    var pendingFood by remember { mutableStateOf<FoodItem?>(null) }
    var pendingAmount by remember { mutableStateOf("") }
    val servings = draft.servings.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val totals = remember(preview) { preview?.recipe?.let { runCatching { RecipeCalculator.calculate(it) }.getOrNull() } }
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
                draft.ingredients.forEachIndexed { index, ingredient ->
                    val snapshot = ingredient.ingredient.snapshot
                    val amount = ingredient.amount.replace(',', '.').toDoubleOrNull()
                    val contribution = amount?.let { snapshot.calories * it / snapshot.amount } ?: 0.0
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("recipe-ingredient-row-$index"),
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
                                    draft.copy(ingredients = draft.ingredients.mapIndexed { i, item ->
                                        if (i == index) item.copy(amount = value) else item
                                    }),
                                )
                            },
                            label = "Cantidad",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(104.dp).testTag("recipe-ingredient-$index"),
                        )
                        KpknTextAction(
                            "Quitar",
                            { onChange(draft.copy(ingredients = draft.ingredients.filterIndexed { i, _ -> i != index })) },
                            color = WizardColors.textMuted,
                        )
                    }
                }
                KpknTextField(query, onQuery, label = "Buscar ingrediente", modifier = Modifier.testTag("recipe-search"))
                results.take(6).forEach { food ->
                    KpknTextAction("Añadir ${food.name}", {
                        pendingFood = food
                        pendingAmount = if (food.recipe != null) "1" else "100"
                    }, leadingIcon = KpknLineIcons.Plus)
                }
            }

            KpknModule {
                KpknModuleHeader("Detalles del lote")
                KpknTextField(
                    draft.finalWeight,
                    { onChange(draft.copy(finalWeight = it)) },
                    label = "Peso final cocinado (g, opcional)",
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
            onSave,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            enabled = !saving,
            tag = "recipe-save",
        )
    }
    pendingFood?.let { food ->
        val amount = pendingAmount.replace(',', '.').toDoubleOrNull()
        KpknAlertDialog(
            onDismissRequest = { pendingFood = null },
            title = { Text("Añadir ingrediente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(food.name, style = KpknType.bodyStrong, color = WizardColors.text)
                    KpknTextField(
                        pendingAmount,
                        { pendingAmount = it },
                        label = if (food.recipe != null) "Raciones" else "Gramos",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("recipe-amount-confirm"),
                    )
                    if (amount == null || amount <= 0.0) {
                        Text("Indica una cantidad mayor que cero.", style = KpknType.caption, color = WizardColors.danger)
                    }
                }
            },
            confirmButton = {
                KpknPrimaryPill(
                    "Añadir",
                    {
                        if (amount != null && amount > 0.0) {
                            onAddIngredient(food, amount)
                            pendingFood = null
                        }
                    },
                    enabled = amount != null && amount > 0.0,
                )
            },
            dismissButton = { KpknTextAction("Cancelar", { pendingFood = null }, color = WizardColors.textMuted) },
        )
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
