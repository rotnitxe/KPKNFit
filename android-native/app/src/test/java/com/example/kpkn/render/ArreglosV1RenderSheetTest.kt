package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.exercises.catalogv2.CatalogDisplayNames
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.FoodPreparation
import com.example.kpkn.domain.nutrition.FoodPreparations
import com.example.kpkn.domain.nutrition.OilAmount
import com.example.kpkn.domain.nutrition.PortionOption
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.home.design.HomeNutritionToday
import com.example.kpkn.screens.nutrition.NutritionSamples
import com.example.kpkn.screens.nutrition.design.NutritionDayModule
import com.example.kpkn.screens.nutrition.design.logger.LoggerAddFoodAction
import com.example.kpkn.screens.nutrition.design.logger.LoggerDescribeField
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodCard
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodSheet
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodSheetState
import com.example.kpkn.screens.nutrition.design.logger.LoggerIcons
import com.example.kpkn.screens.nutrition.design.logger.LoggerPendingLine
import com.example.kpkn.screens.nutrition.design.logger.LoggerSaveBar
import com.example.kpkn.screens.nutrition.design.logger.LoggerSummaryCard
import com.example.kpkn.screens.nutrition.design.logger.loggerAmountLine
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodIcon
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodKcal
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodSubtitle
import com.example.kpkn.screens.nutrition.design.logger.loggerMatchLine
import com.example.kpkn.screens.nutrition.design.logger.loggerQuestionFor
import com.example.kpkn.screens.nutrition.design.logger.loggerSaveBarLine
import com.example.kpkn.screens.nutrition.design.logger.loggerSizeOptions
import com.example.kpkn.screens.nutrition.design.logger.loggerUnits
import com.example.kpkn.screens.nutrition.nutrients.NutrientFootprintSection
import com.example.kpkn.screens.nutrition.nutrients.NutrientMealModule
import com.example.kpkn.screens.nutrition.nutrients.NutrientsContent
import com.example.kpkn.screens.nutrition.nutrients.NutrientsDayModule
import com.example.kpkn.screens.nutrition.nutrients.NutrientsSamples
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.plan.DetailSheet
import com.example.kpkn.screens.onboarding.design.entreno.plan.PLAN_DETAIL_TAG
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFicha
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.programdetail.ProgramPlanDisplaySummary
import com.example.kpkn.screens.programdetail.StructureSubTab
import com.example.kpkn.screens.programdetail.design.ProgramFloatingBar
import com.example.kpkn.screens.programdetail.design.ProgramHeader
import com.example.kpkn.screens.programdetail.design.ProgramHeaderActions
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramHeaderStatus
import com.example.kpkn.screens.programdetail.design.ProgramRibbonWeek
import com.example.kpkn.screens.programdetail.design.programPlanFicha
import com.example.kpkn.screens.programdetail.design.progress.FourWeeksMuscle
import com.example.kpkn.screens.programdetail.design.progress.FourWeeksMuscleTag
import com.example.kpkn.screens.programdetail.design.progress.ProgramFourWeeks
import com.example.kpkn.screens.programdetail.design.progress.ProgramFourWeeksEntry
import com.example.kpkn.screens.programdetail.design.progress.ProgramFourWeeksScreen
import com.example.kpkn.screens.programdetail.design.progress.ProgressMus
import com.example.kpkn.screens.programdetail.design.progress.buildProgramFourWeeks
import com.example.kpkn.screens.programdetail.design.progress.progressWeeksOf
import com.example.kpkn.screens.programs.PlanInfoFicha
import com.example.kpkn.screens.programs.PlanInfoMode
import com.example.kpkn.screens.programs.PlanInfoModelBuilder
import com.example.kpkn.ui.design.KpknSurface
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO de «Arreglos v1» (no comprueba nada: escribe PNG con el prefijo `av1_` en `KPKN_RENDER_DIR`, para revisarlos a ojo
 * contra el lienzo). Dibuja las piezas reales de la app con datos de ejemplo: el resultado del registro con una duda, el campo de
 * descripción, la hoja «Ajustar un alimento», la huella radial de la comida y del día, la lista de vitaminas y minerales, la tarjeta
 * de Inicio, «Tu día», la cabecera de Program Detail con su barra flotante, «Tus últimas 4 semanas» (página y tarjeta) y las fichas
 * del plan (catálogo y «Sobre el programa»). A 360 dp, al 100 % y al 130 % de letra, sin movimiento. Cada escena es su propia prueba
 * (su propia composición), así que una que falle no tumba a las demás. Sin `KPKN_RENDER_DIR` la clase se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xxhdpi", application = android.app.Application::class)
