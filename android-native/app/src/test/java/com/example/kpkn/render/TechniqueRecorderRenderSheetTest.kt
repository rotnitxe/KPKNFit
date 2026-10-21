package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.framing.FramingIssue
import com.example.kpkn.domain.biomechanics.framing.FramingVerdict
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.workout.technique.TechniqueRecorderStageContent
import com.example.kpkn.screens.workout.components.ExpandedMediaOverlay
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.screens.workout.technique.design.FramingChip
import com.example.kpkn.screens.workout.technique.design.FramingGuideOverlay
import com.example.kpkn.screens.workout.technique.design.RecTimerPill
import com.example.kpkn.screens.workout.technique.design.RecordButton
import com.example.kpkn.screens.workout.technique.design.RecorderTopBar
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE RENDER del grabador de técnica (VT8a; no comprueba nada: escribe PNG).
 * Se ejecuta solo cuando KPKN_RENDER_DIR está configurado.
 *
 * Dibuja las piezas a 360 dp de ancho al 100 % y al 130 % de escala de fuente:
 * - Barra superior con y sin insignias;
 * - Botón de grabación en reposo y grabando;
 * - Cronómetro (píldora);
 * - Chips de encuadre (OK y cada aviso);
 * - Silueta de líneas de guía (lado y frente sobre fondo gris).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class TechniqueRecorderRenderSheetTest {

    companion object {
        private const val HOJA = "hoja_rec"

        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private var hostView: View? = null

    private fun save(name: String, tag: String = HOJA) {
        // Las capas de Canvas necesitan un frame de dibujo antes de View.draw en nativo.
        rule.runOnUiThread { hostView!!.invalidate() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, name) }
    }

    private fun render(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale), LocalWizardReducedMotion provides true) {
                Box(
                    modifier = Modifier
                        .testTag(HOJA)
                        .width(360.dp)
                        .background(Color.Black)
                        .padding(vertical = 12.dp),
                ) {
                    content()
                }
            }
        }
        save(name)
    }

    @Test
    fun topBarWithBadges100() = render("topbar_with_badges_100", fontScale = 1f) {
        RecorderTopBar(
            exerciseName = "Sentadilla con barra",
            setLabel = "Serie 2",
            is60FpsGranted = true,
            showsPlateBadge = true,
            plateDiameterMm = 450,
            plateBadgeExpanded = false,
            onTogglePlateExpanded = {},
            onSelectPlateDiameterMm = {},
            onCustomPlateSelected = {},
            onClose = {},
        )
    }

    @Test
    fun topBarWithBadges130() = render("topbar_with_badges_130", fontScale = 1.3f) {
        RecorderTopBar(
            exerciseName = "Sentadilla con barra",
            setLabel = "Serie 2",
            is60FpsGranted = true,
            showsPlateBadge = true,
            plateDiameterMm = 450,
            plateBadgeExpanded = false,
            onTogglePlateExpanded = {},
            onSelectPlateDiameterMm = {},
            onCustomPlateSelected = {},
            onClose = {},
        )
    }

    @Test
    fun topBarNoBadges100() = render("topbar_no_badges_100", fontScale = 1f) {
        RecorderTopBar(
            exerciseName = "Dominadas",
            setLabel = "Serie 1",
            is60FpsGranted = false,
            showsPlateBadge = false,
            plateDiameterMm = null,
            plateBadgeExpanded = false,
            onTogglePlateExpanded = {},
            onSelectPlateDiameterMm = {},
            onCustomPlateSelected = {},
            onClose = {},
        )
    }

    @Test
    fun topBarNoBadges130() = render("topbar_no_badges_130", fontScale = 1.3f) {
        RecorderTopBar(
            exerciseName = "Dominadas",
            setLabel = "Serie 1",
            is60FpsGranted = false,
            showsPlateBadge = false,
            plateDiameterMm = null,
            plateBadgeExpanded = false,
            onTogglePlateExpanded = {},
            onSelectPlateDiameterMm = {},
            onCustomPlateSelected = {},
            onClose = {},
        )
    }

    @Test
    fun recordButtons100() = render("record_buttons_100", fontScale = 1f) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordButton(isRecording = false, onClick = {})
            RecordButton(isRecording = true, onClick = {})
        }
    }

    @Test
    fun recordButtons130() = render("record_buttons_130", fontScale = 1.3f) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordButton(isRecording = false, onClick = {})
            RecordButton(isRecording = true, onClick = {})
        }
    }

    @Test
    fun recTimerPill100() = render("rec_timer_pill_100", fontScale = 1f) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecTimerPill(elapsedSeconds = 12L)
            RecTimerPill(elapsedSeconds = 75L)
        }
    }

    @Test
    fun recTimerPill130() = render("rec_timer_pill_130", fontScale = 1.3f) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecTimerPill(elapsedSeconds = 12L)
            RecTimerPill(elapsedSeconds = 75L)
        }
    }

    @Test
    fun framingChips100() = render("framing_chips_100", fontScale = 1f) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FramingChip(verdict = FramingVerdict(ok = true, issues = emptyList(), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.NO_PERSON), view = null))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.TWO_PEOPLE), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.TOO_CLOSE), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.FEET_OUT), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.HEAD_OUT), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.TOO_SMALL), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.WRONG_VIEW), view = ViewKind.FRONT))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.FACE_HIDDEN), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.TILTED), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.NO_PLATE), view = ViewKind.SIDE))
        }
    }

    @Test
    fun framingChips130() = render("framing_chips_130", fontScale = 1.3f) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FramingChip(verdict = FramingVerdict(ok = true, issues = emptyList(), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.FEET_OUT), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.WRONG_VIEW), view = ViewKind.FRONT))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.TILTED), view = ViewKind.SIDE))
            FramingChip(verdict = FramingVerdict(ok = false, issues = listOf(FramingIssue.NO_PLATE), view = ViewKind.SIDE))
        }
    }

    @Test
    fun framingSilhouetteSide100() = render("framing_silhouette_side_100", fontScale = 1f) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .height(480.dp)
                .background(Color(0xFF222222)),
        ) {
            FramingGuideOverlay(preferredView = ViewKind.SIDE, hasBarbell = true, profileId = AnalysisProfileId.SQUAT)
        }
    }

    @Test
    fun framingSilhouetteSide130() = render("framing_silhouette_side_130", fontScale = 1.3f) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .height(480.dp)
                .background(Color(0xFF222222)),
        ) {
            FramingGuideOverlay(preferredView = ViewKind.SIDE, hasBarbell = true, profileId = AnalysisProfileId.SQUAT)
        }
    }

    @Test
    fun framingSilhouetteFront100() = render("framing_silhouette_front_100", fontScale = 1f) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .height(480.dp)
                .background(Color(0xFF222222)),
        ) {
            FramingGuideOverlay(preferredView = ViewKind.FRONT, hasBarbell = false)
        }
    }

    @Test
    fun framingSilhouetteFront130() = render("framing_silhouette_front_130", fontScale = 1.3f) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .height(480.dp)
                .background(Color(0xFF222222)),
        ) {
            FramingGuideOverlay(preferredView = ViewKind.FRONT, hasBarbell = false)
        }
    }

    private fun renderFullScreen(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale), LocalWizardReducedMotion provides true) {
                Box(
                    modifier = Modifier
                        .testTag(HOJA)
                        .width(360.dp)
                        .height(880.dp)
                        .background(Color.Black),
                ) {
                    content()
                }
            }
        }
        save(name)
    }

    @Test fun benchGuide130() = renderFullScreen("guide_bench_130", 1.3f) {
        FramingGuideOverlay(preferredView = ViewKind.SIDE, hasBarbell = true, profileId = AnalysisProfileId.BENCH)
    }
    @Test fun deadliftGuide130() = renderFullScreen("guide_deadlift_130", 1.3f) {
        FramingGuideOverlay(preferredView = ViewKind.SIDE, hasBarbell = true, profileId = AnalysisProfileId.DEADLIFT)
    }

    @Test
    fun fullscreenIdle100() = renderFullScreen("modo_visual_grabador_reposo_100", fontScale = 1f) {
        FullScreenRecorderPreview(isRecording = false, is60FpsGranted = true, showsPlateBadge = true)
    }

    @Test
    fun fullscreenIdle130() = renderFullScreen("modo_visual_grabador_reposo_130", fontScale = 1.3f) {
        FullScreenRecorderPreview(isRecording = false, is60FpsGranted = true, showsPlateBadge = true)
    }

    @Test
    fun fullscreenRecording100() = renderFullScreen("modo_visual_grabador_grabando_100", fontScale = 1f) {
        FullScreenRecorderPreview(isRecording = true, elapsedSeconds = 12L, is60FpsGranted = true, showsPlateBadge = true)
    }

    @Test
    fun fullscreenRecording130() = renderFullScreen("modo_visual_grabador_grabando_130", fontScale = 1.3f) {
        FullScreenRecorderPreview(isRecording = true, elapsedSeconds = 12L, is60FpsGranted = true, showsPlateBadge = true)
    }

    @Test fun expandedSaving100() = renderFullScreen("modo_visual_grabador_guardando_100", 1f) {
        FullScreenRecorderPreview(isProcessing = true)
    }

    @Test fun expandedSaving130() = renderFullScreen("modo_visual_grabador_guardando_130", 1.3f) {
        FullScreenRecorderPreview(isProcessing = true)
    }

    @Test fun expandedSaveError100() = renderFullScreen("modo_visual_grabador_error_guardado_100", 1f) {
        FullScreenRecorderPreview(captureError = "No se pudo guardar el vídeo. Puedes reintentar.")
    }

    @Test fun expandedSaveError130() = renderFullScreen("modo_visual_grabador_error_guardado_130", 1.3f) {
        FullScreenRecorderPreview(captureError = "No se pudo guardar el vídeo. Puedes reintentar.")
    }

    @Test fun expandedPlateSelector100() = renderFullScreen("modo_visual_grabador_selector_disco_100", 1f) {
        FullScreenRecorderPreview(is60FpsGranted = true, showsPlateBadge = true, plateBadgeExpanded = true)
    }

    @Test fun expandedPlateSelector130() = renderFullScreen("modo_visual_grabador_selector_disco_130", 1.3f) {
        FullScreenRecorderPreview(is60FpsGranted = true, showsPlateBadge = true, plateBadgeExpanded = true)
    }

    @Test
    fun fullscreenNoCamera100() = renderFullScreen("modo_visual_grabador_sin_camara_100", fontScale = 1f) {
        FullScreenRecorderPreview(hasCamera = false, hasPermission = true)
    }

    @Test
    fun fullscreenNoCamera130() = renderFullScreen("modo_visual_grabador_sin_camara_130", fontScale = 1.3f) {
        FullScreenRecorderPreview(hasCamera = false, hasPermission = true)
    }

    @Test
    fun fullscreenNoPermission100() = renderFullScreen("modo_visual_grabador_sin_permiso_100", fontScale = 1f) {
        FullScreenRecorderPreview(hasCamera = true, hasPermission = false, isPermanentlyDenied = false)
    }

    @Test
    fun fullscreenNoPermission130() = renderFullScreen("modo_visual_grabador_sin_permiso_130", fontScale = 1.3f) {
        FullScreenRecorderPreview(hasCamera = true, hasPermission = false, isPermanentlyDenied = true)
    }
}

