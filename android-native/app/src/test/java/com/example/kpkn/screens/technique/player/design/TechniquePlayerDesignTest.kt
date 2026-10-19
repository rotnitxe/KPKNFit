package com.example.kpkn.screens.technique.player.design

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.center
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.technique.TechniqueSamples
import com.example.kpkn.screens.technique.player.TechniqueSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los mandos, la línea de tiempo y los chips del reproductor de técnica (VT7a) por su semántica: lo que lee TalkBack, que todo lo
 * tocable mida al menos 48 dp y que cada toque llegue a su lambda.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class TechniquePlayerDesignTest {
    @get:Rule
    val rule = createComposeRule()

    private fun stateDescription(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    // ── Mandos ────────────────────────────────────────────────────────────────

    @Test
    fun theControlsNameEachButtonAndReachTheirLambdas() {
        val calls = ArrayList<String>()
        rule.setContent {
            TechniquePlayerControls(
                isPlaying = false,
                speed = TechniqueSpeed.NORMAL,
                loopEnabled = false,
                onPlayPause = { calls += "play" },
                onFrameBack = { calls += "back" },
                onFrameForward = { calls += "forward" },
                onSpeedChange = { calls += "speed:$it" },
                onLoopToggle = { calls += "loop" },
            )
        }
        rule.onNodeWithContentDescription("Reproducir").assertHasClickAction().performClick()
        rule.onNodeWithContentDescription("Fotograma anterior").performClick()
        rule.onNodeWithContentDescription("Fotograma siguiente").performClick()
        rule.onNodeWithText("0,25×").performClick()
        rule.onNodeWithTag(TECHNIQUE_LOOP_TAG).assert(stateDescription("Desactivado")).performClick()
        assertEquals(listOf("play", "back", "forward", "speed:QUARTER", "loop"), calls)
    }

    @Test
    fun playingShowsPauseAndEveryButtonIsAtLeast48dp() {
        rule.setContent {
            TechniquePlayerControls(
                isPlaying = true,
                speed = TechniqueSpeed.HALF,
                loopEnabled = true,
                onPlayPause = {},
                onFrameBack = {},
                onFrameForward = {},
                onSpeedChange = {},
                onLoopToggle = {},
            )
        }
        rule.onNodeWithContentDescription("Pausar").assertExists()
        rule.onNodeWithContentDescription("Reproducir").assertDoesNotExist()
        rule.onNodeWithTag(TECHNIQUE_LOOP_TAG).assert(stateDescription("Activado"))
        for (tag in listOf(TECHNIQUE_PLAY_TAG, TECHNIQUE_FRAME_BACK_TAG, TECHNIQUE_FRAME_FORWARD_TAG, TECHNIQUE_LOOP_TAG, TECHNIQUE_SPEED_TAG)) {
            rule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun withoutRepsTheLoopIsOffAndDoesNotRespond() {
        var toggled = false
        rule.setContent {
            TechniquePlayerControls(
                isPlaying = false,
                speed = TechniqueSpeed.NORMAL,
                loopEnabled = true,
                onPlayPause = {},
                onFrameBack = {},
                onFrameForward = {},
                onSpeedChange = {},
                onLoopToggle = { toggled = true },
                loopAvailable = false,
            )
        }
        rule.onNodeWithTag(TECHNIQUE_LOOP_TAG).assertIsNotEnabled().performClick()
        assertEquals(false, toggled)
        rule.onNodeWithTag(TECHNIQUE_PLAY_TAG).assertIsEnabled()
    }

    // ── Línea de tiempo ───────────────────────────────────────────────────────

    @Test
    fun theScrubberReadsPositionDurationAndRepetitionForTalkBack() {
        rule.setContent {
            TechniqueScrubber(
                positionUs = { 13_000_000L },
                durationUs = TechniqueSamples.MIXED_DURATION_US,
                reps = TechniqueSamples.mixedMarks,
                selectedRep = 3,
                onSeek = {},
            )
        }
        rule.onNodeWithTag(TECHNIQUE_SCRUBBER_TAG)
            .assert(stateDescription("Posición 0:13 de 0:31, repetición 4 de 8"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun outsideEveryRepetitionOnlyThePositionIsSpoken() {
        rule.setContent {
            TechniqueScrubber(
                positionUs = { 1_000_000L },
                durationUs = TechniqueSamples.MIXED_DURATION_US,
                reps = TechniqueSamples.mixedMarks,
                selectedRep = -1,
                onSeek = {},
            )
        }
        rule.onNodeWithTag(TECHNIQUE_SCRUBBER_TAG).assert(stateDescription("Posición 0:01 de 0:31"))
    }

    @Test
    fun adjustingTheScrubberWithAccessibilitySeeksBetweenStartAndEnd() {
        val calls = ArrayList<String>()
        rule.setContent {
            TechniqueScrubber(
                positionUs = { 0L },
                durationUs = 20_000_000L,
                reps = emptyList(),
                selectedRep = -1,
                onSeek = { calls += "seek:$it" },
                onScrubStart = { calls += "start" },
                onScrubEnd = { calls += "end" },
            )
        }
        rule.onNodeWithTag(TECHNIQUE_SCRUBBER_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(listOf("start", "seek:10000000", "end"), calls)
    }

    @Test
    fun tappingTheScrubberSeeksThere() {
        val calls = ArrayList<String>()
        rule.setContent {
            TechniqueScrubber(
                positionUs = { 0L },
                durationUs = 20_000_000L,
                reps = emptyList(),
                selectedRep = -1,
                onSeek = { calls += "seek:$it" },
                onScrubStart = { calls += "start" },
                onScrubEnd = { calls += "end" },
            )
        }
        // El centro de la pista es la mitad del vídeo.
        rule.onNodeWithTag(TECHNIQUE_SCRUBBER_TAG).performTouchInput { click(center) }
        assertEquals(listOf("start", "seek:10000000", "end"), calls)
    }

    @Test
    fun draggingTheScrubberSeeksContinuouslyFromStartToEnd() {
        val calls = ArrayList<String>()
        rule.setContent {
            TechniqueScrubber(
                positionUs = { 0L },
                durationUs = 20_000_000L,
                reps = emptyList(),
                selectedRep = -1,
                onSeek = { calls += "seek:$it" },
                onScrubStart = { calls += "start" },
                onScrubEnd = { calls += "end" },
            )
        }
        rule.onNodeWithTag(TECHNIQUE_SCRUBBER_TAG).performTouchInput { swipeRight() }
        assertEquals("start", calls.first())
        assertEquals("end", calls.last())
        val seeks = calls.filter { it.startsWith("seek:") }.map { it.removePrefix("seek:").toLong() }
        assertTrue("pocas búsquedas: ${seeks.size}", seeks.size >= 3)
        assertTrue("las búsquedas deberían crecer: $seeks", seeks.zipWithNext().all { (a, b) -> b >= a })
        // El dedo llega al borde derecho: la última búsqueda es el final del vídeo.
        assertEquals(20_000_000L, seeks.last())
    }

    @Test
    fun anUnknownDurationIsSafe() {
        rule.setContent {
            TechniqueScrubber(positionUs = { 5_000_000L }, durationUs = 0L, reps = emptyList(), selectedRep = -1, onSeek = {})
        }
        // Sin duración conocida, solo la posición.
        rule.onNodeWithTag(TECHNIQUE_SCRUBBER_TAG).assert(stateDescription("Posición 0:05"))
    }

    // ── Chips ─────────────────────────────────────────────────────────────────

    @Test
    fun theChipsNumberTheRepsMarkPartialsAndFailuresAndSelect() {
        var chosen = -1
        rule.setContent {
            Column {
                TechniqueRepChips(
                    reps = TechniqueSamples.mixedMarks,
                    selectedRep = 3,
                    onSelectRep = { chosen = it },
                )
            }
        }
        // Lo que se ve («3·p», «6·f») y lo que se oye.
        rule.onNodeWithText("3·p", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("6·f", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(techniqueRepChipTag(1)).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Repetición 1")))
        rule.onNodeWithTag(techniqueRepChipTag(3)).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Repetición 3, parcial")))
        rule.onNodeWithTag(techniqueRepChipTag(6)).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Repetición 6, fallida")))
        rule.onNodeWithTag(techniqueRepChipTag(4)).assertIsSelected()
        rule.onNodeWithTag(techniqueRepChipTag(1)).assertIsNotSelected()
        // Cada chip mide al menos 48 × 48 dp de zona táctil, también el de un solo dígito.
        rule.onNodeWithTag(techniqueRepChipTag(1)).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(techniqueRepChipTag(2)).performClick()
        assertEquals(1, chosen)
    }

    @Test
    fun tappingTheAirAroundAPillAlsoSelectsIt() {
        var chosen = -1
        rule.setContent { TechniqueRepChips(reps = TechniqueSamples.mixedMarks, selectedRep = 3, onSelectRep = { chosen = it }) }
        // A 2 px del borde izquierdo de la zona táctil: en el aire entre píldoras, no sobre la píldora.
        rule.onNodeWithTag(techniqueRepChipTag(2)).performTouchInput { click(Offset(2f, centerY)) }
        assertEquals(1, chosen)
    }

    @Test
    fun aChosenChipOutOfViewIsBroughtIntoView() {
        // 16 repeticiones no caben a 360 dp: la 12.ª, elegida, solo existe en pantalla si la fila se desplazó hasta ella.
        val largas = List(16) { TechniqueSamples.mixedMarks[it % 8].copy(index = it) }
        rule.setContent { TechniqueRepChips(reps = largas, selectedRep = 11, onSelectRep = {}) }
        rule.waitForIdle()
        rule.onNodeWithTag(techniqueRepChipTag(12)).assertIsDisplayed()
        rule.onNodeWithTag(techniqueRepChipTag(1)).assertDoesNotExist()
    }

    @Test
    fun noRepsNoChips() {
        rule.setContent { TechniqueRepChips(reps = emptyList(), selectedRep = -1, onSelectRep = {}) }
        rule.onNodeWithTag(TECHNIQUE_REP_CHIPS_TAG).assertDoesNotExist()
    }
}
