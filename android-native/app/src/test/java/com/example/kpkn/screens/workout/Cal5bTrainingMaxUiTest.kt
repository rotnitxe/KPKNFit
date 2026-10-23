package com.example.kpkn.screens.workout

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class Cal5bTrainingMaxUiTest {
    @get:Rule val rule = createComposeRule()
    private var host: View? = null
    private var confirmed: Double? = null
    private var dismissed = false

    private fun mount(height: Int = 880, fontScale: Float = 1f, error: String? = null) {
        rule.setContent {
            host = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("cal5b-scene")) {
                    TrainingMaxEntryBody("Press militar", suggestedKg = 72.0, fraction = 0.9,
                        onConfirm = { confirmed = it }, onDismiss = { dismissed = true }, missing = true, error = error)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun proposalIsOnlySavedAfterExplicitConfirmation() {
        mount()
        assertNull(confirmed)
        rule.onNodeWithText("90 % de tu 1RM", substring = true).assertExists()
        rule.onNodeWithText("Confirmar TM").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(72.0, confirmed!!, 0.0)
    }

    @Test fun cancellingKeepsTheProposedValueUnconfirmed() {
        mount()
        rule.onNode(hasSetTextAction()).performTextReplacement("75")
        rule.onNodeWithText("Ahora no").performClick()
        assertTrue(dismissed)
        assertNull(confirmed)
    }

    @Test fun editingProposalSavesTheEnteredTmAndNoImpliedOneRm() {
        mount()
        rule.onNode(hasSetTextAction()).performTextReplacement("74,5")
        rule.onNodeWithText("Confirmar TM").performClick()
        assertEquals(74.5, confirmed!!, 0.0)
    }

    private fun render(height: Int, scale: Float) {
        mount(height, scale)
        rule.onNodeWithTag("cal5b-scene").assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(height.dp)
        rule.onNodeWithText("Confirmar TM").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Ahora no").assertHeightIsAtLeast(48.dp)
        assertNull(confirmed)
        val destination = System.getenv("KPKN_RENDER_DIR") ?: return
        val view = checkNotNull(host)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        val folder = File(destination).apply { mkdirs() }
        FileOutputStream(File(folder, "CAL5b-preparacion-${height}-${(scale * 100).toInt()}.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun preparation880font100() = render(880, 1f)
    @Test fun preparation880font130() = render(880, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun preparation740font100() = render(740, 1f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun preparation740font130() = render(740, 1.3f)
    private fun retryAt(height: Int) {
        mount(height,1.3f,error = "No se pudo guardar el TM. Tu valor sigue aqu\u00ed; puedes reintentar.")
        val retry = rule.onNodeWithText("Reintentar TM").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val results = mutableListOf<TextLayoutResult>()
        retry.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("Reintentar TM",layout.layoutInput.text.text)
        assertFalse(layout.didOverflowHeight)
        assertEquals(layout.layoutInput.text.length,layout.getLineEnd(layout.lineCount-1,visibleEnd = true))
        repeat(layout.lineCount) { line ->
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(layout.getLineLeft(line) >= -1f)
            assertTrue(layout.getLineRight(line) <= layout.size.width+1f)
        }
        retry.performTouchInput { click() }
        assertEquals(72.0,confirmed!!,0.0)
    }
    @Test fun retry880font130() = retryAt(880)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun retry740font130() = retryAt(740)
}
