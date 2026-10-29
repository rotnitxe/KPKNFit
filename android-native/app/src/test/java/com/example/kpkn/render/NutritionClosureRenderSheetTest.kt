package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.R
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.MealGroup
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.CaffeineLimitEngine
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.GoalsAbsence
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.screens.nutrition.MealHistoryContent
import com.example.kpkn.screens.nutrition.MealHistoryDaySheet
import com.example.kpkn.screens.nutrition.NutritionEditorSamples
import com.example.kpkn.screens.nutrition.NutritionHistorySamples
import com.example.kpkn.screens.nutrition.NutritionSamples
import com.example.kpkn.screens.nutrition.NutritionWeekDay
import com.example.kpkn.screens.nutrition.design.NutritionDayModule
import com.example.kpkn.screens.nutrition.design.NutritionHeader
import com.example.kpkn.screens.nutrition.design.NutritionMealModule
import com.example.kpkn.screens.nutrition.design.NutritionSupplementsModule
import com.example.kpkn.screens.nutrition.design.NutritionWeekTrendModule
import com.example.kpkn.screens.nutrition.design.editor.NutritionPlanEditorActions
import com.example.kpkn.screens.nutrition.design.editor.NutritionPlanEditorContent
import com.example.kpkn.screens.nutrition.design.logger.LoggerDescribeField
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodCard
import com.example.kpkn.screens.nutrition.design.logger.LoggerSummaryCard
import com.example.kpkn.screens.nutrition.design.logger.LoggerWriteMode
import com.example.kpkn.screens.nutrition.design.logger.loggerAmountLine
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodIcon
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodKcal
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodSubtitle
import com.example.kpkn.screens.nutrition.design.logger.loggerQuestionFor
import com.example.kpkn.screens.nutrition.design.logger.loggerSaveBarLine
import com.example.kpkn.screens.nutrition.design.logger.loggerUnits
import com.example.kpkn.screens.nutrition.design.logger.LoggerHeader
import com.example.kpkn.screens.nutrition.design.logger.LoggerSaveBar
import com.example.kpkn.screens.nutrition.design.logger.LoggerTabs
import com.example.kpkn.screens.nutrition.nutritionHeroDateLabel
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE CIERRE de Nutrición (N6; no comprueba nada: escribe PNG con el prefijo `n6_` en `KPKN_RENDER_DIR`). Recorre la raíz con datos
 * (en tres recortes), sin objetivos, solo seguimiento y cargando; el registro (describir vacío, alimentos con aclaración y guardando);
 * el editor arriba y abajo; el historial y el detalle de un día, y el widget. El widget de Glance no se pinta en Robolectric: se dibuja
 * su misma composición en Compose (fondo, símbolo, nombre y dos píldoras), así que es una aproximación del diseño, no el widget real.
 * A 360 dp, al 100 % y al 130 % de letra, sin movimiento. Sin `KPKN_RENDER_DIR` la clase se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xxhdpi", application = android.app.Application::class)
class NutritionClosureRenderSheetTest {

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

        /** Días de la semana de la raíz con datos: los de la muestra, con su meta de 2.300 kcal. */
        private val weekWithGoal = NutritionSamples.week(listOf(2120, 2480, 1930, 1240, 0, 0, 0), goal = 2300)

