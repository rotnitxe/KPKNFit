package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.sessionassistant.SessionAssistantReport
import com.example.kpkn.domain.sessionassistant.Verdict
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ER1e: el asistente con su control de pestañas, y «Aplicar selección» que aplica solo lo marcado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AssistantSheetComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun detail(id: String) = AssistantSuggestionDetail(
        id = id,
        label = "Detalle $id",
        action = AssistantDetailAction.LowerRpe(exerciseId = "sentadilla", amount = 0.5),
        defaultAccepted = true,
    )

    private fun suggestion(details: List<AssistantSuggestionDetail>) = AssistantSuggestion(
        id = "bajar-rpe",
        type = AssistantActionType.LOWER_RPE,
        title = "Bajar la intensidad",
        message = "La sesión sube de carga en la última serie.",
        details = details,
    )

    private fun reportWith(suggestions: List<AssistantSuggestion>) = SessionAssistantReport(
        veredicto = Verdict.OPTIMAL,
        scoreEstimado = 80,
        riesgos = emptyList(),
        ajustes = suggestions,
        oportunidades = emptyList(),
        tarjetasFantasma = emptyList(),
        plantillasCompatibles = emptyList(),
        volumenPorMusculo = emptyMap(),
        umbralesPorMusculo = emptyMap(),
        drenajeEstimado = PredictedDrain(cns = 0, muscular = 0, spinal = 0),
        duracionEstimada = 0,
        resumenTexto = "",
    )

    @Test
    fun elControlSegmentadoCambiaDePestana() {
        val state = SessionEditorUiState(assistantReport = reportWith(listOf(suggestion(listOf(detail("a"))))))
        rule.setContent {
            AssistantSheet(
                uiState = state,
                templates = emptyList(),
                onApplyAssistantSuggestion = { _, _ -> },
                onTemplateSearchChange = {},
                onSelectTemplate = {},
                onConfirmApplyTemplate = {},
                onCancelTemplateApply = {},
            )
        }
        // Pestaña inicial: Impacto (la torre de RINGS).
        rule.onNodeWithText("RINGS DE LA SESIÓN").assertExists()
        rule.onNodeWithText("Sugerencias").performClick()
        rule.onNodeWithText("Bajar la intensidad").assertExists()
        rule.onNodeWithText("RINGS DE LA SESIÓN").assertDoesNotExist()
    }

    @Test
    fun aplicarSeleccion_aplicaSoloLoMarcado() {
        val details = listOf(detail("a"), detail("b"), detail("c"))
        var applied: List<String>? = null
        rule.setContent {
            AssistantSuggestionCard(
                suggestion = suggestion(details),
                onApplySuggestion = { _, ids -> applied = ids },
            )
        }
        // Se desmarca el segundo detalle y se aplica lo que queda marcado.
        rule.onNodeWithText("Detalle b").performClick()
        rule.onNodeWithText("Aplicar selección").performClick()
        assertNotNull(applied)
        assertEquals(listOf("a", "c"), applied)
    }
}
