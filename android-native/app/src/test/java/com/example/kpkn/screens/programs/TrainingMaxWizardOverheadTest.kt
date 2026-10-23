package com.example.kpkn.screens.programs

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.markTag
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
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
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = Application::class)
class TrainingMaxWizardOverheadTest {
    @get:Rule val rule = createComposeRule()
    private var confirmed: PowerliftingProfile? = null
    private var dismissed = false
    private var hostView: View? = null

    private fun mount(initial: PowerliftingProfile, height: Int = 880, scale: Float = 1f,
        autoEstimate: PowerliftingProfile? = null) {
        val overlays = KpknOverlayHostController(tourSeen = object : TourSeenStore {
            override fun seenIds(): Set<String> = emptySet()
            override fun markSeen(tourId: String) = Unit
        })
        rule.setContent {
            hostView = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides overlays) {
                KPKNTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("tm-viewport")) {
                        TrainingMaxWizard(initial = initial, autoEstimate = autoEstimate,
                            trainingMaxPercent = 0.90, confirmLabel = "Guardar TM",
                            onDismiss = { dismissed = true }, onConfirm = { confirmed = it })
                        KpknOverlayHostContent(overlays)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
    }

    @Test fun overheadAloneEnablesSaveShowsItsTmAndConfirmsItsOneRmWithoutInventingOtherLifts() {
        mount(PowerliftingProfile(overhead1RM = 80.0), autoEstimate = PowerliftingProfile(squat1RM = 200.0))
        rule.onNodeWithText("Calcular desde mi historial").assertDoesNotExist()
        rule.onNodeWithText("Press militar · TM 72 kg").assertIsDisplayed()
        rule.onNodeWithTag(TM_SAVE_TAG).assertIsEnabled().performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(80.0, confirmed!!.overhead1RM!!, 0.0)
            assertEquals(72.0, confirmed!!.overheadTM!!, 0.0)
            assertNull(confirmed!!.squat1RM); assertNull(confirmed!!.bench1RM); assertNull(confirmed!!.deadlift1RM)
            assertNull(confirmed!!.squatTM); assertNull(confirmed!!.benchTM); assertNull(confirmed!!.deadliftTM)
            assertFalse(dismissed)
        }
    }

    @Test fun aStoredOverheadTmDoesNotBecomeAnInventedOneRmOrEnableSave() {
        mount(PowerliftingProfile(overheadTM = 72.0))
        rule.onNodeWithTag(TM_SAVE_TAG).assertIsNotEnabled()
        rule.onNodeWithText("Press militar · TM 72 kg").assertDoesNotExist()
        assertNull(confirmed)
    }

    private fun fullText(label: String) {
        // The sheet's merged ancestor exposes the first descendant's layout (the title).
        // Read the Text node itself so geometry belongs to this exact preview row.
        val node = rule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(label, layout.layoutInput.text.text)
        assertFalse(layout.didOverflowHeight)
        assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        repeat(layout.lineCount) { line ->
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(layout.getLineLeft(line) >= -1f)
            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
        }
    }

    private fun geometry(height: Int, scale: Float) {
        mount(PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0,
            overhead1RM = 80.0), height, scale)
        val viewport = rule.onNodeWithTag("tm-viewport", useUnmergedTree = true)
            .assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(height.dp).fetchSemanticsNode().boundsInRoot
        val labels = listOf("Sentadilla · TM 180 kg", "Press de banca · TM 108 kg",
            "Peso muerto · TM 198 kg", "Press militar · TM 72 kg")
        labels.forEach(::fullText)
        listOf(rule.onNodeWithTag(TM_SAVE_TAG), rule.onNodeWithText("Más tarde")).forEach { action ->
            action.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val bounds = action.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
            assertTrue(bounds.top >= viewport.top - 1 && bounds.bottom <= viewport.bottom + 1)
        }
        save("TM-cuatro-${height}-${(scale * 100).toInt()}")
        // Native scroll in the sheet, away from the horizontal mark ruler.
        val scroll = rule.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag(markTag(LiftMark.OVERHEAD_PRESS))),
            useUnmergedTree = true)
        repeat(4) {
            scroll.performTouchInput { swipe(Offset(this.width * 0.95f, this.height * 0.85f), Offset(this.width * 0.95f, this.height * 0.15f), durationMillis = 400) }
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
        }
        rule.onNodeWithTag(markTag(LiftMark.OVERHEAD_PRESS)).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        labels.forEach(::fullText)
        rule.onNodeWithTag(TM_SAVE_TAG).assertIsDisplayed().assertIsEnabled()
        save("TM-cuatro-${height}-${(scale * 100).toInt()}-militar")
        rule.onNodeWithText("Más tarde").performTouchInput { click() }
        rule.runOnIdle { assertTrue(dismissed); assertNull(confirmed) }
    }

    private fun save(name: String) {
        val directory = System.getenv("KPKN_RENDER_DIR") ?: return
        val host = checkNotNull(hostView)
        val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(bitmap)) }
        FileOutputStream(File(File(directory).apply { mkdirs() }, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun fourLifts880at100() = geometry(880, 1f)
    @Test fun fourLifts880at130() = geometry(880, 1.3f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun fourLifts740at100() = geometry(740, 1f)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun fourLifts740at130() = geometry(740, 1.3f)
}
