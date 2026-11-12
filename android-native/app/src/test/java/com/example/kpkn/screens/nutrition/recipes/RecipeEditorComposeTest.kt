package com.example.kpkn.screens.nutrition.recipes

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-mdpi")
class RecipeEditorComposeTest {
    @get:Rule val roomRule = RoomTestRule()
    @get:Rule val rule = createComposeRule()

    private val catalogFoods = (0 until 8).map { index ->
        FoodItem(
            id = "pollo-$index",
            name = "Pollo $index",
            servingSize = 100.0,
            servingUnit = "g",
            unit = "g",
            calories = 165.0 + index,
            protein = 31.0,
            carbs = 0.0,
            fats = 3.6,
            isCustom = false,
        )
    }

    private val nestedRecipe = RecipeCalculator.asFood(
        id = "nested-sauce",
        name = "Salsa proteica",
        recipe = RecipeData(
            ingredients = listOf(
                RecipeIngredient(
                    snapshot = LoggedFood(foodName = "Yogur", amount = 100.0, unit = "g", calories = 60.0, protein = 10.0)
                )
            ),
            servings = 2,
            finalWeightGrams = 100.0,
        ),
    )

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context)
    }

    @After fun tearDown() {
        NutritionRepository.closeInstance()
    }

    private fun inputInTag(tag: String, useUnmergedTree: Boolean = false): SemanticsNodeInteraction =
        rule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = useUnmergedTree,
        )

    @Test fun `flujo añadir ingrediente sin truncamiento volver y quitar fila conservando la segunda`() {
        var latestDraft = RecipeDraft(name = "Bowl inicial", servings = "2")
        val allFoods = catalogFoods + nestedRecipe
        rule.setContent {
            var draft by remember { mutableStateOf(latestDraft) }
            var query by remember { mutableStateOf("") }
            latestDraft = draft
            val results = remember(query) {
                if (query.isBlank()) allFoods
                else allFoods.filter { it.name.contains(query, ignoreCase = true) }
            }
            RecipeEditorContent(
                draft = draft,
                preview = runCatching { draft.food("preview") }.getOrNull(),
                error = null,
                saving = false,
                query = query,
                results = results,
                onChange = { draft = it; latestDraft = it },
                onQuery = { query = it },
                onAddIngredient = { food, amount ->
                    val scaled = if (food.recipe != null) RecipeCalculator.portion(food, servings = amount)
                    else NutritionRepositoryScaledHelper.scaleFood(food, amount)
                    val updated = draft.copy(ingredients = draft.ingredients + RecipeIngredientDraft(RecipeIngredient(scaled, food.id)))
                    draft = updated
                    latestDraft = updated
                },
                onSave = {},
                onClose = {},
            )
        }

        // Open explicit picker and verify >6 items render without truncation
        rule.onNodeWithTag("recipe-add-ingredient").performClick()
        rule.onNodeWithTag("recipe-ingredient-picker").assertExists()
        inputInTag("recipe-search").performTextReplacement("pollo")
        for (i in 0 until 8) {
            rule.onNodeWithTag("recipe-search-result-pollo-$i").performScrollTo().assertExists()
        }

        // Back button preserves draft
        rule.onNodeWithTag("recipe-picker-back").performClick()
        rule.onNodeWithTag("recipe-editor").assertExists()
        assertEquals("Bowl inicial", latestDraft.name)

        // Add Pollo 0 twice as two independent rows
        rule.onNodeWithTag("recipe-add-ingredient").performClick()
        inputInTag("recipe-search").performTextReplacement("Pollo 0")
        rule.onNodeWithTag("recipe-search-result-pollo-0").performClick()
        rule.onNodeWithTag("recipe-amount-confirm", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Pollo 0 · Gramos", useUnmergedTree = true).assertExists()
        inputInTag("recipe-amount-confirm", useUnmergedTree = true).performTextReplacement("150")
        rule.onNodeWithTag("recipe-amount-confirm-add", useUnmergedTree = true).performClick()

        rule.onNodeWithTag("recipe-add-ingredient").performClick()
        inputInTag("recipe-search").performTextReplacement("Pollo 0")
        rule.onNodeWithTag("recipe-search-result-pollo-0").performClick()
        inputInTag("recipe-amount-confirm", useUnmergedTree = true).performTextReplacement("250")
        rule.onNodeWithTag("recipe-amount-confirm-add", useUnmergedTree = true).performClick()

        assertEquals(2, latestDraft.ingredients.size)
        val firstId = latestDraft.ingredients[0].id
        val secondId = latestDraft.ingredients[1].id
        assertNotEquals(firstId, secondId)

        // Edit row 2 to 300g, then delete row 1; row 2 keeps exact value and kcal
        rule.onNodeWithTag("recipe-ingredient-$secondId").performScrollTo()
        inputInTag("recipe-ingredient-$secondId").performTextReplacement("300")
        rule.onNodeWithTag("recipe-ingredient-remove-$firstId").performScrollTo().performClick()

        assertEquals(1, latestDraft.ingredients.size)
        assertEquals(secondId, latestDraft.ingredients.single().id)
        assertEquals("300", latestDraft.ingredients.single().amount)
        val computed = latestDraft.food("preview")
        // 300g of Pollo 0 (165 kcal/100g) across 2 servings => 247.5 kcal per serving
        assertEquals(247.5, computed.calories, 0.01)

        // Nested recipe confirmation dialog prompts for Raciones
        rule.onNodeWithTag("recipe-add-ingredient").performClick()
        inputInTag("recipe-search").performTextReplacement("Salsa")
        rule.onNodeWithTag("recipe-search-result-nested-sauce").performClick()
        rule.onNodeWithText("Salsa proteica · Raciones").assertExists()
    }

    @Test fun `errores en linea por campo al guardar borrador invalido`() {
        var latestDraft = RecipeDraft(
            name = "",
            servings = "2.5",
            finalWeight = "abc",
            ingredients = emptyList(),
        )
        rule.setContent {
            var draft by remember { mutableStateOf(latestDraft) }
            RecipeEditorContent(
                draft = draft,
                preview = null,
                error = null,
                saving = false,
                query = "",
                results = catalogFoods,
                onChange = { draft = it; latestDraft = it },
                onQuery = {},
                onAddIngredient = { _, _ -> },
                onSave = {},
                onClose = {},
            )
        }

        rule.onNodeWithTag("recipe-save").performClick()
        rule.onNodeWithTag("recipe-name").performScrollTo()
        rule.onNodeWithText("Escribe un nombre de hasta 80 caracteres.").assertExists()
        rule.onNodeWithTag("recipe-servings-error").performScrollTo().assertExists()
        rule.onNodeWithTag("recipe-ingredients-error").performScrollTo().assertExists()
        rule.onNodeWithTag("recipe-final-weight").performScrollTo()
        rule.onNodeWithText("Revisa el peso final en gramos.").assertExists()
    }

    @Test fun `error por ingrediente invalido en linea`() {
        val row = RecipeIngredientDraft(RecipeIngredient(LoggedFood(foodName = "Pollo 0", amount = 100.0, calories = 165.0)), amount = "100")
        rule.setContent {
            var draft by remember { mutableStateOf(RecipeDraft(name = "Plato", servings = "1", ingredients = listOf(row))) }
            RecipeEditorContent(
                draft = draft,
                preview = runCatching { draft.food("p") }.getOrNull(),
                error = null,
                saving = false,
                query = "",
                results = catalogFoods,
                onChange = { draft = it },
                onQuery = {},
                onAddIngredient = { _, _ -> },
                onSave = {},
                onClose = {},
            )
        }

        rule.onNodeWithTag("recipe-ingredient-${row.id}").performScrollTo()
        inputInTag("recipe-ingredient-${row.id}").performTextReplacement("0")
        rule.onNodeWithTag("recipe-save").performClick()
        rule.onNodeWithTag("recipe-ingredient-error-${row.id}").performScrollTo().assertExists()
    }

    @Test fun `StateRestorationTester conserva borrador y dialogo de confirmacion abierto`() {
        val restoration = StateRestorationTester(rule)
        var restoredDraft = RecipeDraft()
        restoration.setContent {
            var draft by rememberSaveable(stateSaver = recipeDraftSaver) { mutableStateOf(RecipeDraft()) }
            var query by rememberSaveable { mutableStateOf("") }
            restoredDraft = draft
            val filtered = remember(query) {
                if (query.isBlank()) catalogFoods
                else catalogFoods.filter { it.name.contains(query, ignoreCase = true) }
            }
            RecipeEditorContent(
                draft = draft,
                preview = runCatching { draft.food("r") }.getOrNull(),
                error = null,
                saving = false,
                query = query,
                results = filtered,
                onChange = { draft = it; restoredDraft = it },
                onQuery = { query = it },
                onAddIngredient = { food, amount ->
                    val scaled = NutritionRepositoryScaledHelper.scaleFood(food, amount)
                    val updated = draft.copy(ingredients = draft.ingredients + RecipeIngredientDraft(RecipeIngredient(scaled, food.id)))
                    draft = updated
                    restoredDraft = updated
                },
                onSave = {},
                onClose = {},
            )
        }

        inputInTag("recipe-name").performTextReplacement("Bowl Proteico")
        rule.onNodeWithTag("recipe-add-ingredient").performClick()
        inputInTag("recipe-search").performTextReplacement("Pollo 0")
        rule.onNodeWithTag("recipe-search-result-pollo-0").performClick()
        inputInTag("recipe-amount-confirm", useUnmergedTree = true).performTextReplacement("175")

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("recipe-amount-confirm", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("recipe-amount-confirm-add", useUnmergedTree = true).performClick()
        assertEquals("Bowl Proteico", restoredDraft.name)
        assertEquals(1, restoredDraft.ingredients.size)
        assertEquals(175.0, restoredDraft.ingredients.single().amount.toDouble(), 0.001)
    }

    @Test fun `reintento tras fallo preserva borrador edicion sube version y log historico mantiene instantanea v1`() = runBlocking {
        val repo = NutritionRepository.getInstance()
        val v1Base = RecipeDraft(
            name = "Arroz con pollo v1",
            servings = "2",
            finalWeight = "400",
            ingredients = listOf(
                RecipeIngredientDraft(RecipeIngredient(LoggedFood(foodName = "Pollo 0", amount = 200.0, calories = 330.0, protein = 62.0)))
            ),
        ).food("rec-v1")
        repo.saveCustomFood(v1Base)
        val storedV1 = repo.foodDatabase.value.first { it.id == "rec-v1" }
        assertEquals(1, storedV1.recipe!!.version)

        val logV1Portion = RecipeCalculator.portion(storedV1, servings = 1.0)
        repo.saveNutritionLog(
            NutritionLog(
                id = "log-v1",
                date = "2026-11-10",
                mealType = MealType.LUNCH,
                foods = listOf(logV1Portion),
            )
        )
        val originalLoggedCalories = logV1Portion.calories

        var saveAttempt = 0
        var saved = false
        rule.setContent {
            var showOverlay by remember { mutableStateOf(false) }
            if (!showOverlay) {
                var draft by remember { mutableStateOf(RecipeDraft.fromFood(storedV1)) }
                var error by remember { mutableStateOf<String?>(null) }
                RecipeEditorContent(
                    draft = draft,
                    preview = runCatching { draft.food(storedV1.id, storedV1) }.getOrNull(),
                    error = error,
                    saving = false,
                    query = "",
                    results = emptyList(),
                    onChange = { draft = it; error = null },
                    onQuery = {},
                    onAddIngredient = { _, _ -> },
                    onSave = {
                        saveAttempt += 1
                        if (saveAttempt == 1) {
                            error = "Error de guardado simulado. Tu borrador sigue aquí."
                        } else {
                            runBlocking { repo.saveCustomFood(draft.food(storedV1.id, storedV1)) }
                            saved = true
                        }
                    },
                    onClose = {},
                )
            }
        }

        // Modify servings to 4 => per-serving calories halve, then fail first save and retry cleanly
        inputInTag("recipe-servings").performTextReplacement("4")
        rule.onNodeWithTag("recipe-save").performClick()
        rule.onNodeWithTag("recipe-error").assertExists()
        assertFalse(saved)

        rule.onNodeWithTag("recipe-save").performClick()
        rule.waitUntil(5_000) { saved }

        val storedV2 = repo.foodDatabase.value.first { it.id == "rec-v1" }
        assertEquals(2, storedV2.recipe!!.version)
        assertEquals(4, storedV2.recipe!!.servings)
        assertEquals(originalLoggedCalories, repo.nutritionLogs.value.first { it.id == "log-v1" }.foods.first().calories, 0.01)
    }

    @Test fun `selector de porcion oculta gramos si no hay peso final y los muestra si existe`() {
        val noWeightFood = RecipeCalculator.asFood(
            id = "no-weight",
            name = "Sin peso final",
            recipe = RecipeSamples.food.recipe!!.copy(finalWeightGrams = null),
        )
        var mode by mutableIntStateOf(0)
        rule.setContent {
            val food = if (mode == 0) noWeightFood else RecipeSamples.food
            RecipePortionContent(food = food, onAdd = {}, onClose = {})
        }

        rule.onNode(hasText("Raciones") and hasClickAction()).assertExists()
        rule.onNode(hasText("Gramos") and hasClickAction()).assertDoesNotExist()

        rule.runOnUiThread { mode = 1 }
        rule.onNode(hasText("Raciones") and hasClickAction()).assertExists()
        rule.onNode(hasText("Gramos") and hasClickAction()).assertExists()
    }
}

private object NutritionRepositoryScaledHelper {
    fun scaleFood(food: FoodItem, amount: Double): LoggedFood {
        val ratio = if (food.servingSize > 0.0) amount / food.servingSize else 1.0
        return LoggedFood(
            id = "",
            foodName = food.name,
            amount = amount,
            unit = food.servingUnit.ifBlank { "g" },
            calories = food.calories * ratio,
            protein = food.protein * ratio,
            carbs = food.carbs * ratio,
            fats = food.fats * ratio,
        )
    }
}
