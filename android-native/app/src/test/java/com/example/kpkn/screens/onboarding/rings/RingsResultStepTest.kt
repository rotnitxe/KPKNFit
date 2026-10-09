package com.example.kpkn.screens.onboarding.rings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.domain.onboarding.RingsChannelCoverage
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Pantalla del resultado de RINGS sin VM: sin botones de edición, deslizadores que llaman a sus setters y músculos desplegables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RingsResultStepTest {

    @get:Rule
    val rule = createComposeRule()

    /** Lo que la pantalla entrega a los setters, en orden. */
    private class Record {
        val channels = mutableListOf<Pair<String, Int>>()
        val muscles = mutableListOf<Pair<String, Int>>()
        var resets = 0
    }

    private fun show(record: Record, answers: SetupRingsAnswers = SetupRingsAnswers(), expanded: Boolean = false) {
        rule.setContent {
            // Como en el wizard, el paso va dentro de una página desplazable: así los toques llegan aunque queden bajo el borde.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RingsResultBody(
                    coverage = COVERAGE,
                    muscleEstimates = PILLARS.associateWith { 60 },
                    answers = answers,
                    error = null,
                    loading = false,
                    today = TODAY,
                    onCommitChannel = { channel, value -> record.channels += channel to value },
                    onCommitMuscle = { pillar, value -> record.muscles += pillar to value },
                    onResetMuscles = { record.resets++ },
                    onRetry = {},
                    initiallyExpanded = expanded,
                )
            }
        }
    }

    @Test
    fun `el resultado no tiene los botones de edición de antes`() {
        show(Record())
        rule.onNodeWithText("Editar días entrenados").assertDoesNotExist()
        rule.onNodeWithText("Editar sensaciones").assertDoesNotExist()
        rule.onNodeWithText("Editar molestias").assertDoesNotExist()
    }

    @Test
    fun `soltar el deslizador de Energía guarda ese valor`() {
        val record = Record()
        show(record)
        val setProgress = rule.onNodeWithTag("rings-result-energia").fetchSemanticsNode().config[SemanticsActions.SetProgress]
        rule.runOnUiThread { setProgress.action?.invoke(80f) }
        rule.waitForIdle()
        assertEquals(listOf("energia" to 80), record.channels)
    }

    @Test
    fun `desplegar muestra un deslizador por pilar y Restablecer avisa de la acción`() {
        val record = Record()
        show(record, answers = SetupRingsAnswers(manualMusclesV2 = mapOf("Pectorales" to 70)))
        rule.onNodeWithTag("rings-result-muscle-Pectorales").assertDoesNotExist()
        rule.onNodeWithTag("rings-result-muscles-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("rings-result-muscle-Pectorales").assertExists()
        rule.onNodeWithTag("rings-result-reset").performScrollTo().performClick()
        assertEquals(1, record.resets)
    }

    @Test
    fun `soltar el deslizador de un músculo guarda su pilar`() {
        val record = Record()
        show(record, expanded = true)
        val setProgress = rule.onNodeWithTag("rings-result-muscle-Dorsales").fetchSemanticsNode().config[SemanticsActions.SetProgress]
        rule.runOnUiThread { setProgress.action?.invoke(55f) }
        rule.waitForIdle()
        assertEquals(listOf("Dorsales" to 55), record.muscles)
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 9)
        val PILLARS: List<String> = InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS

        val COVERAGE: RingsCoverage = RingsCoverage(
            muscular = channel(RecoveryChannelId.MUSCULAR, 62),
            system = channel(RecoveryChannelId.SYSTEM, 74),
            structure = channel(RecoveryChannelId.STRUCTURE, null),
        )

        fun channel(id: RecoveryChannelId, score: Int?) = RingsChannelCoverage(
            channel = id,
            source = if (score == null) RingsCoverageSource.NO_DATA else RingsCoverageSource.INITIAL_ESTIMATE,
            uncertainty = if (score == null) 100 else 40,
            score = score,
        )
    }
}
