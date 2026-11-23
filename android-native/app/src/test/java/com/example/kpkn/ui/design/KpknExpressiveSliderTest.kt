package com.example.kpkn.ui.design

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
class KpknExpressiveSliderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun expressiveSliderRendersLowMidHighAndSnapsDiscreteSetProgress() {
        var latestValue = 1f
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    var value by remember { mutableFloatStateOf(1f) }
                    KpknExpressiveSlider(
                        value = value,
                        onValueChange = {
                            value = it
                            latestValue = it
                        },
                        valueRange = 1f..5f,
                        steps = 3,
                        minColor = Color(0xFF5B7FA6),
                        midColor = Color(0xFFE6E1D5),
                        maxColor = Color(0xFFFF5E57),
                        sparkAtMax = true,
                        coolGlowAtMin = true,
                        bubbleLabel = { "v=${it.toInt()}" },
                        semanticLabel = "Energía expresiva",
                        modifier = Modifier.testTag("expressive-slider"),
                    )
                }
            }
        }

        val slider = rule.onNodeWithTag("expressive-slider")
        slider.assertIsDisplayed()
        slider.assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(current = 1f, range = 1f..5f, steps = 3),
            ),
        )

        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(3.4f))
        }
        rule.runOnIdle { assertEquals(3f, latestValue, 0.001f) }

        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(5f))
        }
        rule.runOnIdle { assertEquals(5f, latestValue, 0.001f) }

        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(-4f))
        }
        rule.runOnIdle { assertEquals(1f, latestValue, 0.001f) }
    }
}
