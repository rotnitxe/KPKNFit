package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class Cal3SummaryUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun summaryShowsEachResolutionOnceAt880() = show(880, 1f)
    @Test fun summaryShowsEachResolutionOnceAt740WithLargeText() = show(740, 1.3f)
    private fun show(height: Int, font: Float) {
        val f = cal3Fixture()
        val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, 1_790_000_003_000L).calibrationDecisions.single()
        val records = listOf(applied,
            f.record.copy(id = "kept", sourceSetId = "another-source", exerciseName = "Press banca", status = CalibrationStatus.KEPT),
            f.record.copy(id = "old", status = CalibrationStatus.SUPERSEDED))
        var confirmations = 0
        lateinit var view: android.view.View
        rule.setContent {
            view = LocalView.current
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font), LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null) {
                KPKNTheme { Box(Modifier.requiredSize(360.dp, height.dp).padding(top = 45.dp, bottom = 24.dp).testTag("cal3-summary-scene")) {
                    RecordedSessionFinishSheet(name=f.state.session!!.name,
                        completedExercises=listOf(CompletedExercise(f.exercise.id,f.exercise.name,sets=listOf(f.completed))),
                        durationMinutes=10,initialNotes="",saving=false,calibrationDecisions=records,
                        onNotes={},onAlbum={},onConfirm={_,_->confirmations++},onDismiss={})
                } }
            }
        }
        rule.onAllNodesWithTag("finish-calibration-line").assertCountEquals(2)
        rule.onNodeWithText("Sentadilla · Aplicada · 3 series calibradas").assertIsDisplayed()
        rule.onNodeWithText("Press banca · Conservaste las cargas del plan").assertIsDisplayed()
        assertEquals(0, confirmations)
        saveLiveQaRender(rule, view, "cal3-summary-$height")
    }
}
