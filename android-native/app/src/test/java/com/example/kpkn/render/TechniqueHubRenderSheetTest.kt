package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.profile.ProfileAlbumsCard
import com.example.kpkn.screens.profile.ProfileTechniqueCard
import com.example.kpkn.screens.profile.ProfileTechniqueUiState
import com.example.kpkn.screens.technique.hub.TechniqueHubSamples
import com.example.kpkn.screens.technique.hub.TechniqueHubScreen
import com.example.kpkn.screens.technique.hub.TechniqueHubUiState
import com.example.kpkn.screens.technique.hub.design.TechniqueSettingsModule
import com.example.kpkn.screens.technique.importer.VideoImportContent
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class TechniqueHubRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun onlyWhenAsked() {
            assumeTrue("La hoja requiere KPKN_RENDER_DIR", !System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        }
    }
    @get:Rule val rule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private var host: View? = null

    /** Fotogramas locales de prueba, con el mismo cargador que los vídeos reales. */
    private val recent by lazy {
        TechniqueHubSamples.recent.mapIndexed { index, video ->
            val file = folder.newFile("frame-$index.png")
            val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(42 + index * 2, 38, 34))
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(242, 238, 230); strokeWidth = 4f
            }
            canvas.drawLine(18f, 45f, 102f, 45f, paint)
            canvas.drawLine(60f, 45f, 60f, 78f, paint)
            canvas.drawLine(60f, 78f, 40f, 104f, paint)
            canvas.drawLine(60f, 78f, 82f, 104f, paint)
            canvas.drawCircle(60f, 31f, 7f, paint)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            video.copy(media = WorkoutMedia(video.analysis.mediaId, WorkoutMediaKind.VIDEO, "", thumbPath = file.absolutePath,
                createdAtMs = video.analysis.recordedAtMs, exerciseName = video.analysis.exerciseName, durationMs = 12_000L))
        }
    }

    private fun render(name: String, scale: Float, padding: Boolean = false, thumbnails: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            host = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides true) {
                Box(Modifier.width(360.dp).background(KpknSurface.canvas).testTag("hub-render").then(if (padding) Modifier.padding(16.dp) else Modifier)) {
                    content()
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("hub-render", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val view = requireNotNull(host)
        if (thumbnails) {
            val centers = (0..2).map { index ->
                rule.onNodeWithTag("technique-recent-recent-$index").fetchSemanticsNode().boundsInRoot.center
            }
            val probe = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                // Coil carga en IO: la captura espera los tres fotogramas, no un tiempo arbitrario.
                rule.waitUntil(20_000L) {
                    // Mismo ciclo de captura que la hoja del descanso: renovar las capas antes de leer píxeles.
                    rule.runOnUiThread { view.invalidate() }
                    rule.mainClock.advanceTimeByFrame()
                    rule.waitForIdle()
                    rule.runOnUiThread { view.draw(android.graphics.Canvas(probe).apply { drawColor(android.graphics.Color.BLACK) }) }
                    centers.all { center ->
                        val pixel = probe.getPixel(center.x.toInt(), center.y.toInt())
                        android.graphics.Color.red(pixel) > 200 && android.graphics.Color.green(pixel) > 200 && android.graphics.Color.blue(pixel) > 200
                    }
                }
            } finally {
                probe.recycle()
            }
            rule.waitForIdle()
        }
        val complete = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(complete).apply { drawColor(android.graphics.Color.BLACK) }) }
        val left = bounds.left.toInt().coerceIn(0, complete.width - 1)
        val top = bounds.top.toInt().coerceIn(0, complete.height - 1)
        val width = bounds.width.toInt().coerceIn(1, complete.width - left)
        val height = bounds.height.toInt().coerceIn(1, complete.height - top)
        val crop = Bitmap.createBitmap(complete, left, top, width, height)
        val row = IntArray(crop.width)
        var useful = crop.height
        for (y in crop.height - 1 downTo 0) {
            crop.getPixels(row, 0, crop.width, 0, y, crop.width, 1)
            if (row.any { it != android.graphics.Color.BLACK }) {
                useful = minOf(crop.height, y + 1 + (24 * view.resources.displayMetrics.density).toInt())
                break
            }
        }
        val final = Bitmap.createBitmap(crop, 0, 0, crop.width, useful)
        val directory = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { final.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Composable private fun hub(state: TechniqueHubUiState) {
        TechniqueHubScreen(state, {}, {}, {}, {}, {}, {}, {}, {})
    }
    @Composable private fun profile(populated: Boolean) {
        KPKNTheme {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileAlbumsCard({})
                ProfileTechniqueCard(
                    if (populated) ProfileTechniqueUiState(recent.take(3), 12, recent[1].analysis.recordedAtMs, recent[1].analysis)
                    else ProfileTechniqueUiState(), {},
                )
            }
        }
    }
    @Composable private fun settings() {
        TechniqueSettingsModule(TechniqueHubSamples.settings, true, {}, {}, {}, {}, {})
    }
    @Composable private fun importer() {
        VideoImportContent(TechniqueHubSamples.importing, TechniqueHubSamples.exercises, {}, {}, {}, {}, {})
    }
    @Test fun hubData100() = render("hub_data_100", 1f, thumbnails = true) { hub(TechniqueHubSamples.populated.copy(recentVideos = recent)) }
    @Test fun hubData130() = render("hub_data_130", 1.3f, thumbnails = true) { hub(TechniqueHubSamples.populated.copy(recentVideos = recent)) }
    @Test fun hubEmpty100() = render("hub_empty_100", 1f) { hub(TechniqueHubSamples.empty) }
    @Test fun hubEmpty130() = render("hub_empty_130", 1.3f) { hub(TechniqueHubSamples.empty) }
    @Test fun profileData100() = render("profile_technique_data_100", 1f, true, thumbnails = true) { profile(true) }
    @Test fun profileData130() = render("profile_technique_data_130", 1.3f, true, thumbnails = true) { profile(true) }
    @Test fun profileEmpty100() = render("profile_technique_empty_100", 1f, true) { profile(false) }
    @Test fun profileEmpty130() = render("profile_technique_empty_130", 1.3f, true) { profile(false) }
    @Test fun settings100() = render("hub_settings_100", 1f, true) { settings() }
    @Test fun settings130() = render("hub_settings_130", 1.3f, true) { settings() }
    @Test fun import100() = render("import_sheet_100", 1f, true) { importer() }
    @Test fun import130() = render("import_sheet_130", 1.3f, true) { importer() }
}
