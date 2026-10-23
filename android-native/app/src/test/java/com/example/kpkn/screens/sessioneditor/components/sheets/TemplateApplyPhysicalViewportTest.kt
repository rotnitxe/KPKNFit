package com.example.kpkn.screens.sessioneditor.components.sheets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class TemplateApplyPhysicalViewportTest {
    @get:Rule val rule = createComposeRule()
    private var view: View? = null

    private fun check(height: Int, scale: Float) {
        var open by mutableStateOf(true)
        val applied = mutableListOf<SessionTemplateApplyMode>()
        val template = SessionTemplate(id = "qa-cardio", sourceType = SessionTemplateSourceType.USER,
            name = "QA cardio", description = "", session = Session("qa-session", "QA cardio"))
        rule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(height.dp).testTag("template-viewport")) {
                        EditorOverlayTestHost {
                            if (open) TemplateApplyDialog(
                                decision = SessionTemplateApplyDecision(template),
                                onDismiss = { open = false },
                                onApply = { applied += it; open = false },
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        for (label in listOf("Reemplazar", "Añadir al final")) {
            val button = rule.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val bounds = button.getUnclippedBoundsInRoot()
            val viewport = rule.onNodeWithTag("template-viewport").getUnclippedBoundsInRoot()
            assertTrue(label, bounds.left >= viewport.left && bounds.right <= viewport.right &&
                bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse(label, layout.didOverflowHeight)
            assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            for (line in 0 until layout.lineCount) {
                assertFalse(label, layout.isLineEllipsized(line))
                assertTrue(label, layout.getLineLeft(line) >= -1f)
                assertTrue(label, layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
        System.getenv("KPKN_RENDER_DIR")?.let { path ->
            val dir = File(path).apply { mkdirs() }
            val host = requireNotNull(view)
            val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { host.draw(Canvas(bitmap)) }
            File(dir, "d4_template_apply_${height}_${(scale * 100).toInt()}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        assertTrue(applied.isEmpty())
        rule.onNodeWithText("Reemplazar").performClick()
        assertEquals(listOf(SessionTemplateApplyMode.REPLACE), applied)
        rule.runOnIdle { open = true }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Añadir al final").performClick()
        assertEquals(listOf(SessionTemplateApplyMode.REPLACE, SessionTemplateApplyMode.APPEND), applied)
    }

    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun altura740Letra100() = check(740, 1f)
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun altura740Letra130() = check(740, 1.3f)
    @Test fun altura880Letra100() = check(880, 1f)
    @Test fun altura880Letra130() = check(880, 1.3f)
}
