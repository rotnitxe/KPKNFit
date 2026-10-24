package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.progress.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-mdpi", application = Application::class)
class ExerciseProgressRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() { assumeTrue(System.getenv("KPKN_RENDER_DIR") != null) }
    }
    @get:Rule val rule = createComposeRule()
    private var host: View? = null
    private fun render(name: String, scale: Float, content: @Composable () -> Unit) {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            KPKNTheme {
                host = LocalView.current
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides true) {
                    Box(Modifier.width(360.dp).height(2300.dp).background(KpknSurface.canvas).testTag("exercise-progress-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("exercise-progress-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "exercise_progress_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }
    @Test fun strengthAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("strength", 1f) { ExerciseProgressScreen(ExerciseProgressSamples.state("strength"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun strengthAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("strength", 1.3f) { ExerciseProgressScreen(ExerciseProgressSamples.state("strength"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun bodyweightAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("bodyweight", 1f) { ExerciseProgressScreen(ExerciseProgressSamples.state("bodyweight"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun bodyweightAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("bodyweight", 1.3f) { ExerciseProgressScreen(ExerciseProgressSamples.state("bodyweight"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun assistedAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("assisted", 1f) { ExerciseProgressScreen(ExerciseProgressSamples.state("assisted"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun assistedAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("assisted", 1.3f) { ExerciseProgressScreen(ExerciseProgressSamples.state("assisted"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun timedAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("timed", 1f) { ExerciseProgressScreen(ExerciseProgressSamples.state("timed"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun timedAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("timed", 1.3f) { ExerciseProgressScreen(ExerciseProgressSamples.state("timed"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun sparseAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("sparse", 1f) { ExerciseProgressScreen(ExerciseProgressSamples.state("sparse"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun sparseAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("sparse", 1.3f) { ExerciseProgressScreen(ExerciseProgressSamples.state("sparse"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun emptyAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("empty", 1f) { ExerciseProgressScreen(ExerciseProgressSamples.state("empty"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun emptyAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("empty", 1.3f) { ExerciseProgressScreen(ExerciseProgressSamples.state("empty"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun brokenlineageAt100() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("broken_lineage", 1f) { ExerciseProgressScreen(ExerciseProgressSamples.state("broken_lineage"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
    @Test fun brokenlineageAt130() {
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        render("broken_lineage", 1.3f) { ExerciseProgressScreen(ExerciseProgressSamples.state("broken_lineage"), {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
    }
}
