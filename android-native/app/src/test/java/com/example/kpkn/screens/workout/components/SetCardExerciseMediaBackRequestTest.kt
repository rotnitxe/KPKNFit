package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.media.WorkoutMediaSides
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class SetCardExerciseMediaBackRequestTest {
    @get:Rule val rule = createComposeRule()
    private var opened: WorkoutMediaCaptureRequest? = null
    private val exercise = Exercise(id = "exercise", name = "Ejercicio de prueba", canonicalExerciseId = "canonical",
        catalogDefinitionId = "definition", catalogConfigurationId = "configuration")

    private fun openRecorder(exercise: Exercise, side: String) {
        rule.setContent {
            KPKNTheme {
                Box(Modifier.width(320.dp).height(560.dp)) {
                    SetCardExerciseMediaBack(
                        exercise = exercise, onFlipBack = {}, setIndex = 2, side = side,
                        weightKg = 95.0, reps = 8, isPr = true,
                        onOpenRecorder = { opened = it },
                    )
                }
            }
        }
        rule.onNodeWithTag(MediaShutterTag).performClick()
        rule.waitForIdle()
        assertNotNull(opened)
    }

    @Test fun actual_shutter_emits_bilateral_catalog_request_without_selected_left() {
        openRecorder(exercise, "left")
        assertEquals(WorkoutMediaCaptureRequest(
            exerciseId = "exercise", canonicalExerciseId = "canonical", exerciseName = "Ejercicio de prueba",
            setIndex = 2, side = null, weightKg = 95.0, reps = 8, isPr = true,
            catalogDefinitionId = "definition", catalogConfigurationId = "configuration",
        ), opened)
        assertNull(opened?.side)
    }

    @Test fun actual_shutter_keeps_unilateral_right_and_catalog_configuration() {
        openRecorder(exercise.copy(unilateralMode = UnilateralMode.UNILATERAL_PAIRED), " R ")
        assertEquals("right", opened?.side)
        assertEquals("R", WorkoutMediaSides.token(opened?.side))
        assertEquals("definition", opened?.catalogDefinitionId)
        assertEquals("configuration", opened?.catalogConfigurationId)
        assertEquals(2, opened?.setIndex)
        assertEquals(8, opened?.reps)
    }
}
