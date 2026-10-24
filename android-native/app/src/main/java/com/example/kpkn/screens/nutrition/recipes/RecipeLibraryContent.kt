package com.example.kpkn.screens.nutrition.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.screens.nutrition.customfood.FoodFavoriteAction
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeLibraryContent(
    recipes: List<FoodItem>, deletingId: String?, busy: Boolean, error: String?,
    onCreate: () -> Unit, onPick: (FoodItem) -> Unit, onEdit: (FoodItem) -> Unit,
    onDuplicate: (FoodItem) -> Unit, onAskDelete: (FoodItem) -> Unit, onDelete: (FoodItem) -> Unit,
    onCancelDelete: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
    favoriteIds: Set<String> = emptySet(), onToggleFavorite: (FoodItem) -> Unit = {},
) {
    Column(modifier.background(KpknSurface.canvas).testTag("recipe-library")) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KpknIconButton(KpknLineIcons.Close, "Cerrar recetas", onClose)
            Text("Recetas", style = KpknType.title, color = WizardColors.text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Guarda el lote una vez y registra las raciones que comas. Los registros anteriores conservan su propia copia.",
                style = KpknType.body, color = WizardColors.textMuted)
            if (recipes.isEmpty()) Text("Todavía no tienes recetas.", style = KpknType.bodyStrong, color = WizardColors.text)
            recipes.forEach { food ->
                KpknModule(Modifier.fillMaxWidth()) {
                    Text(food.name, style = KpknType.bodyStrong, color = WizardColors.text)
                    RecipeNumbers("Por ración", food.calories, food.protein, food.carbs, food.fats)
                    Text("${food.recipe!!.servings} raciones · versión ${food.recipe.version}", style = KpknType.caption, color = WizardColors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FoodFavoriteAction(food.name, food.id in favoriteIds, { onToggleFavorite(food) })
                        KpknTextAction("Usar", { if (!busy) onPick(food) }, leadingIcon = KpknLineIcons.Plus)
                        KpknTextAction("Editar", { if (!busy) onEdit(food) }, leadingIcon = KpknLineIcons.Edit)
                        KpknTextAction("Duplicar", { if (!busy) onDuplicate(food) })
                        KpknTextAction("Eliminar", { if (!busy) onAskDelete(food) }, color = WizardColors.danger)
                    }
                    if (deletingId == food.id) {
                        Text("¿Eliminar esta receta? Las comidas registradas se conservan.", style = KpknType.body, color = WizardColors.text)
                        KpknPrimaryPill("Sí, eliminar", { onDelete(food) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        KpknTextAction("Cancelar", onCancelDelete)
                    }
                }
            }
            error?.let { Text(it, style = KpknType.body, color = WizardColors.danger) }
        }
        KpknPrimaryPill("Crear receta", onCreate, modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = !busy, tag = "recipe-create")
    }
}
