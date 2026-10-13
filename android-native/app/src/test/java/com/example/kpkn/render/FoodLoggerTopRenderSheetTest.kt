package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
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
import com.example.kpkn.screens.nutrition.design.logger.LoggerAnalysisNotice
import com.example.kpkn.screens.nutrition.design.logger.LoggerAnalysisPanel
import com.example.kpkn.screens.nutrition.design.logger.LoggerDescribeField
import com.example.kpkn.screens.nutrition.design.logger.LoggerHeader
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchEmpty
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchField
import com.example.kpkn.screens.nutrition.design.logger.LoggerSearchRow
import com.example.kpkn.screens.nutrition.design.logger.LoggerTabs
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

/**
 * HOJA DE DIBUJO del registro de comida, parte de arriba (N3a): cabecera, pestañas, Describir (vacío, con texto, analizando, con
 * error y en edición) y Buscar (con cinco resultados y sin resultados). No comprueba nada: escribe PNG en `KPKN_RENDER_DIR`, a 360 dp,
 * al 100 % y al 130 % de letra. Sin esa variable se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class FoodLoggerTopRenderSheetTest {

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

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Escena que se dibuja ahora, y letra. Fuera de la composición para poder cambiarlas desde la prueba. */
    private var scene by mutableStateOf("describir_vacio")
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

    /** Cabecera del registro con los datos de ejemplo de la comida y la fecha. */
    @Composable
    private fun Header(title: String, meal: MealType, date: String) {
        LoggerHeader(
            title = title,
            dateLabel = date,
            mealType = meal,
            onMealSelect = {},
            onSettings = {},
            onClose = {},
        )
    }

    @Composable
    private fun Scene() {
        val describeText = NutritionSamples.DESCRIBE_TEXT
        when (scene) {
            "describir_vacio" -> Sheet {
                Header("Registrar comida", MealType.LUNCH, "Hoy · lun 13 oct")
                LoggerTabs(activeTab = 0, onTabSelect = {})
                LoggerDescribeField(text = "", onTextChange = {}, onImeDone = {}, onPrivacyInfo = {})
            }
            "describir_texto" -> Sheet {
                Header("Registrar comida", MealType.LUNCH, "Hoy · lun 13 oct")
                LoggerTabs(activeTab = 0, onTabSelect = {})
                LoggerDescribeField(text = describeText, onTextChange = {}, onImeDone = {}, onPrivacyInfo = {})
            }
            "analizando" -> Sheet {
                Header("Registrar comida", MealType.LUNCH, "Hoy · lun 13 oct")
                LoggerTabs(activeTab = 0, onTabSelect = {})
                LoggerDescribeField(text = describeText, onTextChange = {}, onImeDone = {}, onPrivacyInfo = {}, enabled = false)
                LoggerAnalysisPanel(interpreting = true, elapsedMs = 0L)
            }
            "error" -> Sheet {
                Header("Registrar comida", MealType.LUNCH, "Hoy · lun 13 oct")
                LoggerTabs(activeTab = 0, onTabSelect = {})
                LoggerDescribeField(text = describeText, onTextChange = {}, onImeDone = {}, onPrivacyInfo = {})
                LoggerAnalysisNotice(
                    title = "No se pudo analizar esta descripción",
                    message = "Inténtalo de nuevo o registra los alimentos manualmente.",
                    onRetry = {},
                    technicalDetail = "IllegalStateException: dataset de porciones no cargado",
                    detailShown = false,
                )
            }
            "buscar_5" -> Sheet {
                Header("Registrar comida", MealType.LUNCH, "Hoy · lun 13 oct")
                LoggerTabs(activeTab = 1, onTabSelect = {})
                LoggerSearchField(query = "pan", onQueryChange = {}, onImeSearch = {})
                NutritionSamples.panResults.forEach { row ->
                    LoggerSearchRow(
                        name = row.name,
                        kcal = row.kcal,
                        basisGrams = row.basisGrams,
                        caption = row.caption,
                        onAdd = {},
                    )
                }
            }
            "buscar_sin" -> Sheet {
                Header("Registrar comida", MealType.LUNCH, "Hoy · lun 13 oct")
                LoggerTabs(activeTab = 1, onTabSelect = {})
                LoggerSearchField(query = "xyz", onQueryChange = {}, onImeSearch = {})
                LoggerSearchEmpty(query = "xyz", onDescribeInstead = {})
            }
            "editar" -> Sheet {
                Header("Editar comida", MealType.DINNER, "lun 13 oct")
                LoggerTabs(activeTab = 0, onTabSelect = {})
                LoggerDescribeField(text = "Arroz con pollo y ensalada", onTextChange = {}, onImeDone = {}, onPrivacyInfo = {})
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

    /** Pinta una escena a 100 % y 130 % de letra y guarda la hoja `n3a_<escena>` y `n3a_<escena>_s130`. */
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
            val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
            saveBitmap("n3a_$sceneName$suffix", crop(drawView(), bounds))
        }
    }

    @Test
    fun renderFoodLoggerTop() {
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
        for (name in listOf("describir_vacio", "describir_texto", "analizando", "error", "buscar_5", "buscar_sin", "editar")) {
            renderScene(name)
        }
    }
}
