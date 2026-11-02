package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.CardioLiveCard
import com.example.kpkn.screens.workout.WorkoutHeaderBar
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Producción completa (header + máquina de cardio), no una réplica del HTML escrita para la prueba. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = Application::class)
class CardioPulidoRenderSheetTest {
    @get:Rule val rule = createComposeRule()
    private var host: View? = null
    private val nor = CardioDetails(type = CardioType.TREADMILL, intervalBlocks = buildList {
        add(CardioIntervalBlock(id = "warm", type = CardioBlockType.WARMUP, durationSeconds = 600, speedKmh = 7.5, targetHrPercent = 65))
        for (n in 1..4) {
            add(CardioIntervalBlock(id = "work$n", type = CardioBlockType.WORK, durationSeconds = 240, speedKmh = 12.0, inclinePercent = 1.0, targetHrPercent = 85))
            if (n < 4) add(CardioIntervalBlock(id = "recover$n", type = CardioBlockType.RECOVER, durationSeconds = 180, speedKmh = 7.0, targetHrPercent = 65))
        }
        add(CardioIntervalBlock(id = "cool", type = CardioBlockType.COOLDOWN, durationSeconds = 300, speedKmh = 6.0, targetHrPercent = 55))
    }, intervalRounds = 1)

    // Datos alineados con `cv-trabajo` del lienzo: 24:22 transcurridos de 40:00, 160 lpm, 61 kg y 2:16 en Z4.
    @Test fun work() = draw("cv-trabajo", nor, elapsed = 1462, hr = 160, inZoneSeconds = 136)
    @Test fun aboveZone() = draw("cv-sobre-zona", nor, elapsed = 1462, hr = 176)
    @Test fun noPulse() = draw("cv-continuo", CardioDetails(type = CardioType.STAIR_CLIMBER, targetDurationSeconds = 1200), elapsed = 19)
    @Test fun ready() = draw("cv-listo", nor, elapsed = 0, status = CardioExecutionStatus.READY)
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun compact() = draw("cv-trabajo-360x740", nor, elapsed = 1462, hr = 160, height = 740, inZoneSeconds = 136)
    @Test fun font130() = draw("cv-trabajo-fs1.3", nor, elapsed = 1462, hr = 160, font = 1.3f, inZoneSeconds = 136)

    // Captura a densidad 1× (360×880 px): la misma rejilla de píxeles que el lienzo, sin reescalado de densidad.
    @Test @Config(qualifiers = "w360dp-h880dp-mdpi")
    fun workMdpi() = draw("cv-trabajo-mdpi", nor, elapsed = 1462, hr = 160, inZoneSeconds = 136, density = 1f)

    private fun draw(name: String, details: CardioDetails, elapsed: Int, hr: Int? = null, height: Int = 880, font: Float = 1f,
        status: CardioExecutionStatus = CardioExecutionStatus.RUNNING, inZoneSeconds: Int = 0, density: Float = 3f) {
        assumeTrue("requiere KPKN_RENDER_DIR", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            host = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(density, font), LocalWizardReducedMotion provides true) {
                KPKNTheme { MaterialTheme {
                    Column(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("cv-render")) {
                        WorkoutHeaderBar(exerciseName = if (details.hasIntervals()) "Cinta · Noruego 4×4" else "Escaladora",
                            sessionName = "Pierna · Semana 1", groupName = null, startTimeMs = System.currentTimeMillis() - 2_886_000,
                            isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
                            totalExerciseCount = 4, completedWorkingSetCount = 9, totalWorkingSetCount = 10,
                            // La franja de estado del lienzo mide 30 px; el render deja ese alto antes de la cabecera.
                            statusBarInsets = WindowInsets(top = 30.dp), onToggleVoice = {}, onMoreClick = {}, cardioLayout = true)
                        CardioLiveCard(details, null, Color.White, Modifier.weight(1f).fillMaxWidth(),
                            executionState = CardioTimerState(exerciseId = "cardio", elapsedSeconds = elapsed,
                                totalSeconds = details.effectiveDurationSeconds(), remainingSeconds = details.effectiveDurationSeconds() - elapsed, status = status),
                            liveHeartRateBpm = hr, exerciseName = if (details.hasIntervals()) "Cinta · Noruego 4×4" else "Escaladora",
                            maxHeartRateBpm = 190, bodyWeightKg = 61.0, onRecord = { _, _, _ -> true })
                    }
                } }
            }
        }
        rule.waitForIdle()
        // Los segundos en zona del lienzo (2:16) se consiguen dejando correr el reloj de prueba con el pulso
        // del reloj; no se inyecta ningún contador a mano.
        if (status == CardioExecutionStatus.RUNNING && hr != null && inZoneSeconds > 0) {
            // Avance sin dibujar cada fotograma: solo interesan los tics de 1 s del contador de zona.
            rule.mainClock.advanceTimeBy(inZoneSeconds * 1_000L, ignoreFrameDuration = true)
            rule.waitForIdle()
        }
        repeat(4) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        val bounds = rule.onNodeWithTag("cv-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }
}
