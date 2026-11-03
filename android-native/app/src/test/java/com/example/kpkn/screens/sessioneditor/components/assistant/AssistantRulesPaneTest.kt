package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.RulesSection
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La cuarta pestaña del asistente: Valores · Límites · Tiempo (pulido `as-reg-*`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class AssistantRulesPaneTest {

    @get:Rule
    val rule = createComposeRule()

    private val session = Session(
        id = "sesion",
        name = "Pierna A",
        exercises = listOf(Exercise(id = "squat", name = "Sentadilla", sets = listOf(ExerciseSet(id = "serie")))),
        parts = listOf(
            SessionPart(id = "p1", name = "Principal", targetDurationMinutes = 25),
            SessionPart(id = "p2", name = "Accesorios", targetDurationMinutes = 15),
        ),
        targetDurationMinutes = 45,
    )

    private val breakdown = SessionTimeBreakdown(
        setupSeconds = 360, executionSeconds = 1260, restSeconds = 1680, warmupSeconds = 180,
        totalSeconds = 3480, exerciseCount = 2, totalSetCount = 2,
    )

    @Composable
    private fun Pane(
        state: SessionEditorUiState,
        section: RulesSection,
        actions: AsRulesActions = AsRulesActions.None,
    ) {
        Box(Modifier.width(360.dp).height(880.dp)) {
            AssistantRulesPane(
                uiState = state,
                actions = actions,
                limitStatuses = null,
                clashBanner = null,
                section = section,
                onSection = {},
                onGoSuggestions = {},
                onToast = { _, _ -> },
                onApplied = {},
            )
        }
    }

    @Test
    fun limitesMuestraLosMaximosYLaExplicacionDeLosRigidos() {
        rule.setContent { Pane(SessionEditorUiState(session = session), RulesSection.LIMITS) }
        rule.onNodeWithText("RPE máximo").assertExists()
        rule.onAllNodesWithText("Series por músculo").assertCountEquals(2)
        rule.onNodeWithText("Límites rígidos").performScrollTo().assertExists()
        rule.onNodeWithText("Solo te aviso al guardar. El asistente no propone ajustes de límites.").assertExists()
    }

    @Test
    fun sinLimitesCadaFilaLoDiceYUnaLineaInvitaAPonerUno() {
        rule.setContent { Pane(SessionEditorUiState(session = session), RulesSection.LIMITS) }
        rule.onNodeWithText("Sin límites. Pon un máximo y el asistente avisará cuando la sesión lo supere.").assertExists()
        rule.onAllNodesWithText("Sin límite").onLast().assertExists()
    }

    @Test
    fun valoresConservaLaIntensidadGlobalEnUnaFilaPlegada() {
        rule.setContent { Pane(SessionEditorUiState(session = session), RulesSection.VALUES) }
        rule.onNodeWithText("Aplicar intensidad").assertDoesNotExist()
        rule.onNodeWithText("Modificar intensidad").performScrollTo().performClick()
        rule.onNodeWithText("Aplicar intensidad").assertExists()
    }

    @Test
    fun valoresAplicaLaIntensidadGlobalConFalloComoValorCero() {
        var applied: Triple<IntensityMode, Double, Set<String>?>? = null
        val actions = AsRulesActions(
            onApplyRules = { ApplyRulesOutcome.NoChanges },
            onRuleDefaultsChange = { _, _, _, _, _, _, _, _, _, _ -> },
            onPatchRuleDefaults = { _, _ -> },
            onRuleLimitsChange = { _, _ -> },
            onAdvancedRuleLimitsChange = { _, _, _, _ -> },
            onApplyGlobalIntensityAdjustment = { mode, value, muscles -> applied = Triple(mode, value, muscles) },
            setTargetDuration = {},
            setPartTargetDuration = { _, _ -> },
            setExerciseTargetDuration = { _, _ -> },
            onDistributeTargetAcrossParts = {},
            onApplyRuleTemplate = { _, _ -> },
            onSaveRuleTemplate = {},
            onRenameRuleTemplate = { _, _ -> },
            onDeleteRuleTemplate = {},
            onApplyTimeCoachSuggestion = {},
            onDismissTimeCoachSuggestion = {},
        )
        rule.setContent { Pane(SessionEditorUiState(session = session), RulesSection.VALUES, actions) }
        rule.onNodeWithText("Modificar intensidad").performScrollTo().performClick()
        // «Fallo» también es un segmento de Valores de serie: el de la fila plegada es el último del árbol.
        rule.onAllNodesWithText("Fallo").onLast().performScrollTo().performClick()
        rule.onNodeWithText("Aplicar intensidad").performScrollTo().performClick()
        assertEquals(IntensityMode.FAILURE, applied?.first)
        assertEquals(0.0, applied?.second ?: -1.0, 0.0)
        assertNull(applied?.third)
    }

    @Test
    fun aplicarALaSesionLlamaALasReglasYAvisaSiNoCambiaNada() {
        var toast: String? = null
        var calls = 0
        val actions = AsRulesActions.None.let {
            AsRulesActions(
                onApplyRules = { calls++; ApplyRulesOutcome.NoChanges },
                onRuleDefaultsChange = it.onRuleDefaultsChange, onPatchRuleDefaults = it.onPatchRuleDefaults,
                onRuleLimitsChange = it.onRuleLimitsChange, onAdvancedRuleLimitsChange = it.onAdvancedRuleLimitsChange,
                onApplyGlobalIntensityAdjustment = it.onApplyGlobalIntensityAdjustment, setTargetDuration = it.setTargetDuration,
                setPartTargetDuration = it.setPartTargetDuration, setExerciseTargetDuration = it.setExerciseTargetDuration,
                onDistributeTargetAcrossParts = it.onDistributeTargetAcrossParts, onApplyRuleTemplate = it.onApplyRuleTemplate,
                onSaveRuleTemplate = it.onSaveRuleTemplate, onRenameRuleTemplate = it.onRenameRuleTemplate,
                onDeleteRuleTemplate = it.onDeleteRuleTemplate, onApplyTimeCoachSuggestion = it.onApplyTimeCoachSuggestion,
                onDismissTimeCoachSuggestion = it.onDismissTimeCoachSuggestion,
            )
        }
        rule.setContent {
            Box(Modifier.width(360.dp).height(880.dp)) {
                AssistantRulesPane(
                    uiState = SessionEditorUiState(session = session), actions = actions, limitStatuses = null, clashBanner = null,
                    section = RulesSection.VALUES, onSection = {}, onGoSuggestions = {}, onToast = { t, _ -> toast = t }, onApplied = {},
                )
            }
        }
        rule.onNodeWithText("Aplicar a la sesión").performClick()
        assertEquals(1, calls)
        assertEquals("Sin cambios", toast)
    }

    @Test
    fun tiempoOfreceLimiteYEstimadoDesdeElReloj() {
        var selected: Int? = null
        val actions = AsRulesActions(
            onApplyRules = { ApplyRulesOutcome.NoChanges },
            onRuleDefaultsChange = { _, _, _, _, _, _, _, _, _, _ -> },
            onPatchRuleDefaults = { _, _ -> },
            onRuleLimitsChange = { _, _ -> },
            onAdvancedRuleLimitsChange = { _, _, _, _ -> },
            onApplyGlobalIntensityAdjustment = { _, _, _ -> },
            setTargetDuration = { selected = it },
            setPartTargetDuration = { _, _ -> },
            setExerciseTargetDuration = { _, _ -> },
            onDistributeTargetAcrossParts = {},
            onApplyRuleTemplate = { _, _ -> },
            onSaveRuleTemplate = {},
            onRenameRuleTemplate = { _, _ -> },
            onDeleteRuleTemplate = {},
            onApplyTimeCoachSuggestion = {},
            onDismissTimeCoachSuggestion = {},
        )
        rule.setContent { Pane(SessionEditorUiState(session = session, estimatedDurationMinutes = 58), RulesSection.TIME, actions) }
        rule.onNodeWithText("Estimado", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("60 min").performScrollTo().performClick()
        assertEquals(60, selected)
    }

    @Test
    fun tiempoRepartePresupuestoPorGrupoConSuLeyenda() {
        rule.setContent {
            Pane(SessionEditorUiState(session = session, sessionTimeBreakdown = breakdown, estimatedDurationMinutes = 58), RulesSection.TIME)
        }
        rule.onNodeWithText("Principal 25 min · Accesorios 15 min · 5 min libres").performScrollTo().assertExists()
        rule.onNodeWithText(com.example.kpkn.screens.sessioneditor.sessionTimeBreakdownCaption(breakdown)).assertExists()
    }

    @Test
    fun stepLimit_arrancaEnUnValorRazonableYElMinimoLoQuita() {
        assertEquals(9.0, stepLimit(null, +1, 9.0, 0.5, 1.0)!!, 0.0)
        assertNull(stepLimit(null, -1, 9.0, 0.5, 1.0))
        assertEquals(9.5, stepLimit(9.0, +1, 9.0, 0.5, 1.0)!!, 0.0)
        assertEquals(1.0, stepLimit(1.5, -1, 9.0, 0.5, 1.0)!!, 0.0)
        assertNull(stepLimit(1.0, -1, 9.0, 0.5, 1.0))
        assertTrue(stepLimit(6.0, -1, 6.0, 1.0, 1.0)!! == 5.0)
    }
}
