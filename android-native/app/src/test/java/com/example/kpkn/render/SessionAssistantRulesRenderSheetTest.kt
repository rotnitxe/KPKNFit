package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CalorieRange
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.domain.sessionassistant.SessionAssistantReport
import com.example.kpkn.domain.sessionassistant.TimeCoachAction
import com.example.kpkn.domain.sessionassistant.TimeCoachFatigueDelta
import com.example.kpkn.domain.sessionassistant.TimeCoachSuggestion
import com.example.kpkn.domain.sessionassistant.Verdict
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.SessionDraftSnapshot
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.SessionCloneDayOption
import com.example.kpkn.screens.sessioneditor.components.ExerciseGoalDialog
import com.example.kpkn.screens.sessioneditor.components.ExerciseSmartLoadDialog
import com.example.kpkn.screens.sessioneditor.components.sheets.AssistantSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.HistorySheet
import com.example.kpkn.screens.sessioneditor.components.sheets.RulesSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SessionClonerSheet
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

/**
 * HOJA DE DIBUJO del asistente y de las reglas (ER1e): el asistente en sus tres pestañas, Reglas y Tiempo (dentro y fuera del límite),
 * Versiones, Transferir, Meta y Carga inteligente, con piezas reales y datos de ejemplo. No comprueba nada: escribe PNG a 360 × 880 dp, con la letra al
 * 100 % y al 130 %, sin movimiento. Solo corre con `KPKN_RENDER_DIR`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class SessionAssistantRulesRenderSheetTest {

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

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Los casos de la hoja, en orden, y el rótulo que se pulsa antes de dibujar (si hace falta). */
    private val cases = listOf(
        "asistente_impacto" to null,
        "asistente_sugerencias" to "Sugerencias",
        "asistente_plantillas" to "Plantillas",
        "asistente_mis_plantillas" to "Plantillas",
        "reglas" to null,
        "tiempo_dentro" to null,
        "tiempo_fuera" to null,
        "versiones" to null,
        "transferir" to null,
        "meta" to null,
        "carga_inteligente" to null,
    )

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

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

    /** Ventana visible de un teléfono de 360 × 880 dp; el contenido largo se desplaza. */
    private fun heightOf(@Suppress("UNUSED_PARAMETER") name: String): Int = 880

    // ─── Datos de ejemplo ─────────────────────────────────────────────────────

    private val squat = Exercise(
        id = "sentadilla",
        name = "Sentadilla trasera",
        exerciseDbId = "squat",
        trainingMode = TrainingMode.REPS,
        goal1RM = 102.5,
        sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, targetRPE = 8.0),
            ExerciseSet(id = "s2", targetReps = 8, targetRPE = 8.0),
            ExerciseSet(id = "s3", targetReps = 6, targetRPE = 8.5),
        ),
        targetDurationMinutes = 14,
    )

    private val press = Exercise(
        id = "press",
        name = "Press de banca",
        exerciseDbId = "bench",
        trainingMode = TrainingMode.REPS,
        sets = listOf(
            ExerciseSet(id = "p1", targetReps = 10, targetRPE = 7.5),
            ExerciseSet(id = "p2", targetReps = 10, targetRPE = 7.5),
        ),
        targetDurationMinutes = 10,
    )

    private val session = Session(
        id = "dia-pierna-a",
        name = "Pierna A",
        parts = listOf(
            SessionPart(id = "pierna", name = "Pierna", exercises = listOf(squat), targetDurationMinutes = 30),
            SessionPart(id = "empuje", name = "Empuje", exercises = listOf(press), targetDurationMinutes = 20),
        ),
        targetDurationMinutes = 90,
    )

    private val breakdown = SessionTimeBreakdown(
        setupSeconds = 1200,
        executionSeconds = 2100,
        restSeconds = 1500,
        warmupSeconds = 300,
        totalSeconds = 4800,
        exerciseCount = 2,
        totalSetCount = 5,
    )


    private val suggestion = AssistantSuggestion(
        id = "bajar-rpe",
        type = AssistantActionType.LOWER_RPE,
        title = "Bajar la intensidad de la última serie",
        message = "Sube la carga en la última serie de sentadilla.",
        details = listOf(
            AssistantSuggestionDetail(
                id = "d1",
                label = "Sentadilla trasera · RPE 8,5 → 8",
                action = AssistantDetailAction.LowerRpe(exerciseId = "sentadilla", amount = 0.5),
                defaultAccepted = true,
            ),
            AssistantSuggestionDetail(
                id = "d2",
                label = "Press de banca · RPE 7,5 → 7",
                action = AssistantDetailAction.LowerRpe(exerciseId = "press", amount = 0.5),
                defaultAccepted = false,
            ),
        ),
    )

    private val report = SessionAssistantReport(
        veredicto = Verdict.WARNING,
        scoreEstimado = 72,
        riesgos = emptyList(),
        ajustes = listOf(suggestion),
        oportunidades = emptyList(),
        tarjetasFantasma = emptyList(),
        plantillasCompatibles = emptyList(),
        volumenPorMusculo = mapOf("Cuádriceps" to 8.0, "Pectorales" to 4.0),
        umbralesPorMusculo = emptyMap(),

        duracionEstimada = 80,
        resumenTexto = "",
    )

    private val timeCoachSuggestion = TimeCoachSuggestion(
        id = "quitar-accesorio",
        title = "Quitar el ejercicio accesorio",
        explanation = "Ahorra 10 min y baja la fatiga de la columna.",
        minutesSaved = 10,
        fatigueDelta = TimeCoachFatigueDelta.LOWER,
        action = TimeCoachAction.RemoveExercise(exerciseId = "press"),
    )

    private val assistantState = SessionEditorUiState(
        session = session,
        sessionTimeBreakdown = breakdown,
        estimatedDurationMinutes = 80,
        assistantReport = report,
    )

    /** Reglas y tiempo. Con [limit] en 120 min el reloj queda dentro del límite; con 60, fuera. */
    private fun rulesState(initialTab: Int, limit: Int, suggestions: List<TimeCoachSuggestion>) = SessionEditorUiState(
        session = session.copy(targetDurationMinutes = limit),
        sessionTimeBreakdown = breakdown,
        estimatedDurationMinutes = 80,
        rulesSheetInitialTab = initialTab,
        timeCoachSuggestions = suggestions,
    )

    private val historyState = SessionEditorUiState(
        session = session,
        localDraftHistory = listOf(
            SessionDraftSnapshot(
                id = "v1",
                session = session.copy(name = "Pierna A (antes)"),
                savedAtMs = 1_760_500_000_000L,
                reason = "Entreno completado",
                changedFields = listOf("Series", "Descanso"),
                exerciseCount = 2,
                setCount = 5,
                partCount = 2,
            ),
            SessionDraftSnapshot(
                id = "v2",
                session = session,
                savedAtMs = 1_760_600_000_000L,
                reason = "Cambio manual",
                changedFields = listOf("Meta"),
                exerciseCount = 2,
                setCount = 5,
                partCount = 2,
            ),
        ),
    )

    /** Un bloque de la hoja del caso, con la letra del grosor pedido y sin movimiento. */
    @Composable
    private fun Sheet(name: String) {
        when (name) {
            "asistente_impacto", "asistente_sugerencias", "asistente_plantillas", "asistente_mis_plantillas" -> AssistantSheet(
                uiState = assistantState,
                templates = emptyList(),
                onApplyAssistantSuggestion = { _, _ -> },
                onTemplateSearchChange = {},
                onSelectTemplate = {},
                onConfirmApplyTemplate = {},
                onCancelTemplateApply = {},
            )
            "reglas" -> RulesSheet(
                uiState = rulesState(initialTab = 0, limit = 120, suggestions = emptyList()),
                onApplyRules = { ApplyRulesOutcome.NoChanges },
                onRuleDefaultsChange = { _, _, _, _, _, _, _, _, _, _ -> },
                onRuleLimitsChange = { _, _ -> },
                onAdvancedRuleLimitsChange = { _, _, _, _ -> },
                onApplyGlobalIntensityAdjustment = { _, _, _ -> },
                setTargetDuration = {},
                setPartTargetDuration = { _, _ -> },
                setExerciseTargetDuration = { _, _ -> },
            )
            "tiempo_dentro" -> RulesSheet(
                uiState = rulesState(initialTab = 1, limit = 120, suggestions = emptyList()),
                onApplyRules = { ApplyRulesOutcome.NoChanges },
                onRuleDefaultsChange = { _, _, _, _, _, _, _, _, _, _ -> },
                onRuleLimitsChange = { _, _ -> },
                onAdvancedRuleLimitsChange = { _, _, _, _ -> },
                onApplyGlobalIntensityAdjustment = { _, _, _ -> },
                setTargetDuration = {},
                setPartTargetDuration = { _, _ -> },
                setExerciseTargetDuration = { _, _ -> },
            )
            "tiempo_fuera" -> RulesSheet(
                uiState = rulesState(initialTab = 1, limit = 60, suggestions = listOf(timeCoachSuggestion)),
                onApplyRules = { ApplyRulesOutcome.NoChanges },
                onRuleDefaultsChange = { _, _, _, _, _, _, _, _, _, _ -> },
                onRuleLimitsChange = { _, _ -> },
                onAdvancedRuleLimitsChange = { _, _, _, _ -> },
                onApplyGlobalIntensityAdjustment = { _, _, _ -> },
                setTargetDuration = {},
                setPartTargetDuration = { _, _ -> },
                setExerciseTargetDuration = { _, _ -> },
            )
            "versiones" -> HistorySheet(uiState = historyState, onRestoreSnapshot = {})
            "transferir" -> SessionClonerSheet(
                uiState = SessionEditorUiState(
                    session = session,
                    weekId = "semana-3",
                    cloneDayOptions = listOf(
                        SessionCloneDayOption(
                            key = "viernes-3", macroIndex = 0, mesoIndex = 0,
                            weekId = "semana-3", dayOfWeek = 5,
                            macroName = "Bloque 1", blockName = "Bloque 1",
                            mesoName = "Mesociclo 1", weekName = "Semana 3",
                        ),
                    ),
                ),
                onCloneCurrentToTargets = { _, _, _ -> },
                onImportFromSource = { _, _, _ -> },
            )
            "meta" -> ExerciseGoalDialog(
                exercise = squat,
                goalRmInput = "102,5",
                onGoalRmInputChange = {},
                onUpdateExercise = {},
                onDismiss = {},
            )
            "carga_inteligente" -> ExerciseSmartLoadDialog(
                exercise = squat,
                rmInputMode = "direct",
                onRmInputModeChange = {},
                directRmInput = "120",
                onDirectRmInputChange = {},
                prWeightInput = "",
                onPrWeightInputChange = {},
                prRepsInput = "",
                onPrRepsInputChange = {},
                customUnitInput = "",
                localPrEstimatedRm = null,
                resolved1RM = 120.0,
                onUpdateExercise = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun renderAssistantRulesSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                key(current) {
                    Box(
                        Modifier
                            .testTag("sheet")
                            .width(360.dp)
                            .height(heightOf(cases[current].first).dp)
                            .background(Color.Black),
                    ) {
                        Sheet(cases[current].first)
                    }
                }
            }
        }
        for ((index, case) in cases.withIndex()) {
            val (name, tapLabel) = case
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = index
                    fontScale = scale
                }
                rule.waitForIdle()
                if (tapLabel != null) {
                    rule.onNodeWithText(tapLabel).performClick()
                    rule.waitForIdle()
                }
                // La hoja conserva su estado entre escalas de letra: el toque solo se hace en la primera.
                if (name == "asistente_mis_plantillas" && suffix == "s100") {
                    rule.onNodeWithText("Mis plantillas · 0").performClick()
                    rule.waitForIdle()
                }
                if (name == "transferir" && suffix == "s100") {
                    rule.onNodeWithText("Viernes · Semana 3").performClick()
                    rule.waitForIdle()
                }
                save("sheet", "${name}_$suffix")
            }
        }
    }
}
