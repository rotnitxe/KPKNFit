package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateKind
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cuerpo del asistente de la sesión (SE4) montado suelto: en Robolectric los toques dentro de una `KpknSheet` no llegan, así que se
 * prueba el contenido sin la hoja.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SessionAssistantContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setSheet(content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    private fun trainingTemplate() = SessionTemplate(
        id = "sys-training-fixture",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Empuje de prueba",
        description = "Plantilla de prueba",
        kind = SessionTemplateKind.TRAINING,
        publicationStatus = SessionTemplatePublicationStatus.VERIFIED,
        session = Session(
            id = "tpl-session",
            name = "Empuje",
            exercises = listOf(Exercise(id = "ex-press", name = "Press banca")),
        ),
    )

    @Test
    fun cambiarDePestanaAvisaAlControl() {
        var selected: SessionAssistantTab? = null
        setSheet {
            SessionAssistantSheetContent(
                tab = SessionAssistantTab.IMPACT,
                onTabSelect = { selected = it },
                impact = SessionEditorSamples.impact,
                analysisReady = true,
                suggestions = emptyList(),
                onApplySuggestion = { _, _ -> },
                templatesTab = {},
            )
        }
        rule.onNodeWithText("Sugerencias").performClick()
        assertEquals(SessionAssistantTab.SUGGESTIONS, selected)
    }

    @Test
    fun aplicarUnaSugerenciaSinDetallesLlamaConSuId() {
        var appliedId: String? = null
        var appliedIds: List<String>? = null
        setSheet {
            SessionAssistantSheetContent(
                tab = SessionAssistantTab.SUGGESTIONS,
                onTabSelect = {},
                impact = null,
                analysisReady = true,
                suggestions = listOf(SessionEditorSamples.suggestions[1]),
                onApplySuggestion = { id, ids ->
                    appliedId = id
                    appliedIds = ids
                },
                templatesTab = {},
            )
        }
        rule.onNodeWithText("Aplicar").performClick()
        assertEquals("sug-rpe", appliedId)
        assertEquals(emptyList<String>(), appliedIds)
    }

    @Test
    fun aplicarConDetallesLlevaSoloLosMarcadosYLosQueSeMarcan() {
        var appliedIds: List<String>? = null
        setSheet {
            SessionAssistantSheetContent(
                tab = SessionAssistantTab.SUGGESTIONS,
                onTabSelect = {},
                impact = null,
                analysisReady = true,
                suggestions = listOf(SessionEditorSamples.suggestions[0]),
                onApplySuggestion = { _, ids -> appliedIds = ids },
                templatesTab = {},
            )
        }
        // El segundo detalle viene sin marcar por el motor: «Aplicar» lleva solo el primero.
        rule.onNodeWithText("Aplicar").performClick()
        assertEquals(listOf("d-press"), appliedIds)
        // Al marcar el segundo, «Aplicar» lleva los dos.
        rule.onNodeWithText("Aperturas · 3 a 2 series").performClick()
        rule.onNodeWithText("Aplicar").performClick()
        assertEquals(listOf("d-press", "d-fly"), appliedIds)
    }

    @Test
    fun aplicarUnaPlantillaConContenidoPideConfirmacion() {
        setSheet {
            SessionAssistantTemplatesTab(
                templates = emptyList(),
                searchQuery = "",
                applyDecision = SessionTemplateApplyDecision(trainingTemplate()),
                onSearchChange = {},
                onSelectTemplate = {},
                onConfirmApplyTemplate = {},
                onCancelApply = {},
            )
        }
        rule.onNodeWithText("Aplicar plantilla").assertExists()
        rule.onNodeWithText("Reemplazar").assertExists()
        rule.onNodeWithText("Añadir al final").assertExists()
    }

    @Test
    fun sinDecisionPendienteNoHayConfirmacion() {
        setSheet {
            SessionAssistantTemplatesTab(
                templates = emptyList(),
                searchQuery = "",
                applyDecision = null,
                onSearchChange = {},
                onSelectTemplate = {},
                onConfirmApplyTemplate = {},
                onCancelApply = {},
            )
        }
        rule.onNodeWithText("Reemplazar").assertDoesNotExist()
    }
}
