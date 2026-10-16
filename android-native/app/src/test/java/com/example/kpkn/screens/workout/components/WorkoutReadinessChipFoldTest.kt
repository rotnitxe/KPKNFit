package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.domain.auge.DiscomfortSuggestionEngine
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Los chips de molestias del estado previo en tres filas (LV10b): de entrada solo tres filas y, tras el último chip, «Más (N)»; «Más»
 * los despliega todos y «Menos» los vuelve a plegar. Una molestia elegida nunca queda oculta. La cuenta de filas depende de lo que
 * mide el texto, así que esta clase usa gráficos nativos (con los de por defecto de Robolectric todos los textos miden casi cero).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1400dp-xxhdpi")
class WorkoutReadinessChipFoldTest {

    @get:Rule
    val rule = createComposeRule()

    /** Piernas y espalda baja: nueve molestias sugeridas, de sobra para pasar de tres filas a 360 dp. */
    private val pillars = listOf("Cuádriceps", "Glúteos", "Isquiosurales", "Pantorrillas", "Erectores Espinales")
    private val suggested = DiscomfortSuggestionEngine.suggestForMuscles(pillars).map { it.id }

    /** La última de la lista: la primera que queda oculta tras «Más». */
    private val last = suggested.last()

    private var saved: List<String>? = null

    private fun show(initialDiscomforts: List<String> = emptyList()) {
        saved = null
        rule.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize()) {
                    WorkoutReadinessSheet(
                        showReadinessSheet = true,
                        gender = null,
                        sessionMuscleStartingBatteries = linkedMapOf("Cuádriceps" to 54),
                        readinessNeuralStart = 88,
                        readinessMuscularStart = 54,
                        readinessSpinalStart = 82,
                        hazeState = HazeState(),
                        onSave = { _, _, _, _, discomforts, _, _, _, _ -> saved = discomforts },
                        initialDiscomforts = initialDiscomforts,
                        suggestionPillars = pillars,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun tap(tag: String) {
        node(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private val notChips = setOf("readiness-discomfort-search", "readiness-discomfort-more", "readiness-discomfort-less")

    private val chipMatcher = SemanticsMatcher("chip de molestia") { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        tag != null && tag.startsWith("readiness-discomfort-") && tag !in notChips
    }

    /** Las etiquetas de los chips de molestia que están en pantalla (sin el buscador ni «Más» y «Menos»). */
    private fun shownChips(): List<String> =
        rule.onAllNodes(chipMatcher, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    /** Cuántas filas ocupan los chips en pantalla, contando también el chip «Más» o «Menos» si está. */
    private fun rowsShown(): Int {
        val tops = rule.onAllNodes(chipMatcher, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.top.toInt() }.toMutableSet()
        listOf("readiness-discomfort-more", "readiness-discomfort-less").forEach { tag ->
            val nodes = rule.onAllNodes(SemanticsMatcher("etiqueta $tag") { it.config.getOrNull(SemanticsProperties.TestTag) == tag }, useUnmergedTree = true)
                .fetchSemanticsNodes()
            nodes.forEach { tops += it.boundsInRoot.top.toInt() }
        }
        return tops.size
    }

    /** El N de «Más (N)». */
    private fun hiddenCount(): Int {
        val text = rule.onNodeWithTag("readiness-discomfort-more").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        val match = Regex("""Más \((\d+)\)""").find(text)
        assertNotNull("el chip dice «Más (N)»: $text", match)
        return match!!.groupValues[1].toInt()
    }

    @Test
    fun `con muchas molestias solo salen tres filas y Mas dice cuantas quedan fuera`() {
        show()
        assertTrue("hacen falta muchas sugeridas: $suggested", suggested.size >= 9)
        node("readiness-discomfort-none").assertExists()
        node("readiness-discomfort-more").assertExists()
        node("readiness-discomfort-less").assertDoesNotExist()
        assertEquals("tres filas, contando la del «Más»", 3, rowsShown())
        // Ninguna + las sugeridas son todos los chips; los que no se ven son los que dice «Más (N)».
        val total = 1 + suggested.size
        assertEquals(total - shownChips().size, hiddenCount())
        assertTrue("alguna queda oculta", hiddenCount() > 0)
    }

    @Test
    fun `los chips ocultos ni se componen`() {
        show()
        node("readiness-discomfort-$last").assertDoesNotExist()
    }

    @Test
    fun `Mas despliega todas las molestias y Menos las vuelve a plegar`() {
        show()
        tap("readiness-discomfort-more")
        node("readiness-discomfort-$last").assertExists()
        node("readiness-discomfort-less").assertExists()
        node("readiness-discomfort-more").assertDoesNotExist()
        assertEquals("todos los chips a la vista", 1 + suggested.size, shownChips().size)
        tap("readiness-discomfort-less")
        node("readiness-discomfort-$last").assertDoesNotExist()
        node("readiness-discomfort-more").assertExists()
        assertEquals(3, rowsShown())
    }

    @Test
    fun `una molestia elegida con todo desplegado sigue a la vista al plegar`() {
        show()
        tap("readiness-discomfort-more")
        tap("readiness-discomfort-$last")
        node("readiness-discomfort-$last").assertIsOn()
        tap("readiness-discomfort-less")
        // Sin fijarla arriba quedaría oculta tras «Más» estando elegida.
        node("readiness-discomfort-$last").assertExists()
        node("readiness-discomfort-$last").assertIsOn()
    }

    @Test
    fun `una molestia elegida desde la busqueda sigue a la vista al limpiar el texto`() {
        show()
        rule.onNode(hasSetTextAction()).performTextReplacement(discomfortLabel(last))
        rule.waitForIdle()
        tap("readiness-discomfort-$last")
        rule.onNodeWithContentDescription("Limpiar búsqueda", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        node("readiness-discomfort-$last").assertExists()
        node("readiness-discomfort-$last").assertIsOn()
    }

    @Test
    fun `una molestia que ya venia elegida sale a la vista aunque cayera tras Mas`() {
        show(initialDiscomforts = listOf(last))
        node("readiness-discomfort-$last").assertExists()
        node("readiness-discomfort-$last").assertIsOn()
        tap("readiness-confirm")
        assertEquals(listOf(last), saved)
    }
}
