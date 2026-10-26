package com.example.kpkn.screens.workout.technique

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.workout.WorkoutCameraChoice
import com.example.kpkn.screens.workout.WorkoutCameraLens
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class WorkoutCameraSwitchUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun recorderExposesBothLensesAndDisablesTheSwitchWhileRecording() {
        var choice by mutableStateOf(WorkoutCameraChoice().detected(back = true, front = true))
        var recording by mutableStateOf(false)
        rule.setContent { KPKNTheme {
            TechniqueRecorderStageContent(isRecording = recording, cameraChoice = choice,
                onSwitchCamera = { choice = choice.switched(recording) })
        } }
        rule.onNodeWithContentDescription("Usar cámara frontal").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(WorkoutCameraLens.FRONT, choice.lens); recording = true }
        rule.onNodeWithContentDescription("Usar cámara trasera").assertIsNotEnabled()
    }
}
