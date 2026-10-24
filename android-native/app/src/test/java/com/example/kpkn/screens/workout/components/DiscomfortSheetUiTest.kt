package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DISCOMFORT_KNOWLEDGE
import com.example.kpkn.data.models.DiscomfortSection
import com.example.kpkn.data.models.PROFESSIONAL_ADVICE
import com.example.kpkn.data.models.discomfortKnowledge
import com.example.kpkn.domain.discomfort.DiscomfortEvidence
import com.example.kpkn.domain.discomfort.DiscomfortEvidenceKind
import com.example.kpkn.domain.discomfort.DiscomfortInsight
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La ficha de una molestia (LV6, B2, F7): zona, título, qué es, causas, «EN TU CASO» solo con datos, qué puedes hacer con el cierre de
 * consultar a un profesional y «Reportar molestia», sin selector de gravedad; y las seis animaciones curadas, una por sección.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class DiscomfortSheetUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val nbsp = "\u00A0"

    private fun content(body: @Composable () -> Unit) {
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    CompositionLocalProvider(LocalRestClockFrozen provides 1.7f) { body() }
                }
            }
        }
    }

    private fun evidence(kind: DiscomfortEvidenceKind, text: String, advice: String) = DiscomfortEvidence(
        kind = kind,
        number = 35.0,
        text = text,
        headline = text,
        advice = advice,
        strength = 0.8,
    )

    private fun body(id: String, selected: Boolean = false, insight: DiscomfortInsight? = null, onToggle: () -> Unit = {}) = content {
        Box(Modifier.size(360.dp, 800.dp).background(Color(0xFF0E0E0E))) {
            DiscomfortSheetBody(
                knowledge = discomfortKnowledge(id)!!,
                selected = selected,
                insight = insight,
                onToggleReport = onToggle,
                animated = false,
            )
        }
    }

    @Test
    fun theSheetShowsTheZoneTheCausesAndTheClosingAdviceWithoutSeverity() {
        val knowledge = discomfortKnowledge("knee_patellar")!!
        body("knee_patellar")
        rule.onNodeWithText("RODILLA").assertExists()
        rule.onNodeWithText(knowledge.title).assertExists()
        rule.onNodeWithText(knowledge.explanation).assertExists()
        rule.onNodeWithText("CAUSAS COMUNES").assertExists()
        knowledge.commonCauses.forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("QUÉ PUEDES HACER").assertExists()
        rule.onNodeWithText(knowledge.whatYouCanDo).assertExists()
        rule.onNodeWithText(PROFESSIONAL_ADVICE).assertExists()
        rule.onNodeWithText("Reportar molestia").assertExists()
        // Sin datos del motor no hay «EN TU CASO» y la app no mide la gravedad: ni selector ni «Leve».
        rule.onAllNodesWithText("EN TU CASO").assertCountEquals(0)
        rule.onAllNodesWithText("¿CUÁNTO MOLESTA?").assertCountEquals(0)
        rule.onAllNodesWithText("Leve").assertCountEquals(0)
    }

    @Test
    fun previousReportsDoNotBecomeTodaysLoadAdvice() {
        val insight = DiscomfortInsight(
            discomfortId = "knee_patellar",
            headline = "Registro anterior",
            today = emptyList(),
            week = listOf(evidence(DiscomfortEvidenceKind.REPORTED_HISTORY, "Registraste molestia de rodilla el 13-oct-2026.", "")),
            advice = listOf("Deja 2 repeticiones en reserva.", PROFESSIONAL_ADVICE),
            persistent = false,
            strength = 0.8,
        )
        body("knee_patellar", insight = insight)
        rule.onNodeWithText("REGISTROS ANTERIORES").assertExists()
        rule.onAllNodesWithText("Hoy").assertCountEquals(0)
        rule.onNodeWithText("Registraste molestia de rodilla el 13-oct-2026.").assertExists()
        rule.onAllNodesWithText("Esta semana").assertCountEquals(0)
        rule.onAllNodesWithText("Deja 2 repeticiones en reserva.").assertCountEquals(0)
        rule.onAllNodesWithText(PROFESSIONAL_ADVICE).assertCountEquals(1)
    }

    @Test
    fun theButtonReportsOrTakesTheDiscomfortOffAndNeverShowsSeverity() {
        var toggles = 0
        body("shoulder_anterior", selected = false, onToggle = { toggles++ })
        rule.onNodeWithText("Reportar molestia").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun aChosenDiscomfortOffersToTakeItOff() {
        body("shoulder_anterior", selected = true)
        rule.onNodeWithText("Quitar molestia").assertExists()
        rule.onAllNodesWithText("Reportar molestia").assertCountEquals(0)
    }

    @Test
    fun everySectionOfTheCatalogHasItsOwnAnimationAndColor() {
        val sections = DiscomfortSection.entries
        assertEquals(6, sections.size)
        assertEquals("cada sección, su propio color de zona", sections.size, sections.map { discomfortZoneColor(it) }.toSet().size)
        content {
            Row {
                sections.forEach { section ->
                    DiscomfortScene(section = section, modifier = Modifier.size(width = 55.dp, height = 60.dp), animated = false)
                }
            }
        }
        rule.waitForIdle()
        // Las animaciones de las cinco zonas del cuerpo las usa al menos una molestia con conocimiento, y todas caen en una de las seis.
        assertTrue(DISCOMFORT_KNOWLEDGE.all { it.animation in sections })
        assertTrue(sections.filter { it != DiscomfortSection.GENERAL }.all { s -> DISCOMFORT_KNOWLEDGE.any { it.animation == s } })
    }

    @Test
    fun everyDiscomfortOfTheCatalogHasASheet() {
        val ids = DISCOMFORT_CATALOG.map { it.id }.filter { it != "none" }
        assertTrue(ids.isNotEmpty())
        ids.forEach { id -> assertTrue("sin ficha: $id", discomfortKnowledge(id) != null) }
    }

    @Test
    fun theLeadingFigureOfAnEvidenceTakesTheAccentColor() {
        val withFigure = emphasizeLeadingFigure("+35${nbsp}% de series de cuádriceps sobre tu media", Color.Red)
        assertEquals(1, withFigure.spanStyles.size)
        assertEquals(0, withFigure.spanStyles.single().start)
        assertEquals("+35${nbsp}%".length, withFigure.spanStyles.single().end)
        assertTrue(emphasizeLeadingFigure("4 series pesadas de sentadilla", Color.Red).spanStyles.isEmpty())
        assertTrue(emphasizeLeadingFigure("Técnica de 5/10 en sentadilla", Color.Red).spanStyles.isEmpty())
    }
}
