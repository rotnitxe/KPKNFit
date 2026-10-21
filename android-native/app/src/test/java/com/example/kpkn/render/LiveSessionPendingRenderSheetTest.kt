package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.screens.workout.components.RoadmapPendingAction
import com.example.kpkn.screens.workout.components.WorkoutRoadmapBar
import com.example.kpkn.ui.theme.KPKNTheme
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

private const val PENDIENTES_TAG = "lv4-pendientes"

private fun ejercicio(id: String, nombre: String) = Exercise(
    id = id,
    name = nombre,
    sets = listOf(ExerciseSet(id = "$id-1", targetReps = 8, weight = 60.0), ExerciseSet(id = "$id-2", targetReps = 8, weight = 60.0)),
)

/**
 * Hoja de render de lo que queda pendiente al cerrar una capa de la sesión por accidente (LV4, 15): la fila del asa del cockpit con la
 * píldora «Estado previo» (a la izquierda) y «Terminar» (a la derecha), al 100 % y al 130 % de letra, en la pantalla de 360 dp del teléfono.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h700dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveSessionPendingRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
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

    @Test
    fun ambosPendientes100() {
        onlyWhenAsked()
        draw(fontScale = 1f, name = "pendientes_ambos_100", both = true, readiness = true)
    }

    @Test
    fun ambosPendientes130() {
        onlyWhenAsked()
        draw(fontScale = 1.3f, name = "pendientes_ambos_130", both = true, readiness = true)
    }

    @Test
    fun ambosPendientes150() {
        onlyWhenAsked()
        draw(fontScale = 1.5f, name = "pendientes_ambos_150", both = true, readiness = true)
    }

    @Test
    fun estadoPrevio100() {
        onlyWhenAsked()
        draw(fontScale = 1f, name = "pendientes_estado_100", both = false, readiness = true)
    }

    @Test
    fun terminar130() {
        onlyWhenAsked()
        draw(fontScale = 1.3f, name = "pendientes_terminar_130", both = false, readiness = false)
    }

    @Test
    fun sinPendientes100() {
        onlyWhenAsked()
        draw(fontScale = 1f, name = "pendientes_ninguno_100", both = false, readiness = null)
    }

    /** [readiness]: `true` solo «Estado previo», `false` solo «Terminar», `null` ninguno; con [both] salen las dos. */
    private fun draw(fontScale: Float, name: String, both: Boolean, readiness: Boolean?) {
        val pending = buildList {
            if (both || readiness == true) add(RoadmapPendingAction("Estado previo") {})
            if (both || readiness == false) add(RoadmapPendingAction("Terminar") {})
        }
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        // El cockpit mide 360 dp menos los 12 dp de relleno a cada lado que le pone la pantalla de la sesión.
                        Box(
                            Modifier
                                .width(360.dp)
                                .background(Color.Black)
                                .testTag(PENDIENTES_TAG)
                                .padding(horizontal = 12.dp),
                        ) {
                            WorkoutRoadmapBar(
                                exercises = listOf(ejercicio("a", "Press banca"), ejercicio("b", "Remo con barra"), ejercicio("c", "Sentadilla")),
                                currentIdx = 0,
                                completedSets = emptyMap(),
                                onSelect = {},
                                pendingActions = pending,
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(PENDIENTES_TAG, name)
    }

    private fun save(tag: String, name: String) {
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
