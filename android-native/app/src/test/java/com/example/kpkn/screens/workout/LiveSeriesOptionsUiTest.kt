package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.screens.workout.components.SeriesOptionsSheet
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object NoToursForSeriesSheet : TourSeenStore {
    override fun seenIds(): Set<String> = emptySet()
    override fun markSeen(tourId: String) = Unit
}

/**
 * Las opciones de la serie (LV11, A1; prototipo C1) con la pieza real: cada acción llama a SU función para la serie que se pulsó, lo que
 * no se puede hacer se ve apagado y el tipo de serie se aplica con su alcance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveSeriesOptionsUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            KPKNTheme {
                val host = remember { KpknOverlayHostController(tourSeen = NoToursForSeriesSheet) }
                CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        if (host.entries.isNotEmpty()) {
                            Box(Modifier.fillMaxSize().zIndex(400f)) { KpknOverlayHostContent(host) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun click(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    // ── C1 · Opciones de la serie ──────────────────────────────────────────────────────────────────────────────────────────────

    private val series: Exercise = liveExercise("a", sets = 3)
    private val sameSetTarget = SeriesTypeTarget(exerciseId = "a", fromSetIdx = 1, toSetIdx = 1)

    private class SeriesCalls {
        var edited = 0
        var added = 0
        var dismissed = 0
        val skipped = mutableListOf<Int>()
        val deleted = mutableListOf<Int>()
        val applied = mutableListOf<Pair<SeriesTypeTarget, SeriesTechnique>>()
    }

    private fun showSeries(
        calls: SeriesCalls,
        completedSets: Map<String, CompletedSet> = emptyMap(),
        target: SeriesTypeTarget = sameSetTarget,
    ) = show {
        SeriesOptionsSheet(
            exercise = series,
            target = target,
            completedSets = completedSets,
            omittedSetKeys = emptySet(),
            onDismiss = { calls.dismissed++ },
            onApply = { t, technique -> calls.applied += t to technique },
            onEdit = { calls.edited++ },
            onAddSet = { calls.added++ },
            onSkipSet = { calls.skipped += it },
            onDeleteSet = { calls.deleted += it },
        )
    }

    @Test
    fun theSeriesSheetHeaderSaysWhichSetItIsAndItsPlan() {
        showSeries(SeriesCalls())

        rule.onNodeWithText("Serie 2").assertExists()
        rule.onNodeWithText("40 kg × 8").assertExists()
    }

    @Test
    fun theAddTileIsShortOnScreenButTheScreenReaderStillSaysAddSet() {
        showSeries(SeriesCalls())

        rule.onNodeWithText("Añadir").assertExists()
        rule.onNodeWithContentDescription("Añadir serie").assertExists()
    }

    @Test
    fun everyActionOfTheSeriesSheetCallsItsFunctionForTheSetThatWasPressed() {
        val calls = SeriesCalls()
        showSeries(calls)

        click("Editar")
        click("Añadir")
        click("Saltar")
        click("Borrar")

        assertEquals("Editar abre las series del ejercicio", 1, calls.edited)
        assertEquals("Añadir serie añade al ejercicio", 1, calls.added)
        assertEquals("Saltar omite la serie pulsada (la segunda)", listOf(1), calls.skipped)
        assertEquals("Borrar quita la serie pulsada (la segunda)", listOf(1), calls.deleted)
        assertEquals("cada acción cierra la hoja", 4, calls.dismissed)
    }

    @Test
    fun aDoneSetCannotBeSkippedNorDeletedButCanStillBeEditedAndExtended() {
        val calls = SeriesCalls()
        showSeries(calls, completedSets = mapOf("a_1" to CompletedSet(id = "c", weight = 40.0, reps = 8)))

        rule.onNodeWithText("Saltar").assertIsNotEnabled()
        rule.onNodeWithText("Borrar").assertIsNotEnabled()
        rule.onNodeWithText("Editar").assertIsEnabled()
        rule.onNodeWithText("Añadir").assertIsEnabled()
        // La cabecera enseña lo que de verdad se hizo.
        rule.onNodeWithText("40 kg × 8").assertExists()
    }

    @Test
    fun applyingATypeUsesOnlyTheSetThatWasPressedByDefault() {
        val calls = SeriesCalls()
        showSeries(calls)

        click("Drop-set")
        click("Aplicar")

        val (target, technique) = calls.applied.single()
        assertEquals(SeriesTechnique.DROPSET, technique)
        assertEquals(setOf(1), target.selectedSetIndices)
        assertEquals("a", target.exerciseId)
    }

    @Test
    fun applyingATypeFromThisSetToTheEndMarksTheFollowingSetsToo() {
        val calls = SeriesCalls()
        showSeries(calls)

        click("Rest-pause")
        click("Desde S2 hasta el final")
        click("Aplicar")

        val (target, technique) = calls.applied.single()
        assertEquals(SeriesTechnique.REST_PAUSE, technique)
        assertEquals(setOf(1, 2), target.selectedSetIndices)
    }

    @Test
    fun theDoneSetsOfTheScopeCannotBeMarked() {
        val calls = SeriesCalls()
        showSeries(
            calls,
            completedSets = mapOf("a_0" to CompletedSet(id = "c", weight = 40.0, reps = 8)),
            target = SeriesTypeTarget(exerciseId = "a", fromSetIdx = 1, toSetIdx = 1),
        )

        // El nodo de una serie hecha es la marca de «Completada» y no responde.
        rule.onNodeWithContentDescription("Completada").performClick()
        click("Drop-set")
        click("Aplicar")

        assertEquals(setOf(1), calls.applied.single().first.selectedSetIndices)
    }
}
