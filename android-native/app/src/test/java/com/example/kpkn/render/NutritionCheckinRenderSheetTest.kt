package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.nutrition.checkin.*
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
class NutritionCheckinRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() { assumeTrue(System.getenv("KPKN_RENDER_DIR") != null) }
    }
    @get:Rule val rule = createComposeRule()
    private var host: View? = null
    private fun render(name: String, scale: Float) {
        rule.setContent { KPKNTheme {
            host = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides true) {
                val viewport = name == "apply_error_viewport"
                val scroll = rememberScrollState()
                Column(Modifier.testTag("checkin-render").width(360.dp)
                    .then(if (viewport) Modifier.height(600.dp) else Modifier)
                    .background(KpknSurface.canvas).padding(16.dp)
                    .then(if (viewport) Modifier.verticalScroll(scroll) else Modifier)) {
                    if (name == "card") NutritionCheckinCard({})
                    else {
                        val state = NutritionCheckinSamples.state(when (name) {
                            "paused_proposal" -> "paused"
                            "apply_error_viewport" -> "apply_error"
                            else -> name
                        })
                        NutritionCheckinContent(if (name == "paused_proposal") state.copy(stepIndex = state.steps.lastIndex) else state,
                            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        } }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("checkin-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val directory = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(directory, "checkin_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }
    @Test fun card100() = render("card", 1f)
    @Test fun card130() = render("card", 1.3f)
    @Test fun introduction100() = render("ready", 1f)
    @Test fun introduction130() = render("ready", 1.3f)
    @Test fun loggingBreak100() = render("paused", 1f)
    @Test fun loggingBreak130() = render("paused", 1.3f)
    @Test fun fasting100() = render("fasting", 1f)
    @Test fun fasting130() = render("fasting", 1.3f)
    @Test fun partial100() = render("partial_days", 1f)
    @Test fun partial130() = render("partial_days", 1.3f)
    @Test fun weigh100() = render("weigh_in", 1f)
    @Test fun weigh130() = render("weigh_in", 1.3f)
    @Test fun proposal100() = render("proposal_change", 1f)
    @Test fun proposal130() = render("proposal_change", 1.3f)
    @Test fun nochange100() = render("proposal_nochange", 1f)
    @Test fun nochange130() = render("proposal_nochange", 1.3f)
    @Test fun pausedProposal100() = render("paused_proposal", 1f)
    @Test fun pausedProposal130() = render("paused_proposal", 1.3f)
    @Test fun error100() = render("apply_error", 1f)
    @Test fun error130() = render("apply_error", 1.3f)
    @Test fun errorViewport100() = render("apply_error_viewport", 1f)
    @Test fun errorViewport130() = render("apply_error_viewport", 1.3f)
}
