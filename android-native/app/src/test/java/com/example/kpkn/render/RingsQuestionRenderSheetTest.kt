package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.domain.onboarding.RingsMainLiftsState
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.IntensityFlameArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolClock
import com.example.kpkn.screens.onboarding.rings.RingsFeelingsContent
import com.example.kpkn.screens.onboarding.rings.RingsGroupSetsContent
import com.example.kpkn.screens.onboarding.rings.RingsIntensityContent
import com.example.kpkn.screens.onboarding.rings.RingsMainLiftsContent
import com.example.kpkn.screens.onboarding.rings.RingsSessionDaysContent
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
 * HOJAS DE DIBUJO de las preguntas de RINGS (E3d): no comprueba nada, escribe PNG. Solo corre con `KPKN_RENDER_DIR`; sin ella se
 * salta. Cada pantalla se pinta con algo elegido y con la animación en su cuadro final (movimiento reducido), a 360 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class RingsQuestionRenderSheetTest {

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

    /** La vista de la actividad, para dibujarla a un bitmap (como en `WizardRenderSheetTest`). */
    private var hostView: View? = null

    /** Fecha fija de las hojas: viernes 9 de octubre de 2026. */
    private val today = LocalDate.of(2026, 10, 9)

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Pinta [content] en una columna de 360 dp sobre negro, con la animación en su cuadro final. [fontScale] 1,3 = letra al 130 %. */
    private fun sheet(fontScale: Float = 1f, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalWizardReducedMotion provides true,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                Column(
                    Modifier
                        .testTag("sheet")
                        .background(Color.Black)
                        .width(360.dp)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    content()
                }
            }
        }
        rule.waitForIdle()
    }

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
    fun sessionDaysWithThreeDaysChosen() {
        onlyWhenAsked()
        sheet {
            RingsSessionDaysContent(
                answers = SetupRingsAnswers(
                    sessionDaysState = RingsSessionDaysState.DECLARED,
                    trainedDayOffsets = setOf(0, 1, 3),
                ),
                today = today,
                onToggleDay = {},
                onNoTraining = {},
                onUnknown = {},
            )
        }
        save("rings_session_days")
    }

    @Test
    fun sessionDaysWithNoTrainingChosen() {
        onlyWhenAsked()
        sheet {
            RingsSessionDaysContent(
                answers = SetupRingsAnswers(sessionDaysState = RingsSessionDaysState.NONE),
                today = today,
                onToggleDay = {},
                onNoTraining = {},
                onUnknown = {},
            )
        }
        save("rings_session_days_none")
    }

    @Test
    fun mainLiftsWithThreeChosen() {
        onlyWhenAsked()
        sheet {
            RingsMainLiftsContent(
                answers = SetupRingsAnswers(
                    mainLiftsState = RingsMainLiftsState.DECLARED,
                    mainLifts = setOf(RingsLiftPattern.SQUAT, RingsLiftPattern.BENCH, RingsLiftPattern.CARDIO_LONG),
                ),
                onToggle = {},
                onNone = {},
                onUnknown = {},
            )
        }
        save("rings_main_lifts")
    }

    @Test
    fun groupSetsWithLegsOpen() {
        onlyWhenAsked()
        sheet {
            RingsGroupSetsContent(
                answers = SetupRingsAnswers(
                    sessionDaysState = RingsSessionDaysState.DECLARED,
                    trainedDayOffsets = setOf(1),
                    groupSets = mapOf(RingsMuscleGroup.CHEST to 10, RingsMuscleGroup.LEGS to 12, RingsMuscleGroup.CORE to 4),
                    groupSetsUnknown = setOf(RingsMuscleGroup.SHOULDERS),
                ),
                today = today,
                onSets = { _, _ -> },
                initialOpen = RingsMuscleGroup.LEGS,
            )
        }
        save("rings_group_sets")
    }

    @Test
    fun intensityWithHardChosen() {
        onlyWhenAsked()
        sheet {
            RingsIntensityContent(
                selected = InitialRecoveryIntensity.HARD,
                onSelect = {},
            )
        }
        save("rings_intensity")
    }

    @Test
    fun feelingsWithOneChosenPerChannel() {
        onlyWhenAsked()
        sheet {
            RingsFeelingsContent(
                answers = SetupRingsAnswers(muscleFeeling = 4, energy = 2, structureFeeling = 1),
                onLevel = { _, _ -> },
                onNotSay = {},
            )
        }
        save("rings_feelings")
    }

    /** Reloj de símbolos con los segundos fijados: cada cuadro de la hoja es el que se pide, sin correr. */
    private fun clockAt(seconds: Float): SymbolClock = SymbolClock().also { it.seconds = seconds }

    @Test
    fun k7IntensidadSinRespuesta() {
        onlyWhenAsked()
        sheet {
            RingsIntensityContent(selected = null, onSelect = {})
        }
        save("k7_intensidad_sin_respuesta")
    }

    @Test
    fun k7IntensidadDuraA130() {
        onlyWhenAsked()
        sheet(fontScale = 1.3f) {
            RingsIntensityContent(selected = InitialRecoveryIntensity.HARD, onSelect = {})
        }
        save("k7_intensidad_dura_130")
    }

    @Test
    fun k7LlamaEnLosCuatroNivelesYDosCuadrosDeParpadeo() {
        onlyWhenAsked()
        sheet {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // La llama encendida en Suave, Moderada, Dura y Muy dura (t = 0, 1/3, 2/3 y 1).
                listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { t ->
                    IntensityFlameArt(
                        level = { t },
                        lit = { 1f },
                        clock = clockAt(0f),
                        reducedMotion = false,
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                }
                // Dos cuadros de parpadeo y de chispas en «Muy dura».
                listOf(0.35f, 0.8f).forEach { seconds ->
                    IntensityFlameArt(
                        level = { 1f },
                        lit = { 1f },
                        clock = clockAt(seconds),
                        reducedMotion = false,
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                }
            }
        }
        save("k7_llama_niveles")
    }
}