class ArreglosV1RenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /**
     * Un recorte: el nombre del PNG (sin prefijo ni escala), la etiqueta del nodo que se recorta, si se quitan las filas negras de arriba
     * y de abajo (con [bottomMarginDp] de aire al pie) y, si hace falta, qué hacer antes de dibujar (desplazar la ficha, por ejemplo).
     */
    private data class Capture(
        val name: String,
        val tag: String = "sheet",
        val trimTop: Boolean = false,
        val trimBottom: Boolean = false,
        val bottomMarginDp: Int = 24,
        val prepare: (() -> Unit)? = null,
    )

    /** La escala de letra. Fuera de la composición para poder cambiarla desde la prueba. */
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    // ── Dibujo a PNG ─────────────────────────────────────────────────────────

    /** Escribe el recorte de [capture] en `KPKN_RENDER_DIR/av1_<nombre><sufijo>.png`. */
    private fun save(capture: Capture, suffix: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(capture.tag).fetchSemanticsNode().boundsInRoot
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
        if (cropped !== full) full.recycle()
        val density = view.resources.displayMetrics.density
        val result = trimBlack(cropped, capture.trimTop, capture.trimBottom, (capture.bottomMarginDp * density).toInt())
        FileOutputStream(File(dir, "av1_${capture.name}$suffix.png")).use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Si la fila [y] es toda negra pura (el fondo de la escena). */
    private fun isBlackRow(bitmap: Bitmap, y: Int, row: IntArray): Boolean {
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        return row.all { it == android.graphics.Color.BLACK }
    }

    /** Quita las filas negras de arriba y/o de abajo; por abajo deja [bottomMarginPx] de aire. */
    private fun trimBlack(bitmap: Bitmap, top: Boolean, bottom: Boolean, bottomMarginPx: Int): Bitmap {
        if (!top && !bottom) return bitmap
        val row = IntArray(bitmap.width)
        var first = 0
        var last = bitmap.height - 1
        if (top) while (first < last && isBlackRow(bitmap, first, row)) first++
        if (bottom) {
            while (last > first && isBlackRow(bitmap, last, row)) last--
            last = minOf(bitmap.height - 1, last + bottomMarginPx)
        }
        return if (first == 0 && last == bitmap.height - 1) bitmap else Bitmap.createBitmap(bitmap, 0, first, bitmap.width, last - first + 1)
    }

    /**
     * Monta [scene] una vez y dibuja cada recorte de [captures] a 100 % (`_s100`) y a 130 % (`_s130`) de letra. Cada escena es su propia
     * prueba porque Compose solo admite un `setContent` por prueba y una composición caída no se puede reanudar.
     */
    private fun render(scene: String, vararg captures: Capture) {
        onlyWhenAsked()
        rule.setContent {
            val density = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
                LocalContentColor provides Color.White,
            ) {
                SceneContent(scene)
            }
        }
        val list = if (captures.isEmpty()) listOf(Capture(scene)) else captures.toList()
        for (capture in list) {
            for ((scale, suffix) in listOf(1f to "_s100", 1.3f to "_s130")) {
                rule.runOnUiThread { fontScale = scale }
                rule.waitForIdle()
                capture.prepare?.invoke()
                rule.waitForIdle()
                // Las hojas entran con una animación corta: que termine antes de dibujar.
                rule.mainClock.advanceTimeBy(1500)
                rule.waitForIdle()
                save(capture, suffix)
            }
        }
    }

    /** Desplaza la ficha del plan hasta el final (la ventana de la ficha mide 820 dp y es más larga). */
    private fun scrollDetailToEnd() {
        rule.onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag(PLAN_DETAIL_TAG)))
            .onFirst()
            .performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, 100_000f) }
    }

    // ── Pruebas: una por escena ──────────────────────────────────────────────

    @Test fun nutriRegistro() = render("nutri_registro")

    @Test fun nutriCampo() = render("nutri_campo")

    @Test fun nutriHojaAlimento() = render("nutri_hoja_alimento", Capture("nutri_hoja_alimento", trimTop = true))

    @Test fun nutriHuellaComida() = render("nutri_huella_comida")

    @Test fun nutriHuellaDia() = render("nutri_huella_dia")

    @Test fun nutriLista() = render("nutri_lista")

    @Test fun inicioNutricionHoy() = render("inicio_nutricion_hoy")

    @Test fun nutriDia() = render("nutri_dia")

    @Test fun pdCabecera() = render("pd_cabecera")

    @Test fun pdCuatroSemanas() = render("pd_cuatro_semanas", Capture("pd_cuatro_semanas", trimBottom = true, bottomMarginDp = 40))

    @Test fun pdCuatroSemanasTarjeta() = render("pd_cuatro_semanas_tarjeta")

    @Test fun pdFichaCatalogo() = render(
        "pd_ficha_catalogo",
        Capture("pd_ficha_catalogo_a"),
        Capture("pd_ficha_catalogo_b", prepare = this::scrollDetailToEnd),
    )

    @Test fun pdSobrePrograma() = render(
        "pd_sobre_programa",
        Capture("pd_sobre_programa_a"),
        Capture("pd_sobre_programa_b", prepare = this::scrollDetailToEnd),
    )

    // ── Escenas ──────────────────────────────────────────────────────────────

    @Composable
    private fun SceneContent(name: String) {
        when (name) {
            "nutri_registro" -> RegistroScene()
            "nutri_campo" -> Page(horizontal = 20.dp, vertical = 16.dp) {
                LoggerDescribeField(text = "Papas fritas medianas", onTextChange = {}, onAnalyze = {}, onDictate = {})
            }
            "nutri_hoja_alimento" -> HojaAlimentoScene()
            "nutri_huella_comida" -> Page(horizontal = 16.dp, vertical = 16.dp) {
                NutrientMealModule(meal = NutrientsSamples.meal(), onOpen = {})
            }
            "nutri_huella_dia" -> Page(horizontal = 16.dp, vertical = 16.dp) {
                NutrientsDayModule(state = NutrientsSamples.state("complete"), onOpen = {})
            }
            "nutri_lista" -> Box(Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas)) {
                NutrientsContent(state = NutrientsSamples.state("complete"), onOpenNutrient = {})
            }
            "inicio_nutricion_hoy" -> Page(horizontal = 16.dp, vertical = 16.dp) {
                HomeNutritionToday(state = HomeSamples.nutritionTwoMeals(), onAddMeal = {}, onOpenDay = {})
            }
            "nutri_dia" -> Page(horizontal = 16.dp, vertical = 16.dp) {
                NutritionDayModule(
                    loaded = true,
                    days = NutritionSamples.week(listOf(2120, 2480, 1930, 1240, 0, 0, 0), goal = 2300),
                    selectedDate = NutritionSamples.today.toString(),
                    totals = NutritionSamples.totals,
                    goals = NutritionSamples.goals,
                    hasActivePlan = true,
                    onSelectDate = {},
                    onDefineGoals = {},
                    trainingBurnKcal = NutritionSamples.balance.trainingBurnKcal,
                )
            }
            "pd_cabecera" -> CabeceraScene()
            "pd_cuatro_semanas" -> Box(Modifier.testTag("sheet").width(360.dp)) {
                ProgramFourWeeksScreen(
                    model = fourWeeksModel,
                    phaseColorOf = { weekId -> fourWeeksPhaseColors[weekId] ?: ProgressMus },
                    onBack = {},
                    onShare = {},
                )
            }
            "pd_cuatro_semanas_tarjeta" -> Page(horizontal = 16.dp, vertical = 16.dp) {
                ProgramFourWeeksEntry(model = fourWeeksModel, onClick = {})
            }
            "pd_ficha_catalogo" -> FichaScene(catalogFicha, readOnly = false)
            "pd_sobre_programa" -> FichaScene(aboutFicha, readOnly = true)
        }
    }

    /** Una página de 360 dp sobre negro con el margen dado, con la etiqueta «sheet» para recortarla entera. */
    @Composable
    private fun Page(
        horizontal: Dp,
        vertical: Dp,
        spacing: Dp = 0.dp,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Column(
            modifier = Modifier
                .testTag("sheet")
                .width(360.dp)
                .background(KpknSurface.canvas)
                .padding(horizontal = horizontal, vertical = vertical),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }

    // ── Nutrición ────────────────────────────────────────────────────────────

    /**
     * El resultado del registro con una duda (lienzo r-result): el resumen, «1 duda por revisar», el pollo sin duda, el arroz con su
     * pregunta de porción, «Añadir otro alimento» y la barra de guardar.
     */
    @Composable
    private fun RegistroScene() {
        val portion = ClarificationRequest.Portion(
            requestId = "quantity",
            options = listOf(
                PortionOption("Lo estimado (150 g)", 150.0),
                PortionOption("plato (250 g)", 250.0),
                PortionOption("puñado (40 g)", 40.0),
                PortionOption("taza (60 g)", 60.0),
            ),
        )
        val foods = listOf<Pair<ResolvedTag, ClarificationRequest?>>(
            NutritionSamples.chickenTag to null,
            NutritionSamples.riceTag to portion,
        )
        val logged = foods.mapNotNull { it.first.loggedFood }
        val kcal = logged.map { it.calories }.sum().roundToInt()
        val protein = logged.map { it.protein }.sum()
        Page(horizontal = 20.dp, vertical = 16.dp, spacing = 12.dp) {
            LoggerSummaryCard(
                kcal = kcal,
                protein = protein,
                carbs = logged.map { it.carbs }.sum(),
                fats = logged.map { it.fats }.sum(),
            )
            LoggerPendingLine(1)
            foods.forEachIndexed { index, (tag, request) ->
                val food = tag.loggedFood
                val foodName = (tag.foodItem?.name ?: tag.tag).replaceFirstChar { it.uppercase() }
                val grams = tag.amountGrams ?: food?.amount ?: 0.0
                LoggerFoodCard(
                    name = foodName,
                    subtitle = loggerFoodSubtitle(loggerAmountLine(grams, loggerUnits(foodName, tag.foodItem, tag.tag)), request != null),
                    kcal = loggerFoodKcal((food?.calories ?: 0.0).roundToInt(), request != null),
                    protein = food?.protein ?: 0.0,
                    carbs = food?.carbs ?: 0.0,
                    fats = food?.fats ?: 0.0,
                    icon = loggerFoodIcon(foodName),
                    onOpen = {},
                    question = request?.let { loggerQuestionFor(tag, it) },
                    index = index,
                )
            }
            LoggerAddFoodAction(onClick = {})
            LoggerSaveBar(
                kcal = kcal,
                line = loggerSaveBarLine(foods.size, protein.roundToInt(), 0),
                actionLabel = "Guardar en almuerzo",
                enabled = true,
                onAction = {},
            )
        }
    }

    /**
     * La hoja «Ajustar un alimento» con papas fritas (frito, aceite normal) y la huella debajo de «Aporta». La hoja se dibuja con su
     * propio contenedor (`KpknSheet` sin ventana: sin anfitrión de overlays cae a una capa a pantalla completa), así que va en una
     * caja alta sobre negro y la prueba quita el negro de arriba.
     */
    @Composable
    private fun HojaAlimentoScene() {
        val grams = 200.0
        val state = LoggerFoodSheetState(
            name = "Papas fritas",
            icon = LoggerIcons.Bowl,
            matchLine = loggerMatchLine(0.92, estimated = false, source = "base KPKN"),
            grams = grams,
            units = loggerUnits("Papas fritas", null, "papas fritas"),
            kcal = 596,
            protein = 6.0,
            carbs = 61.0,
            fats = 36.0,
            sizes = loggerSizeOptions(grams),
            preparations = FoodPreparations.available(
                withRecord = setOf(FoodPreparation.COOKED, FoodPreparation.GRILLED, FoodPreparation.BAKED, FoodPreparation.FRIED),
                category = "TUBERCULOS",
                current = FoodPreparation.FRIED,
            ),
            preparation = FoodPreparation.FRIED,
            oil = OilAmount.NORMAL,
            oilKcal = 222,
        )
        Box(Modifier.testTag("sheet").width(360.dp).height(2400.dp).background(Color.Black)) {
            LoggerFoodSheet(
                state = state,
                onGramsChange = {},
                onSizeChange = {},
                onPreparationChange = {},
                onOilChange = {},
                onToggleExcluded = {},
                onMacroChange = { _, _ -> },
                onChange = {},
                onRemove = {},
                onDismiss = {},
                footprint = { NutrientFootprintSection(model = NutrientsSamples.food().footprint) },
            )
        }
    }

    // ── Program Detail ───────────────────────────────────────────────────────

    /**
     * La cabecera del programa con ⋯ junto al nombre (y su punto de pendientes) y la barra flotante (atrás y tres pestañas). En la
     * app la barra flota arriba, sobre el contenido: aquí va arriba y la cabecera debajo.
     */
    @Composable
    private fun CabeceraScene() {
        val phases = listOf(
            Color(0xFF6EA8F4) to true,
            Color(0xFF6EA8F4) to true,
            Color(0xFFF49A6E) to true,
            Color(0xFFF49A6E) to false,
            Color(0xFFF49A6E) to false,
            Color(0xFF8BD6A0) to false,
        )
        val state = ProgramHeaderState(
            name = "Fuerza de base",
            status = ProgramHeaderStatus.ACTIVE,
            meta = "Fuerza · 6 semanas · 3 bloques",
            identity = coverIdentityFor("generated:strength-muscle", null),
            photoUrl = null,
            bundled = null,
            progressLabel = "Semana 4 de 6 · Intensificación",
            primary = ProgramHeaderPrimary.Start("Torso A"),
            ribbon = phases.mapIndexed { index, (color, done) -> ProgramRibbonWeek(color = color, done = done, current = index == 3) },
            blockLabel = "Bloque 2 de 3",
            todayAlso = "Hoy también: Cardio y potencia · ≈ 32 min",
        )
        Column(Modifier.testTag("sheet").width(360.dp).background(Color.Black)) {
            ProgramFloatingBar(selected = StructureSubTab.SEMANA, onSelect = {}, onBack = {}, hazeState = null)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                ProgramHeader(
                    state = state,
                    actions = ProgramHeaderActions(onPrimary = {}, onShare = {}, pendingCount = 2, onMore = {}, moreDot = true),
                )
            }
        }
    }

    /**
     * Una ficha del plan (catálogo o «Sobre el programa»), en una ventana de 820 dp: la ficha es larga y se dibuja arriba y, desplazada
     * hasta el final, abajo. Es el contenido de `PlanFichaOverlay` sin su ventana de diálogo. En modo lectura no hay botón del pie.
     */
    @Composable
    private fun FichaScene(ficha: PlanFicha, readOnly: Boolean) {
        val content = if (readOnly) ficha.content.copy(primaryLabel = null) else ficha.content
        Box(Modifier.testTag("sheet").width(360.dp).height(820.dp)) {
            DetailSheet(
                model = ficha.detail,
                selected = false,
                reduced = true,
                blur = false,
                shown = { 1f },
                onChoose = {},
                onClose = {},
                ficha = content,
                onSecondary = if (content.secondaryLabel != null) ({}) else null,
            )
        }
    }

    // ── Datos de ejemplo de Program Detail ───────────────────────────────────

    /** Colores de las fases del programa de ejemplo (para «Lo que viene»). */
    private val fourWeeksPhaseColors by lazy { progressWeeksOf(fwProgram).associate { it.id to it.color } }

    /** «Tus últimas 4 semanas» del programa de ejemplo (hoy es el jueves 29 de octubre de 2026), con cuatro músculos de ejemplo. */
    private val fourWeeksModel: ProgramFourWeeks by lazy {
        buildProgramFourWeeks(
            program = fwProgram,
            logs = fwLogs,
            currentWeekId = "w4",
            today = fwToday,
            zone = ZoneOffset.UTC,
            proposal = "propuesta: sentadilla a 105 kg",
        ).copy(
            muscles = listOf(
                FourWeeksMuscle("Pecho", "12 series por semana · rango 10–20", FourWeeksMuscleTag.IN_RANGE),
                FourWeeksMuscle("Espalda", "11 por semana · rango 10–22", FourWeeksMuscleTag.JUST),
                FourWeeksMuscle("Cuádriceps", "5 por semana · mínimo útil 8", FourWeeksMuscleTag.BELOW),
                FourWeeksMuscle("Core", "Sin trabajo directo en el plan", FourWeeksMuscleTag.NONE),
            ),
        )
    }

    /** La ficha del plan PHAT del catálogo de producción, como la arma `PlanInfoFicha` (sin miniaturas: como en sus pruebas). */
    private val catalogFicha: PlanFicha by lazy {
        CatalogCompositionTestSupport.install()
        val entry = PersonalizedPlanCatalog.find(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID) ?: error("Falta la entrada PHAT del catálogo")
        val index = CatalogDisplayNames.buildDisplayNameIndex(CatalogCompositionTestSupport.catalog)
            .mapKeys { (key, _) -> key.trim().lowercase() }
        val names: (String) -> String? = { id -> index[id.trim().lowercase()] }
        val equipmentOf: (String) -> String? = { id -> CatalogCompositionTestSupport.metadata.metadata(id)?.equipmentId }
        val info = PlanInfoModelBuilder.build(entry, PlanInfoMode.LIBRARY, names = names, equipmentOf = equipmentOf)
        PlanInfoFicha.build(entry, info, names = names, imageOf = { null })
    }

    /** «Sobre el programa» en modo lectura: el programa «Mi PHAT» de ejemplo con su resumen del catálogo. */
    private val aboutFicha: PlanFicha by lazy {
        CatalogCompositionTestSupport.install()
        programPlanFicha(
            summary = ProgramPlanDisplaySummary(
                catalogEntry = PersonalizedPlanCatalog.find(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID),
                planName = "PHAT",
                provenanceLabel = "Plan original",
                sourceLine = "Layne Norton",
                summary = "Cinco días: dos de fuerza y tres de músculo.",
                planNotes = listOf("Las series rápidas van al 65 % de tu carga habitual."),
                requiredMaterial = "barra, banco y rack",
                changes = listOf(
                    "Remo con barra → remo en máquina · Cambio manual",
                    "Sentadilla → sentadilla hack · Molestia en la rodilla",
                    "Cambio sin detalle",
                ),
                daysPerWeek = "5 días",
                maxSessionMinutes = 75,
                weeklyVolume = "",
                pendingLoadCount = 2,
            ),
            program = abProgram,
            description = null,
            calendarRange = "6 oct – 16 nov",
            isActive = true,
            canOpenPlanInfo = true,
            currentWeekId = null,
            today = LocalDate.of(2026, 10, 29),
            imageOf = { null },
        )
    }
}

