package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.sessionassistant.SessionAssistantReport
import com.example.kpkn.domain.sessionassistant.Verdict
import com.example.kpkn.screens.sessioneditor.AssistantSuggestionOutcome
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantCallbacks
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantSheetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El asistente con sus cuatro pestañas, y «Aplicar selección» que aplica solo lo marcado (pulido `as-*`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class AssistantSheetComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val session = Session(
        id = "s",
        name = "Legs II",
        exercises = listOf(Exercise(id = "ex", name = "Sentadilla", sets = listOf(ExerciseSet(id = "a"), ExerciseSet(id = "b")))),
        lastModifiedAtMs = 7L,
    )

    private fun detail(id: String) = AssistantSuggestionDetail(
        id = id,
        label = "Detalle $id",
        action = AssistantDetailAction.LowerRpe(exerciseId = "ex", amount = 0.5),
        defaultAccepted = true,
    )

    private fun suggestion(details: List<AssistantSuggestionDetail>, id: String = "bajar-rpe", title: String = "Bajar la intensidad") = AssistantSuggestion(
        id = id,
        type = AssistantActionType.LOWER_RPE,
        title = title,
        message = "La sesión sube de carga en la última serie.",
        details = details,
    )

    private fun reportWith(suggestions: List<AssistantSuggestion>, notes: List<String> = emptyList()) = SessionAssistantReport(
        veredicto = Verdict.OPTIMAL,
        scoreEstimado = 80,
        riesgos = emptyList(),
        ajustes = suggestions,
        oportunidades = emptyList(),
        tarjetasFantasma = emptyList(),
        plantillasCompatibles = emptyList(),
        volumenPorMusculo = emptyMap(),
        umbralesPorMusculo = emptyMap(),
        duracionEstimada = 0,
        resumenTexto = "",
        notas = notes,
    )

    @Composable
    private fun Sheet(state: SessionEditorUiState, callbacks: AssistantCallbacks = AssistantCallbacks()) {
        Box(Modifier.width(360.dp).height(880.dp)) {
            AssistantSheetContent(
                uiState = state,
                templates = emptyList(),
                archivedUserTemplates = emptyList(),
                templatesReady = true,
                corruptTemplateIds = emptyList(),
                exerciseIndex = emptyMap(),
                facetsById = emptyMap(),
                callbacks = callbacks,
                fullHeight = true,
                onFullHeight = {},
            )
        }
    }

    @Test
    fun lasCuatroPestanasCambianElCuerpo() {
        val state = SessionEditorUiState(session = session, assistantReport = reportWith(listOf(suggestion(listOf(detail("a"))))))
        rule.setContent { Sheet(state) }
        rule.onNodeWithText("Volumen por músculo", ignoreCase = true).assertExists()
        rule.onNodeWithText("Sugerencias").performClick()
        rule.onNodeWithText("Bajar la intensidad").assertExists()
        rule.onNodeWithText("Volumen por músculo", ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithText("Plantillas").performClick()
        rule.onNodeWithContentDescription("Buscar plantilla").assertExists()
        rule.onNodeWithText("Reglas").performClick()
        rule.onNodeWithText("Alcance de las reglas", ignoreCase = true).assertExists()
    }

    @Test
    fun aplicarSeleccion_aplicaSoloLoMarcado() {
        var applied: List<String>? = null
        val state = SessionEditorUiState(
            session = session,
            assistantReport = reportWith(listOf(suggestion(listOf(detail("a"), detail("b"), detail("c"))))),
        )
        rule.setContent { Sheet(state, AssistantCallbacks(onApplySuggestion = { _, ids, _ -> applied = ids })) }
        rule.onNodeWithText("Sugerencias").performClick()
        // Se desmarca el segundo detalle y se aplica lo que queda marcado.
        rule.onNodeWithText("Detalle b").performClick()
        rule.onNodeWithText("Aplicar selección").performClick()
        assertNotNull(applied)
        assertEquals(listOf("a", "c"), applied)
    }

    @Test
    fun sinNingunaCasillaElBotonQuedaApagado() {
        var applied: List<String>? = null
        val state = SessionEditorUiState(session = session, assistantReport = reportWith(listOf(suggestion(listOf(detail("a"))))))
        rule.setContent { Sheet(state, AssistantCallbacks(onApplySuggestion = { _, ids, _ -> applied = ids })) }
        rule.onNodeWithText("Sugerencias").performClick()
        rule.onNodeWithText("Detalle a").performClick()
        rule.onNodeWithText("Aplicar selección").performClick()
        assertEquals(null, applied)
    }

    @Test
    fun sugerenciasMuestranComoMuchoTresAvisosIncluidasLasNotas() {
        val suggestions = (1..3).map { index -> suggestion(listOf(detail("$index")), id = "ajuste-$index", title = "Ajuste $index") }
        val state = SessionEditorUiState(session = session, assistantReport = reportWith(suggestions, notes = listOf("Nota adicional")))
        rule.setContent { Sheet(state) }
        rule.onNodeWithText("Sugerencias").performClick()
        rule.onNodeWithText("Ajuste 3").assertExists()
        rule.onNodeWithText("Nota adicional").assertDoesNotExist()
    }

    @Test
    fun unaSugerenciaAplicadaSePliegaEnUnaLineaConDeshacer() {
        var undone: String? = null
        val state = SessionEditorUiState(
            session = session,
            assistantReport = reportWith(listOf(suggestion(listOf(detail("a"))))),
            assistantOutcomes = listOf(
                AssistantSuggestionOutcome(
                    suggestionId = "bajar-rpe",
                    kind = AssistantSuggestionOutcome.Kind.APPLIED,
                    summary = "1 cambio · −2 min",
                    before = session,
                    variant = WeekVariant.A,
                    stamp = session.lastModifiedAtMs,
                    position = 0,
                ),
            ),
        )
        rule.setContent { Sheet(state, AssistantCallbacks(onUndoOutcome = { undone = it })) }
        rule.onNodeWithText("Sugerencias").performClick()
        rule.onNodeWithText("Aplicado").assertExists()
        rule.onNodeWithText("1 cambio · −2 min").assertExists()
        rule.onNodeWithText("Aplicar selección").assertDoesNotExist()
        rule.onNodeWithText("Deshacer").performClick()
        assertEquals("bajar-rpe", undone)
    }

    @Test
    fun unaSugerenciaDescartadaSeOlvidaSiLaSesionCambia() {
        val state = SessionEditorUiState(
            session = session,
            assistantReport = reportWith(listOf(suggestion(listOf(detail("a"))))),
            assistantOutcomes = listOf(
                AssistantSuggestionOutcome(
                    suggestionId = "bajar-rpe",
                    kind = AssistantSuggestionOutcome.Kind.DISMISSED,
                    summary = "",
                    before = null,
                    variant = WeekVariant.A,
                    stamp = session.lastModifiedAtMs - 1,
                    position = 0,
                ),
            ),
        )
        rule.setContent { Sheet(state) }
        rule.onNodeWithText("Sugerencias").performClick()
        rule.onNodeWithText("Descartada").assertDoesNotExist()
        rule.onNodeWithText("Aplicar selección").assertExists()
    }

    @Test
    fun misPlantillasAbreUnaListaDesdeSuBoton() {
        rule.setContent { Sheet(SessionEditorUiState(session = session)) }
        rule.onNodeWithText("Plantillas").performClick()
        rule.onNodeWithText("Mis plantillas · 0").performClick()
        rule.onNodeWithText("Aún no tienes plantillas").assertExists()
        rule.onNodeWithContentDescription("Volver").performClick()
        rule.onNodeWithContentDescription("Buscar plantilla").assertExists()
    }

    @Test
    fun sinEjerciciosMetricasInvitaAEmpezarConUnaPlantilla() {
        rule.setContent { Sheet(SessionEditorUiState(session = Session("e", "Vacía"))) }
        rule.onNodeWithText("Aún no hay nada que medir").assertExists()
        rule.onNodeWithText("Empezar con una plantilla").performClick()
        rule.onNodeWithContentDescription("Buscar plantilla").assertExists()
    }
}
