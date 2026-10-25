package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.programdetail.components.LocalVolumeCalibrationSaved
import com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult
import com.example.kpkn.screens.programdetail.components.VolumeCalibrationSheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE entrega los toques sobre el panel recortado; LEGACY los pierde (BodyRecordSheetTest).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class VolumeCalibrationOriginalsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun saving_preserves_the_original_note_after_the_legacy_consumer_closes_the_sheet() {
        val show = mutableStateOf(true)
        var legacy: VolumeCalibrationResult? = null
        var originals: VolumeCalibrationResult? = null
        val order = mutableListOf<String>()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f),
                LocalVolumeCalibrationSaved provides { result -> originals = result; order += "originals" }) {
                if (show.value) VolumeCalibrationSheet(ProgramMode.HYPERTROPHY, onDismiss = {},
                    onSave = { result -> legacy = result; order += "legacy"; show.value = false })
            }
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Notas opcionales", useUnmergedTree = true).assertExists()
        rule.onNode(hasSetTextAction(), useUnmergedTree = true).performScrollTo().performTextReplacement("Mi nota original")
        rule.runOnIdle { assertNull(legacy); assertNull(originals) }
        rule.onNodeWithText("Guardar calibración", useUnmergedTree = true).performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(listOf("legacy", "originals"), order)
            assertEquals("Mi nota original", originals!!.notes)
            assertEquals(legacy, originals)
        }
    }
}
