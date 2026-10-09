package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.competitionArtOf
import com.example.kpkn.screens.onboarding.entreno.CompetitionStepContent
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Hoja de render de «¿Compites?»: los símbolos de las seis disciplinas (en el cuadro final, y un bucle de los que tienen
 * destello), el paso con «Sí, tengo fecha» (calendario y línea de bloques) a 360 dp y, además, a 320 dp con la letra al 130 %.
 * Sin `KPKN_RENDER_DIR` no dibuja nada.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w2100dp-h2100dp-mdpi", application = android.app.Application::class)
class CompetitionRenderSheetTest {

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

    private val disciplines = listOf(
        TrainingGoalProfile.POWERLIFTING,
        TrainingGoalProfile.WEIGHTLIFTING,
        TrainingGoalProfile.STRONGMAN,
        TrainingGoalProfile.BODYBUILDING,
        TrainingGoalProfile.ARMWRESTLING,
        TrainingGoalProfile.CALISTHENICS,
    )

    /** Lo que se dibuja ahora: «symbols», «step» (360 dp) o «step320» (320 dp con la letra al 130 %). */
    private var current by mutableStateOf("symbols")

    private var hostView: View? = null

    /** Filas: disciplina; columnas: con fecha, sin fecha y no compito, en su cuadro final (movimiento reducido). */
    @Composable
    private fun SymbolsSheet() {
        Column {
            for (profile in disciplines) {
                Row {
                    for (answer in CompetitionAnswer.entries) {
                        val art = competitionArtOf(profile, answer, reduced = true)
                        // Tamaño real (64 px, ≈ 60 dp con densidad 1), para ver si se entiende a esa escala.
                        SymbolRenderSheet.Frame(art, art.restT, selected = false, px = 64f)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            // Una pasada completa del bucle con destello, en seis cuadros, por disciplina.
            for (profile in disciplines) {
                val art = competitionArtOf(profile, CompetitionAnswer.WITH_DATE, reduced = false)
                SymbolRenderSheet.Strip(art, List(6) { art.period * it / 6f })
            }
        }
    }

    /**
     * El paso completo con «Sí, tengo fecha»: 14 semanas hasta el 4 de enero de 2027, powerlifting, con movimiento reducido.
     * [width] y [fontScale] fijan el teléfono: 360 dp con la letra normal, o 320 dp con la letra al 130 %.
     */
    @Composable
    private fun StepSheet(width: Dp = 360.dp, fontScale: Float = 1f) {
        CompositionLocalProvider(
            LocalWizardReducedMotion provides true,
            LocalDensity provides Density(1f, fontScale),
        ) {
            Column(Modifier.width(width).background(Color.Black).padding(24.dp)) {
                CompetitionStepContent(
                    profile = TrainingGoalProfile.POWERLIFTING,
                    answer = CompetitionAnswer.WITH_DATE,
                    meetDateIso = "2027-01-04",
                    meetName = "Nacional Open",
                    weekStartDay = 1,
                    onAnswer = {},
                    onMeetDate = {},
                    onMeetName = {},
                    today = LocalDate.of(2026, 10, 9),
                )
            }
        }
    }

    @Test
    fun renderTheCompetitionSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.testTag("sheet").background(Color.Black).padding(16.dp)) {
                    when (current) {
                        "symbols" -> SymbolsSheet()
                        "step320" -> StepSheet(width = 320.dp, fontScale = 1.3f)
                        else -> StepSheet()
                    }
                }
            }
        }
        for (name in listOf("symbols", "step", "step320")) {
            rule.runOnUiThread { current = name }
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
            rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, "c4a_$name") }
        }
    }
}