// ── Fixtures de «Tus últimas 4 semanas» (los de ProgramFourWeeksModelTest) ───────────────────────────────────────────────────────

/*
 * Hoy es el jueves 29 de octubre de 2026: las 4 semanas de calendario empiezan los lunes 5, 12, 19 y 26 de octubre. El programa tiene 6
 * semanas (w1–w2 acumulación, w3–w5 intensificación, w6 descarga); cada semana, tres sesiones obligatorias el lunes, el miércoles y el
 * viernes, de un ejercicio con 3 series.
 */
private val fwToday = LocalDate.of(2026, 10, 29)

private val fwProgram = Program(
    id = "p",
    name = "Prueba",
    macrocycles = listOf(
        Macrocycle(
            id = "m",
            name = "Macrociclo",
            blocks = listOf(
                fwBlock("b1", BlockGoal.ACCUMULATION, fwWeek("w1"), fwWeek("w2")),
                fwBlock("b2", BlockGoal.INTENSIFICATION, fwWeek("w3"), fwWeek("w4"), fwWeek("w5")),
                fwBlock("b3", BlockGoal.DELOAD, fwWeek("w6")),
            ),
        ),
    ),
)

private fun fwBlock(id: String, goal: BlockGoal, vararg weeks: ProgramWeek) = Block(
    id = id,
    name = "Bloque $id",
    goal = goal,
    mesocycles = listOf(Mesocycle(id = "$id-meso", name = "Mesociclo $id", weeks = weeks.toList())),
)

