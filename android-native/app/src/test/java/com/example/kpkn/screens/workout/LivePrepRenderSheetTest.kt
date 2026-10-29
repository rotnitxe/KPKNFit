package com.example.kpkn.screens.workout

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.LocalRestClockFrozen
import com.example.kpkn.screens.workout.components.PrepRestFullscreen
import com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard
import com.example.kpkn.screens.workout.components.WarmupPhaseRow
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * HOJA DE RENDER de la tarjeta de aproximación con kg y del descanso de aproximación minimizado (tarjeta) y expandido (pantalla completa).
 * No comprueba nada: escribe PNG en `KPKN_RENDER_DIR`; sin esa variable se salta. Composables reales, sin animaciones.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LivePrepRenderSheetTest {
    @get:Rule val rule = createComposeRule()
    private var view: View? = null

    private fun row(index: Int, percent: Double, reps: Int, done: Boolean = false, kg: Double? = null, doneReps: Int? = null) =
        WarmupPhaseRow(
            exerciseId = "sent", exerciseBadge = null, index = index,
            warmup = WarmupSetDefinition(id = "w${index + 1}", percentageOfWorkingWeight = percent, targetReps = reps, restBetween = 60),
            suggestedWeightKg = 100.0 * percent, actualWeightKg = kg, isCompleted = done, actualReps = doneReps,
        )

    private val rows = listOf(row(0, .4, 8, done = true, kg = 40.0, doneReps = 8), row(1, .6, 5), row(2, .8, 3))

    @Composable
    private fun BoxScope.card(restSeconds: Int?) {
        Box(Modifier.align(Alignment.Center).width(312.dp)) {
            LiveSessionCardFrame {
                Box(Modifier.fillMaxSize()) {
                    WarmupPhaseLiveCard(
                        rows = rows, sessionAccentColor = Color(0xFFF49A6E), onToggleComplete = { _, _, _ -> }, onSkip = {}, onContinue = {},
                        modifier = Modifier.fillMaxSize(), onAddSet = {},
                        inlineRestRemainingSeconds = restSeconds, inlineRestTotalSeconds = restSeconds?.let { 60 },
                        onSkipInlineRest = {}, isActivePage = true, onWeightDraft = { _, _ -> }, workingWeightKg = 100.0,
                        onCompleteWithReps = { _, _, _ -> }, exerciseName = "Sentadilla trasera",
                        onToggleInlineRestExpanded = {}, onAdjustInlineRest = {}, onToggleInlineRestPause = {},
                    )
                }
            }
        }
    }

    @Composable
    private fun fullscreen() {
        PrepRestFullscreen(
            eyebrow = "PAUSA ENTRE APROXIMACIONES", title = "Sentadilla trasera", remainingSeconds = 42, totalSeconds = 60, isPaused = false,
            onAdjust = {}, onTogglePause = {}, onMinimize = {}, onEnd = {}, lastText = "40 kg × 8",
            nextTitle = "Aproximación A2", nextDose = "60 kg × 5",
        )
    }

    @Test fun warmupKgAt880() = scene("prep-warmup-kg-880") { card(null) }

    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    @Test fun warmupKgAt740() = scene("prep-warmup-kg-740") { card(null) }

    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    @Test fun warmupKgAt740LargeFont() = scene("prep-warmup-kg-740-130", fontScale = 1.3f) { card(null) }

    @Test fun warmupRestMinimizedAt880() = scene("prep-warmup-rest-card-880") { card(42) }

    @Test fun warmupRestExpandedAt880() = scene("prep-warmup-rest-full-880") { fullscreen() }

    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    @Test fun warmupRestExpandedAt740() = scene("prep-warmup-rest-full-740") { fullscreen() }

    private fun scene(name: String, fontScale: Float = 1f, content: @Composable BoxScope.() -> Unit) {
        assumeTrue(!System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        rule.setContent {
            view = LocalView.current
            val height = LocalConfiguration.current.screenHeightDp
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides null, LocalRestClockFrozen provides 1.7f,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density, fontScale),
                LocalLiveSessionCompactLayout provides (height < 800),
            ) {
                KPKNTheme { MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("prep-render-scene")) { content() }
                } }
            }
        }
        rule.waitForIdle()
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        val bounds = rule.onNodeWithTag("prep-render-scene").fetchSemanticsNode().boundsInRoot
        val host = checkNotNull(view)
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(full)) }
        val left = bounds.left.toInt().coerceAtLeast(0); val top = bounds.top.toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(full, left, top, bounds.width.toInt().coerceAtMost(full.width - left),
            bounds.height.toInt().coerceAtMost(full.height - top))
        File(folder, "$name.png").outputStream().use { assertTrue(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        cropped.recycle(); full.recycle()
    }
}
