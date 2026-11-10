package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.screens.workout.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** All cases, including pure frame engines, are intentionally run on the physical Android target. */
@RunWith(AndroidJUnit4::class)
class RecoveryRevisionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun runnerHazardsConsumeLivesAndRestartKeepsBest() {
        val game = RestGameEngine(initialBest = 9, random = Random(42))
        game.obstacles += RestGameObstacle(RestGameEngine.RUNNER_X, kind = RestHazard.GAP)
        assertTrue(game.step(.016f))
        assertEquals(2, game.lives)
        assertEquals(1, game.hits)
        assertEquals(9, game.best)
        assertFalse("One collision cannot remove several lives in consecutive frames", game.step(.016f))
        game.reset()
        assertEquals(3, game.lives)
        assertEquals(9, game.best)
        assertTrue(game.obstacles.isEmpty())
        assertTrue(game.supplements.isEmpty())
        assertFalse(game.ended)
    }

    @Test fun runnerSupplementChangesJumpWithoutOwningRestTime() {
        val game = RestGameEngine(random = Random(13))
        assertTrue(game.jump())
        repeat(4) { game.step(.016f) }
        game.supplements += RestGameSupplement(RestGameEngine.RUNNER_X + 12f, RestSupplement.JUMP)
        game.step(.016f)
        assertEquals(RestSupplement.JUMP, game.power)
        assertTrue(game.powerSeconds > 0f)
        game.obstacles.clear()
        repeat(80) { game.obstacles.clear(); game.step(.016f) }
        assertTrue(game.onGround)
        assertTrue(game.jump())
        assertTrue(game.velocity > RestGameEngine.JUMP_SPEED)
    }

    @Test fun flappyWaitsForFirstFlapThenGravityCostsLives() {
        val game = FlappyWorld(360f, 520f, best = 0, random = Random(9), reduced = true)
        repeat(60) { assertFalse(game.update(.016f)) }
        assertEquals("Before the first flap the bird hovers and cannot lose lives", 3, game.lives)
        game.press(0f, 0f)
        var ended = false
        var frames = 0
        while (!ended && frames < 60 * 120) { ended = game.update(.016f); frames++ }
        assertTrue("Without more flaps the bird hits the ground until the game ends", ended)
        assertEquals(0, game.lives)
    }

    @Test fun shooterAutoFiresAndFollowsRelativeDrag() {
        val game = ShooterWorld(360f, 520f, best = 0, random = Random(4), reduced = true)
        assertEquals(3, game.lives)
        game.press(180f, 400f)
        game.move(120f, 400f)
        var ended = false
        var frames = 0
        while (!ended && game.score == 0 && frames < 60 * 120) { ended = game.update(.016f); frames++ }
        assertTrue("Automatic fire scores or the waves end the game", ended || game.score > 0)
        game.release()
    }

    @Test fun arcadePickerPlayAndBackPreserveControllerClock() {
        var returned = 0
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    RestArcade(remainingSeconds = 73, totalSeconds = 120, nextSet = null,
                        onBack = { returned++ }, onSkip = {}, running = false)
                }
            }
        }
        rule.onNodeWithTag("rest-game-runner").assertIsDisplayed().performClick()
        rule.onNodeWithTag("arcade-rest-clock").assertTextEquals("1:13")
        rule.onNodeWithTag("arcade-back").performClick()
        rule.onNodeWithTag("rest-game-runner").assertIsDisplayed()
        rule.onNodeWithTag("arcade-rest-clock").assertTextEquals("1:13")
        rule.runOnIdle { assertEquals(0, returned) }
        rule.onNodeWithTag("arcade-back").performClick()
        rule.runOnIdle { assertEquals(1, returned) }
    }

    @Test fun introductionUsesExactMessageAndAcknowledgesWithoutStartingARest() {
        var acknowledgements = 0
        rule.setContent { MaterialTheme { RestFirstExperience(onDone = { acknowledgements++ }, animationsOn = false) } }
        rule.onNodeWithText("El descanso es tan importante como la serie, así que lo dejamos en pantalla completa por defecto").assertIsDisplayed()
        rule.onNodeWithTag("rest-intro-done").performClick()
        rule.runOnIdle { assertEquals(1, acknowledgements) }
    }
}
