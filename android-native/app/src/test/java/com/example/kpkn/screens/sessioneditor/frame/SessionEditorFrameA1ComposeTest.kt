package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ED-A · A1 del marco del editor: la cabecera sin RIR global, la píldora de día y semana, el resumen en fichas y la isla inferior.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class SessionEditorFrameA1ComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val nbsp = " "

    // ───────────────────────────── A1 · cabecera, fila superior e isla ─────────────────────────────

    @Composable
    private fun Header(protocolLabel: String?, chips: List<String>) {
        SessionEditorFrameHeader(
            sessionId = "s",
            name = "Pierna A",
            description = "",
            protocolLabel = protocolLabel,
            summaryChips = chips,
            onNameChange = {},
            onDescriptionChange = {},
            onDaySwipe = { false },
        )
    }

    @Test
    fun laCabeceraNoLlevaUnChipDeRirGlobal() {
        rule.setContent { Header(protocolLabel = null, chips = sessionFrameSummaryChips(5, 14, 58)) }

        // ED-A · A1: no hay un RIR de toda la sesión; cada ejercicio y cada serie llevan el suyo.
        rule.onAllNodesWithText("RIR", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Pierna A").assertIsDisplayed()
        rule.onNodeWithText("5 ejercicios · 14 series").assertIsDisplayed()
        rule.onNodeWithText("≈${nbsp}58${nbsp}min").assertIsDisplayed()
    }

    @Test
    fun conProtocoloLaCabeceraSoloDiceElProtocolo() {
        rule.setContent { Header(protocolLabel = "Fuerza · 5/3/1", chips = sessionFrameSummaryChips(7, 22, 54)) }

        rule.onNodeWithText("FUERZA · 5/3/1").assertIsDisplayed()
        rule.onAllNodesWithText("RIR", substring = true).assertCountEquals(0)
    }

    @Test
    fun laPildoraDiceElDiaYLaSemana() {
        assertEquals("Jue · Semana 3", frameDayWeekLabel(day = 4, weekNumber = 3, sessionName = "Pierna A"))
        assertEquals("Jue", frameDayWeekLabel(day = 4, weekNumber = null, sessionName = "Pierna A"))
        assertEquals("Pierna A", frameDayWeekLabel(day = null, weekNumber = 3, sessionName = "Pierna A"))
        assertEquals("Sesión", frameDayWeekLabel(day = null, weekNumber = null, sessionName = ""))
        val roadmap = listOf(
            SessionRoadmapOption(0, 0, 0, 0, "w1", "Macro", "Bloque 1", "Semana 1", 3),
            SessionRoadmapOption(0, 0, 0, 2, "w3", "Macro", "Bloque 1", "Semana 3", 3),
        )
        assertEquals(3, frameWeekNumber(roadmap, "w3"))
        assertEquals(1, frameWeekNumber(roadmap, "w1"))
        assertNull(frameWeekNumber(roadmap, "otra"))
        assertNull(frameWeekNumber(roadmap, ""))
    }

    @Test
    fun laFilaSuperiorMuestraDiaYSemanaYSusBotonesMidenMasDe44dp() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            SessionEditorFrameTopBar(
                dayLabel = "Jue · Semana 3",
                forward = true,
                canGoPrevious = true,
                canGoNext = true,
                onBack = {},
                onPrevious = {},
                onNext = {},
                onOpenWeek = {},
                onTransfer = {},
                onHistory = {},
            )
        }

        rule.onNodeWithText("Jue · Semana 3").assertIsDisplayed()
        listOf("Atrás", "Día anterior", "Día siguiente", "Más opciones").forEach { label ->
            val size = rule.onNodeWithContentDescription(label).fetchSemanticsNode().size
            assertTrue("$label mide ${size.height / density} dp de alto", size.height / density >= 44f)
            assertTrue("$label mide ${size.width / density} dp de ancho", size.width / density >= 44f)
        }
    }

    @Test
    fun elResumenNoCuentaSeriesEnUnaSesionSoloDeCardio() {
        assertEquals(listOf("5 ejercicios · 14 series", "≈${nbsp}58${nbsp}min"), sessionFrameSummaryChips(5, 14, 58))
        assertEquals(listOf("2 ejercicios", "≈${nbsp}42${nbsp}min"), sessionFrameSummaryChips(2, 0, 42, showSets = false))
        assertEquals(listOf("1 ejercicio · 1 serie"), sessionFrameSummaryChips(1, 1, null))
        assertEquals(listOf("Sin ejercicios"), sessionFrameSummaryChips(0, 0, 0))
    }

    @Test
    fun laIslaInferiorTieneElAnilloDelTiempoAnadirAsistenteYReglas() {
        val calls = mutableListOf<String>()
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.fillMaxSize()) {
                SessionEditorFrameBottomBar(
                    hazeState = HazeState(),
                    suggestionCount = 3,
                    estimatedMinutes = 58,
                    limitMinutes = 60,
                    onAdd = { calls += "add" },
                    onAssistant = { calls += "assistant" },
                    onRules = { calls += "rules" },
                    onTime = { calls += "time" },
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        rule.onNodeWithTag("session_editor_bar_time").performClick()
        rule.onNodeWithTag("session_editor_bar_add").performClick()
        rule.onNodeWithTag("session_editor_bar_assistant").performClick()
        rule.onNodeWithTag("session_editor_bar_rules").performClick()
        assertEquals(listOf("time", "add", "assistant", "rules"), calls)
        // El número de sugerencias va en el botón del asistente.
        rule.onNodeWithText("3").assertIsDisplayed()
        // Cada botón mide 44 dp o más.
        listOf("session_editor_bar_time", "session_editor_bar_add", "session_editor_bar_assistant", "session_editor_bar_rules").forEach { tag ->
            val size = rule.onNodeWithTag(tag).fetchSemanticsNode().size
            assertTrue("$tag mide ${size.height / density} dp de alto", size.height / density >= 44f)
        }
    }

    @Test
    fun elAnilloDelTiempoDiceLosMinutosYElLimite() {
        rule.setContent {
            Column {
                SessionEditorFrameBottomBar(
                    hazeState = HazeState(),
                    suggestionCount = 0,
                    estimatedMinutes = 58,
                    limitMinutes = null,
                    onAdd = {},
                    onAssistant = {},
                    onRules = {},
                    onTime = {},
                )
            }
        }

        // Sin límite, solo los minutos; sin sugerencias, el asistente no lleva número.
        rule.onNodeWithContentDescription("Tiempo estimado 58 minutos").assertExists()
        rule.onNodeWithText("58").assertExists()
        rule.onAllNodesWithText("0").assertCountEquals(0)
    }

}
