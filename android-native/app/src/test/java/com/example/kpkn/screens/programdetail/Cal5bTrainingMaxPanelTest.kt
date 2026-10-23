package com.example.kpkn.screens.programdetail

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.tour.TourSeenStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp-xxhdpi", application = Application::class)
class Cal5bTrainingMaxPanelTest {
    @get:Rule val rule = createComposeRule()
    private var saved: Pair<String, Double>? = null
    private fun mount() {
        val exercises = listOf(Exercise("press", "Press militar", catalogConfigurationId = CatalogIds.OHP,
            referenceTrainingMax = 72.0, sets = listOf(ExerciseSet("p", loadBasis = LoadBasis.PERCENT_TM))),
            Exercise("row", "Remo con barra", catalogConfigurationId = CatalogIds.ROW,
                referenceTrainingMax = 90.0, sets = listOf(ExerciseSet("r", loadBasis = LoadBasis.PERCENT_TM))))
        val program = Program("plan", "Fuerza", macrocycles = listOf(Macrocycle("m", "Macro", blocks = listOf(
            Block("b", "Bloque", mesocycles = listOf(Mesocycle("meso", "Meso", weeks = listOf(ProgramWeek("w", "Semana",
                sessions = listOf(Session("s", "Fuerza", exercises = exercises)))))))))))
        val overlays = KpknOverlayHostController(tourSeen = object : TourSeenStore {
            override fun seenIds(): Set<String> = emptySet()
            override fun markSeen(tourId: String) = Unit
        })
        rule.setContent { CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
            ExerciseTrainingMaxPanel(program, onConfirm = { id, kg -> saved = id to kg })
            KpknOverlayHostContent(overlays)
        } }
        rule.waitForIdle()
    }

    @Test fun overheadAndAnExerciseOutsideTheLegacyFourShowTheirRealTms() {
        mount()
        rule.onNodeWithText("Press militar").assertExists()
        rule.onNodeWithText("TM 72 kg").assertExists()
        rule.onNodeWithText("Remo con barra").assertExists()
        rule.onNodeWithText("TM 90 kg").assertExists()
        rule.onNodeWithContentDescription("Editar TM de Remo con barra").assertHeightIsAtLeast(48.dp)
        assertNull(saved)
    }

    @Test fun anEditedDraftDoesNotPersistWhenTheSheetIsDismissed() {
        mount()
        rule.onNodeWithContentDescription("Editar TM de Remo con barra").performClick()
        rule.mainClock.advanceTimeBy(3_000); rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextReplacement("95")
        rule.onNodeWithText("Ahora no").performClick()
        rule.waitForIdle()
        assertNull(saved)
        rule.onNodeWithText("TM 90 kg").assertExists()
    }

    @Test fun confirmingTheRealEntrySendsTheExerciseIdAndExplicitValue() {
        mount()
        rule.onNodeWithContentDescription("Editar TM de Remo con barra").performClick()
        rule.mainClock.advanceTimeBy(3_000); rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextReplacement("95")
        rule.onNodeWithText("Confirmar TM").performClick()
        rule.waitForIdle()
        assertEquals("row", saved!!.first)
        assertEquals(95.0, saved!!.second, 0.0)
    }
}
