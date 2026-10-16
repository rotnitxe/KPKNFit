package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.RestGameEngine
import com.example.kpkn.screens.workout.RestHeadline
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.screens.workout.RestOverrun
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.components.LocalRestClockFrozen
import com.example.kpkn.screens.workout.components.RestLiveCard
import com.example.kpkn.screens.workout.components.RestMiniChip
import com.example.kpkn.screens.workout.components.RestOverrunCard
import com.example.kpkn.screens.workout.components.RestOverrunOverlay
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
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
import kotlin.random.Random

private const val SHEET_TAG = "lv6-hoja"
private val ACCENT = Color(0xFFF49A6E)

/** Instante (s) en que se congelan el destello y el corredor para que el PNG sea siempre el mismo. */
private const val FROZEN_SECONDS = 1.7f

/**
 * HOJA DE RENDER del descanso con los RINGS (LV6). Sin `KPKN_RENDER_DIR` se salta. Dibuja a 360 dp, con la letra al 100 % y al 130 %,
 * las piezas REALES con datos de ejemplo: las tres formas del reloj (cada una en dos momentos del llenado), la tarjeta, el chip del
 * descanso minimizado, el minijuego (dos fotogramas, en pantalla completa y en la tarjeta) y «Descanso cumplido».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class LiveRestRenderSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private val nbsp = "\u00A0"

    private val restState = WorkoutRestModalState(
        exerciseId = "e1",
        exerciseName = "Sentadilla trasera con barra alta",
        kind = RestTimerKind.STANDARD,
        plannedSeconds = 195,
        suggestedSeconds = 249,
        activeSeconds = 249,
    )
    private val lastSet = CompletedSet(id = "last", weight = 95.0, reps = 6)
    private val suggestion = PendingRestSuggestion(
        plannedSeconds = 195,
        adaptiveSeconds = 249,
        exerciseName = "Sentadilla trasera con barra alta",
        exerciseId = "e1",
        lastSet = lastSet,
        advancedFeedback = null,
    )
    private val nextSet = RestNextSetSummary(
        setNumber = 2,
        setTotal = 4,
        loadKg = 95.0,
        repsText = "4–6",
        intensityText = "RIR 2",
    )
    private val headline = RestHeadline("Sentadilla trasera con barra alta", "Serie 1 de 4 hecha")
    private val relator = "95 kg × 6 a RIR 2: lo previsto. Respira, la serie 2 te espera."
    private val overrun = RestOverrun(endedAtMs = 1_000L, totalSeconds = 249, kind = RestTimerKind.STANDARD)

    /** Un jugador que salta a tiempo, para tener fotogramas del juego con mancuernas y saltos. */
    private fun playedEngine(steps: Int = 0, airborneWithScore: Int? = null): RestGameEngine {
        val engine = RestGameEngine(initialBest = 12, random = Random(4)).also { it.worldWidth = 320f }
        var i = 0
        while (i < 60 * 120) {
            val next = engine.obstacles.filter { it.x + RestGameEngine.OBSTACLE_W > RestGameEngine.RUNNER_X }.minByOrNull { it.x }
            if (next != null) {
                val ahead = next.x - (RestGameEngine.RUNNER_X + RestGameEngine.RUNNER_HIT_RIGHT)
                if (ahead in 0f..(engine.speed * 0.34f)) engine.jump()
            }
            engine.step(1f / 60f)
            i++
            if (airborneWithScore != null) {
                if (engine.score >= airborneWithScore && !engine.onGround && engine.height > 40f) break
            } else if (i >= steps) {
                break
            }
        }
        return engine
    }

    // ── pantalla completa ───────────────────────────────────────────────────────────────────────────────────────────

    private fun full(
        name: String,
        shape: RestClockShape,
        remaining: Int,
        fontScale: Float,
        adaptiveActive: Boolean = false,
        withSuggestion: Boolean = true,
    ) {
        onlyWhenAsked()
        draw(name, fontScale, 360.dp, 880.dp) {
            RestTimerOverlay(
                state = restState,
                remainingSeconds = remaining,
                hazeState = HazeState(),
                pendingRestSuggestion = suggestion.takeIf { withSuggestion },
                lastCompletedSet = lastSet,
                isAdaptiveActive = adaptiveActive,
                sessionAccentColor = ACCENT,
                onDecrease = {},
                onIncrease = {},
                onSkip = {},
                skipExerciseLabel = "Saltar series restantes de Sentadilla trasera con barra alta",
                onSkipExercise = {},
                onUseAdaptive = {},
                onMinimize = {},
                relatorText = relator,
                relatorPhaseKey = "lv6",
                shape = shape,
                headline = headline,
                nextSet = nextSet,
            )
        }
    }

    @Test
    fun full_torre_a_100() = full("full_torre_a_100", RestClockShape.TOWER, remaining = 220, fontScale = 1f)

    @Test
    fun full_torre_b_100() = full("full_torre_b_100", RestClockShape.TOWER, remaining = 55, fontScale = 1f, adaptiveActive = true, withSuggestion = false)

    @Test
    fun full_pista_a_100() = full("full_pista_a_100", RestClockShape.TRACK, remaining = 175, fontScale = 1f)

    @Test
    fun full_pista_b_100() = full("full_pista_b_100", RestClockShape.TRACK, remaining = 50, fontScale = 1f)

    @Test
    fun full_espiral_a_100() = full("full_espiral_a_100", RestClockShape.SPIRAL, remaining = 175, fontScale = 1f)

    @Test
    fun full_espiral_b_100() = full("full_espiral_b_100", RestClockShape.SPIRAL, remaining = 50, fontScale = 1f)

    @Test
    fun full_torre_130() = full("full_torre_130", RestClockShape.TOWER, remaining = 161, fontScale = 1.3f)

    @Test
    fun full_pista_130() = full("full_pista_130", RestClockShape.TRACK, remaining = 161, fontScale = 1.3f)

    @Test
    fun full_espiral_130() = full("full_espiral_130", RestClockShape.SPIRAL, remaining = 161, fontScale = 1.3f)

    // ── tarjeta ─────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun card(
        name: String,
        shape: RestClockShape,
        remaining: Int,
        fontScale: Float,
        playing: Boolean = false,
        engine: RestGameEngine? = null,
        withExtras: Boolean = true,
    ) {
        onlyWhenAsked()
        // Altura de la tarjeta de la serie medida en LR1a (388 dp al 100 %, 418 dp al 130 %): el paginador la usa para el descanso.
        val cardHeight = if (fontScale > 1f) 418.dp else 388.dp
        draw(name, fontScale, 360.dp, cardHeight + 32.dp) {
            Box(Modifier.fillMaxSize().padding(vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.width(272.dp).height(cardHeight)) {
                    RestLiveCard(
                        remainingSeconds = remaining,
                        totalSeconds = 249,
                        sessionAccentColor = ACCENT,
                        restState = restState,
                        pendingRestSuggestion = suggestion.takeIf { withExtras },
                        lastSetOutcome = null,
                        lastCompletedSet = lastSet,
                        onDecrease = {},
                        onIncrease = {},
                        onSkip = {},
                        onUseAdaptive = {},
                        onExpand = {},
                        skipExerciseLabel = "Saltar series restantes de Sentadilla trasera con barra alta".takeIf { withExtras },
                        onSkipExercise = if (withExtras) ({}) else null,
                        shape = shape,
                        nextSet = nextSet,
                        clockActive = false,
                        initiallyPlaying = playing,
                        gameEngine = engine,
                    )
                }
            }
        }
    }

    @Test
    fun tarjeta_torre_100() = card("tarjeta_torre_100", RestClockShape.TOWER, remaining = 200, fontScale = 1f)

    @Test
    fun tarjeta_pista_100() = card("tarjeta_pista_100", RestClockShape.TRACK, remaining = 125, fontScale = 1f)

    @Test
    fun tarjeta_espiral_100() = card("tarjeta_espiral_100", RestClockShape.SPIRAL, remaining = 60, fontScale = 1f)

    @Test
    fun tarjeta_torre_130() = card("tarjeta_torre_130", RestClockShape.TOWER, remaining = 161, fontScale = 1.3f)

    @Test
    fun tarjeta_espiral_130() = card("tarjeta_espiral_130", RestClockShape.SPIRAL, remaining = 161, fontScale = 1.3f)

    @Test
    fun tarjeta_sin_extras_100() = card("tarjeta_sin_extras_100", RestClockShape.TOWER, remaining = 161, fontScale = 1f, withExtras = false)

    // ── descanso minimizado (F5) ────────────────────────────────────────────────────────────────────────────────────

    private fun minimizado(name: String, fontScale: Float) {
        onlyWhenAsked()
        draw(name, fontScale, 360.dp, 360.dp) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(128.dp)) {
                    Text(
                        text = "Sentadilla Trasera con Barra Alta",
                        style = KpknType.title,
                        color = WizardColors.text,
                        modifier = Modifier.padding(start = 20.dp, top = 40.dp),
                    )
                }
                // El chip va en el sitio de la banda del relator: misma altura, sin mover la tarjeta.
                Box(
                    Modifier.fillMaxWidth().height(WorkoutUiTokens.liveRelatorBandHeight(LocalDensity.current)),
                    contentAlignment = Alignment.Center,
                ) {
                    RestMiniChip(remainingSeconds = 161, totalSeconds = 249, onOpen = {}, onAddTime = {})
                }
                Box(
                    Modifier
                        .padding(horizontal = 44.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color(0xFF111111), RoundedCornerShape(24.dp)),
                )
            }
        }
    }

    @Test
    fun minimizado_100() = minimizado("minimizado_100", 1f)

    @Test
    fun minimizado_130() = minimizado("minimizado_130", 1.3f)

    // ── minijuego (F8) ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun juego(name: String, fontScale: Float, engine: RestGameEngine) {
        onlyWhenAsked()
        draw(name, fontScale, 360.dp, 880.dp) {
            RestTimerOverlay(
                state = restState,
                remainingSeconds = 161,
                hazeState = HazeState(),
                pendingRestSuggestion = suggestion,
                lastCompletedSet = lastSet,
                sessionAccentColor = ACCENT,
                onDecrease = {},
                onIncrease = {},
                onSkip = {},
                onMinimize = {},
                shape = RestClockShape.TOWER,
                headline = headline,
                nextSet = nextSet,
                clockActive = false,
                initiallyPlaying = true,
                gameEngine = engine,
            )
        }
    }

    @Test
    fun juego_a_100() = juego("juego_a_100", 1f, playedEngine(steps = 200))

    @Test
    fun juego_b_100() = juego("juego_b_100", 1f, playedEngine(airborneWithScore = 3))

    @Test
    fun juego_a_130() = juego("juego_a_130", 1.3f, playedEngine(steps = 200))

    @Test
    fun juego_tarjeta_a_100() =
        card("juego_tarjeta_a_100", RestClockShape.TOWER, remaining = 161, fontScale = 1f, playing = true, engine = playedEngine(steps = 200))

    @Test
    fun juego_tarjeta_b_100() =
        card("juego_tarjeta_b_100", RestClockShape.TOWER, remaining = 161, fontScale = 1f, playing = true, engine = playedEngine(airborneWithScore = 3))

    @Test
    fun juego_tarjeta_a_130() =
        card("juego_tarjeta_a_130", RestClockShape.TOWER, remaining = 161, fontScale = 1.3f, playing = true, engine = playedEngine(steps = 200))

    // ── descanso cumplido (F6) ──────────────────────────────────────────────────────────────────────────────────────

    private fun cumplido(name: String, shape: RestClockShape, fontScale: Float) {
        onlyWhenAsked()
        draw(name, fontScale, 360.dp, 880.dp) {
            RestOverrunOverlay(
                overrun = overrun,
                hazeState = HazeState(),
                shape = shape,
                headline = headline,
                nextSet = nextSet,
                onStart = {},
                onExtend = {},
                onMinimize = {},
                clockActive = false,
                extraSecondsOverride = 24,
            )
        }
    }

    @Test
    fun cumplido_torre_100() = cumplido("cumplido_torre_100", RestClockShape.TOWER, 1f)

    @Test
    fun cumplido_pista_100() = cumplido("cumplido_pista_100", RestClockShape.TRACK, 1f)

    @Test
    fun cumplido_espiral_130() = cumplido("cumplido_espiral_130", RestClockShape.SPIRAL, 1.3f)

    private fun cumplidoTarjeta(name: String, shape: RestClockShape, fontScale: Float) {
        onlyWhenAsked()
        val cardHeight = if (fontScale > 1f) 418.dp else 388.dp
        draw(name, fontScale, 360.dp, cardHeight + 32.dp) {
            Box(Modifier.fillMaxSize().padding(vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.width(272.dp).height(cardHeight)) {
                    RestOverrunCard(
                        overrun = overrun,
                        shape = shape,
                        nextSet = nextSet,
                        onStart = {},
                        onExtend = {},
                        onExpand = {},
                        clockActive = false,
                        extraSecondsOverride = 24,
                    )
                }
            }
        }
    }

    @Test
    fun cumplido_tarjeta_100() = cumplidoTarjeta("cumplido_tarjeta_100", RestClockShape.TOWER, 1f)

    @Test
    fun cumplido_tarjeta_130() = cumplidoTarjeta("cumplido_tarjeta_130", RestClockShape.SPIRAL, 1.3f)

    // ── dibujo ──────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun draw(name: String, fontScale: Float, width: Dp, height: Dp, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(3f, fontScale),
                LocalRestClockFrozen provides FROZEN_SECONDS,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .size(width, height)
                                .background(Color.Black)
                                .testTag(SHEET_TAG),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(SHEET_TAG, name)
    }

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
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
