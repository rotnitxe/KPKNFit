package com.example.kpkn.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h1000dp-mdpi")
class KpknNativeTimePickerDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun numericStylesKeepAccessibleSpSizesAndDoNotChangeBodyTypography() {
        val base = Typography()
        val local = timePickerNumericTypography(base)

        assertEquals(48f, local.displayLarge.fontSize.value, 0f)
        assertEquals(40f, local.displayMedium.fontSize.value, 0f)
        assertEquals(32f, local.displaySmall.fontSize.value, 0f)
        assertEquals(56f, local.displayLarge.lineHeight.value, 0f)
        assertEquals("'tnum' 1", local.displayLarge.fontFeatureSettings)
        assertEquals(base.bodySmall, local.bodySmall)
    }

    @Test
    fun effectiveNumericTextSizeGrowsWithTheSystemFontScale() {
        val fontScale = mutableFloatStateOf(1f)
        val measuredHeight = mutableIntStateOf(0)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                MaterialTheme(typography = timePickerNumericTypography(Typography())) {
                    Text(
                        "10:05",
                        style = MaterialTheme.typography.displayLarge,
                        onTextLayout = { measuredHeight.intValue = it.size.height },
                    )
                }
            }
        }

        val heights = mutableMapOf<Float, Int>()
        listOf(1f, 1.3f, 1.5f, 2f).forEach { scale ->
            rule.runOnIdle { fontScale.value = scale }
            rule.waitForIdle()
            heights[scale] = measuredHeight.intValue
        }

        val measured = heights.values.toList()
        assertTrue("la escala de letra conserva o aumenta la altura progresivamente: $heights", measured.zipWithNext().all { (before, after) -> after >= before })
        assertTrue("2× debe aumentar la altura efectiva: $heights", heights.getValue(2f) > heights.getValue(1f))
    }

    @Test
    fun dialAndKeyboardKeepBothTimeSamplesInsideThePanelAcrossViewportsAndScales() {
        val viewportWidth = mutableIntStateOf(320)
        val fontScale = mutableFloatStateOf(1f)
        val initialHour = mutableIntStateOf(0)
        val initialMinute = mutableIntStateOf(30)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                Box(Modifier.width(viewportWidth.intValue.dp).height(900.dp)) {
                    EditorOverlayTestHost {
                        key(initialHour.intValue, initialMinute.intValue) {
                            KpknNativeTimePickerDialog(
                                title = "Descanso",
                                initialHour = initialHour.intValue,
                                initialMinute = initialMinute.intValue,
                                hint = "Minutos : segundos",
                                onConfirm = { _, _ -> },
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()

        val samples = listOf(0 to 30, 10 to 5)
        for ((hour, minute) in samples) {
            rule.runOnIdle {
                initialHour.intValue = hour
                initialMinute.intValue = minute
            }
            for (width in listOf(320, 360, 412)) {
                for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                    rule.runOnIdle {
                        viewportWidth.intValue = width
                        fontScale.value = scale
                    }
                    rule.waitForIdle()

                    assertPickerPanelBounds("native-time-picker-dial", width)
                    val hourText = hour.toString().padStart(2, '0')
                    val minuteText = minute.toString().padStart(2, '0')
                    assertTrue(
                        "$hourText debe estar accesible en ${width}dp / $scale×",
                        rule.onAllNodesWithText(hourText, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                    )
                    assertTrue(
                        "$minuteText debe estar accesible en ${width}dp / $scale×",
                        rule.onAllNodesWithText(minuteText, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                    )

                    rule.onNodeWithContentDescription("Usar teclado").performClick()
                    rule.waitForIdle()
                    assertPickerPanelBounds("native-time-picker-keyboard", width)
                    assertEquals(2, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
                    rule.onNodeWithContentDescription("Usar reloj").performClick()
                    rule.waitForIdle()
                }
            }
        }
    }

    @Test
    fun keyboardInputKeepsFocusAndCommitsTheEditedHourAndMinute() {
        val confirmed = mutableStateOf<Pair<Int, Int>?>(null)
        rule.setContent {
            EditorOverlayTestHost {
                KpknNativeTimePickerDialog(
                    title = "Descanso",
                    initialHour = 10,
                    initialMinute = 5,
                    onConfirm = { hour, minute -> confirmed.value = hour to minute },
                    onDismiss = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithContentDescription("Usar teclado").performClick()
        rule.waitForIdle()
        val fields = rule.onAllNodes(hasSetTextAction())
        assertEquals(2, fields.fetchSemanticsNodes().size)
        fields[0].performClick()
        fields[0].assertIsFocused()
        fields[0].performTextReplacement("11")
        fields[1].performTextReplacement("45")
        rule.onNodeWithText("Aceptar").performClick()
        rule.waitForIdle()

        assertEquals(11 to 45, confirmed.value)
    }

    private fun assertPickerPanelBounds(tag: String, viewportWidthDp: Int) {
        val panel = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("$tag debe tener área visible: panel=$panel root=$root", panel.width > 0f && panel.height > 0f)
        assertTrue("$tag debe quedar dentro de la ventana: panel=$panel root=$root", panel.left >= root.left && panel.top >= root.top && panel.right <= root.right && panel.bottom <= root.bottom)
        rule.onNodeWithTag(tag, useUnmergedTree = true)
            .assertLeftPositionInRootIsEqualTo(28.dp)
            .assertWidthIsEqualTo((viewportWidthDp - 56).dp)
    }
}
