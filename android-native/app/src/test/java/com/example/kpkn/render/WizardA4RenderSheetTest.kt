package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanTuningContext
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.screens.onboarding.NutritionTargetControl
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardNutritionGoal
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanCallbacks
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanModel
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanPanel
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.drawNutritionGoalScene
import com.example.kpkn.ui.theme.Syne
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
 * HOJA DE RENDER del objetivo de nutrición (A4): la regla del peso objetivo con y sin valor, la sección «Hasta tu objetivo»
 * y los fotogramas de la celebración, con su último cuadro completo. No comprueba nada: escribe PNG en `KPKN_RENDER_DIR`
 * y sin esa variable se salta. Mismo montaje que [WizardRenderSheetTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WizardA4RenderSheetTest {

    companion object {
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

    /** La vista de Compose de la prueba, para dibujarla a un bitmap (como en la hoja de los tableros). */
    private var hostView: View? = null

    /** Tiempo de la escena de la celebración que se dibuja; se cambia desde la prueba. */
    private var frameT by mutableStateOf(1.2f)

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = AndroidCanvas(full)
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

    /** Un plan de 2.000 kcal con 2.400 de gasto: pierde unos 0,36 kg por semana. */
    private fun planTuning(direction: PlanDirection): PlanTuning {
        val values = PlanValues(kcal = 2000, proteinG = 160, carbsG = 200, fatG = 60)
        val context = PlanTuningContext(weightKg = 80.0, eerKcal = 2400.0, direction = direction, sex = EerSex.MALE)
        return PlanTuning(context, baseline = values, values = values)
    }

    /** La escena de la meta a la escala de su cuadro (176 × 92 unidades), como en la celebración. */
    @Composable
    private fun GoalScene(t: Float, tag: String) {
        Canvas(Modifier.testTag(tag).width(300.dp).aspectRatio(176f / 92f)) {
            val k = size.width / 176f
            withTransform({
                scale(k, k, Offset.Zero)
                translate(-12f, -44f)
            }) {
                drawNutritionGoalScene(t)
            }
        }
    }

    @Test
    fun renderGoalRuleWithoutValue() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Box(Modifier.testTag("sheet").wrapContentSize().background(Color.Black)) {
                Box(Modifier.width(360.dp).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    NutritionTargetControl(
                        unit = WizardMassUnit.KG,
                        currentKg = 80.0,
                        direction = PlanDirection.DEFICIT,
                        pace = WizardPacePreset.MEDIUM,
                        committedKg = null,
                        onCommit = {},
                    )
                }
            }
        }
        save("sheet", "a4_goal_rule_empty_s100")
    }

    @Test
    fun renderGoalRuleWithDifferenceAndEstimate() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Box(Modifier.testTag("sheet").wrapContentSize().background(Color.Black)) {
                Box(Modifier.width(360.dp).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    NutritionTargetControl(
                        unit = WizardMassUnit.KG,
                        currentKg = 80.0,
                        direction = PlanDirection.DEFICIT,
                        pace = WizardPacePreset.MEDIUM,
                        committedKg = 71.5,
                        onCommit = {},
                    )
                }
            }
        }
        save("sheet", "a4_goal_rule_diff_s100")
    }

    @Test
    fun renderHastaTuObjetivoSection() {
        onlyWhenAsked()
        val noop = WizardNutritionPlanCallbacks(
            onProtein = {},
            onCarbs = {},
            onFat = {},
            onPaceRate = {},
            onPacePreset = {},
            onChangeFinished = {},
            onReset = {},
            onSelectDay = {},
        )
        rule.setContent {
            hostView = LocalView.current
            Box(Modifier.testTag("sheet").wrapContentSize().background(Color.Black)) {
                Box(Modifier.width(360.dp).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    WizardNutritionPlanPanel(
                        model = WizardNutritionPlanModel(
                            tuning = planTuning(PlanDirection.DEFICIT),
                            goal = WizardNutritionGoal(currentKg = 80.0, targetKg = 72.0),
                        ),
                        callbacks = noop,
                    )
                }
            }
        }
        save("setup-nutrition-goal", "a4_goal_projection_s100")
    }

    @Test
    fun renderCelebrationFrames() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            Box(Modifier.testTag("sheet").wrapContentSize().background(Color.Black)) {
                Column(
                    Modifier.width(360.dp).padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GoalScene(frameT, "scene")
                    if (frameT >= 6f) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Objetivo establecido",
                            style = TextStyle(
                                fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                                lineHeight = 30.sp, color = WizardColors.text, textAlign = TextAlign.Center,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Te acompañaremos en todo el camino.",
                            style = TextStyle(
                                fontFamily = Syne, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                lineHeight = 20.sp, color = WizardColors.text.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center,
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Fecha estimada: entre marzo y abril de 2027",
                            style = WizardTypography.note,
                            color = WizardColors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        for ((t, name) in listOf(1.2f to "a4_goal_celebration_t12", 2.6f to "a4_goal_celebration_t26", 3.2f to "a4_goal_celebration_t32")) {
            frameT = t
            save("scene", name)
        }
        frameT = 6.5f
        save("sheet", "a4_goal_celebration_final")
    }
}
