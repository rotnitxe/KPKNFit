package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.FeedbackScoreBlock
import com.example.kpkn.screens.workout.FeedbackToggleRow
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.design.WorkoutRestPanel
import com.example.kpkn.screens.workout.design.WorkoutRestPill
import com.example.kpkn.screens.workout.design.workoutRestNextLine
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
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

private const val SHEET_TAG = "rest-render-sheet"

/**
 * HOJA DE DIBUJO del descanso único de la sesión en vivo (KPKN LS3): no comprueba nada, escribe PNG. Solo corre con la variable de entorno
 * `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta. Pinta a 360 dp, al 100 % y al 130 % de letra:
 * el descanso a 1:45 de 2:30, con descanso sugerido, en cero, «Siguiente ronda» en una superserie, la píldora minimizada bajo la fila
 * superior y el feedback del ejercicio con sus piezas reales.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WorkoutRestRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que WizardRenderSheetTest). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Test fun descansoA145De230Al100() { onlyWhenAsked(); draw("ls3_rest_105_of_150_100", 1f) { RestSample(105, 150, STANDARD_NEXT) } }
    @Test fun descansoA145De230Al130() { onlyWhenAsked(); draw("ls3_rest_105_of_150_130", 1.3f) { RestSample(105, 150, STANDARD_NEXT) } }

    @Test fun conDescansoSugeridoAl100() { onlyWhenAsked(); draw("ls3_rest_suggested_100", 1f) { RestSample(105, 150, STANDARD_NEXT, suggested = 150) } }
    @Test fun conDescansoSugeridoAl130() { onlyWhenAsked(); draw("ls3_rest_suggested_130", 1.3f) { RestSample(105, 150, STANDARD_NEXT, suggested = 150) } }

    @Test fun enCeroAl100() { onlyWhenAsked(); draw("ls3_rest_zero_100", 1f) { RestSample(0, 150, STANDARD_NEXT) } }
    @Test fun enCeroAl130() { onlyWhenAsked(); draw("ls3_rest_zero_130", 1.3f) { RestSample(0, 150, STANDARD_NEXT) } }

    @Test fun superserieSiguienteRondaAl100() { onlyWhenAsked(); draw("ls3_rest_round_100", 1f) { RestSample(105, 150, ROUND_NEXT) } }
    @Test fun superserieSiguienteRondaAl130() { onlyWhenAsked(); draw("ls3_rest_round_130", 1.3f) { RestSample(105, 150, ROUND_NEXT) } }

    @Test fun pildoraSobreLaFilaSuperiorAl100() { onlyWhenAsked(); draw("ls3_rest_pill_100", 1f) { PillSample() } }
    @Test fun pildoraSobreLaFilaSuperiorAl130() { onlyWhenAsked(); draw("ls3_rest_pill_130", 1.3f) { PillSample() } }

    @Test fun feedbackDelEjercicioAl100() { onlyWhenAsked(); draw("ls3_rest_feedback_100", 1f) { FeedbackSample() } }
    @Test fun feedbackDelEjercicioAl130() { onlyWhenAsked(); draw("ls3_rest_feedback_130", 1.3f) { FeedbackSample() } }

    private fun draw(name: String, fontScale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    Box(Modifier.background(Color.Black).padding(16.dp).testTag(SHEET_TAG)) {
                        Column(modifier = Modifier.fillMaxWidth()) { content() }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.waitForIdle()
        save(SHEET_TAG, name)
    }

    /** El panel con datos de ejemplo: el descanso, su línea siguiente, el sugerido y los botones tal cual salen en la pantalla. */
    @Composable
    private fun RestSample(remaining: Int, total: Int, nextLine: String?, suggested: Int? = null) {
        WorkoutRestPanel(
            remainingSeconds = remaining,
            totalSeconds = total,
            nextLine = nextLine,
            suggestedSeconds = suggested,
            onAdjustSeconds = {},
            onSkip = {},
            onUseSuggestion = {},
        )
    }

    /** La píldora minimizada bajo una fila superior de 56 dp (la de la sesión, con el cronómetro de ejemplo). */
    @Composable
    private fun PillSample() {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                Text(text = "24:31", style = KpknType.metricM, color = WizardColors.text)
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                WorkoutRestPill(remainingSeconds = 105, totalSeconds = 150, onOpen = {})
            }
            Spacer(modifier = Modifier.height(160.dp))
        }
    }

    /** El feedback del ejercicio con sus piezas reales (la regla, las dos puntuaciones, «Fallo», la molestia y la píldora). */
    @Composable
    private fun FeedbackSample() {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(text = "Press banca", style = KpknType.bodyStrong, color = WizardColors.text)
                    FeedbackScoreBlock(
                        eyebrow = "Calidad técnica",
                        value = 8,
                        contentLabel = "Calidad técnica de Press banca",
                        onValueChange = {},
                    )
                    FeedbackScoreBlock(
                        eyebrow = "Qué tan intenso fue",
                        value = 9,
                        contentLabel = "Intensidad percibida de Press banca",
                        onValueChange = {},
                        trailing = { KpknChip(text = "Fallo", selected = false, onClick = {}) },
                    )
                }
            }
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                FeedbackToggleRow(text = "¿Sientes alguna molestia?", open = false, onToggle = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknChip(text = "Hombro", selected = true, onClick = {})
                KpknChip(text = "Codo", selected = false, onClick = {})
            }
            KpknPrimaryPill(text = "Registrar feedback", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
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

    private val STANDARD_NEXT: String? = workoutRestNextLine(RestTimerKind.STANDARD, nextSetNumber = 3, plannedWeightKg = 80.0, plannedReps = 8)
    private val ROUND_NEXT: String? = workoutRestNextLine(RestTimerKind.SUPERSET_ROUND, nextSetNumber = null, plannedWeightKg = null, plannedReps = null)
}
