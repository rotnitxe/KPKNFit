package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.domain.workout.VoiceCommandRecord
import com.example.kpkn.domain.workout.VoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryStatus
import com.example.kpkn.domain.workout.VoiceHistoryValues
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import com.example.kpkn.screens.workout.WorkoutVoiceUiState
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.screens.workout.components.LocalRestClockFrozen
import com.example.kpkn.screens.workout.components.LocalVoiceFlightTargets
import com.example.kpkn.screens.workout.components.VoiceCapsuleContext
import com.example.kpkn.screens.workout.components.VoiceCapsuleLayer
import com.example.kpkn.screens.workout.components.VoiceFlightField
import com.example.kpkn.screens.workout.components.VoiceFlightTargets
import com.example.kpkn.screens.workout.components.VoiceHeardCapture
import com.example.kpkn.screens.workout.components.VoiceLiveFeed
import com.example.kpkn.screens.workout.components.VoiceModeChoices
import com.example.kpkn.screens.workout.components.voiceFieldTargetKey
import com.example.kpkn.screens.workout.components.voiceFlightTarget
import com.example.kpkn.screens.workout.components.voiceHeardCapture
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
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
 * HOJA DE RENDER del Modo Voz como capa viva (iteración 03, pestaña «Modo Voz»): escucha, frase a medio decir, «Entendido» con sus
 * fichas, el vuelo a los campos, el recibo, «Por revisar», la pausa, «KPKN habla», el descanso y la hoja «Modo de voz». No comprueba
 * nada: escribe PNG de 360 × 740 dp en `KPKN_RENDER_DIR` (sin esa variable se salta) para compararlos con las capturas del prototipo.
 * Debajo de la cápsula va una maqueta mínima de los tres campos de la tarjeta, que son los destinos REALES del vuelo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class VoiceLiveLayerRenderSheetTest {
    @get:Rule val rule = createComposeRule()

    private var view: View? = null
    private val now = 10_000_000L
    // La cápsula del prototipo queda a 18 + 126 (dock) + 8 del borde inferior.
    private val bottom = 152.dp
    private val context = VoiceCapsuleContext(exerciseName = "Sentadilla", setLabel = "serie 3", captureModeLabel = "Manos libres", plannedReps = 8)

    private fun entry(status: VoiceHistoryStatus, values: VoiceHistoryValues, transcript: String, at: Long = now) = VoiceHistoryEntry(
        id = "v-$status", sessionKey = "s", exerciseId = "sq", exerciseName = "Sentadilla", setIdx = 2, transcript = transcript,
        values = values, status = status, createdAtEpochMs = at, updatedAtEpochMs = at,
    )

    private val older = entry(VoiceHistoryStatus.REGISTERED, VoiceHistoryValues(weightKg = 100.0, reps = 8, intensityValue = 2.0), "cien kilos ocho rir dos", 1_000L)
        .copy(id = "old", setIdx = 1)
    private val command = VoiceCommandRecord(id = "c1", atEpochMs = 2_000L, transcript = "más quince", value = "+15 s", detail = "descanso")

    private fun heard(): VoiceHeardCapture = voiceHeardCapture(
        WorkoutVoiceUiState.Applied(
            exerciseId = "sq", setIdx = 2,
            interpretation = WorkoutVoiceInterpretation(
                transcript = "cien kilos, ocho, RIR dos", weightKg = 100.0, metricValue = 8, intensityValue = 2.0,
                intensityKind = WorkoutVoiceIntensityKind.RIR,
            ),
            message = "",
        ),
        exerciseName = "Sentadilla", quantityUnit = "reps", atMs = now,
    )

    @Composable
    private fun Layer(
        entries: List<VoiceHistoryEntry> = listOf(older),
        enabled: Boolean = true,
        feed: VoiceLiveFeed = VoiceLiveFeed(listening = true),
        heard: VoiceHeardCapture? = null,
        ctx: VoiceCapsuleContext = context,
        commands: List<VoiceCommandRecord> = listOf(command),
        expanded: Boolean = false,
        guide: String? = null,
    ) {
        VoiceCapsuleLayer(
            entries = entries, voiceEnabled = enabled, context = ctx, expanded = expanded, onExpandedChange = {}, bottomPadding = bottom,
            notice = null, guideSpeech = guide, onStopGuide = {}, onTogglePause = {}, onDisable = {}, onListenExample = {},
            onConfirmCorrection = { _, _, _, _ -> }, onUndo = {}, onDiscard = {}, nowMs = { now }, commands = commands,
            live = mutableStateOf(feed), levelDb = mutableStateOf(-35f), heard = heard,
        )
    }

    @Test fun listen() = scene("voice-listen") { FakeCard(); Layer() }

    @Test fun recordPartial() = scene("voice-record-partial") { FakeCard(); Layer(feed = VoiceLiveFeed("cien kilos, ocho", listening = true)) }

    @Test fun recordUnderstood() = scene("voice-record-understood", animate = true, advanceMs = 470) { FakeCard(); Layer(heard = heard()) }

    @Test fun recordFlight() = scene("voice-record-flight", animate = true, advanceMs = 1_000) { FakeCard(); Layer(heard = heard()) }

    @Test fun recordLanded() = scene("voice-record-landed", animate = true, advanceMs = 1_450) { FakeCard(); Layer(heard = heard()) }

    @Test fun receipt() = scene("voice-receipt") {
        FakeCard()
        Layer(entries = listOf(older, entry(VoiceHistoryStatus.REGISTERED, VoiceHistoryValues(weightKg = 100.0, reps = 8, intensityValue = 2.0),
            "cien kilos, ocho, RIR dos")))
    }

    @Test fun review() = scene("voice-review") {
        FakeCard()
        Layer(entries = listOf(older, entry(VoiceHistoryStatus.REVIEW, VoiceHistoryValues(weightKg = 100.0), "cien kilos, ocho… o seis repeticiones")))
    }

    @Test fun paused() = scene("voice-paused") { FakeCard(); Layer(enabled = false, feed = VoiceLiveFeed()) }

    @Test fun speak() = scene("voice-speak") { FakeCard(); Layer(feed = VoiceLiveFeed(spokenText = "Aléjate: no se ven los pies")) }

    @Test fun restPhrase() = scene("voice-rest-phrase") {
        Layer(ctx = context.copy(resting = true, restRemainingSeconds = 92), feed = VoiceLiveFeed("más quince", listening = true))
    }

    @Test fun restReceipt() = scene("voice-rest-receipt") {
        Layer(ctx = context.copy(resting = true, restRemainingSeconds = 107), commands = listOf(command.copy(id = "c2", atEpochMs = now)))
    }

    @Test fun records() = scene("voice-records") {
        Layer(entries = listOf(older, entry(VoiceHistoryStatus.REVIEW, VoiceHistoryValues(weightKg = 100.0, reps = 6), "cien kilos, ocho… o seis repeticiones", 3_000L)),
            expanded = true)
    }

    @Test fun modes() = scene("voice-modes", overlay = true) {
        LiveSheet(title = "Modo de voz", onDismiss = {}, footer = null) {
            LiveSheetSubtitle("Elige cómo entrenar con voz.")
            VoiceModeChoices(VoiceCaptureMode.HANDS_FREE) {}
        }
    }

    /** Maqueta de los tres campos de la tarjeta (carga, repeticiones, esfuerzo) con sus destinos reales del vuelo. */
    @Composable
    private fun BoxScope.FakeCard() {
        val label = TextStyle(fontFamily = WizardFonts.body, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 150.dp).fillMaxWidth().padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color(0xFF111111)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Text("CARGA", style = label, color = Color(0xFFB9B5AE))
            Row(Modifier.padding(top = 6.dp).height(56.dp).voiceFlightTarget(voiceFieldTargetKey("sq", 2, VoiceFlightField.LOAD)),
                verticalAlignment = Alignment.Bottom) {
                Text("100", style = TextStyle(fontFamily = WizardFonts.display, fontSize = 47.sp, fontWeight = FontWeight.ExtraBold), color = Color(0xFFF2EEE6))
                Text(" kg", style = TextStyle(fontSize = 11.sp), color = Color(0xFF92908A))
            }
            Text("REPETICIONES", Modifier.padding(top = 16.dp), style = label, color = Color(0xFFB9B5AE))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).voiceFlightTarget(voiceFieldTargetKey("sq", 2, VoiceFlightField.QUANTITY)),
                horizontalArrangement = Arrangement.SpaceBetween) {
                (6..10).forEach { n ->
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (n == 8) Color(0xFFF2EEE6) else Color(0x04FFFFFF)),
                        contentAlignment = Alignment.Center) {
                        Text("$n", style = TextStyle(fontFamily = WizardFonts.display, fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            color = if (n == 8) Color(0xFF111111) else Color(0xFFB9B5AE))
                    }
                }
            }
            Text("ESFUERZO", Modifier.padding(top = 16.dp), style = label, color = Color(0xFFB9B5AE))
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(30.dp).voiceFlightTarget(voiceFieldTargetKey("sq", 2, VoiceFlightField.EFFORT)),
                contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x22FFFFFF)))
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFF2EEE6)))
            }
        }
    }

    private fun scene(name: String, animate: Boolean = false, advanceMs: Long = 0, overlay: Boolean = false, content: @Composable BoxScope.() -> Unit) {
        assumeTrue(!System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        if (animate) rule.mainClock.autoAdvance = false
        val targets = VoiceFlightTargets()
        rule.setContent {
            view = LocalView.current
            val host = if (overlay) rememberKpknOverlayHostController() else null
            CompositionLocalProvider(
                LocalWizardReducedMotion provides !animate, LocalKpknOverlayHost provides host, LocalRestClockFrozen provides 1.7f,
                LocalVoiceFlightTargets provides targets,
            ) {
                KPKNTheme { MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, 740.dp).background(Color.Black).testTag("voice-scene")) {
                        content()
                        if (host != null) KpknOverlayHostContent(host)
                    }
                } }
            }
        }
        if (animate) {
            // Paso a paso para que los efectos (fichas, vuelo, brillo) avancen con el reloj de la prueba.
            var left = advanceMs
            while (left > 0) {
                val step = minOf(16L, left)
                rule.mainClock.advanceTimeBy(step)
                left -= step
            }
        } else {
            rule.waitForIdle()
        }
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        val bounds = rule.onNodeWithTag("voice-scene").fetchSemanticsNode().boundsInRoot
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
