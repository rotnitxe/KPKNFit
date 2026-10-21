package com.example.kpkn.screens.workout.technique

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.framing.FramingIssue
import com.example.kpkn.domain.biomechanics.framing.FramingVerdict
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.ExpandedMediaOverlay
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.screens.workout.TechniqueRecordingResult
import com.example.kpkn.screens.workout.awaitTechniqueRecordingResult
import com.example.kpkn.screens.workout.technique.design.FramingGuideOverlay
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class TechniqueRecorderStageUiTest {
    @get:Rule val rule = createComposeRule()
    private val actions = mutableListOf<String>()
    private var plate by mutableStateOf<Int?>(450)
    private var plateExpanded by mutableStateOf(false)

    private fun show(scale: Float, recording: Boolean = false, permission: Boolean = true, warning: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, scale), LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(880.dp)) {
                        ExpandedMediaOverlay(Rect.Zero, "Modo visual · serie 3", "95\u00A0kg × 6 · RIR 2", {}, stage = { modifier ->
                            TechniqueRecorderStageContent(
                                modifier = modifier, isRecording = recording, elapsedSeconds = 12L,
                                is60FpsGranted = true, showsPlateBadge = true,
                                plateDiameterMm = plate, plateBadgeExpanded = plateExpanded,
                                onTogglePlateExpanded = { plateExpanded = !plateExpanded },
                                onSelectPlateDiameterMm = { plate = it },
                                profileId = AnalysisProfileId.SQUAT,
                                verdict = FramingVerdict(!warning, if (warning) listOf(FramingIssue.FEET_OUT) else emptyList(), ViewKind.SIDE),
                                hasPermission = permission,
                                onRecord = { actions += "rec" },
                                onSwitchToPhoto = { actions += "photo" },
                                onOpenFicha = { actions += "ficha" },
                                onOpenAlbum = { actions += "album" },
                                onOpenGallery = { actions += "gallery" },
                            )
                        })
                    }
                }
            }
        }
    }

    private fun checkLayout() {
        val guide = rule.onNodeWithTag("technique_guide_text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val record = rule.onNodeWithTag("technique_record").fetchSemanticsNode().boundsInRoot
        val controls = rule.onNodeWithTag("technique_controls").fetchSemanticsNode().boundsInRoot
        assertTrue("La guía tapa los controles", guide.bottom < controls.top)
        assertTrue("La guía tapa REC", guide.bottom < record.top)
        assertTrue(record.width >= 48f * 3f && record.height >= 48f * 3f)
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                rule.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) }
                layouts.forEach { layout ->
                    val text = layout.layoutInput.text.text
                    val diagnostic = "Texto cortado: $text; tamaño=${layout.size}"
                    assertTrue(diagnostic, layout.lineCount > 0)
                    assertEquals(diagnostic, text.trimEnd().length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
                    repeat(layout.lineCount) { line ->
                        assertFalse(diagnostic, layout.isLineEllipsized(line))
                    }
                    // El render nativo reserva 219 px de párrafo para «Foto», aunque los glifos
                    // ocupan los 84 px del Text. Comprobamos cada carácter, sin tolerancias,
                    // en vez de hasVisualOverflow, que incluye ese espacio vacío del párrafo.
                    text.indices.filterNot { text[it].isWhitespace() }.forEach { offset ->
                        val glyph = layout.getBoundingBox(offset)
                        assertTrue("$diagnostic; carácter=$offset; límites=$glyph",
                            glyph.left >= 0f && glyph.top >= 0f &&
                                glyph.right <= layout.size.width && glyph.bottom <= layout.size.height)
                    }
                }
            }
    }

    @Test fun escena_en_reposo_al_100_sin_texto_cortado() { show(1f); checkLayout() }
    @Test fun escena_en_reposo_al_130_sin_texto_cortado() { show(1.3f, warning = true); checkLayout() }
    @Test fun escena_grabando_al_130_separa_guia_cronometro_y_rec() { show(1.3f, recording = true); checkLayout() }

    @Test fun sin_permiso_la_grabacion_esta_desactivada() {
        show(1.3f, permission = false)
        rule.onNodeWithTag("technique_record").assertIsNotEnabled()
    }

    @Test fun las_acciones_de_la_escena_conservan_su_destino() {
        show(1f)
        rule.onNodeWithContentDescription("Cambiar a foto").performClick()
        rule.onNodeWithContentDescription("Ficha del ejercicio").performClick()
        rule.onNodeWithText("Galería").performClick()
        rule.onNodeWithText("Ver álbum").performClick()
        rule.onNodeWithTag("technique_record").performClick()
        assertEquals(listOf("photo", "ficha", "gallery", "album", "rec"), actions)
    }

    @Test fun el_overlay_espera_finalize_al_recibir_la_orden_de_plegar() {
        val recording = MutableStateFlow(true)
        var requested by mutableStateOf(false)
        var open by mutableStateOf(true)
        val events = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                Box(Modifier.width(360.dp).height(880.dp)) {
                    if (open) ExpandedMediaOverlay(
                        startBounds = Rect.Zero, title = "Modo visual · serie 3", subtitle = "95\u00A0kg × 6 · RIR 2",
                        onFolded = { events += "fold"; open = false },
                        foldRequested = requested,
                        beforeFold = { stopTechniqueRecorderBeforeExit({ events += "stop" }, recording) },
                        stage = { modifier -> Box(modifier) },
                    )
                }
            }
        }
        rule.runOnIdle { requested = true }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf("stop"), events) }
        rule.onNodeWithTag("media_expanded").assertExists()
        rule.runOnIdle { recording.value = false }
        rule.waitForIdle()
        rule.onNodeWithTag("media_expanded").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf("stop", "fold"), events) }
    }


    @Test fun el_selector_de_disco_conserva_la_eleccion() {
        show(1.3f)
        rule.onNodeWithText("Disco 45\u00A0cm").performClick()
        rule.onNodeWithText("35 cm").performClick()
        rule.onNodeWithText("Disco 35\u00A0cm").assertExists()
        rule.onNodeWithText("45 cm").assertDoesNotExist()
    }


    private class PersistenceScene {
        val completion = TechniqueRecorderCompletion()
        val write = CompletableDeferred<WorkoutMedia?>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var recording by mutableStateOf(false)
        var foldRequested by mutableStateOf(false)
        var open by mutableStateOf(true)
        var starts = 0

        fun start() = completion.start({}, { foldRequested = true }) { callback ->
            starts++
            recording = true
            scope.launch { callback(awaitTechniqueRecordingResult { write.await() }) }
        }
    }

    private fun showPersistenceScene(): PersistenceScene {
        val scene = PersistenceScene()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1.3f), LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(880.dp)) {
                        if (scene.open) ExpandedMediaOverlay(
                            startBounds = Rect.Zero, title = "Modo visual · serie 3", subtitle = "95\u00A0kg × 6 · RIR 2",
                            onFolded = { scene.open = false },
                            stage = { modifier -> TechniqueRecorderStageContent(
                                modifier = modifier,
                                isRecording = scene.recording,
                                isProcessing = scene.completion.pending && !scene.recording,
                                canRecord = !scene.completion.pending,
                                captureError = scene.completion.errorMessage,
                                onRecord = scene::start,
                            ) },
                            foldRequested = scene.foldRequested,
                        )
                    }
                }
            }
        }
        return scene
    }

    @Test fun al_limite_rec_espera_el_guardado_demorado_y_lo_anuncia_a_talkback() {
        val scene = showPersistenceScene()
        try {
            rule.onNodeWithTag("technique_record").performClick()
            rule.runOnIdle { scene.recording = false } // Finalize por límite de duración.
            rule.onNodeWithTag("technique_record").assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Procesando vídeo"))
                .performTouchInput { click() }
            rule.runOnIdle { assertEquals(1, scene.starts); assertFalse(scene.foldRequested) }
            rule.onNodeWithTag("media_expanded").assertExists()
            rule.runOnIdle {
                scene.write.complete(WorkoutMedia("saved", WorkoutMediaKind.VIDEO, "video.mp4", createdAtMs = 1L))
            }
            rule.waitForIdle()
            rule.onNodeWithTag("media_expanded").assertDoesNotExist()
        } finally { scene.scope.cancel() }
    }

    @Test fun descartado_deja_la_escena_abierta_con_aviso_y_rec_disponible() {
        val scene = showPersistenceScene()
        try {
            rule.onNodeWithTag("technique_record").performClick()
            rule.runOnIdle { scene.recording = false; scene.write.complete(null) }
            rule.onNodeWithTag("media_expanded").assertExists()
            rule.onNodeWithText("No se pudo guardar el vídeo. Puedes volver a grabar.").assertExists()
            rule.onNodeWithTag("technique_record").assertIsEnabled()
        } finally { scene.scope.cancel() }
    }

    @Test fun error_de_guardado_muestra_el_aviso_sin_plegar_y_permite_reintentar() {
        val scene = showPersistenceScene()
        try {
            rule.onNodeWithTag("technique_record").performClick()
            rule.runOnIdle { scene.recording = false; scene.write.completeExceptionally(IOException("Room")) }
            rule.onNodeWithTag("media_expanded").assertExists()
            rule.onNodeWithText("No se pudo guardar el vídeo. Puedes reintentar.").assertExists()
            rule.onNodeWithTag("technique_record").assertIsEnabled().performClick()
            rule.runOnIdle { assertEquals(2, scene.starts); assertFalse(scene.foldRequested) }
        } finally { scene.scope.cancel() }
    }

    @Test fun guia_lateral_al_130_reserva_aire_bajo_los_pies() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1.3f)) {
                FramingGuideOverlay(
                    preferredView = ViewKind.SIDE, hasBarbell = true, profileId = AnalysisProfileId.SQUAT,
                    modifier = Modifier.width(360.dp).height(480.dp),
                )
            }
        }
        val silhouette = rule.onNodeWithTag("technique_silhouette", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val guide = rule.onNodeWithTag("technique_guide_text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("La silueta conserva altura", silhouette.height > 0f)
        assertTrue("La guía roza la silueta", guide.top >= silhouette.bottom + 12f * 3f)
    }

}
