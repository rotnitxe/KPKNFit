package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.components.VolumeCalibrationSheet
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCalibrationInvite
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCountControls
import com.example.kpkn.screens.programdetail.design.ProgramVolumeInactive
import com.example.kpkn.screens.programdetail.design.ProgramVolumeModule
import com.example.kpkn.screens.programdetail.design.ProgramVolumeNoSessions
import com.example.kpkn.screens.programdetail.design.ProgramVolumeSummaryModule
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

/**
 * HOJA DE DIBUJO de la pestaña Volumen (P4): la semana con diez músculos en todos los estados, sin calibrar, los dos vacíos, una fila
 * desplegada, el resumen con molestias y la hoja «Calibrar volumen». No comprueba nada: escribe PNG. Pinta a 360 dp, con la letra al 100 %
 * y al 130 %, sin movimiento. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class ProgramVolumeRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Los casos de la hoja, en orden. */
    private val cases = listOf(
        "semana10", "sinCalibrar", "inactivo", "sinSesiones", "desplegada", "resumen", "calibrar",
    )

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

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

    /** Alto de la hoja de cada caso: lo bastante para que el contenido entero quepa al 130 %. */
    private fun heightOf(name: String): Int = when (name) {
        "semana10" -> 1700
        "sinCalibrar" -> 1100
        "inactivo", "sinSesiones" -> 420
        "desplegada" -> 1700
        "resumen" -> 1100
        "calibrar" -> 1500
        else -> 800
    }

    /** Columna con el margen lateral de la pestaña (16 dp) y el aire entre módulos (12 dp), como en `VolumeView`. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }

    /** Pinta el caso [name]. */
    @Composable
    private fun Sheet(name: String) {
        val controls: @Composable () -> Unit = {
            ProgramVolumeCountControls(
                indirectMode = 0,
                onIndirectMode = {},
                adjustByIntensity = false,
                onAdjustByIntensity = {},
            )
        }
        when (name) {
            "semana10" -> Frame {
                ProgramVolumeModule(
                    muscles = ProgramDetailSamples.volumeWeek,
                    scope = ProgramDetailSamples.volumeScopeWeek,
                    onSelectKind = {},
                    onPreviousScope = {},
                    onNextScope = {},
                    expandedMuscle = null,
                    onToggleMuscle = {},
                    breakdown = null,
                    onCalibrate = {},
                    controls = controls,
                )
            }
            "sinCalibrar" -> Frame {
                ProgramVolumeCalibrationInvite(onCalibrate = {})
                ProgramVolumeModule(
                    muscles = ProgramDetailSamples.volumeUncalibrated,
                    scope = ProgramDetailSamples.volumeScopeWeek,
                    onSelectKind = {},
                    onPreviousScope = {},
                    onNextScope = {},
                    expandedMuscle = null,
                    onToggleMuscle = {},
                    breakdown = null,
                    onCalibrate = {},
                    controls = controls,
                )
            }
            "inactivo" -> Frame { ProgramVolumeInactive(onActivate = {}) }
            "sinSesiones" -> Frame { ProgramVolumeNoSessions(onAddSession = {}) }
            "desplegada" -> Frame {
                ProgramVolumeModule(
                    muscles = ProgramDetailSamples.volumeWeek,
                    scope = ProgramDetailSamples.volumeScopeWeek,
                    onSelectKind = {},
                    onPreviousScope = {},
                    onNextScope = {},
                    expandedMuscle = "Pectorales",
                    onToggleMuscle = {},
                    breakdown = ProgramDetailSamples.volumeBreakdownPectorales,
                    onCalibrate = {},
                    controls = controls,
                )
            }
            "resumen" -> Frame {
                ProgramVolumeSummaryModule(
                    totalSets = ProgramDetailSamples.volumeWeek.sumOf { it.sets },
                    activeMuscles = ProgramDetailSamples.volumeWeek.size,
                    topMuscle = "Cuádriceps",
                    split = ProgramDetailSamples.volumeSplit,
                    discomforts = ProgramDetailSamples.volumeDiscomforts,
                    associations = ProgramDetailSamples.volumeAssociations,
                )
            }
            "calibrar" -> Box(Modifier.fillMaxWidth().height(1500.dp)) {
                VolumeCalibrationSheet(
                    currentMode = ProgramMode.HYPERTROPHY,
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
    }

    @Test
    fun renderProgramVolumeSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Box(
                    Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .height(heightOf(cases[current]).dp)
                        .background(Color.Black),
                ) {
                    Sheet(cases[current])
                }
            }
        }
        for ((index, name) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = index
                    fontScale = scale
                }
                rule.waitForIdle()
                save("sheet", "${name}_$suffix")
            }
        }
    }
}
