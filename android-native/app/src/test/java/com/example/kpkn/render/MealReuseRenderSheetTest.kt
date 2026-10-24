package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.QuickEntry
import com.example.kpkn.domain.nutrition.QuickEntryForm
import com.example.kpkn.screens.nutrition.design.NutritionFoodRow
import com.example.kpkn.screens.nutrition.reuse.CopyToContent
import com.example.kpkn.screens.nutrition.reuse.PlannedMealsModule
import com.example.kpkn.screens.nutrition.reuse.QuickEntryContent
import com.example.kpkn.screens.nutrition.reuse.RepeatYesterdayDialog
import com.example.kpkn.screens.nutrition.reuse.ReuseDayActions
import com.example.kpkn.screens.nutrition.reuse.ReuseLoggerEntries
import com.example.kpkn.screens.nutrition.reuse.TemplateRowState
import com.example.kpkn.screens.nutrition.reuse.TemplatesContent
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Hoja de render de N4 (solo con `KPKN_RENDER_DIR`): hoja «Copiar a…» con varias fechas (y «Copiar día a…»), «Repetir ayer» con su
 * confirmación, lista de plantillas (vacía, con datos y renombrando/borrando), entrada rápida válida y con error, fila rápida en el
 * diario, comidas planificadas con las acciones de día y los accesos del registro. A 360 dp, al 100 % y al 130 % de letra.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-mdpi", application = Application::class)
class MealReuseRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() { assumeTrue(System.getenv("KPKN_RENDER_DIR") != null) }
    }

    @get:Rule val rule = createComposeRule()
    private var host: View? = null

    private val today = LocalDate.of(2026, 10, 21)
    private val yesterday = today.minusDays(1)

    private val breakfast = NutritionLog(
        id = "B", date = "${yesterday}T08:30:00.000Z", mealType = MealType.BREAKFAST,
        foods = listOf(LoggedFood(id = "f0", foodName = "Avena con leche", amount = 250.0, calories = 320.0, protein = 14.0, carbs = 48.0, fats = 8.0)),
    )
    private val lunch = NutritionLog(
        id = "L", date = "${yesterday}T13:00:00.000Z", mealType = MealType.LUNCH,
        foods = listOf(
            LoggedFood(id = "f1", foodName = "Arroz con pollo", amount = 300.0, calories = 560.0, protein = 28.0, carbs = 70.0, fats = 12.0),
            LoggedFood(id = "f2", foodName = "Ensalada", amount = 120.0, calories = 60.0, protein = 2.0, carbs = 8.0, fats = 3.0),
        ),
    )
    private val quickLog = NutritionLog(
        id = "Q", date = "${today}T12:00:00.000Z", mealType = MealType.DINNER,
        foods = listOf(QuickEntry.validate(QuickEntryForm(name = "Pizza con amigos", kcal = "850", protein = "35", carbs = "90", fats = "35"), "q").food!!),
    )

    private val threeDays = MealReuse.DateSelection((1L..3L).map { today.plusDays(it) }.toSet() + today)

    private fun render(name: String, scale: Float, heightDp: Int, content: @Composable () -> Unit) {
        // Segunda guarda: sin la variable, ninguna prueba de la clase llega a dibujar (ni revienta con el `!!` de más abajo).
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            KPKNTheme {
                host = LocalView.current
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides true) {
                    Box(Modifier.testTag("reuse-render").width(360.dp).height(heightDp.dp).background(KpknSurface.canvas)) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("reuse-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val directory = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(directory, "reuse_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }

    // ─── Escenas ─────────────────────────────────────────────────────────────

    @Composable private fun copySheet() = CopyToContent(
        title = "Copiar a…", subtitle = "Almuerzo · 20 oct · 620 kcal", sources = listOf(lunch), selection = threeDays, today = today,
        blocked = setOf(yesterday), busy = false, onToggleDate = {}, onTogglePreset = {}, onConfirm = {},
    )

    @Composable private fun copyDaySheet() = CopyToContent(
        title = "Copiar día a…", subtitle = "2 comidas · 20 oct · 940 kcal", sources = listOf(breakfast, lunch),
        selection = MealReuse.DateSelection(setOf(today.plusDays(1)), notice = "Máximo 31 fechas por vez."), today = today,
        blocked = setOf(yesterday), busy = false, onToggleDate = {}, onTogglePreset = {}, onConfirm = {},
    )

    @Composable private fun repeatDialog() = RepeatYesterdayDialog(
        existingMeals = 2, incomingMeals = 3, sourceIsYesterday = true, onAdd = {}, onCancel = {},
    )

    private val summaries = listOf(
        TemplateRowState(MealReuse.summarize(MealReuse.templateFromLog(lunch, "Almuerzo de la semana", "t1", "2026-10-21T10:00:00Z")!!)),
        TemplateRowState(MealReuse.summarize(MealReuse.templateFromLog(breakfast, "Desayuno de siempre", "t2", "2026-10-20T10:00:00Z")!!)),
        TemplateRowState(MealReuse.summarize(MealReuse.templateFromLog(quickLog, "Cena de fin de semana", "t3", "2026-10-19T10:00:00Z")!!)),
    )

    @Composable private fun templates(rows: List<TemplateRowState>) = TemplatesContent(
        rows = rows, failure = null, busy = false, onApply = {}, onStartRename = {}, onRenameTextChange = {}, onConfirmRename = {},
        onCancelRename = {}, onAskDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
    )

    @Composable private fun quick(form: QuickEntryForm, showErrors: Boolean) = QuickEntryContent(
        form = form, validation = QuickEntry.validate(form, "q"), showErrors = showErrors, mealType = MealType.LUNCH,
        onChange = {}, onMealChange = {}, onSubmit = {}, onClose = {},
    )

    @Composable private fun quickRows() = Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KpknModule(Modifier.fillMaxWidth()) {
            NutritionFoodRow(log = lunch.copy(foods = listOf(lunch.foods.first())), onOpen = {}, onLongPress = {})
            NutritionFoodRow(log = quickLog, onOpen = {}, onLongPress = {})
        }
    }

    @Composable private fun dayActions() = Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReuseDayActions("Repetir ayer", canCopyDay = true, onRepeat = {}, onCopyDay = {}, modifier = Modifier.padding(horizontal = 8.dp))
        PlannedMealsModule(
            logs = listOf(lunch.copy(id = "P1", date = "${today.plusDays(1)}T12:00:00.000Z"), breakfast.copy(id = "P2", date = "${today.plusDays(1)}T08:00:00.000Z")),
            canMarkConsumed = false, onMarkConsumed = {}, onRemove = {},
        )
        PlannedMealsModule(
            logs = listOf(lunch.copy(id = "P3", date = "${today}T12:00:00.000Z")),
            canMarkConsumed = true, onMarkConsumed = {}, onRemove = {},
        )
        ReuseLoggerEntries(onTemplates = {}, onQuickEntry = {}, modifier = Modifier.padding(horizontal = 8.dp))
    }

    @Test fun copySheet100() = render("copy_sheet", 1f, 1050) { copySheet() }
    @Test fun copySheet130() = render("copy_sheet", 1.3f, 1400) { copySheet() }
    @Test fun copyDay100() = render("copy_day", 1f, 1050) { copyDaySheet() }
    @Test fun copyDay130() = render("copy_day", 1.3f, 1400) { copyDaySheet() }
    @Test fun repeat100() = render("repeat_yesterday", 1f, 520) { repeatDialog() }
    @Test fun repeat130() = render("repeat_yesterday", 1.3f, 640) { repeatDialog() }
    @Test fun templatesEmpty100() = render("templates_empty", 1f, 520) { templates(emptyList()) }
    @Test fun templatesEmpty130() = render("templates_empty", 1.3f, 700) { templates(emptyList()) }
    @Test fun templates100() = render("templates", 1f, 760) { templates(summaries) }
    @Test fun templates130() = render("templates", 1.3f, 1050) { templates(summaries) }
    @Test fun templatesEdit100() = render("templates_edit", 1f, 820) {
        templates(listOf(summaries[0].copy(renaming = true, renameText = "Almuerzo de la semana"), summaries[1].copy(confirmingDelete = true), summaries[2]))
    }
    @Test fun templatesEdit130() = render("templates_edit", 1.3f, 1250) {
        templates(listOf(summaries[0].copy(renaming = true, renameText = "Almuerzo de la semana"), summaries[1].copy(confirmingDelete = true), summaries[2]))
    }
    @Test fun quickAdd100() = render("quick_add", 1f, 1050) { quick(QuickEntryForm(name = "Café con leche", kcal = "120", protein = "4", carbs = "10", fats = "6"), showErrors = false) }
    @Test fun quickAdd130() = render("quick_add", 1.3f, 1500) { quick(QuickEntryForm(name = "Café con leche", kcal = "120", protein = "4", carbs = "10", fats = "6"), showErrors = false) }
    @Test fun quickError100() = render("quick_add_error", 1f, 1100) { quick(QuickEntryForm(name = "Pizza", kcal = "900", protein = "10", carbs = "x", fats = "5"), showErrors = true) }
    @Test fun quickError130() = render("quick_add_error", 1.3f, 1600) { quick(QuickEntryForm(name = "Pizza", kcal = "900", protein = "10", carbs = "x", fats = "5"), showErrors = true) }
    @Test fun quickRow100() = render("quick_row", 1f, 330) { quickRows() }
    @Test fun quickRow130() = render("quick_row", 1.3f, 440) { quickRows() }
    @Test fun dayActions100() = render("day_actions", 1f, 760) { dayActions() }
    @Test fun dayActions130() = render("day_actions", 1.3f, 1100) { dayActions() }
}
