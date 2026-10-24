package com.example.kpkn.screens.nutrition.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import com.example.kpkn.screens.nutrition.customfood.CustomFoodValidator
import com.example.kpkn.screens.nutrition.scan.FullScreenOverlay
import com.example.kpkn.screens.nutrition.scan.defaultGramsForScanned
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun RecipeLibraryOverlay(
    repository: NutritionRepository, onAdd: (LoggedFood) -> Unit, onDismiss: () -> Unit,
    initial: FoodItem? = null, startInEditor: Boolean = false,
) {
    val foods by repository.foodDatabase.collectAsState()
    val favoriteIds by repository.favoriteFoodIds.collectAsState()
    var editing by remember { mutableStateOf(startInEditor) }
    var base by remember { mutableStateOf(initial.takeIf { startInEditor }) }
    var recipeId by remember { mutableStateOf(initial?.id ?: "recipe_${UUID.randomUUID()}") }
    var draft by remember { mutableStateOf(initial?.takeIf { startInEditor }?.let { RecipeDraft.fromFood(it) } ?: RecipeDraft()) }
    var selected by remember { mutableStateOf(initial.takeUnless { startInEditor }) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<FoodItem>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val preview = remember(draft, recipeId, base) { runCatching { draft.food(recipeId, base) } }
    fun closePage() {
        if (busy) return
        if (editing || selected != null) { editing = false; selected = null; error = null } else onDismiss()
    }
    fun edit(food: FoodItem?) {
        base = food
        recipeId = food?.id ?: "recipe_${UUID.randomUUID()}"
        draft = food?.let { RecipeDraft.fromFood(it) } ?: RecipeDraft()
        editing = true; selected = null; query = ""; error = null
    }
    LaunchedEffect(query, editing) {
        if (!editing || query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        try { results = repository.searchFood(query).filter { it.id != recipeId }.take(6) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "No se pudo buscar el ingrediente. Inténtalo de nuevo." }
    }
    FullScreenOverlay(onBack = ::closePage) {
        when {
            editing -> RecipeEditorContent(
                draft = draft, preview = preview.getOrNull(), error = error, saving = busy,
                query = query, results = results, onQuery = { query = it },
                onChange = { draft = it; error = null },
                onAddIngredient = { food ->
                    try {
                        val snapshot = if (food.recipe != null) RecipeCalculator.portion(food, servings = 1.0)
                            else scaleFoodByPortion(food, amountGrams = defaultGramsForScanned(food))
                        draft = draft.copy(ingredients = draft.ingredients + RecipeIngredientDraft(RecipeIngredient(snapshot, food.id)))
                        query = ""; results = emptyList(); error = null
                    } catch (_: IllegalArgumentException) { error = "No se pudo usar ese ingrediente. Elige otra ficha." }
                },
                onSave = {
                    if (!busy) {
                        val food = preview.getOrNull()
                        if (food == null) error = preview.exceptionOrNull()?.message ?: "Revisa la receta."
                        else {
                            busy = true
                            scope.launch {
                                try { repository.saveCustomFood(food); editing = false; error = null }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { error = "No se pudo guardar la receta. Tu borrador sigue aquí." }
                                finally { busy = false }
                            }
                        }
                    }
                }, onClose = ::closePage, modifier = Modifier.fillMaxSize(),
            )
            selected != null -> RecipePortionContent(selected!!, onAdd = onAdd, onClose = ::closePage)
            else -> RecipeLibraryContent(
                recipes = foods.filter { it.recipe != null }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) },
                favoriteIds = favoriteIds, onToggleFavorite = { food ->
                    scope.launch {
                        try { repository.toggleFavoriteFood(food.id) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = "No se pudo guardar el favorito." }
                    }
                },
                deletingId = deletingId, busy = busy, error = error,
                onCreate = { edit(null) }, onEdit = ::edit, onPick = { selected = it },
                onDuplicate = { food ->
                    edit(food.copy(id = "recipe_${UUID.randomUUID()}", name = "${food.name} (copia)",
                        recipe = food.recipe?.copy(version = 1, updatedAt = "")))
                },
                onAskDelete = { deletingId = it.id }, onCancelDelete = { deletingId = null },
                onDelete = { food ->
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try { repository.deleteCustomFood(food.id); deletingId = null; error = null }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = "No se pudo eliminar la receta. Inténtalo de nuevo." }
                            finally { busy = false }
                        }
                    }
                }, onClose = onDismiss, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipePortionContent(food: FoodItem, onAdd: (LoggedFood) -> Unit, onClose: () -> Unit, initialByWeight: Boolean = false) {
    val gramsPerServing = food.recipe?.let { it.finalWeightGrams?.div(it.servings) }
    var amount by remember(food.id) { mutableStateOf(if (initialByWeight && gramsPerServing != null) gramsPerServing.toString() else "1") }
    var byWeight by remember(food.id) { mutableStateOf(initialByWeight && gramsPerServing != null) }
    val foodId = remember(food.id) { UUID.randomUUID().toString() }
    var added by remember(food.id) { mutableStateOf(false) }
    val preview = remember(food, amount, byWeight) { runCatching {
        val value = requireNotNull(CustomFoodValidator.parseNumber(amount)) { "Escribe una cantidad válida." }
        if (byWeight) RecipeCalculator.portion(food, grams = value, id = foodId)
        else RecipeCalculator.portion(food, servings = value, id = foodId)
    } }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(food.name, style = KpknType.title, color = WizardColors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpknChip("Raciones", !byWeight, { byWeight = false; amount = "1" })
            if (gramsPerServing != null) KpknChip("Gramos", byWeight, {
                byWeight = true; amount = gramsPerServing.toString()
            })
        }
        KpknTextField(amount, { amount = it }, label = if (byWeight) "Gramos cocinados" else "Raciones",
            error = preview.exceptionOrNull()?.message, modifier = Modifier.testTag("recipe-portion"))
        preview.getOrNull()?.let { RecipeNumbers("Esta porción", it.calories, it.protein, it.carbs, it.fats) }
        Text("Se añadirá como borrador. La comida se guarda cuando la confirmes en el registrador.",
            style = KpknType.body, color = WizardColors.textMuted)
        Spacer(Modifier.weight(1f))
        KpknPrimaryPill("Agregar a la comida", {
            if (!added) preview.getOrNull()?.let { added = true; onAdd(it) }
        }, enabled = preview.isSuccess && !added,
            modifier = Modifier.fillMaxWidth(), tag = "recipe-add")
        KpknTextAction("Volver", onClose)
    }
}
