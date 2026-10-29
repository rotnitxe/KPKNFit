package com.example.kpkn.screens.workout.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.media.WorkoutMediaSides
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.screens.workout.technique.TechniqueRecorderStageTag
import com.example.kpkn.screens.workout.technique.TechniqueRecorderRequestKey
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
import org.junit.Assert.assertEquals
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
    private val exercise = Exercise(id = "exercise", name = "Ejercicio de prueba", canonicalExerciseId = "canonical",
        catalogDefinitionId = "definition", catalogConfigurationId = "configuration")

    private fun show(exercise: Exercise = this.exercise, side: String = "left") {
        rule.setContent {
            val host = rememberKpknOverlayHostController()
            CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        Box(Modifier.width(320.dp).height(560.dp)) {
                            SetCardExerciseMediaBack(
                                exercise = exercise, onFlipBack = {}, setIndex = 2, side = side,
                                weightKg = 95.0, reps = 8, isPr = true,
                                showControlsWithoutCamera = true,
                            )
                        }
                        KpknOverlayHostContent(host)
                    }
                }
            }
        }
    }

    private fun openRecorder(exercise: Exercise, side: String): WorkoutMediaCaptureRequest {
        show(exercise, side)
        rule.onNodeWithTag(MediaShutterTag).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MediaExpandedTag).assertIsDisplayed()
        rule.onNodeWithTag(TechniqueRecorderStageTag).assertIsDisplayed()
        return rule.onNodeWithTag(TechniqueRecorderStageTag).fetchSemanticsNode().config[TechniqueRecorderRequestKey]
    }

    @Test fun actual_shutter_emits_bilateral_catalog_request_without_selected_left() {
        val opened = openRecorder(exercise, "left")
        assertEquals(WorkoutMediaCaptureRequest(
            exerciseId = "exercise", canonicalExerciseId = "canonical", exerciseName = "Ejercicio de prueba",
            setIndex = 2, side = null, weightKg = 95.0, reps = 8, isPr = true,
            catalogDefinitionId = "definition", catalogConfigurationId = "configuration",
        ), opened)
        assertNull(opened?.side)
    }

    @Test fun actual_shutter_keeps_unilateral_right_and_catalog_configuration() {
        val opened = openRecorder(exercise.copy(unilateralMode = UnilateralMode.UNILATERAL_PAIRED), " R ")
        assertEquals("right", opened?.side)
        assertEquals("R", WorkoutMediaSides.token(opened?.side))
        assertEquals("definition", opened?.catalogDefinitionId)
        assertEquals("configuration", opened?.catalogConfigurationId)
        assertEquals(2, opened?.setIndex)
        assertEquals(8, opened?.reps)
    }

    @Test fun el_disparador_en_video_expande_y_monta_la_escena_del_grabador() {
        // Refinamiento 02: la cara ya no tiene «Expandir»; en «Vídeo» el disparador abre el visor expandido con el grabador.
        show()
        rule.onNodeWithTag(MediaShutterTag).assertContentDescriptionEquals("Grabar vídeo")
        rule.onNodeWithTag(MediaShutterTag).performClick()
        rule.onNodeWithTag(MediaExpandedTag).assertIsDisplayed()
        rule.onNodeWithTag(TechniqueRecorderStageTag).assertIsDisplayed()
    }

    @Test fun foto_desde_el_grabador_pliega_y_deja_la_tarjeta_en_foto() {
        show()
        rule.onNodeWithTag(MediaShutterTag).performClick()
        // El interruptor «Vídeo» de la cara sigue debajo del visor: se pulsa el del grabador, que es el de dentro del visor expandido.
        rule.onNode(hasContentDescription("Cambiar a foto") and hasAnyAncestor(hasTestTag(MediaExpandedTag))).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MediaExpandedTag).assertDoesNotExist()
        rule.onNodeWithTag(TechniqueRecorderStageTag).assertDoesNotExist()
        rule.onNodeWithTag(MediaFaceTag).assertExists()
        rule.onNodeWithTag(MediaShutterTag).assertContentDescriptionEquals("Hacer foto")
        // En «Foto» el disparador hace la foto (sin cámara en la prueba no hace nada) y no abre el visor expandido.
        rule.onNodeWithTag("media_mode_toggle").assertContentDescriptionEquals("Cambiar a vídeo")
        rule.onNodeWithTag(MediaShutterTag).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MediaExpandedTag).assertDoesNotExist()
        rule.onNodeWithTag(TechniqueRecorderStageTag).assertDoesNotExist()
    }

    @Test fun plegar_el_video_conserva_la_cara_visual_y_permite_reabrirlo() {
        show()
        val initial = rule.onNodeWithTag(MediaFaceTag).fetchSemanticsNode().boundsInRoot
        repeat(2) {
            rule.onNodeWithTag(MediaShutterTag).performClick()
            rule.onNodeWithTag(TechniqueRecorderStageTag).assertIsDisplayed()
            rule.onNodeWithTag("media_fold").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag(MediaExpandedTag).assertDoesNotExist()
            assertEquals(initial, rule.onNodeWithTag(MediaFaceTag).fetchSemanticsNode().boundsInRoot)
            rule.onNodeWithTag(MediaShutterTag).assertContentDescriptionEquals("Grabar vídeo")
        }
    }

}
