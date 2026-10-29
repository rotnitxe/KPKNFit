package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.screens.nutrition.design.logger.LoggerDateSheet
import com.example.kpkn.screens.nutrition.design.logger.LoggerDescribeField
import com.example.kpkn.screens.nutrition.design.logger.LoggerDiscardDialog
import com.example.kpkn.screens.nutrition.design.logger.LoggerFoodCard
import com.example.kpkn.screens.nutrition.design.logger.LoggerForgetDialog
import com.example.kpkn.screens.nutrition.design.logger.LoggerHeader
import com.example.kpkn.screens.nutrition.design.logger.LoggerMeasuresSheet
import com.example.kpkn.screens.nutrition.design.logger.LoggerSaveBar
import com.example.kpkn.screens.nutrition.design.logger.LoggerSavedOverlay
import com.example.kpkn.screens.nutrition.design.logger.LoggerSettingsSheet
import com.example.kpkn.screens.nutrition.design.logger.LoggerSummaryCard
import com.example.kpkn.screens.nutrition.design.logger.LoggerToast
import com.example.kpkn.screens.nutrition.design.logger.LoggerWriteMode
import com.example.kpkn.screens.nutrition.design.logger.loggerAmountLine
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodIcon
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodKcal
import com.example.kpkn.screens.nutrition.design.logger.loggerFoodSubtitle
import com.example.kpkn.screens.nutrition.design.logger.loggerQuestionFor
import com.example.kpkn.screens.nutrition.design.logger.loggerSaveBarLine
import com.example.kpkn.screens.nutrition.design.logger.loggerSavedLine
import com.example.kpkn.screens.nutrition.design.logger.loggerUnits
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
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
 * HOJA DE DIBUJO del registro de comida, parte de abajo (Registros v4): el resumen y las tarjetas de alimentos (una con su duda dentro),
 * la barra de guardar, «Desayuno guardado», el aviso de error, las hojas de ajustes, medidas y fecha, «Olvidar», «Descartar» y la hoja
 * entera. No comprueba nada: escribe PNG en `KPKN_RENDER_DIR`, a 360 dp, al 100 % y al 130 % de letra. Sin esa
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

    /** Una tarjeta de alimento a partir de una etiqueta de ejemplo, con su duda si la tiene. */
    @Composable
    private fun Food(tag: ResolvedTag, index: Int, question: com.example.kpkn.domain.nutrition.ClarificationRequest? = null) {
        val logged = tag.loggedFood
        val name = (tag.foodItem?.name ?: tag.tag).replaceFirstChar { it.uppercase() }
        val grams = tag.amountGrams ?: logged?.amount ?: 0.0
        val approx = question != null
        LoggerFoodCard(
            name = name,
            subtitle = loggerFoodSubtitle(loggerAmountLine(grams, loggerUnits(name, tag.foodItem, tag.tag)), approx),
            kcal = loggerFoodKcal(kotlin.math.round(logged?.calories ?: 0.0).toInt(), approx),
            protein = logged?.protein ?: 0.0,
            carbs = logged?.carbs ?: 0.0,
            fats = logged?.fats ?: 0.0,
            icon = loggerFoodIcon(name),
            onOpen = {},
            question = question?.let { loggerQuestionFor(tag, it) },
            index = index,
        )
    }

    /** El resumen y las tres tarjetas: una normal, una con la duda de la tortilla y el arroz. */
    @Composable
    private fun Foods() {
        LoggerSummaryCard(kcal = 828, protein = 62.0, carbs = 85.0, fats = 24.0, targetKcal = 2300)
        Food(NutritionSamples.chickenTag, 0)
        Food(NutritionSamples.tortillaTag, 1, NutritionSamples.tortillaQuestion)
        Food(NutritionSamples.riceTag, 2)
    }

    /** La barra de guardar con los tres alimentos (828 kcal). */
    @Composable
    private fun SaveBar(enabled: Boolean = true) {
        LoggerSaveBar(
            kcal = 828,
            line = loggerSaveBarLine(3, 62, if (enabled) 0 else 1),
            actionLabel = "Guardar en almuerzo",
            enabled = enabled,
            onAction = {},
        )
    }

    @Composable
    private fun Scene() {
        when (scene) {
            "alimentos" -> Sheet {
                Header(MealType.LUNCH)
                Foods()
                SaveBar()
            }
            "barra_total" -> Sheet {
                Header(MealType.LUNCH)
                SaveBar()
            }
            "barra_apagada" -> Sheet {
                Header(MealType.LUNCH)
                SaveBar(enabled = false)
            }
            "guardado" -> Frame {
                LoggerSavedOverlay(title = "Almuerzo guardado", line = loggerSavedLine(828, 1472))
            }
            "error_guardado" -> Sheet {
                Header(MealType.LUNCH)
                Foods()
                LoggerToast(
                    message = "No se pudo guardar la comida. Tu borrador sigue aquí; vuelve a intentarlo.",
                    actionLabel = "Reintentar",
                    onAction = {},
                )
                SaveBar()
            }
            "dialogo_descartar" -> Frame {
                LoggerDiscardDialog(foodCount = 3, isEditing = false, onDiscard = {}, onKeepEditing = {})
            }
            "hoja_ajustes" -> Frame {
                LoggerSettingsSheet(onMeasures = {}, onFavoritesAndRecipes = {}, onForget = {}, onDismiss = {})
            }
            "hoja_medidas" -> Frame {
                LoggerMeasuresSheet(
                    values = mapOf("cucharadita" to 5f, "cucharada" to 15f, "cucharon" to 90f, "taza" to 250f, "vaso" to 200f, "plato" to 260f, "plato_hondo" to 380f, "bol" to 300f, "copa" to 180f),
                    onValueChange = { _, _ -> },
                    onRestore = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
            "dialogo_olvidar" -> Frame {
                LoggerForgetDialog(onCancel = {}, onForget = {})
            }
            "hoja_fecha" -> Frame {
                LoggerDateSheet(
                    selected = Today,
                    selectedTime = "14:10",
                    today = Today,
                    now = java.time.LocalTime.of(14, 10),
                    onDone = { _, _ -> },
                    onDismiss = {},
                )
            }
            "hoja_entera" -> Sheet {
                Header(MealType.LUNCH)
                LoggerDescribeField(
                    text = NutritionSamples.DESCRIBE_TEXT,
                    onTextChange = {},
                    onAnalyze = {},
                    mode = LoggerWriteMode.RESULT,
                    foodMentions = listOf("pollo a la plancha", "tortilla", "arroz blanco"),
                )
                Foods()
                SaveBar()
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
            if (sceneName == "guardado" || sceneName == "hoja_fecha" || sceneName == "alimentos" || sceneName == "hoja_entera") {
                // El check (600 ms) y la hoja de fecha (su entrada con muelle) necesitan reloj para llegar a su cuadro final.
                rule.mainClock.advanceTimeBy(if (sceneName == "hoja_fecha") 1000L else 1500L)
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
            "barra_total",
            "barra_apagada",
            "guardado",
            "error_guardado",
            "dialogo_descartar",
            "hoja_ajustes",
            "hoja_medidas",
            "dialogo_olvidar",
            "hoja_fecha",
            "hoja_entera",
        )) {
            renderScene(name)
        }
    }
}