private fun fwWeek(id: String) = ProgramWeek(
    id = id,
    name = "Semana $id",
    sessions = listOf(fwSession("$id-a", 1), fwSession("$id-b", 3), fwSession("$id-c", 5)),
)

private fun fwSession(id: String, day: Int) = Session(
    id = id,
    name = "Sesión $id",
    dayOfWeek = day,
    exercises = listOf(Exercise(id = "$id-ex", name = "Ejercicio $id", sets = List(3) { ExerciseSet(id = "$id-ex-$it") })),
)

/** Un entreno de [sessionId] en [weekId] el día [date]: press banca, 3 series de [weight] kg × [reps], 60 minutos. */
private fun fwLog(
    id: String,
    weekId: String,
    sessionId: String,
    date: String,
    discomforts: List<String> = emptyList(),
    weight: Double = 60.0,
    reps: Int = 8,
) = WorkoutLog(
    id = id,
    programId = "p",
    sessionId = sessionId,
    sessionName = "Sesión",
    date = "${date}T10:00:00.000Z",
    durationMinutes = 60,
    completedExercises = listOf(
        CompletedExercise(
            exerciseId = "press",
            exerciseName = "Press banca",
            sets = List(3) { CompletedSet(id = "$id-$it", weight = weight, reps = reps) },
        ),
    ),
    weekId = weekId,
    discomforts = discomforts,
)

