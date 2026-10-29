package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.nutrition.recipes.*
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchRow
import com.example.kpkn.screens.nutrition.design.logger.loggerSearchPortion
import com.example.kpkn.screens.nutrition.design.NutritionFoodRow
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.RecipeCalculator
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-mdpi", application = Application::class)
class RecipeRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() { assumeTrue(System.getenv("KPKN_RENDER_DIR") != null) }
    }
    @get:Rule val rule = createComposeRule()
    private var host: View? = null
    private fun render(name: String, scale: Float, content: @Composable () -> Unit) {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            KPKNTheme {
                host = LocalView.current
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides true) {
                    Box(Modifier.width(360.dp).height(1500.dp).background(KpknSurface.canvas).testTag("recipe-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("recipe-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "recipe_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }
    @Composable private fun library(empty: Boolean) = RecipeLibraryContent(
        recipes = if (empty) emptyList() else listOf(RecipeSamples.food), deletingId = null, busy = false, error = null,
        onCreate = {}, onPick = {}, onEdit = {}, onDuplicate = {}, onAskDelete = {}, onDelete = {}, onCancelDelete = {}, onClose = {},
        modifier = Modifier.fillMaxSize(), favoriteIds = setOf(RecipeSamples.food.id),
    )
    @Composable private fun editing(error: Boolean) = RecipeEditorContent(
        draft = if (error) RecipeSamples.draft.copy(finalWeight = "abc") else RecipeSamples.draft,
        preview = if (error) null else RecipeSamples.food,
        error = if (error) "Revisa el peso final en gramos." else null, saving = false,
        query = "", results = emptyList(), onChange = {}, onQuery = {}, onAddIngredient = {}, onSave = {}, onClose = {},
        modifier = Modifier.fillMaxSize(),
    )
    private val noWeight = RecipeCalculator.asFood("no-weight", "Arroz con pollo", RecipeSamples.food.recipe!!.copy(finalWeightGrams = null))
    @Composable private fun logger() {
        val tag = com.example.kpkn.domain.nutrition.loggedFoodToEditableTag(
            RecipeCalculator.portion(noWeight, servings = 0.5)).copy(isExpanded = true)
        val logged = tag.loggedFood!!
        com.example.kpkn.screens.nutrition.design.logger.LoggerFoodCard(
            name = logged.foodName,
            subtitle = "${java.text.DecimalFormat("0.##").format(logged.amount)} ${logged.unit}",
            kcal = kotlin.math.round(logged.calories).toInt().toString(),
            protein = logged.protein,
            carbs = logged.carbs,
            fats = logged.fats,
            icon = com.example.kpkn.screens.nutrition.design.logger.loggerFoodIcon(logged.foodName),
            onOpen = {},
            modifier = Modifier.padding(16.dp),
        )
    }
    @Composable private fun searchRow() {
        val portion = loggerSearchPortion(noWeight, noWeight.name)
        LoggerSearchRow(noWeight.name, portion.label, portion.kcal, favorite = false, onToggleFavorite = null, onAdd = {}, onOpen = {})
    }
    @Test fun loggerRations100() = render("logger_rations", 1f) { logger() }
    @Test fun loggerRations130() = render("logger_rations", 1.3f) { logger() }
    @Test fun searchNoWeight100() = render("search_no_weight", 1f) { searchRow() }
    @Test fun searchNoWeight130() = render("search_no_weight", 1.3f) { searchRow() }
    @Test fun portionNoWeight100() = render("portion_no_weight", 1f) { RecipePortionContent(noWeight, {}, {}) }
    @Test fun portionNoWeight130() = render("portion_no_weight", 1.3f) { RecipePortionContent(noWeight, {}, {}) }
    @Test fun empty100() = render("empty", 1f) { library(true) }
    @Test fun empty130() = render("empty", 1.3f) { library(true) }
    @Test fun editing100() = render("editing", 1f) { editing(false) }
    @Test fun editing130() = render("editing", 1.3f) { editing(false) }
    @Test fun previewByWeight100() = render("preview_by_weight", 1f) { RecipePortionContent(RecipeSamples.food, {}, {}, initialByWeight = true) }
    @Test fun previewByWeight130() = render("preview_by_weight", 1.3f) { RecipePortionContent(RecipeSamples.food, {}, {}, initialByWeight = true) }
    @Test fun error100() = render("error", 1f) { editing(true) }
    @Test fun error130() = render("error", 1.3f) { editing(true) }
    @Test fun library100() = render("library", 1f) { library(false) }
    @Test fun library130() = render("library", 1.3f) { library(false) }
    @Test fun logged100() = render("logged", 1f) { NutritionFoodRow(NutritionLog(date = "2026-10-24T19:45:00-03:00", foods = listOf(RecipeCalculator.portion(RecipeSamples.food, servings = 1.0))), {}, {}) }
    @Test fun logged130() = render("logged", 1.3f) { NutritionFoodRow(NutritionLog(date = "2026-10-24T19:45:00-03:00", foods = listOf(RecipeCalculator.portion(RecipeSamples.food, servings = 1.0))), {}, {}) }
}
