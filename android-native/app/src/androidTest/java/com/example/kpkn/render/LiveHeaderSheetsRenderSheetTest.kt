package com.example.kpkn.render

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.sessionassistant.*
import com.example.kpkn.screens.workout.*
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.testutil.configureLiveSheetTestWindow
import com.example.kpkn.testutil.verifiedVoiceModesForUiFixture
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real-device render sheet: 360dp wide at the physical available height, both font scales. */
@RunWith(AndroidJUnit4::class)
class LiveHeaderSheetsRenderSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var voiceFixture: AutoCloseable? = null
    @Before fun configureWindow() {
        rule.activityRule.scenario.onActivity { it.configureLiveSheetTestWindow() }
    }
    @After fun restoreVoiceVerification() { voiceFixture?.close() }
    @Test fun tags100() = render("tags", 1f)
    @Test fun tags130() = render("tags", 1.3f)
    @Test fun time100() = render("time", 1f)
    @Test fun time130() = render("time", 1.3f)
    @Test fun ultraFast100() = render("ultrafast", 1f)
    @Test fun ultraFast130() = render("ultrafast", 1.3f)
    @Test fun voice100() = render("voice", 1f)
    @Test fun voice130() = render("voice", 1.3f)

    @OptIn(ExperimentalTestApi::class)
    private fun render(scene: String, scale: Float) {
        if (scene == "voice") voiceFixture = verifiedVoiceModesForUiFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        rule.setContent {
            KPKNTheme { MaterialTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalKpknOverlayHost provides null, LocalRestClockFrozen provides 1.7f) {
                    Box(Modifier.width(360.dp).fillMaxHeight().background(Color.Black).testTag("lv12-canvas")) {
                        when (scene) {
                            "tags" -> WorkoutTagListOverlay(listOf(
                                WorkoutTagListRow(WorkoutTag("machine-a", "Máquina A", "row", subTags = listOf(WorkoutSubTag("pin", "Asiento 3", SubTagCategory.SETUP))), "Máquina A", "Última: 80 kg", true),
                                WorkoutTagListRow(WorkoutTag("machine-b", "Máquina B", "row"), "Máquina B", "Última: 75 kg", false)),
                                {}, {}, {}, hasSeenEducation = false, onCreateInline = { _, _ -> CreateTagResult.InvalidName }, activeSubTagIds = listOf("pin"))
                            "time" -> SessionTimeSheet(1_930, 1_792_170_000_000, 60, Color(0xFFF49A6E), 8, 16, 60, null,
                                {}, {}, {}, {}, PacingAlertMode.FINAL, {}, false, 0, {}, null, {})
                            "ultrafast" -> UltraFastPreviewSheet(UltraFastPreview(3_600, 2_400, 1_200, listOf(
                                UltraFastExerciseChange("curl", "Curl de bíceps", 4, 2, "NORMAL", "DROPSET", UltraFastReason.ISOLATION_DENSIFIED, true, false),
                                UltraFastExerciseChange("press", "Press de banca", 4, 4, "NORMAL", "NORMAL", UltraFastReason.PROTECTED_BASIC, false, false)),
                                listOf(UltraFastSupersetChange("curl", "push", "Curl de bíceps", "Extensión de tríceps", "Polea"))),
                                1_200, emptyList(), emptyMap(), {}, {}, {})
                            "voice" -> VoiceCaptureModeDialog({}, {})
                        }
                    }
                }
            } }
        }
        if (scene == "voice") {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Preparando controles de voz…").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("Modo música", useUnmergedTree = true).performScrollTo().performClick()
        }
        // Capture the final entry pose, with the same vertical viewport as the physical window.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("lv12-canvas")
        val canvasBounds = canvas.getUnclippedBoundsInRoot()
        val footerLabels = when (scene) {
            "tags", "time" -> listOf("Listo")
            "ultrafast" -> listOf("Cancelar", "Aplicar")
            else -> listOf("Ahora no", "Activar")
        }
        footerLabels.forEach { label ->
            val footer = rule.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
            val bounds = footer.getUnclippedBoundsInRoot()
            val tolerance = 0.5.dp
            assertTrue("$scene/$scale: $label must have a visible area", bounds.right > bounds.left && bounds.bottom > bounds.top)
            assertTrue("$scene/$scale: full $label bounds $bounds must fit inside canvas $canvasBounds",
                bounds.left >= canvasBounds.left - tolerance && bounds.top >= canvasBounds.top - tolerance &&
                    bounds.right <= canvasBounds.right + tolerance && bounds.bottom <= canvasBounds.bottom + tolerance)
        }
        val bitmap = canvas.captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "lv12-render").apply { mkdirs() }
        val target = File(directory, "$scene-${(scale * 100).toInt()}.png")
        target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue(target.length() > 0)
    }
}
