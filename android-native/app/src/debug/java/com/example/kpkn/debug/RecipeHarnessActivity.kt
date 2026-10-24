package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.screens.nutrition.recipes.*
import com.example.kpkn.screens.nutrition.design.NutritionFoodRow
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.*
import com.example.kpkn.ui.theme.KPKNTheme

/** Vista previa en memoria: empty, editing, preview_by_weight, error, logged; fontScale, reducedMotion y widthDp. */
class RecipeHarnessActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val initialState = intent.getStringExtra("state") ?: "editing"
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        val sample = RecipeCalculator.asFood("preview-recipe", "Arroz con pollo", RecipeData(
            ingredients = listOf(RecipeIngredient(LoggedFood(foodName = "Arroz cocido", amount = 400.0, calories = 400.0, carbs = 80.0)),
                RecipeIngredient(LoggedFood(foodName = "Pollo cocido", amount = 400.0, calories = 400.0, protein = 80.0, fats = 8.0))),
            servings = 4, finalWeightGrams = 600.0,
        ))
        setContent {
            KPKNTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides reduced) {
                    var state by remember { mutableStateOf(initialState) }
                    var draft by remember { mutableStateOf(RecipeDraft.fromFood(sample).let { if (state == "error") it.copy(finalWeight = "abc") else it }) }
                    var logged by remember { mutableStateOf(RecipeCalculator.portion(sample, servings = 1.0)) }
                    var failure by remember { mutableStateOf<String?>(if (state == "error") "Revisa el peso final en gramos." else null) }
                    val preview = runCatching { draft.food(sample.id) }
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas).statusBarsPadding().navigationBarsPadding()) {
                        Box(Modifier.widthIn(max = width.dp).fillMaxSize()) {
                            when (state) {
                                "empty" -> RecipeLibraryContent(emptyList(), null, false, null,
                                    onCreate = { state = "editing" }, onPick = {}, onEdit = {}, onDuplicate = {}, onAskDelete = {}, onDelete = {},
                                    onCancelDelete = {}, onClose = { finish() }, modifier = Modifier.fillMaxSize())
                                "preview_by_weight" -> RecipePortionContent(sample, { logged = it; state = "logged" }, { state = "editing" }, initialByWeight = true)
                                "logged" -> Column(Modifier.padding(20.dp)) {
                                    Text("Vista previa · guardado solo en memoria", style = KpknType.body, color = WizardColors.textMuted)
                                    NutritionFoodRow(NutritionLog(date = "2026-10-24T19:45:00-03:00", foods = listOf(logged)), {}, {})
                                    KpknTextAction("Volver", { state = "editing" })
                                }
                                else -> RecipeEditorContent(draft, preview.getOrNull(), failure, false, "", emptyList(),
                                    onChange = { draft = it; failure = null }, onQuery = {}, onAddIngredient = {},
                                    onSave = {
                                        val food = preview.getOrNull()
                                        if (food == null) failure = preview.exceptionOrNull()?.message
                                        else { logged = RecipeCalculator.portion(food, servings = 1.0); state = "logged" }
                                    }, onClose = { finish() }, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}
