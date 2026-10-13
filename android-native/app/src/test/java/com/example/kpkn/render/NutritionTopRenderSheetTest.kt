package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.CaffeineLimitEngine
import com.example.kpkn.screens.nutrition.NutritionSamples
import com.example.kpkn.screens.nutrition.design.NutritionDayModule
import com.example.kpkn.screens.nutrition.design.NutritionHeader
import com.example.kpkn.screens.nutrition.design.NutritionSupplementsModule
import com.example.kpkn.screens.nutrition.design.NutritionTopIsland
import com.example.kpkn.screens.nutrition.nutritionHeroDateLabel
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
import java.util.Locale

/**
 * HOJA DE DIBUJO de la raíz de Nutrición (no comprueba nada: escribe PNG). Pinta la cabecera, «Tu día» y los suplementos con
 * los datos de [NutritionSamples], a 360 dp, con la letra al 100 % y al 130 %, y sin movimiento para ver el cuadro final. Además dibuja la
 * isla superior. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class NutritionTopRenderSheetTest {

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
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** El caso que se dibuja ahora, si la isla es el caso, y la escala de letra. Fuera de la composición para poder cambiarlos. */
    private var current by mutableStateOf<NutritionSamples.RenderCase?>(null)
    private var islandOnly by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)

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

    /** La cabecera, «Tu día» (con el entreno del día) y los suplementos de un caso, en el orden de la raíz. */
    @Composable
    private fun NutritionRenderBlock(case: NutritionSamples.RenderCase) {
        NutritionHeader(
            dateLabel = nutritionHeroDateLabel(NutritionSamples.today.toString(), Locale("es")),
            loaded = case.loaded,
            hasActivePlan = case.hasPlan,
            onEditPlan = {},
            onCreatePlan = {},
        )
        NutritionDayModule(
            loaded = case.loaded,
            days = case.days,
            selectedDate = NutritionSamples.today.toString(),
            totals = case.totals,
            goals = case.goals,
            hasActivePlan = case.hasPlan,
            onSelectDate = {},
            onDefineGoals = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            trainingBurnKcal = case.balance.trainingBurnKcal,
        )
        NutritionSupplementsModule(
            loaded = case.loaded,
            caffeineMg = case.totals.caffeineMg,
            caffeineLimits = caffeineLimits,
            creatineTodayG = 5.0,
            creatineTargetG = 5.0,
            creatineStreakDays = 6,
            isToday = true,
            onQuickAddCreatine = {},
            onOpenCreatine = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    @Test
    fun renderNutritionTopSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Column(Modifier.testTag("sheet").width(360.dp).background(Color.Black).padding(bottom = 16.dp)) {
                    if (islandOnly) {
                        Box(Modifier.width(360.dp).height(84.dp)) {
                            NutritionTopIsland(
                                visible = true,
                                title = "Hoy",
                                subtitle = "1.060 kcal restantes",
                            )
                        }
                    } else {
                        current?.let { NutritionRenderBlock(it) }
                    }
                }
            }
        }
        for ((name, case) in NutritionSamples.renderCases()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = case
                    islandOnly = false
                    fontScale = scale
                }
                rule.waitForIdle()
                save("sheet", "${name}_$suffix")
            }
        }
        for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
            rule.runOnUiThread {
                islandOnly = true
                fontScale = scale
            }
            rule.waitForIdle()
            save("sheet", "isla_$suffix")
        }
    }
}
