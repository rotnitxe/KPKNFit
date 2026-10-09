package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.domain.onboarding.RingsChannelCoverage
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.rings.RingsResultBody
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
 * HOJAS DE DIBUJO del resultado de «Tus RINGS» (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se
 * salta. Tres casos: con datos, con la Columna sin calibrar y con los músculos desplegados.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-xxhdpi", application = Application::class)
class RingsResultRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno gráfico (ver `WizardRenderSheetTest`). */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private val TODAY: LocalDate = LocalDate.of(2026, 10, 9)
        private val PILLARS: List<String> = InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS

        private fun channel(id: RecoveryChannelId, score: Int?) = RingsChannelCoverage(
            channel = id,
            source = if (score == null) RingsCoverageSource.NO_DATA else RingsCoverageSource.INITIAL_ESTIMATE,
            uncertainty = if (score == null) 100 else 40,
            score = score,
        )

        private val WITH_DATA = RingsCoverage(
            muscular = channel(RecoveryChannelId.MUSCULAR, 62),
            system = channel(RecoveryChannelId.SYSTEM, 74),
            structure = channel(RecoveryChannelId.STRUCTURE, 58),
        )
        private val WITHOUT_COLUMNA = RingsCoverage(
            muscular = channel(RecoveryChannelId.MUSCULAR, 62),
            system = channel(RecoveryChannelId.SYSTEM, 74),
            structure = channel(RecoveryChannelId.STRUCTURE, null),
        )
        private val TRAINED = SetupRingsAnswers(
            sessionDaysState = RingsSessionDaysState.DECLARED,
            trainedDayOffsets = setOf(0, 2),
            groupSets = mapOf(RingsMuscleGroup.LEGS to 8, RingsMuscleGroup.BACK to 6),
        )
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private fun render(name: String, coverage: RingsCoverage, answers: SetupRingsAnswers, expanded: Boolean) {
        rule.setContent {
            hostView = LocalView.current
            Column(Modifier.background(WizardColors.background).padding(24.dp)) {
                RingsResultBody(
                    coverage = coverage,
                    muscleEstimates = PILLARS.associateWith { 60 },
                    answers = answers,
                    error = null,
                    loading = false,
                    today = TODAY,
                    onCommitChannel = { _, _ -> },
                    onCommitMuscle = { _, _ -> },
                    onResetMuscles = {},
                    onRetry = {},
                    initiallyExpanded = expanded,
                )
            }
        }
        save("rings-result-body", name)
    }

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

    @Test
    fun renderResultWithData() {
        onlyWhenAsked()
        render("rings_result_con_datos", WITH_DATA, TRAINED, expanded = false)
    }

    @Test
    fun renderResultWithColumnaUncalibrated() {
        onlyWhenAsked()
        render("rings_result_columna_sin_calibrar", WITHOUT_COLUMNA, TRAINED, expanded = false)
    }

    @Test
    fun renderResultWithMusclesExpanded() {
        onlyWhenAsked()
        val answers = TRAINED.copy(manualMusclesV2 = mapOf("Pectorales" to 70, "Dorsales" to 35))
        render("rings_result_musculos_desplegados", WITH_DATA, answers, expanded = true)
    }
}
