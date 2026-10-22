package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.CalorieRange
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import com.example.kpkn.domain.sessionassistant.SessionAssistantReport
import com.example.kpkn.domain.sessionassistant.Verdict
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.SessionCloneApplyMode
import com.example.kpkn.screens.sessioneditor.SessionCloneDayOption
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeSummary
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.sessionTimeBreakdownCaption
import com.example.kpkn.data.models.IntensityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ED-E · Compose/Robolectric de las hojas D2 (Impacto), D5 (Reglas: aplicar intensidad), D6 (Tiempo: reparto por grupo) y D8
 * (Transferir: aviso de reemplazo sobre el botón). Las de D1, D3, D4, D7 y el resto de D5/D6/D8 están en sus pruebas hermanas
 * (`SessionEditorSheetWiringTest`, `AssistantSheetComposeTest`, `EditorSheetsD5D8Test`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class EditorSheetsEdeTest {
    @get:Rule val rule = createComposeRule()

    private val squat = Exercise(
        id = "sentadilla", name = "Sentadilla trasera",
        sets = listOf(ExerciseSet(id = "s1", targetReps = 8, targetRPE = 8.0)),
    )
    private val press = Exercise(
        id = "press", name = "Press de banca",
        sets = listOf(ExerciseSet(id = "p1", targetReps = 10, targetRPE = 7.5)),
    )
    private val session = Session(
        id = "dia", name = "Pierna A",
        parts = listOf(
            SessionPart(id = "pierna", name = "Principal", exercises = listOf(squat), targetDurationMinutes = 25),
            SessionPart(id = "empuje", name = "Accesorios", exercises = listOf(press), targetDurationMinutes = 15),
        ),
        targetDurationMinutes = 45,
    )
    private val breakdown = SessionTimeBreakdown(
        setupSeconds = 360, executionSeconds = 1260, restSeconds = 1680, warmupSeconds = 180,
        totalSeconds = 3480, exerciseCount = 2, totalSetCount = 2,
    )
    private val report = SessionAssistantReport(
        veredicto = Verdict.OPTIMAL, scoreEstimado = 80, riesgos = emptyList(), ajustes = emptyList(), oportunidades = emptyList(),
        tarjetasFantasma = emptyList(), plantillasCompatibles = emptyList(), volumenPorMusculo = emptyMap(),
        umbralesPorMusculo = emptyMap(), drenajeEstimado = PredictedDrain(cns = 10, muscular = 20, spinal = 5),
        duracionEstimada = 58, resumenTexto = "",
    )

    @Test fun impactoJuntaDuracionYCaloriasConSuDesgloseDeTiempo() {
        val state = SessionEditorUiState(
            session = session,
            augeSummary = SessionEditorAugeSummary(
                sessionDrain = PredictedDrain(cns = 52, muscular = 64, spinal = 41),
                sessionEnergy = SessionEnergySummary(totalKcal = CalorieRange(low = 380, mid = 412, high = 445)),
            ),
            sessionTimeBreakdown = breakdown,
            assistantReport = report,
        )
        rule.setContent { AssistantImpactTab(uiState = state) }
        rule.onNodeWithText("Duración de la sesión", ignoreCase = true).assertExists()
        rule.onNodeWithText("Calorías estimadas", ignoreCase = true).assertExists()
        rule.onNodeWithText("380–445").assertExists()
        // El desglose es el mismo texto que usa Reglas › Tiempo.
        rule.onNodeWithText(sessionTimeBreakdownCaption(breakdown)).assertExists()
    }

    @Test fun impactoSinCaloriasNoInventaUnNumero() {
        val state = SessionEditorUiState(
            session = session,
            augeSummary = SessionEditorAugeSummary(sessionDrain = PredictedDrain(cns = 1, muscular = 1, spinal = 1)),
            sessionTimeBreakdown = breakdown,
            assistantReport = report,
        )
        rule.setContent { AssistantImpactTab(uiState = state) }
        rule.onNodeWithText("Completa pesos o 1RM en los ejercicios para estimar el gasto calórico").assertExists()
    }

    @Test fun reglasAplicaLaIntensidadGlobalConFalloComoValorCero() {
        var applied: Triple<IntensityMode, Double, Set<String>?>? = null
        rule.setContent {
            RulesSheet(
                uiState = SessionEditorUiState(session = session),
                onApplyRules = { ApplyRulesOutcome.NoChanges },
                onRuleDefaultsChange = { _, _, _, _, _, _, _, _, _, _ -> },
                onRuleLimitsChange = { _, _ -> },
                onAdvancedRuleLimitsChange = { _, _, _, _ -> },
                onApplyGlobalIntensityAdjustment = { mode, value, muscles -> applied = Triple(mode, value, muscles) },
                setTargetDuration = {},
                setPartTargetDuration = { _, _ -> },
                setExerciseTargetDuration = { _, _ -> },
            )
        }
        rule.onNodeWithText("Modificar intensidad").performScrollTo().performClick()
        // «Fallo» también es un chip de Valores de serie: el de la fila plegada es el último del árbol.
        rule.onAllNodesWithText("Fallo").onLast().performScrollTo().performClick()
        rule.onNodeWithText("Aplicar intensidad").performScrollTo().performClick()
        assertEquals(IntensityMode.FAILURE, applied?.first)
        assertEquals(0.0, applied?.second ?: -1.0, 0.0)
        assertNull(applied?.third)
    }

    @Test fun tiempoReparteElPresupuestoPorGrupoConSuLeyenda() {
        rule.setContent {
            RulesTimeTab(
                uiState = SessionEditorUiState(session = session, sessionTimeBreakdown = breakdown, estimatedDurationMinutes = 58),
                setTargetDuration = {},
                onOpenDuration = {},
                onDistributeTargetAcrossParts = {},
                onApplyTimeCoachSuggestion = {},
                onDismissTimeCoachSuggestion = {},
            )
        }
        rule.onNodeWithText("Principal 25 min · Accesorios 15 min").assertExists()
        rule.onNodeWithText("40 de 45 min asignados (5 min libres)").assertExists()
    }

    @Test fun transferirAvisaDelReemplazoSobreElBotonYUsaAnadir() {
        val target = SessionCloneDayOption(
            key = "dia-2", macroIndex = 0, mesoIndex = 0, weekId = "semana", dayOfWeek = 3,
            macroName = "Bloque", blockName = "Bloque 1", mesoName = "Mesociclo 1", weekName = "Semana 1",
            existingSessionId = "otra", existingSessionName = "Pierna B", existingExerciseCount = 5,
        )
        var mode: SessionCloneApplyMode? = null
        rule.setContent {
            SessionClonerSheet(
                uiState = SessionEditorUiState(session = session, cloneDayOptions = listOf(target), weekId = "semana"),
                onCloneCurrentToTargets = { _, _, selectedMode -> mode = selectedMode },
                onImportFromSource = { _, _, _ -> },
            )
        }
        rule.onNodeWithText("Agregar").assertDoesNotExist()
        rule.onNodeWithText("Añadir").assertExists()
        rule.onNodeWithText("Miércoles · Semana 1").performClick()
        rule.onNodeWithText("Reemplazar").performClick()
        rule.onNodeWithText("Reemplazar eliminará 5 ejercicio(s) en los destinos seleccionados.").assertExists()
        rule.onNodeWithText("Copiar ahora").performClick()
        assertEquals(SessionCloneApplyMode.REPLACE, mode)
        assertTrue(mode != SessionCloneApplyMode.APPEND)
    }
}
