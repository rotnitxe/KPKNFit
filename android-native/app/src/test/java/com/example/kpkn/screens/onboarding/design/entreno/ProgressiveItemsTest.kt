package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `rememberProgressiveCount`: los primeros de golpe, el resto de a poco por cuadro, nunca menos de lo ya compuesto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProgressiveItemsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun count(): Int = rule.onNodeWithTag("n").fetchSemanticsNode().config
        .let { (it[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text).toInt() }

    @Test
    fun theFirstItemsComeAtOnceAndTheRestFrameByFrame() {
        // Sin avanzar el reloj no hay cuadros: solo están los primeros.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val shown = rememberProgressiveCount(total = 7, first = 2, perFrame = 2)
            Column { Text(shown.toString(), Modifier.testTag("n")) }
        }
        assertEquals("al principio, solo los primeros", 2, count())
        // Con los cuadros corriendo, al final están todos.
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals("al final están todos", 7, count())
    }

    @Test
    fun withNothingToSplitItReturnsTheTotalAndNeverMore() {
        rule.setContent {
            val shown = rememberProgressiveCount(total = 2, first = 5, perFrame = 1)
            Column { Text(shown.toString(), Modifier.testTag("n")) }
        }
        rule.waitForIdle()
        assertEquals(2, count())
    }

    @Test
    fun whenTheTotalGrowsItKeepsWhatWasAlreadyShownAndSplitsTheNewOnes() {
        var total by mutableIntStateOf(3)
        rule.setContent {
            val shown = rememberProgressiveCount(total = total, first = 1, perFrame = 1)
            Column { Text(shown.toString(), Modifier.testTag("n")) }
        }
        rule.waitForIdle()
        assertEquals(3, count())
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { total = 6 }
        rule.waitForIdle()
        assertEquals("lo ya compuesto no desaparece", 3, count())
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(6, count())
    }
}
