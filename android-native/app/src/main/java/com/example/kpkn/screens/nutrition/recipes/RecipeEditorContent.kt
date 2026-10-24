package com.example.kpkn.screens.nutrition.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.screens.onboarding.design.WizardColors
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
    onAddIngredient: (FoodItem) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(KpknSurface.canvas).testTag("recipe-editor")) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KpknIconButton(KpknLineIcons.Close, "Cerrar receta", onClose)
            Text("Receta propia", style = KpknType.title, color = WizardColors.text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KpknModule {
                KpknTextField(draft.name, { onChange(draft.copy(name = it)) }, label = "Nombre", modifier = Modifier.testTag("recipe-name"))
                Spacer(Modifier.height(12.dp))
                KpknTextField(draft.servings, { onChange(draft.copy(servings = it)) }, label = "Raciones del lote",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.testTag("recipe-servings"))
                Spacer(Modifier.height(12.dp))
                KpknTextField(draft.finalWeight, { onChange(draft.copy(finalWeight = it)) }, label = "Peso final cocinado (g, opcional)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("recipe-final-weight"))
                Text("El agua cambia el peso, no las kcal del lote. Sin peso final podrás registrar por raciones.",
                    style = KpknType.caption, color = WizardColors.textMuted)
            }
            KpknModule {
                KpknModuleHeader("Ingredientes")
                draft.ingredients.forEachIndexed { index, ingredient ->
                    val snapshot = ingredient.ingredient.snapshot
                    KpknTextField(ingredient.amount, { amount -> onChange(draft.copy(ingredients = draft.ingredients.mapIndexed { i, item ->
                        if (i == index) item.copy(amount = amount) else item
                    })) }, label = "${snapshot.foodName} (${snapshot.unit})", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("recipe-ingredient-$index"))
                    KpknTextAction("Quitar ingrediente", { onChange(draft.copy(ingredients = draft.ingredients.filterIndexed { i, _ -> i != index })) },
                        color = WizardColors.textMuted)
                }
                KpknTextField(query, onQuery, label = "Buscar ingrediente", modifier = Modifier.testTag("recipe-search"))
                results.take(6).forEach { food ->
                    KpknTextAction("Añadir ${food.name}", { onAddIngredient(food) }, leadingIcon = KpknLineIcons.Plus)
                }
            }
            preview?.recipe?.let { recipe ->
                val totals = RecipeCalculator.calculate(recipe)
                KpknModule {
                    KpknModuleHeader("Vista previa")
                    RecipeNumbers("Por ración", totals.perServing.calories, totals.perServing.protein, totals.perServing.carbs, totals.perServing.fats)
                    totals.per100g?.let { RecipeNumbers("Por 100 g", it.calories, it.protein, it.carbs, it.fats) }
                    totals.warning?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
                }
            }
            KpknTextField(draft.notes, { onChange(draft.copy(notes = it)) }, label = "Notas (opcional)")
        }
        error?.let { Text(it, style = KpknType.body, color = WizardColors.danger,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("recipe-error")) }
        KpknPrimaryPill(if (saving) "Guardando…" else "Guardar receta", onSave,
            modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = !saving, tag = "recipe-save")
    }
}

@Composable
internal fun RecipeNumbers(label: String, kcal: Double, protein: Double, carbs: Double, fats: Double) {
    Text("$label · ${String.format(Locale.forLanguageTag("es"), "%.0f", kcal)} kcal", style = KpknType.bodyStrong, color = WizardColors.text)
    Text(String.format(Locale.forLanguageTag("es"), "P %.1f g · H %.1f g · G %.1f g", protein, carbs, fats), style = KpknType.caption, color = WizardColors.textMuted)
}