/**
 * w1 entera; w2 sin el viernes 16; w3 solo el lunes 19; w4 (la de hoy) el lunes 26 y sin el miércoles 28. El viernes 30 aún no toca.
 * Molestia en el hombro el lunes 12 y el miércoles 14.
 */
private val fwLogs = listOf(
    fwLog("l1", "w1", "w1-a", "2026-10-05"),
    fwLog("l2", "w1", "w1-b", "2026-10-07"),
    fwLog("l3", "w1", "w1-c", "2026-10-09"),
    fwLog("l4", "w2", "w2-a", "2026-10-12", discomforts = listOf("Hombro derecho")),
    fwLog("l5", "w2", "w2-b", "2026-10-14", discomforts = listOf("Hombro derecho")),
    fwLog("l6", "w3", "w3-a", "2026-10-19"),
    fwLog("l7", "w4", "w4-a", "2026-10-26"),
)

// ── Fixtures de «Sobre el programa» (los de ProgramPlanFichaTest) ────────────────────────────────────────────────────────────────

private fun abSets(count: Int, min: Int, max: Int, rir: Int? = 2, rpe: Double? = null): List<ExerciseSet> = List(count) { index ->
    ExerciseSet(
        id = "s$index",
        targetReps = (min + max) / 2,
        targetRepsRange = RepRange(min, max),
        targetRIR = rir,
        targetRPE = rpe,
        intensityMode = if (rir != null) IntensityMode.RIR else null,
    )
}

