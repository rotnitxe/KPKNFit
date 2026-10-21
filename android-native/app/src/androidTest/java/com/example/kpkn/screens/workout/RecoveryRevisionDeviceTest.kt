package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.components.rings.RingAdjustLevel
import com.example.kpkn.ui.components.rings.RingAdjustPanel
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.MuscleBodyAdjust
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

    @Test fun flappyHasRealGravityFlapScoringAndCollision() {
        val game = RestFlightEngine(RestArcadeMode.FLAPPY, Random(9))
        game.flap()
        val before = game.playerY
        game.step(.016f)
        assertTrue(game.playerY < before)
        game.gates.clear()
        game.gates += RestFlightEngine.Gate(-38f, 210f)
        game.step(.016f)
        assertEquals(1, game.score)
        game.gates += RestFlightEngine.Gate(game.playerX, 80f)
        game.step(.016f)
        assertEquals(2, game.lives)
        assertFalse(game.ended)
    }

    @Test fun shooterMovementShotsHitsAndEnemyDamageAreReal() {
        val game = RestFlightEngine(RestArcadeMode.SHOOTER, Random(4))
        game.step(.016f)
        game.ships.clear(); game.shots.clear()
        game.steer(180f, 350f)
        assertEquals(180f, game.playerX, .001f)
        assertEquals(350f, game.playerY, .001f)
        game.ships += RestFlightEngine.Ship(180f, 170f, hp = 1)
        game.shots += RestFlightEngine.Shot(180f, 175f, false)
        game.step(.016f)
        assertEquals(10, game.score)
        assertTrue(game.ships.isEmpty())
        game.shots += RestFlightEngine.Shot(180f, 345f, true)
        game.step(.016f)
        assertEquals(2, game.lives)
        game.steer(-100f, 1000f)
        assertEquals(18f, game.playerX, .001f)
        assertEquals(395f, game.playerY, .001f)
    }

    @Test fun arcadePickerRestartAndReturnPreserveControllerClock() {
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
        rule.onNodeWithTag("rest-game-restart").performClick()
        rule.onNodeWithTag("arcade-rest-clock").assertTextEquals("1:13")
        rule.onNodeWithTag("arcade-return-rest").performClick()
        rule.runOnIdle { assertEquals(1, returned) }
    }

    @Test fun adjustmentIsInlineAndBackReturnsWithChangedValue() {
        var saved = 0
        rule.setContent {
            var value by remember { mutableIntStateOf(70) }
            var adjusting by remember { mutableStateOf(true) }
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    if (adjusting) RingAdjustPanel(
                        initialChannel = RingChannel.ENERGIA,
                        channels = listOf(RingChannel.ENERGIA),
                        energy = RingAdjustLevel(value, 70),
                        onEnergyChange = { value = it },
                        onDismiss = { saved = value; adjusting = false },
                    )
                    else androidx.compose.material3.Text("Readiness $value")
                }
            }
        }
        rule.onAllNodesWithTag("ring-adjust-sheet").assertCountEquals(0)
        rule.onNodeWithTag("ring-adjust-plus").performClick()
        rule.onNodeWithTag("ring-adjust-back").performClick()
        rule.onNodeWithText("Readiness 75").assertIsDisplayed()
        rule.runOnIdle { assertEquals(75, saved) }
    }

    @Test fun introductionUsesExactMessageAndAcknowledgesWithoutStartingARest() {
        var acknowledgements = 0
        rule.setContent { MaterialTheme { RestFirstExperience(onDone = { acknowledgements++ }, animationsOn = false) } }
        rule.onNodeWithText("El descanso es tan importante como la serie, así que lo dejamos en pantalla completa por defecto").assertIsDisplayed()
        rule.onNodeWithTag("rest-intro-done").performClick()
        rule.runOnIdle { assertEquals(1, acknowledgements) }
    }

    @Test fun anatomicalArmTouchEditsBicepsAndBackKeepsOtherValues() {
        val values = mutableStateMapOf("Bíceps" to 70, "Cuádriceps" to 55, "Isquiosurales" to 60)
        rule.setContent {
            MaterialTheme {
                MuscleBodyAdjust(values = values.toMap(), before = values.toMap(),
                    initialSelected = "Cuádriceps", onValueChange = { id, value -> values[id] = value })
            }
        }
        // Touch the actual right upper arm in native full-body coordinates, not a merged semantic bounding rectangle.
        rule.onNodeWithTag("ring-adjust-body").performTouchInput {
            click(Offset(width * 153f / 220f, height * 126f / 360f))
        }
        rule.onNodeWithTag("ring-adjust-muscle-plus").performClick()
        rule.runOnIdle { assertEquals(80, values["Bíceps"]); assertEquals(55, values["Cuádriceps"]) }
        rule.onNodeWithText("Espalda").performClick()
        rule.onNodeWithTag("ring-adjust-zone-Isquiosurales").assertExists()
        rule.runOnIdle { assertEquals(60, values["Isquiosurales"]) }
    }
}
