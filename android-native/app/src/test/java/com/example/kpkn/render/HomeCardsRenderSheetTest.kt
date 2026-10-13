package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.home.design.HomeBodyToday
import com.example.kpkn.screens.home.design.HomeNutritionToday
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
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
 * HOJA DE DIBUJO de «Nutrición de hoy» y «Cuerpo» (no comprueba nada: escribe PNG). Pinta cada caso de [HomeSamples] a 360 dp, con
 * la letra al 100 % y al 130 %, y sin movimiento para ver el cuadro final. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class HomeCardsRenderSheetTest {

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

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var caseId by mutableStateOf("")
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap (como en la hoja de Home). */
    private var hostView: View? = null

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
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

    @Test
    fun renderHomeCardsSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Column(Modifier.testTag("sheet").width(360.dp).background(Color.Black)) {
                    when (caseId) {
                        "nutricion-dos" -> HomeNutritionToday(
                            state = HomeSamples.nutritionTwoMeals(),
                            onAddMeal = {},
                            onOpenDay = {},
                        )
                        "nutricion-sin-meta" -> HomeNutritionToday(
                            state = HomeSamples.nutritionNoGoal(),
                            onAddMeal = {},
                            onOpenDay = {},
                        )
                        "cuerpo-tendencia" -> HomeBodyToday(state = HomeSamples.bodyWithTrendAndGoal(), onOpen = {})
                        "cuerpo-sin-pesos" -> HomeBodyToday(state = HomeSamples.bodyNoWeights(), onOpen = {})
                        "cuerpo-sin-altura" -> HomeBodyToday(state = HomeSamples.bodyWithoutHeight(), onOpen = {})
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        val cases = listOf(
            "nutricion-dos",
            "nutricion-sin-meta",
            "cuerpo-tendencia",
            "cuerpo-sin-pesos",
            "cuerpo-sin-altura",
        )
        for (id in cases) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    caseId = id
                    fontScale = scale
                }
                rule.waitForIdle()
                save("${id}_$suffix")
            }
        }
    }
}
