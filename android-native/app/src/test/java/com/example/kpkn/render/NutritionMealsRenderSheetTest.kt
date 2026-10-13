package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.CaffeineLimitEngine
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.screens.nutrition.NutritionSamples
import com.example.kpkn.screens.nutrition.design.NutritionDayModule
import com.example.kpkn.screens.nutrition.design.NutritionFoodMenu
import com.example.kpkn.screens.nutrition.design.NutritionHeader
import com.example.kpkn.screens.nutrition.design.NutritionLogIsland
import com.example.kpkn.screens.nutrition.design.NutritionMealModule
import com.example.kpkn.screens.nutrition.design.NutritionSupplementsModule
import com.example.kpkn.screens.nutrition.design.NutritionWeekTrendModule
import com.example.kpkn.screens.nutrition.nutritionHeroDateLabel
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import dev.chrisbanes.haze.HazeState
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * HOJA DE DIBUJO de las comidas de Nutrición (N2; no comprueba nada: escribe PNG): las cuatro comidas con sus registros (una estimación
 * con rango), el menú de una fila con «Mover a…» desplegado, «Últimos 7 días», la isla «Registrar comida» y la raíz de arriba abajo. A
 * 360 dp, al 100 % y al 130 % de letra, sin movimiento. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3600dp-xxhdpi", application = android.app.Application::class)
class NutritionMealsRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        /** Límites de cafeína de ejemplo: 100–300 mg de rango útil y 400 mg de seguridad. */
        private val caffeineLimits = CaffeineLimitEngine.CaffeineLimits(
            idealMinMg = 100.0,
            idealMaxMg = 300.0,
            safetyMaxMg = 400.0,
            singleDoseReferenceMg = 200.0,
            rationale = "Adulto sano: hasta 400 mg al día (EFSA).",
        )

        private const val SCENE_MEALS = 0
        private const val SCENE_MENU = 1
        private const val SCENE_WEEK = 2
        private const val SCENE_ISLAND = 3
        private const val SCENE_ROOT = 4
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Lo que se dibuja ahora, la escala de letra y el ancla del menú (la fila medida). Fuera de la composición para poder cambiarlos. */
    private var scene by mutableStateOf(SCENE_MEALS)
    private var fontScale by mutableStateOf(1f)
    private var menuAnchor by mutableStateOf<Rect?>(null)

    /** La vista de Compose, para dibujarla a un bitmap (como en las hojas de Home). */
    private var hostView: View? = null

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Las cuatro comidas con sus registros de la muestra, en el orden de [MealSlots.mealOrder]. */
    @Composable
    private fun Meals() {
        Column(Modifier.fillMaxWidth()) {
            MealSlots.mealOrder.forEach { meal ->
                val group = NutritionSamples.mealGroups.first { it.mealType == meal }
                NutritionMealModule(
                    mealType = meal,
                    logs = group.logs,
                    totals = group.totals,
                    onAdd = {},
                    onOpenLog = {},
                    onLongPressLog = { _, _ -> },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }

    /** La raíz de arriba abajo con lo de N1 (cabecera, «Tu día» con el entreno, suplementos) y lo de N2 (comidas y «Últimos 7 días»). */
    @Composable
    private fun Root() {
        val today = NutritionSamples.today.toString()
        NutritionHeader(
            dateLabel = nutritionHeroDateLabel(today, Locale("es")),
            loaded = true,
            hasActivePlan = true,
            onEditPlan = {},
            onCreatePlan = {},
        )
        NutritionDayModule(
            loaded = true,
            days = NutritionSamples.week(listOf(2120, 2480, 1930, 1240, 0, 0, 0), goal = 2300),
            selectedDate = today,
            totals = NutritionSamples.totals,
            goals = NutritionSamples.goals,
            hasActivePlan = true,
            onSelectDate = {},
            onDefineGoals = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            trainingBurnKcal = NutritionSamples.balance.trainingBurnKcal,
        )
        Meals()
        NutritionSupplementsModule(
            loaded = true,
            caffeineMg = NutritionSamples.totals.caffeineMg,
            caffeineLimits = caffeineLimits,
            creatineTodayG = 5.0,
            creatineTargetG = 5.0,
            creatineStreakDays = 6,
            isToday = true,
            onQuickAddCreatine = {},
            onOpenCreatine = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        NutritionWeekTrendModule(
            bars = NutritionSamples.weekBars,
            selectedDate = today,
            onSelectDate = {},
            onOpenHistory = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    @Composable
    private fun Sheet() {
        val base = LocalDensity.current
        hostView = LocalView.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density, fontScale = fontScale),
            LocalWizardReducedMotion provides true,
        ) {
            Column(Modifier.testTag("sheet").width(360.dp).background(Color.Black).padding(bottom = 16.dp)) {
                when (scene) {
                    SCENE_MEALS -> Meals()
                    SCENE_MENU -> {
                        // El recorte del menú se hace sobre el bloque de comidas: el velo del menú llena la hoja y no debe alargar la captura.
                        Box(Modifier.testTag("meals-area")) { Meals() }
                        menuAnchor?.let { anchor ->
                            NutritionFoodMenu(
                                anchor = anchor,
                                moveExpanded = true,
                                otherMeals = MealSlots.mealOrder.filter { it != MealType.BREAKFAST },
                                onDuplicate = {},
                                onToggleMove = {},
                                onMove = {},
                                onDelete = {},
                                onDismiss = {},
                            )
                        }
                    }
                    SCENE_WEEK -> NutritionWeekTrendModule(
                        bars = NutritionSamples.weekBars,
                        selectedDate = NutritionSamples.today.toString(),
                        onSelectDate = {},
                        onOpenHistory = {},
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    SCENE_ISLAND -> Box(Modifier.width(360.dp).height(120.dp)) {
                        NutritionLogIsland(
                            hazeState = remember { HazeState() },
                            onClick = {},
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        )
                    }
                    else -> Root()
                }
            }
        }
    }

    @Test
    fun renderNutritionMealsSheets() {
        onlyWhenAsked()
        rule.setContent { Sheet() }
        for ((suffix, scale) in listOf("s100" to 1f, "s130" to 1.3f)) {
            rule.runOnUiThread {
                scene = SCENE_MEALS
                fontScale = scale
                menuAnchor = null
            }
            rule.waitForIdle()
            save("sheet", "n2_comidas_$suffix")

            // El menú se ancla a la fila «Pan integral, Queso fresco» (la estimación con rango), medida en la hoja.
            rule.runOnUiThread { scene = SCENE_MENU }
            rule.waitForIdle()
            val row = rule.onNodeWithText("Pan integral, Queso fresco").fetchSemanticsNode().boundsInRoot
            rule.runOnUiThread { menuAnchor = row }
            rule.waitForIdle()
            save("meals-area", "n2_menu_$suffix")

            rule.runOnUiThread {
                scene = SCENE_WEEK
                menuAnchor = null
            }
            rule.waitForIdle()
            save("sheet", "n2_semana_$suffix")

            rule.runOnUiThread { scene = SCENE_ISLAND }
            rule.waitForIdle()
            save("sheet", "n2_isla_$suffix")

            rule.runOnUiThread { scene = SCENE_ROOT }
            rule.waitForIdle()
            save("sheet", "n2_raiz_$suffix")
        }
    }
}