private val abBench = Exercise(id = "e-bench", name = "Press de banca", catalogConfigurationId = "cfg-bench", sets = abSets(4, 6, 8))
private val abRow = Exercise(id = "e-row", name = "Remo con barra", sets = abSets(3, 8, 12))
private val abSquat = Exercise(id = "e-squat", name = "Sentadilla", sets = abSets(3, 5, 5, rir = 3))
private val abDeadlift = Exercise(id = "e-dl", name = "Peso muerto", sets = abSets(3, 5, 5))

private val abWeek0 = ProgramWeek(
    id = "w0",
    name = "Semana 1",
    sessions = listOf(
        Session(id = "torso", name = "Torso", exercises = listOf(abBench, abRow), dayOfWeek = 1),
        Session(id = "pierna", name = "Pierna", exercises = listOf(abSquat), dayOfWeek = 4),
    ),
)

private val abWeek1 = ProgramWeek(
    id = "w1",
    name = "Semana 2",
    sessions = listOf(Session(id = "tiron", name = "Tirón", exercises = listOf(abDeadlift), dayOfWeek = 2)),
)

private val abProgram = Program(
    id = "p",
    name = "Mi PHAT",
    macrocycles = listOf(
        Macrocycle("m", "Macro", blocks = listOf(Block("b", "Bloque", mesocycles = listOf(Mesocycle("me", "Meso", weeks = listOf(abWeek0, abWeek1)))))),
    ),
)