@Composable
private fun FullScreenRecorderPreview(
    isRecording: Boolean = false,
    elapsedSeconds: Long = 0L,
    is60FpsGranted: Boolean = false,
    showsPlateBadge: Boolean = false,
    plateDiameterMm: Int? = 450,
    hasCamera: Boolean = true,
    hasPermission: Boolean = true,
    isPermanentlyDenied: Boolean = false,
    plateBadgeExpanded: Boolean = false,
    isProcessing: Boolean = false,
    captureError: String? = null,
) {
    KPKNTheme {
        ExpandedMediaOverlay(
            startBounds = Rect.Zero,
            title = "Modo visual · serie 3",
            subtitle = "95\u00A0kg × 6 · RIR 2",
            onFolded = {},
            stage = { modifier ->
                TechniqueRecorderStageContent(
                    modifier = modifier,
                    isRecording = isRecording, elapsedSeconds = elapsedSeconds,
                    is60FpsGranted = is60FpsGranted, showsPlateBadge = showsPlateBadge,
                    plateDiameterMm = plateDiameterMm, plateBadgeExpanded = plateBadgeExpanded,
                    preferredView = ViewKind.SIDE, profileId = AnalysisProfileId.SQUAT,
                    verdict = FramingVerdict(ok = true, issues = emptyList(), view = ViewKind.SIDE),
                    hasPermission = hasPermission, hasCamera = hasCamera,
                    isPermanentlyDenied = isPermanentlyDenied,
                    canRecord = hasCamera && hasPermission,
                    isProcessing = isProcessing, captureError = captureError,
                    onOpenFicha = {}, onOpenAlbum = {}, onOpenGallery = {},
                )
            },
        )
    }
}