        /** La misma semana sin meta: sin objetivos y solo seguimiento. */
        private val weekWithoutGoal = NutritionSamples.week(listOf(2120, 2480, 1930, 1240, 0, 0, 0), goal = null)

    }

    @get:Rule
    val rule = createComposeRule()

    /** Un recorte: la escena que se dibuja, la etiqueta del nodo que se recorta y el nombre del PNG (sin la escala). */
    private data class Capture(val scene: String, val tag: String, val name: String)

    /** Los recortes de la hoja, en orden. La raíz con datos sale en tres tramos; el resto, entero. */
    private val captures = listOf(
        Capture("raiz_datos", "n6-raiz-cabecera", "raiz_datos_a_cabecera"),
        Capture("raiz_datos", "n6-raiz-comidas", "raiz_datos_b_comidas"),
        Capture("raiz_datos", "n6-raiz-final", "raiz_datos_c_final"),
        Capture("raiz_sin_objetivos", "sheet", "raiz_sin_objetivos"),
        Capture("raiz_solo_seguimiento", "sheet", "raiz_solo_seguimiento"),
        Capture("raiz_cargando", "sheet", "raiz_cargando"),
        Capture("registro_describir_vacio", "sheet", "registro_describir_vacio"),
        Capture("registro_alimentos_aclaracion", "sheet", "registro_alimentos_aclaracion"),
        Capture("registro_guardando", "sheet", "registro_guardando"),
        Capture("editor_arriba", "sheet", "editor_arriba"),
        Capture("editor_abajo", "sheet", "editor_abajo"),
        Capture("historial_tres_meses", "sheet", "historial_tres_meses"),
        Capture("historial_detalle_dia", "sheet", "historial_detalle_dia"),
        Capture("widget", "sheet", "widget"),
    )

    /** La escena que se dibuja ahora y la escala de letra. Fuera de la composición para poder cambiarlas desde la prueba. */
    private var scene by mutableStateOf(captures.first().scene)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Escribe el recorte del nodo [tag] en `KPKN_RENDER_DIR/<name>.png`. */
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

    // ── Escenas ──────────────────────────────────────────────────────────────

    /** La escena elegida. Cada una pinta su propia página o su hoja a 360 dp. */
    @Composable
    private fun SceneContent(name: String) {
        when (name) {
            "raiz_datos" -> RootScene(
                loaded = true,
                hasPlan = true,
                days = weekWithGoal,
                totals = NutritionSamples.totals,
                goals = NutritionSamples.goals,
                meals = NutritionSamples.mealGroups,
            )
            "raiz_sin_objetivos" -> RootScene(
                loaded = true,
                hasPlan = false,
                days = weekWithoutGoal,
                totals = NutritionSamples.totals,
                goals = DayGoalsResult.Absent(GoalsAbsence.NO_GOAL),
                meals = NutritionSamples.mealGroups,
            )
            "raiz_solo_seguimiento" -> RootScene(
                loaded = true,
                hasPlan = true,
                days = weekWithoutGoal,
                totals = NutritionSamples.totals,
                goals = NutritionSamples.trackingOnly,
                meals = NutritionSamples.mealGroups,
            )
            "raiz_cargando" -> RootScene(
                loaded = false,
                hasPlan = false,
                days = NutritionSamples.week(listOf(0, 0, 0, 0, 0, 0, 0), goal = null),
                totals = DailyMacroTotals(),
                goals = NutritionSamples.trackingOnly,
                meals = emptyList(),
            )
            "registro_describir_vacio" -> LoggerScene(name)
            "registro_alimentos_aclaracion" -> LoggerScene(name)
            "registro_guardando" -> LoggerScene(name)
            "editor_arriba", "editor_abajo" -> EditorScene(name)
            "historial_tres_meses", "historial_detalle_dia" -> HistoryScene(name)
            "widget" -> WidgetScene()
        }
    }

    /**
     * La raíz de Nutrición tal como la monta la pantalla: cabecera y «Tu día», las cuatro comidas, y suplementos con la tendencia. Cada
     * tramo tiene su etiqueta para recortarlo. Con [meals] vacío, las comidas salen sin registros (la carga).
     */
    @Composable
    private fun RootScene(
        loaded: Boolean,
        hasPlan: Boolean,
        days: List<NutritionWeekDay>,
        totals: DailyMacroTotals,
        goals: DayGoalsResult,
        meals: List<MealGroup>,
    ) {
        val today = NutritionSamples.today.toString()
        Column(
            modifier = Modifier
                .testTag("sheet")
                .width(360.dp)
                .background(KpknSurface.canvas),
        ) {
            Column(modifier = Modifier.testTag("n6-raiz-cabecera")) {
                NutritionHeader(
                    dateLabel = nutritionHeroDateLabel(today),
                    loaded = loaded,
                    hasActivePlan = hasPlan,
                    onEditPlan = {},
                    onCreatePlan = {},
                )
                NutritionDayModule(
                    loaded = loaded,
                    days = days,
                    selectedDate = today,
                    totals = totals,
                    goals = goals,
                    hasActivePlan = hasPlan,
                    onSelectDate = {},
                    onDefineGoals = {},
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    trainingBurnKcal = NutritionSamples.balance.trainingBurnKcal,
                )
            }
            Column(modifier = Modifier.testTag("n6-raiz-comidas")) {
                MealSlots.mealOrder.forEach { meal ->
                    val group = meals.firstOrNull { it.mealType == meal }
                    NutritionMealModule(
                        mealType = meal,
                        logs = group?.logs.orEmpty(),
                        totals = group?.totals ?: DailyMacroTotals(),
                        onAdd = {},
                        onOpenLog = {},
                        onLongPressLog = { _, _ -> },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            Column(modifier = Modifier.testTag("n6-raiz-final")) {
                NutritionSupplementsModule(
                    loaded = loaded,
                    caffeineMg = totals.caffeineMg,
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
                    bars = if (loaded) NutritionSamples.weekBars else emptyList(),
                    selectedDate = today,
                    onSelectDate = {},
                    onOpenHistory = {},
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }

    /** Una hoja del registro a 360 dp, con el margen de la hoja (20 dp a los lados) y la etiqueta «sheet». */
    @Composable
    private fun LoggerScene(name: String) {
        Column(
            modifier = Modifier
                .testTag("sheet")
                .width(360.dp)
                .background(KpknSurface.canvas)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoggerHeader(
                title = "Registrar comida",
                dateLabel = "Hoy · lun 13 oct",
                mealType = MealType.LUNCH,
                onMealSelect = {},
                onSettings = {},
                onClose = {},
            )
            when (name) {
                "registro_describir_vacio" -> {
                    LoggerTabs(activeTab = 0, onTabSelect = {})
                    LoggerDescribeField(text = "", onTextChange = {}, onAnalyze = {}, onDictate = {})
                }
                "registro_alimentos_aclaracion" -> {
                    LoggerDescribeField(
                        text = NutritionSamples.DESCRIBE_TEXT,
                        onTextChange = {},
                        onAnalyze = {},
                        mode = LoggerWriteMode.RESULT,
                        foodMentions = listOf("pollo a la plancha", "tortilla", "arroz blanco"),
                    )
                    LoggerFoodsModule()
                    LoggerTotalBar()
                }
                "registro_guardando" -> {
                    LoggerFoodsModule()
                    LoggerTotalBar(busy = "Guardando…")
                }
            }
        }
    }

    /** El resumen y los tres alimentos del registro: uno normal, la tortilla con su duda dentro y el arroz. */
    @Composable
    private fun LoggerFoodsModule() {
        LoggerSummaryCard(kcal = 828, protein = 62.0, carbs = 85.0, fats = 24.0, targetKcal = 2300)
        listOf(
            NutritionSamples.chickenTag to null,
            NutritionSamples.tortillaTag to NutritionSamples.tortillaQuestion,
            NutritionSamples.riceTag to null,
        ).forEachIndexed { index, (tag, question) ->
            val logged = tag.loggedFood
            val name = (tag.foodItem?.name ?: tag.tag).replaceFirstChar { it.uppercase() }
            val grams = tag.amountGrams ?: logged?.amount ?: 0.0
            LoggerFoodCard(
                name = name,
                subtitle = loggerFoodSubtitle(loggerAmountLine(grams, loggerUnits(name, tag.foodItem, tag.tag)), question != null),
                kcal = loggerFoodKcal(kotlin.math.round(logged?.calories ?: 0.0).toInt(), question != null),
                protein = logged?.protein ?: 0.0,
                carbs = logged?.carbs ?: 0.0,
                fats = logged?.fats ?: 0.0,
                icon = loggerFoodIcon(name),
                onOpen = {},
                question = question?.let { loggerQuestionFor(tag, it) },
                index = index,
            )
        }
    }

    /** La barra de guardar «Guardar en almuerzo»; mientras guarda ([busy]) va apagada. */
    @Composable
    private fun LoggerTotalBar(busy: String? = null) {
        LoggerSaveBar(
            kcal = 828,
            line = loggerSaveBarLine(3, 62, 0),
            actionLabel = "Guardar en almuerzo",
            enabled = busy == null,
            onAction = {},
        )
    }

    /** El editor del plan con la lista en su posición: arriba (0) o abajo, en «Procedencia» (6). */
    @Composable
    private fun EditorScene(name: String) {
        val listState = rememberLazyListState()
        LaunchedEffect(name) { listState.scrollToItem(if (name == "editor_abajo") 6 else 0) }
        Box(
            modifier = Modifier
                .testTag("sheet")
                .width(360.dp)
                .height(780.dp)
                .background(Color.Black),
        ) {
            NutritionPlanEditorContent(
                state = NutritionEditorSamples.editedPlanState(),
                actions = NutritionPlanEditorActions(),
                isNew = false,
                goalOpen = false,
                selectedDay = 2,
                listState = listState,
            )
        }
    }

    /** El historial de tres meses o el detalle de un día, sobre el negro con el vidrio de las islas y hojas. */
    @Composable
    private fun HistoryScene(name: String) {
        val haze = remember { HazeState() }
        CompositionLocalProvider(LocalHazeState provides haze) {
            Box(
                modifier = Modifier
                    .testTag("sheet")
                    .width(360.dp)
                    .height(if (name == "historial_tres_meses") 1100.dp else 760.dp)
                    .background(KpknSurface.canvas)
                    .hazeSource(state = haze),
            ) {
                if (name == "historial_tres_meses") {
                    MealHistoryContent(
                        months = NutritionHistorySamples.history,
                        isLoading = false,
                        hasMore = false,
                        onBack = {},
                        onOpenDay = {},
                        onRegister = {},
                        onLoadMore = {},
                    )
                } else {
                    MealHistoryDaySheet(day = NutritionHistorySamples.diaConRango, onDismiss = {}, onGoToDay = {})
                }
            }
        }
    }

    /** El widget con su misma composición: fondo negro, símbolo y nombre en tinta, y las píldoras «Registrar» y «Buscar». */
    @Composable
    private fun WidgetScene() {
        Box(
            modifier = Modifier
                .testTag("sheet")
                .width(360.dp)
                .height(200.dp)
                .background(KpknSurface.canvas)
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .height(128.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0A0A0A))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.kpkn_simbolo),
                        contentDescription = null,
                        modifier = Modifier.width(28.dp).height(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Nutrición", style = KpknType.bodyStrong, color = Color(0xFFF2EEE6))
                }
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    WidgetPillMirror(
                        text = "Registrar",
                        icon = R.drawable.ic_kpkn_plus,
                        background = Color(0xFFF2EEE6),
                        foreground = Color(0xFF0A0A0A),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    WidgetPillMirror(
                        text = "Buscar",
                        icon = R.drawable.ic_kpkn_search,
                        background = Color(0x1FFFFFFF),
                        foreground = Color(0xFFF2EEE6),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    /** Una píldora del widget: 48 dp de alto, icono de 18 dp y texto. */
    @Composable
    private fun WidgetPillMirror(
        text: String,
        icon: Int,
        background: Color,
        foreground: Color,
        modifier: Modifier,
    ) {
        Row(
            modifier = modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(background)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = text, style = KpknType.action, color = foreground)
        }
    }

    @Test
    fun renderNutritionClosureSheets() {
        onlyWhenAsked()
        rule.setContent {
            val density = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                SceneContent(scene)
            }
        }
        for (capture in captures) {
            for ((scale, suffix) in listOf(1f to "_s100", 1.3f to "_s130")) {
                rule.runOnUiThread {
                    scene = capture.scene
                    fontScale = scale
                }
                rule.waitForIdle()
                save(capture.tag, "n6_${capture.name}$suffix")
            }
        }
    }
}
