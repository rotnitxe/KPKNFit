package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.screens.nutrition.NutritionSamples
import com.example.kpkn.screens.nutrition.design.logger.LoggerDateSheet
import com.example.kpkn.screens.nutrition.design.logger.LoggerDiscardDialog
import com.example.kpkn.screens.nutrition.design.logger.LoggerErrorNotice
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodActions
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodDivider
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodItem
import com.example.kpkn.screens.nutrition.design.logger.LoggerHeader
import com.example.kpkn.screens.nutrition.design.logger.LoggerMeasuresDialog
import com.example.kpkn.screens.nutrition.design.logger.LoggerPrivacyDialog
import com.example.kpkn.screens.nutrition.design.logger.LoggerSaveBar
import com.example.kpkn.screens.nutrition.design.logger.LoggerSettingsDialog
import com.example.kpkn.screens.nutrition.design.logger.LoggerTabs
import com.example.kpkn.screens.nutrition.design.logger.LoggerDescribeField
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
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
import java.time.LocalDate

/**
 * HOJA DE DIBUJO del registro de comida, parte de abajo (N3b): los alimentos interpretados (con una aclaración pendiente y una estimación
 * con rango), la barra de total y guardar (total, guardando y el check de éxito), el error de guardado, los cuatro diálogos, el calendario
 * de la fecha y la hoja entera. No comprueba nada: escribe PNG en `KPKN_RENDER_DIR`, a 360 dp, al 100 % y al 130 % de letra. Sin esa
 * variable se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class FoodLoggerBottomRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private val Today: LocalDate = LocalDate.of(2026, 10, 13)
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Escena que se dibuja ahora, y letra. Fuera de la composición para poder cambiarlas desde la prueba. */
    private var scene by mutableStateOf("alimentos")
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Las acciones de las filas, todas vacías: la hoja de render solo dibuja. */
    private val noActions = LoggerFoodActions(
        onToggleExpanded = {},
        onToggleCorrection = {},
        onCorrectionQueryChange = {},
        onPickCorrection = {},
        onPortionChange = {},
        onPortionOption = {},
        onGramsChange = {},
        onMacroChange = { _, _ -> },
        onRemove = {},
        onOilLevelChange = {},
        onCookingChange = {},
        onIdentityChoice = {},
        onDeclaredComposition = {},
        onUnsure = {},
    )

    /** Columna de 360 dp sobre negro con el margen de la hoja (20 dp a los lados), con la etiqueta de la captura. */
    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .testTag("sheet")
                .width(360.dp)
                .background(KpknSurface.canvas)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }

    /** Marco de 360 x 800 dp sobre negro (un teléfono habitual): donde caen los diálogos y las hojas flotantes. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .testTag("sheet")
                .size(width = 360.dp, height = 800.dp)
                .background(KpknSurface.canvas),
        ) {
            content()
        }
    }

    /** Cabecera del registro con la fecha de hoy. */
    @Composable
    private fun Header(meal: MealType, title: String = "Registrar comida") {
        LoggerHeader(
            title = title,
            dateLabel = "Hoy · lun 13 oct",
            mealType = meal,
            onMealSelect = {},
            onSettings = {},
            onClose = {},
            onDateClick = {},
        )
    }

    /** El módulo «Lo que comiste» con sus tres alimentos: uno normal, uno con la aclaración pendiente y uno estimado con rango. */
    @Composable
    private fun FoodsModule(riceExpanded: Boolean = false) {
        KpknModule {
            KpknModuleHeader(label = "Lo que comiste")
            Spacer(Modifier.height(4.dp))
            Text(text = "Resumen de la comida", style = KpknType.caption, color = WizardColors.textMuted)
            LoggerFoodItem(
                tag = NutritionSamples.chickenTag,
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = null,
                matchUncertain = false,
                actions = noActions,
            )
            LoggerFoodDivider()
            LoggerFoodItem(
                tag = NutritionSamples.tortillaTag,
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = NutritionSamples.tortillaQuestion,
                matchUncertain = true,
                actions = noActions,
            )
            LoggerFoodDivider()
            LoggerFoodItem(
                tag = if (riceExpanded) NutritionSamples.riceTag.copy(isExpanded = true) else NutritionSamples.riceTag,
                correcting = false,
                correctionQuery = "",
                correctionResults = null,
                pendingQuestion = null,
                matchUncertain = false,
                actions = noActions,
            )
        }
    }

    /** La barra con el total de los tres alimentos (828 kcal, aproximado por la estimación) y la acción de guardar. */
    @Composable
    private fun TotalBar(busy: String? = null, succeeded: Boolean = false) {
        LoggerSaveBar(
            kcal = 828,
            protein = 62,
            carbs = 85,
            fats = 24,
            approximate = true,
            actionLabel = "Guardar en Almuerzo",
            enabled = busy == null,
            onAction = {},
            actionIcon = KpknLineIcons.Check,
            busyLabel = busy,
            succeeded = succeeded,
        )
    }

    @Composable
    private fun Scene() {
        when (scene) {
            "alimentos" -> Sheet {
                Header(MealType.LUNCH)
                FoodsModule()
                TotalBar()
            }
            "alimento_ajustes" -> Sheet {
                Header(MealType.LUNCH)
                FoodsModule(riceExpanded = true)
                TotalBar()
            }
            "barra_total" -> Sheet {
                Header(MealType.LUNCH)
                TotalBar()
            }
            "barra_guardando" -> Sheet {
                Header(MealType.LUNCH)
                TotalBar(busy = "Guardando…")
            }
            "barra_exito" -> Sheet {
                Header(MealType.LUNCH)
                TotalBar(succeeded = true)
            }
            "error_guardado" -> Sheet {
                Header(MealType.LUNCH)
                FoodsModule()
                LoggerErrorNotice(
                    message = "No se pudo guardar la comida. Tu borrador sigue aquí; vuelve a intentarlo.",
                    onDismiss = {},
                    onRetry = {},
                )
                TotalBar()
            }
            "dialogo_descartar" -> Frame {
                LoggerDiscardDialog(isEditing = false, onDiscard = {}, onKeepEditing = {})
            }
            "dialogo_configuracion" -> Frame {
                LoggerSettingsDialog(onAdjustMeasures = {}, onForgetLearned = {}, onClose = {})
            }
            "dialogo_medidas" -> Frame {
                LoggerMeasuresDialog(
                    values = mapOf("cucharadita" to 5f, "cucharada" to 15f, "cucharon" to 90f, "taza" to 250f, "vaso" to 200f, "plato" to 260f, "plato_hondo" to 380f, "bol" to 300f, "copa" to 180f),
                    onValueChange = { _, _ -> },
                    onRestore = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
            "dialogo_privacidad" -> Frame {
                LoggerPrivacyDialog(onClose = {})
            }
            "hoja_fecha" -> Frame {
                LoggerDateSheet(selected = Today, today = Today, onPick = {}, onDismiss = {})
            }
            "hoja_entera" -> Sheet {
                Header(MealType.LUNCH)
                LoggerTabs(activeTab = 0, onTabSelect = {})
                LoggerDescribeField(text = NutritionSamples.DESCRIBE_TEXT, onTextChange = {}, onImeDone = {}, onPrivacyInfo = {})
                FoodsModule()
                TotalBar()
            }
        }
    }

    /** Dibuja la vista entera sobre negro, tal como está ahora. */
    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun crop(full: Bitmap, bounds: Rect): Bitmap {
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        return Bitmap.createBitmap(full, left, top, width, height)
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Pinta una escena a 100 % y 130 % de letra y guarda `n3b_<escena>` y `n3b_<escena>_s130`. La escena del check avanza el reloj
     * 700 ms: es el último fotograma, con el trazo completo.
     */
    private fun renderScene(sceneName: String) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que la escena nueva llegue a la vista antes de capturar.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            if (sceneName == "barra_exito" || sceneName == "hoja_fecha") {
                // El check (600 ms) y la hoja de fecha (su entrada con muelle) necesitan reloj para llegar a su cuadro final.
                rule.mainClock.advanceTimeBy(if (sceneName == "hoja_fecha") 1000L else 700L)
                rule.waitForIdle()
            }
            val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
            saveBitmap("n3b_$sceneName$suffix", crop(drawView(), bounds))
        }
    }

    @Test
    fun renderFoodLoggerBottom() {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides false,
            ) {
                Scene()
            }
        }
        for (name in listOf(
            "alimentos",
            "alimento_ajustes",
            "barra_total",
            "barra_guardando",
            "barra_exito",
            "error_guardado",
            "dialogo_descartar",
            "dialogo_configuracion",
            "dialogo_medidas",
            "dialogo_privacidad",
            "hoja_fecha",
            "hoja_entera",
        )) {
            renderScene(name)
        }
    }
}
