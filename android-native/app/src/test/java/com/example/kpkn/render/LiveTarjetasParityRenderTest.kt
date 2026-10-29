package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.LiveSessionCardFrame
import com.example.kpkn.screens.workout.LiveSessionLine
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.WorkoutHeaderBar
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.liveLineTestSnapshot
import com.example.kpkn.screens.workout.liveSessionCardHeightDp
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Paridad de Tarjetas con la captura 01 del Refinamiento 02 (`capturas-v2/01-tarjetas-lista.png`): cápsula de la sesión, contexto del
 * ejercicio, tarjeta de serie en su marco fijo y roadmap flotante, con los datos de la captura (Sentadilla, serie 3 / 4, 100 kg × 8).
 * Dibuja las piezas REALES a 360 × 740, 393 × 852 y 412 × 1007 dp, con el texto al 100 % y al 130 %. Escribe PNG y medidas solo con
 * `KPKN_RENDER_DIR`; las medidas del marco y del roadmap se comprueban siempre que corre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class LiveTarjetasParityRenderTest {
    @get:Rule val rule = createComposeRule()
    private var view: View? = null
    private var density = 1f

    private val plannedSet = ExerciseSet("sq3", weight = 100.0, targetReps = 8, targetRIR = 2, intensityMode = IntensityMode.RIR)
    private val squat = Exercise("sq", "Sentadilla Trasera con Barra Alta", sets = List(4) { plannedSet.copy(id = "sq$it") })
    private val rdl = Exercise("rdl", "Peso Muerto Rumano", sets = List(3) { plannedSet.copy(id = "rdl$it", weight = 140.0, targetReps = 5) })
    private val press = Exercise("press", "Prensa de Piernas", sets = List(2) { plannedSet.copy(id = "press$it", weight = 214.0, targetReps = 10) })

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun tarjetas360x740() = scene(360, 740, 1f, "tarjetas-360x740-100", 312, liveSessionCardHeightDp(360, 740).value.toInt())
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun tarjetas360x740Text130() = scene(360, 740, 1.3f, "tarjetas-360x740-130", 312, liveSessionCardHeightDp(360, 740).value.toInt())
    @Test @Config(qualifiers = "w393dp-h852dp-xxhdpi") fun tarjetas393x852() = scene(393, 852, 1f, "tarjetas-393x852-100", 345, liveSessionCardHeightDp(393, 852).value.toInt())
    @Test @Config(qualifiers = "w393dp-h852dp-xxhdpi") fun tarjetas393x852Text130() = scene(393, 852, 1.3f, "tarjetas-393x852-130", 345, liveSessionCardHeightDp(393, 852).value.toInt())
    @Test @Config(qualifiers = "w412dp-h1007dp-xxhdpi") fun tarjetas412x1007() = scene(412, 1007, 1f, "tarjetas-412x1007-100", 364, liveSessionCardHeightDp(412, 1007).value.toInt())
    @Test @Config(qualifiers = "w412dp-h1007dp-xxhdpi") fun tarjetas412x1007Text130() = scene(412, 1007, 1.3f, "tarjetas-412x1007-130", 364, liveSessionCardHeightDp(412, 1007).value.toInt())
    /** El Z Flip (360 × 880): marco de 312 × 430 (pantalla alta: +36 dp) y el hueco sobrante entre la tarjeta y el roadmap (decisión 1). */
    @Test @Config(qualifiers = "w360dp-h880dp-xxhdpi") fun tarjetasFlip360x880() = scene(360, 880, 1f, "tarjetas-360x880-100", 312, liveSessionCardHeightDp(360, 880).value.toInt())

    private fun scene(width: Int, height: Int, fontScale: Float, name: String, frameW: Int, frameH: Int) {
        rule.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.requiredSize(width.dp, height.dp).background(Color.Black).testTag("tarjetas-scene")) {
                            Column(Modifier.fillMaxWidth()) {
                                Spacer(Modifier.height(28.dp))
                                WorkoutHeaderBar(
                                    exerciseName = squat.name,
                                    exerciseChips = listOf("Barra", "Alta"),
                                    sessionName = "Pierna · Semana 1",
                                    groupName = null,
                                    startTimeMs = System.currentTimeMillis() - (38 * 60 + 24) * 1000L,
                                    isComplete = false,
                                    background = null,
                                    sessionTimeRemainingSeconds = null,
                                    onAdjustTimeLimit = {},
                                    completedWorkingSetCount = 2,
                                    totalWorkingSetCount = 9,
                                    totalExerciseCount = 3,
                                    activeMainTags = listOf(WorkoutTag(id = "comp", name = "Competición")),
                                    hasCreatedTags = true,
                                    onToggleVoice = {},
                                    onMoreClick = {},
                                    compactLayout = height < 800,
                                    statusBarInsets = WindowInsets(0, 0, 0, 0),
                                )
                                Spacer(Modifier.height(8.dp))
                                LiveSessionCardFrame {
                                    SetInputCardV2(
                                        squat, 2, plannedSet,
                                        CompletedSet("prev", weight = 100.0, reps = 8, rir = 2), null,
                                        onShowHistory = {}, onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                                        onSetBodyWeight = {}, initialBodyWeight = 80.0,
                                        recordActionHolder = remember { RecordActionHolder() },
                                    )
                                }
                            }
                            LiveSessionLine(
                                listOf(squat, rdl, press), squat.id, liveLineTestSnapshot(4, 2, preparation = true),
                                {}, {}, {}, {}, {}, recordVisible = true, recordEnabled = true,
                                sessionCompletedCount = 2, sessionTotalCount = 9,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 18.dp),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val frame = rule.onNodeWithTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag("live-session-line").fetchSemanticsNode().boundsInRoot
        val header = rule.onNodeWithTag("live-session-header").fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("live-card-actions").fetchSemanticsNode().boundsInRoot
        fun dp(px: Float) = px / density
        val measurements = "viewport=${width}x$height dp; fontScale=$fontScale\n" +
            listOf("header" to header, "card" to frame, "actions" to actions, "dock" to dock).joinToString("\n") { (label, r) ->
                "$label: x=${dp(r.left)}, y=${dp(r.top)}, w=${dp(r.width)}, h=${dp(r.height)} dp"
            }
        println(measurements)
        assertEquals("ancho del marco", frameW.toFloat(), dp(frame.width), 1f)
        assertEquals("alto del marco", frameH.toFloat(), dp(frame.height), 1f)
        assertTrue("las acciones quedan dentro del marco", actions.bottom <= frame.bottom + 1f)
        val detail = rule.onNodeWithTag("live-card-detail").fetchSemanticsNode().boundsInRoot
        assertTrue("«Parciales, ayuda y ROM» no queda bajo las acciones: ${dp(detail.bottom)} / ${dp(actions.top)} dp", detail.bottom <= actions.top + 1f)
        assertTrue("la cabecera no pisa la tarjeta", header.bottom <= frame.top + 1f)
        assertTrue("el roadmap no tapa la tarjeta: tarjeta ${dp(frame.bottom)} dp, roadmap ${dp(dock.top)} dp", frame.bottom <= dock.top)
        val dir = System.getenv("KPKN_RENDER_DIR") ?: return
        val folder = File(dir).apply { mkdirs() }
        File(folder, "$name-medidas.txt").writeText(measurements)
        save("tarjetas-scene", File(folder, "$name.png"))
    }

    private fun save(tag: String, file: File) {
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val host = checkNotNull(view)
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(full)) }
        val left = bounds.left.toInt().coerceAtLeast(0)
        val top = bounds.top.toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(full, left, top,
            bounds.width.toInt().coerceAtMost(full.width - left), bounds.height.toInt().coerceAtMost(full.height - top))
        FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
