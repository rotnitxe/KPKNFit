package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.SetupWeighIn
import com.example.kpkn.screens.onboarding.design.WEIGHIN_ADD_TAG
import com.example.kpkn.screens.onboarding.design.WEIGHIN_FAT_TOGGLE_TAG
import com.example.kpkn.screens.onboarding.design.WEIGHIN_MUSCLE_TOGGLE_TAG
import com.example.kpkn.screens.onboarding.design.WEIGHINS_BODY_TAG
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeighInsBody
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * HOJAS DE DIBUJO del paso de pesajes (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 * Cada caso es una prueba propia: 0, 1 y 3 pesajes, y el editor abierto con grasa y músculo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class K5WeighInRenderSheetTest {

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
    private val today = LocalDate.of(2026, 10, 12)

    private val sample = listOf(
        SetupWeighIn(id = "a", dateIso = "2026-10-11", weightKg = 80.4, bodyFatPercent = 19.0, muscleMassPercent = 37.5),
        SetupWeighIn(id = "b", dateIso = "2026-10-08", weightKg = 80.9, bodyFatPercent = 20.5),
        SetupWeighIn(id = "c", dateIso = "2026-10-03", weightKg = 81.6),
    )

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Recorta el nodo con la marca [tag] de la vista completa y lo guarda en `KPKN_RENDER_DIR/<name>.png`. */
    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
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

    @Test
    fun renderWithoutWeighIns() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            WizardWeighInsBody(rows = emptyList(), unit = WizardMassUnit.KG, currentKg = 80.0, today = today, onSave = {}, onDelete = {})
        }
        rule.waitForIdle()
        save(WEIGHINS_BODY_TAG, "k5_weighins_0")
    }

    @Test
    fun renderWithOneWeighIn() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            WizardWeighInsBody(rows = sample.take(1), unit = WizardMassUnit.KG, currentKg = 80.0, today = today, onSave = {}, onDelete = {})
        }
        rule.waitForIdle()
        save(WEIGHINS_BODY_TAG, "k5_weighins_1")
    }

    @Test
    fun renderWithThreeWeighIns() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            WizardWeighInsBody(rows = sample, unit = WizardMassUnit.KG, currentKg = 80.0, today = today, onSave = {}, onDelete = {})
        }
        rule.waitForIdle()
        save(WEIGHINS_BODY_TAG, "k5_weighins_3")
    }

    @Test
    fun renderEditorWithFatAndMuscle() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            WizardWeighInsBody(rows = sample, unit = WizardMassUnit.KG, currentKg = 80.0, today = today, onSave = {}, onDelete = {})
        }
        rule.onNodeWithTag(WEIGHIN_ADD_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEIGHIN_FAT_TOGGLE_TAG).performClick()
        rule.onNodeWithTag(WEIGHIN_MUSCLE_TOGGLE_TAG).performClick()
        rule.waitForIdle()
        save(WEIGHINS_BODY_TAG, "k5_weighins_editor")
    }

    /** El mismo editor con la letra al 130 %: para comprobar que los dos porcentajes no se cortan. */
    @Test
    fun renderEditorAt130Percent() {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                WizardWeighInsBody(rows = sample, unit = WizardMassUnit.KG, currentKg = 80.0, today = today, onSave = {}, onDelete = {})
            }
        }
        rule.onNodeWithTag(WEIGHIN_ADD_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEIGHIN_FAT_TOGGLE_TAG).performClick()
        rule.onNodeWithTag(WEIGHIN_MUSCLE_TOGGLE_TAG).performClick()
        rule.waitForIdle()
        save(WEIGHINS_BODY_TAG, "k5_weighins_editor_130")
    }
}
