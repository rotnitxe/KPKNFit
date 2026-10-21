package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import com.example.kpkn.screens.workout.components.MediaFaceTag
import com.example.kpkn.screens.workout.components.SetCardExerciseMediaBack
import com.example.kpkn.screens.workout.design.VideoThumbBadge
import com.example.kpkn.screens.workout.design.VisualModeAnalysisAction
import com.example.kpkn.screens.workout.design.VisualModeAnalysisLine
import com.example.kpkn.screens.workout.design.videoThumbContentDescription
import com.example.kpkn.screens.workout.design.visualModeAnalysisLine
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val MEDIA_SHEET_TAG = "lv8-media-sheet"
private const val MEDIA_DENSITY = 3f

/**
 * HOJA DE RENDER de «Modo visual» de la ronda LV8 (A3 y G3): la cara de la tarjeta con la cámara, «Foto» y «Vídeo», «Ficha», «Galería»,
 * «Ver álbum» y la línea «Se guarda en el álbum con la serie 3 · 95 kg × 6 · RIR 2.», y el mismo «Modo visual» expandido ENCIMA de todo.
 * Dibuja la pieza REAL (`SetCardExerciseMediaBack`) con un anfitrión de superposiciones como el de la app, a 360 dp, al 100 % y al 130 %
 * de letra y con la altura que mide la tarjeta de la serie. Solo corre con `KPKN_RENDER_DIR`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveCardRenderSheetMediaTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private val serie = ExerciseSet(
        id = "sq-1",
        targetReps = 6,
        targetRIR = 2,
        intensityMode = IntensityMode.RIR,
        weight = 95.0,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    private val ejercicio = Exercise(
        id = "sq",
        name = "Sentadilla trasera",
        trainingMode = TrainingMode.REPS,
        sets = listOf(serie),
    )

    /** Lo que mide la tarjeta de la serie (las tres caras miden lo mismo): sale de la prueba de alturas, al 100 % y al 130 % de letra. */
    private fun alturaDeLaTarjeta(fontScale: Float): Float = if (fontScale > 1.15f) CARD_HEIGHT_130 else CARD_HEIGHT_100

    @Test fun modoVisualTarjeta100() { onlyWhenAsked(); dibujar(1f, "modo_visual_tarjeta_100") }
    @Test fun modoVisualTarjeta130() { onlyWhenAsked(); dibujar(1.3f, "modo_visual_tarjeta_130") }
    @Test fun modoVisualExpandido100() { onlyWhenAsked(); dibujar(1f, "modo_visual_expandido_100", expandido = true) }
    @Test fun modoVisualExpandido130() { onlyWhenAsked(); dibujar(1.3f, "modo_visual_expandido_130", expandido = true) }

    @Test fun despuesDeGrabar100() { onlyWhenAsked(); dibujar(1f, "modo_visual_despues_de_grabar_100", despuesDeGrabar = true) }
    @Test fun despuesDeGrabar130() { onlyWhenAsked(); dibujar(1.3f, "modo_visual_despues_de_grabar_130", despuesDeGrabar = true) }

    @Test fun analisisEnLaTarjeta100() { onlyWhenAsked(); dibujarAnalisis(1f, "100") }
    @Test fun analisisEnLaTarjeta130() { onlyWhenAsked(); dibujarAnalisis(1.3f, "130") }

    /** Cada captura usa la misma instancia y las mismas medidas; solo cambia el resultado de Room. */
    private fun dibujarAnalisis(fontScale: Float, escala: String) {
        val states = listOf(
            "sin_video" to null,
            "analizando" to SetVideoAnalysisState.Analyzing(42),
            "alta" to SetVideoAnalysisState.Suggest(8, 1, 0, CountConfidence.ALTA),
            "media" to SetVideoAnalysisState.Suggest(8, 0, 0, CountConfidence.MEDIA),
            "no_concluyente" to SetVideoAnalysisState.Inconclusive,
            "fallo" to SetVideoAnalysisState.Failed("ERR_CODEC"),
            "sin_soporte" to SetVideoAnalysisState.Unsupported("Este ejercicio todavía no admite análisis de técnica"),
            "discrepancia" to SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.ALTA),
            "coincide" to SetVideoAnalysisState.Agrees(8),
            "intento_fallido" to SetVideoAnalysisState.Suggest(0, 0, 1, CountConfidence.ALTA),
            "discrepancia_larga" to SetVideoAnalysisState.Mismatch(12, 8, 3, 2, CountConfidence.ALTA, loggedPartials = 2),
            "error_guardado_largo" to SetVideoAnalysisState.Suggest(8, 1, 0, CountConfidence.ALTA),
            "sin_soporte_largo" to SetVideoAnalysisState.Unsupported("Este ejercicio todavía no admite análisis de técnica con esta variante. Puedes grabarlo y conservarlo en el álbum de la sesión para revisarlo después."),
            "sin_insignia" to null,
        )
        val badges = linkedMapOf(
            "analizando" to VideoThumbBadge.Analyzing(42),
            "exacto" to VideoThumbBadge.Reps(8, approximate = false),
            "incierto" to VideoThumbBadge.Inconclusive,
        )
        val thumbs = badges.keys.mapIndexed { index, id ->
            WorkoutMedia(id, WorkoutMediaKind.VIDEO, "", createdAtMs = 1L, exerciseId = ejercicio.id, setIndex = 2,
                durationMs = if (index == 1) 65_000L else 12_000L)
        }
        var current by mutableStateOf<SetVideoAnalysisState?>(null)
        var currentCaptureError by mutableStateOf<String?>(null)
        var currentBadges by mutableStateOf<Map<String, VideoThumbBadge>>(badges)
        val actions = mutableListOf<String>()
        val actionFailures = mutableListOf<String>()
        val alto = alturaDeLaTarjeta(fontScale)
        rule.setContent {
            hostView = LocalView.current
            val anfitrion = rememberKpknOverlayHostController()
            CompositionLocalProvider(
                LocalDensity provides Density(MEDIA_DENSITY, fontScale),
                LocalKpknOverlayHost provides anfitrion,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp).height((alto + 32).dp).background(Color.Black).testTag(MEDIA_SHEET_TAG)) {
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp).width(272.dp).height(alto.dp)) {
                                SetCardExerciseMediaBack(
                                    exercise = ejercicio,
                                    onFlipBack = {},
                                    setIndex = 2,
                                    weightKg = 95.0,
                                    reps = 6,
                                    effortLabel = "RIR 2",
                                    showControlsWithoutCamera = true,
                                    mediaFilesOverride = thumbs,
                                    videoThumbBadges = currentBadges,
                                    videoAnalysis = current?.let { SetVideoAnalysisUi("serie-3", it) },
                                    captureErrorOverride = currentCaptureError,
                                    onOpenAnalysis = { actions += "open:$it" },
                                    onRetryAnalysis = { actions += "analysis-retry:$it" },
                                    onRetryPendingCaptures = { actions += "capture-retry" },
                                )
                            }
                            KpknOverlayHostContent(anfitrion)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val border = rule.onNodeWithTag(MediaFaceTag).fetchSemanticsNode().boundsInRoot
        val camera = rule.onNodeWithTag("media_stage_compact").fetchSemanticsNode().boundsInRoot
        states.forEach { (name, state) ->
            rule.runOnIdle {
                current = state
                currentCaptureError = if (name == "error_guardado_largo") "No se pudo guardar el medio seleccionado. Puedes reintentar." else null
                currentBadges = when (name) {
                    "media" -> badges + ("exacto" to VideoThumbBadge.Reps(8, approximate = true))
                    "fallo" -> badges + ("incierto" to VideoThumbBadge.Failed)
                    "sin_insignia" -> emptyMap()
                    else -> badges
                }
            }
            rule.waitForIdle()
            assertEquals("El borde cambia con $name al $escala %", border, rule.onNodeWithTag(MediaFaceTag).fetchSemanticsNode().boundsInRoot)
            assertEquals("La cámara cambia con $name al $escala %", camera, rule.onNodeWithTag("media_stage_compact").fetchSemanticsNode().boundsInRoot)
            val line = rule.onNodeWithTag("media_analysis_line")
            val node = line.fetchSemanticsNode()
            assertEquals(48f, node.boundsInRoot.height / MEDIA_DENSITY, 0.01f)
            val layouts = mutableListOf<TextLayoutResult>()
            rule.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) }
            val layout = layouts.single()
            assertFalse("La frase se corta en $name al $escala %", layout.hasVisualOverflow)
            assertTrue(layout.lineCount <= 2)
            if (name == "sin_soporte_largo") assertTrue(layout.layoutInput.text.text.endsWith('…'))
            val message = rule.onNodeWithTag("media_camera_message").fetchSemanticsNode().boundsInRoot
            val messageArea = rule.onNodeWithTag("media_camera_message_area").fetchSemanticsNode().boundsInRoot
            val strip = rule.onNodeWithTag("media_thumb_strip").fetchSemanticsNode().boundsInRoot
            assertTrue("El mensaje se monta sobre las miniaturas", message.bottom < strip.top)
            assertEquals(messageArea.center.y, message.center.y, 1f)
            for (media in thumbs) {
                val badge = currentBadges[media.id]
                val thumb = rule.onNodeWithTag("media_thumb_${media.id}").fetchSemanticsNode()
                assertEquals(listOf(videoThumbContentDescription(badge, media.durationMs)), thumb.config[SemanticsProperties.ContentDescription])
                assertEquals(Role.Button, thumb.config[SemanticsProperties.Role])
                assertTrue(thumb.boundsInRoot.height >= 48f * MEDIA_DENSITY)
                assertTrue(thumb.boundsInRoot.width >= 48f * MEDIA_DENSITY)
                val play = rule.onNodeWithTag("media_play_${media.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(12f, play.width / MEDIA_DENSITY, 0.01f)
                if (badge != null) {
                    val badgeBounds = rule.onNodeWithTag("media_badge_${media.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals(16f, badgeBounds.height / MEDIA_DENSITY, 0.01f)
                    assertTrue(badgeBounds.width >= 16f * MEDIA_DENSITY)
                    assertFalse("Play e insignia se superponen", play.overlaps(badgeBounds))
                    rule.onNodeWithTag("media_duration_${media.id}", useUnmergedTree = true).assertDoesNotExist()
                } else {
                    val duration = rule.onNodeWithTag("media_duration_${media.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertFalse("Play y duración se superponen", play.overlaps(duration))
                    rule.onNodeWithTag("media_badge_${media.id}", useUnmergedTree = true).assertDoesNotExist()
                }
            }
            guardar(MEDIA_SHEET_TAG, "modo_visual_analisis_${name}_$escala")
            if (state != null) {
                val model = currentCaptureError?.let { VisualModeAnalysisLine("$it · reintentar", "reintentar", VisualModeAnalysisAction.RETRY) }
                    ?: visualModeAnalysisLine(state, 2)
                assertEquals(listOf(model.text), node.config[SemanticsProperties.ContentDescription])
                val before = actions.size
                if (model.action != VisualModeAnalysisAction.NONE) {
                    assertEquals(Role.Button, node.config[SemanticsProperties.Role])
                    val expected = when {
                        currentCaptureError != null -> "capture-retry"
                        model.action == VisualModeAnalysisAction.OPEN -> "open:serie-3"
                        else -> "analysis-retry:serie-3"
                    }
                    fun checkActionCount(count: Int, target: String) {
                        rule.runOnIdle {
                            val expectedActions = List(count) { expected }
                            if (actions.drop(before) != expectedActions) {
                                actionFailures += "$name/$escala/$target: ${actions.drop(before)}; esperado $expectedActions"
                            }
                        }
                    }
                    line.performSemanticsAction(SemanticsActions.OnClick) { it() }
                    checkActionCount(1, "TalkBack")
                    val actionLabel = model.actionLabel!!.replace(' ', '\u00A0')
                    val displayed = layout.layoutInput.text.text
                    val start = displayed.lastIndexOf(actionLabel)
                    assertTrue("Falta la acción completa en $name", start >= 0)
                    assertTrue(displayed.endsWith(actionLabel))
                    assertTrue(layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) >= start + actionLabel.length)
                    if (name == "discrepancia_larga" || (name == "error_guardado_largo" && fontScale > 1f)) assertTrue(displayed.contains('…'))
                    val glyph = layout.getBoundingBox(start)
                    val textTop = (node.boundsInRoot.height - layout.size.height) / 2f
                    line.performTouchInput { click(Offset(glyph.center.x, textTop + glyph.center.y)) }
                    checkActionCount(2, "palabra")
                    // Tocar debajo de la palabra prueba el objetivo de 48 dp, sin separar la acción de la frase.
                    line.performTouchInput { click(Offset(glyph.center.x, node.boundsInRoot.height - 1f)) }
                    checkActionCount(3, "banda48")
                } else assertFalse(node.config.contains(SemanticsActions.OnClick))
            }
        }
        assertTrue("Acciones del Modo visual:\n${actionFailures.joinToString("\n")}", actionFailures.isEmpty())
    }

    private fun dibujar(fontScale: Float, nombre: String, expandido: Boolean = false, despuesDeGrabar: Boolean = false) {
        val alto = alturaDeLaTarjeta(fontScale)
        rule.setContent {
            hostView = LocalView.current
            val anfitrion = rememberKpknOverlayHostController()
            CompositionLocalProvider(
                LocalDensity provides Density(MEDIA_DENSITY, fontScale),
                LocalKpknOverlayHost provides anfitrion,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(if (expandido) 880.dp else (alto + 32).dp)
                                .background(Color.Black)
                                .testTag(MEDIA_SHEET_TAG),
                        ) {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                                    .width(272.dp)
                                    .height(alto.dp),
                            ) {
                                SetCardExerciseMediaBack(
                                    exercise = ejercicio,
                                    onFlipBack = {},
                                    setIndex = 2,
                                    weightKg = 95.0,
                                    reps = 6,
                                    effortLabel = "RIR 2",
                                    mediaFilesOverride = if (despuesDeGrabar) listOf(WorkoutMedia(
                                        "nuevo", WorkoutMediaKind.VIDEO, "", createdAtMs = 1L,
                                        exerciseId = ejercicio.id, setIndex = 2, durationMs = 12_000L,
                                    )) else null,
                                    videoThumbBadges = if (despuesDeGrabar) mapOf("nuevo" to VideoThumbBadge.Analyzing(0)) else emptyMap(),
                                    videoAnalysis = if (despuesDeGrabar) SetVideoAnalysisUi("nuevo", SetVideoAnalysisState.Analyzing(0)) else null,
                                    showControlsWithoutCamera = true,
                                )
                            }
                            KpknOverlayHostContent(anfitrion)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        if (expandido) {
            rule.onNodeWithText("Foto").performClick()
            rule.onNodeWithTag("media_expand").performClick()
            rule.waitForIdle()
        }
        guardar(MEDIA_SHEET_TAG, nombre)
    }

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun guardar(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Alto en dp de la tarjeta de la serie (anverso, «Avanzado» y «Modo visual»), medido por `SetExecutionCardEffortTest` al 100 % y al 130 %. */
private const val CARD_HEIGHT_100 = 384.67f
private const val CARD_HEIGHT_130 = 401.67f
