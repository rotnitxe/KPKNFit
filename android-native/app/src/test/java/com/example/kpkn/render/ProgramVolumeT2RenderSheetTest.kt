package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.design.*
import com.example.kpkn.ui.design.KpknSurface
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Comprueba unidades iguales en plan y «Real», porciones por rol y nota sin fecha al 100 % y al 130 %. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1500dp-xxhdpi", application = Application::class)
class ProgramVolumeT2RenderSheetTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("Se dibuja solo cuando se pide", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule val rule = createComposeRule()
    private var fontScale by mutableStateOf(1f)
    private var host: View? = null

    @Test
    fun renderVolumeContract() {
        val muscle = ProgramVolumeMuscle("Deltoides", 6.0, ProgramVolumeZones(4, 12, 18), 4.0, false, false, 18.0)
        val scope = ProgramVolumeScope(
            "Programa completo", "Promedio semanal de 3 semanas. 2026-10-05 – 2026-10-25.",
            listOf("Programa", "Semana"), 0, false, false,
        )
        val breakdown = ProgramVolumeBreakdown(
            listOf(ProgramVolumeLine("Press de hombros", 4.0), ProgramVolumeLine("Elevación lateral", 2.0)),
            listOf(
                ProgramVolumeSubMuscle("Deltoides Anterior", 4.0, listOf(ProgramVolumeLine("Press de hombros", 4.0)), MuscleRole.PRIMARY),
                ProgramVolumeSubMuscle("Deltoides Lateral", 2.0, listOf(ProgramVolumeLine("Elevación lateral", 2.0)), MuscleRole.PRIMARY),
                ProgramVolumeSubMuscle("Deltoides Lateral", 2.0, listOf(ProgramVolumeLine("Press de hombros", 2.0)), MuscleRole.SECONDARY),
                ProgramVolumeSubMuscle("Deltoides Posterior", 0.0, emptyList()),
            ),
        )
        rule.setContent {
            host = LocalView.current
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Box(Modifier.width(360.dp).height(1500.dp).background(KpknSurface.canvas).padding(16.dp)) {
                    ProgramVolumeModule(
                        muscles = listOf(muscle), scope = scope,
                        onSelectKind = {}, onPreviousScope = {}, onNextScope = {},
                        expandedMuscle = muscle.name, onToggleMuscle = {}, breakdown = breakdown, onCalibrate = {},
                        controls = { ProgramVolumeCountControls(2, {}, true, {}) },
                        unassignedCount = 2,
                    )
                }
            }
        }
        val output = File(System.getenv("KPKN_RENDER_DIR")!!).also { it.mkdirs() }
        for ((scale, name) in listOf(1f to "t2-s100", 1.3f to "t2-s130")) {
            rule.runOnUiThread { fontScale = scale }
            rule.waitForIdle()
            rule.onNodeWithContentDescription("6 series/sem", useUnmergedTree = true).assertExists()
            for (text in listOf("series/sem", "Real 4 series/sem", "Deltoides Anterior · Directo", "Deltoides Lateral · Secundario")) {
                val layouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithText(text, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                assertFalse("sin recortes al $scale: $text", layouts.single().hasVisualOverflow)
            }
            val view = host!!
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { view.draw(Canvas(bitmap)) }
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
