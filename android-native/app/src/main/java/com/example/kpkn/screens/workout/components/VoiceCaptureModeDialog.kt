package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType

private enum class VoiceCheckStep { CHOICES, INTRO, CONNECTING, LISTENING, PASSED, FAILED }
private const val VoiceCheckPrefs = "workout_voice_first_use_v2"

/** The shared voice port is leased only after the caller has disabled live voice. No commands are parsed. */
@Composable
fun VoiceCaptureModeDialog(onChosen: (VoiceCaptureMode) -> Unit, onDismissRequest: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val latestChosen by rememberUpdatedState(onChosen)
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val engine = remember(context) {
        com.example.kpkn.services.workout.WorkoutVoiceRuntime.apply { initialize(context) }.speechEngine()
    }
    var verified by remember { mutableStateOf<Set<VoiceCaptureMode>?>(null) }
    LaunchedEffect(context) {
        verified = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs = context.getSharedPreferences(VoiceCheckPrefs, android.content.Context.MODE_PRIVATE)
            VoiceCaptureMode.entries.filter { prefs.getBoolean(it.name, false) }.toSet()
        }
    }
    var selected by remember { mutableStateOf<VoiceCaptureMode?>(null) }
    var step by remember { mutableStateOf(VoiceCheckStep.CHOICES) }
    var message by remember { mutableStateOf<String?>(null) }
    var heard by remember { mutableStateOf("") }
    var ownsCapture by remember { mutableStateOf(false) }
    var transitioning by remember { mutableStateOf(false) }
    var exitDelivered by remember { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(0) }
    val testJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val rms by engine.rmsLevel.collectAsState()
    val route by engine.activeRouteLabel.collectAsState()

    fun stopBefore(action: suspend () -> Unit) {
        if (transitioning || exitDelivered) return
        transitioning = true
        epoch += 1
        testJob[0]?.cancel()
        scope.launch {
            try {
                // This finishes before onChosen can enable the normal voice controller.
                if (engine.stopAndAwait()) {
                    ownsCapture = false
                    action()
                } else {
                    step = VoiceCheckStep.FAILED
                    message = "No se pudo cerrar el micrófono. Intenta de nuevo."
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                step = VoiceCheckStep.FAILED
                message = "No se pudo cerrar el micrófono. Intenta de nuevo."
            } finally { transitioning = false }
        }
    }
    fun activate() {
        val mode = selected ?: return
        if (mode !in verified.orEmpty() && step != VoiceCheckStep.PASSED) return
        stopBefore {
            val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.getSharedPreferences(VoiceCheckPrefs, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(mode.name, true).commit()
            }
            if (saved) {
                verified = verified.orEmpty() + mode
                if (!exitDelivered) { exitDelivered = true; latestChosen(mode) }
            }
            else { step = VoiceCheckStep.FAILED; message = "No se pudo guardar la prueba. Intenta de nuevo." }
        }
    }

    fun dismiss() {
        if (latestDismiss == null) return
        stopBefore {
            if (!exitDelivered) { exitDelivered = true; latestDismiss?.invoke() }
        }
    }

    fun startTest() {
        val mode = selected ?: return
        if (transitioning || exitDelivered || step == VoiceCheckStep.CONNECTING || step == VoiceCheckStep.LISTENING) return
        epoch += 1
        val token = epoch
        testJob[0]?.cancel()
        step = VoiceCheckStep.CONNECTING; message = null; heard = ""
        testJob[0] = scope.launch {
            try {
            if (!engine.stopAndAwait()) {
                step = VoiceCheckStep.FAILED; message = "El micrófono sigue ocupado. Intenta de nuevo."
                return@launch
            }
            if (epoch != token) return@launch
            ownsCapture = true
            kotlinx.coroutines.coroutineScope {
                launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    engine.finalResults.collect { hypotheses ->
                        if (epoch != token || step != VoiceCheckStep.LISTENING) return@collect
                        val transcript = hypotheses.firstOrNull()?.text.orEmpty().trim()
                        heard = transcript
                        if (transcript.lowercase(java.util.Locale.ROOT).split(Regex("[^a-záéíóúñ]+" )).contains("listo")) {
                            step = VoiceCheckStep.PASSED
                            engine.pause()
                        } else if (transcript.isNotBlank()) message = "Di la palabra «Listo» para probar este modo."
                    }
                }
                launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    engine.errors.collect { error ->
                        if (epoch == token && step != VoiceCheckStep.PASSED) {
                            step = VoiceCheckStep.FAILED; message = error; engine.pause()
                        }
                    }
                }
                engine.updateCommandContext(null, com.example.kpkn.services.workout.VoicePipelineStage.LISTENING)
                engine.start(this, holdMicRouteAcrossPause = true, captureMode = mode)
                step = VoiceCheckStep.LISTENING
                kotlinx.coroutines.delay(20_000)
                if (epoch == token && step == VoiceCheckStep.LISTENING) {
                    step = VoiceCheckStep.FAILED
                    message = "No se reconoció «Listo». Acerca el micrófono y vuelve a intentar."
                    engine.pause()
                }
            }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (epoch == token) {
                    step = VoiceCheckStep.FAILED
                    message = error.message ?: "No se pudo iniciar el micrófono. Intenta de nuevo."
                    if (ownsCapture) engine.pause()
                }
            }
        }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startTest()
        else { step = VoiceCheckStep.FAILED; message = "Permite usar el micrófono para probar tu voz." }
    }
    fun requestTest() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startTest()
        else permission.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(engine, owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && ownsCapture) {
                epoch += 1; testJob[0]?.cancel(); engine.stop(); ownsCapture = false
                step = VoiceCheckStep.INTRO
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            epoch += 1; testJob[0]?.cancel()
            // Never stop a controller that onChosen just enabled: ownership was released before its callback.
            if (ownsCapture) { engine.stop(); ownsCapture = false }
        }
    }
    androidx.activity.compose.BackHandler(enabled = onDismissRequest != null) { dismiss() }
    // A drag animation must not hide this sheet before asynchronous stop has succeeded.
    HeaderSheet(onDismiss = { dismiss() }, modifier = modifier, dismissible = false) { _ ->
        HeaderSheetFrame(if (step == VoiceCheckStep.CHOICES) "Modo de voz" else "Probemos tu voz", footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderSheetAction("Ahora no", { dismiss() }, Modifier.weight(1f), enabled = onDismissRequest != null && !transitioning)
                when (step) {
                    VoiceCheckStep.CHOICES -> HeaderSheetAction("Activar", ::activate, Modifier.weight(1f), primary = true, enabled = selected != null && verified != null && !transitioning)
                    VoiceCheckStep.INTRO, VoiceCheckStep.FAILED -> HeaderSheetAction(if (step == VoiceCheckStep.FAILED) "Reintentar" else "Comenzar prueba", ::requestTest, Modifier.weight(1f), primary = true, enabled = !transitioning)
                    VoiceCheckStep.PASSED -> HeaderSheetAction("Activar", ::activate, Modifier.weight(1f), primary = true, enabled = !transitioning)
                    else -> HeaderSheetAction("Escuchando…", {}, Modifier.weight(1f), enabled = false)
                }
            }
        }) {
            if (step == VoiceCheckStep.CHOICES) {
                VoiceModeChoices(selected) { mode ->
                    if (verified != null && !transitioning) {
                        selected = mode; message = null
                        step = if (mode in verified.orEmpty()) VoiceCheckStep.CHOICES else VoiceCheckStep.INTRO
                    }
                }
                if (verified == null) Text("Preparando controles de voz…", style = KpknType.caption, color = WizardColors.textMuted)
            } else {
                VoiceFirstUseArt(listening = step == VoiceCheckStep.LISTENING, passed = step == VoiceCheckStep.PASSED, rms = rms)
                Text(when (step) {
                    VoiceCheckStep.PASSED -> "Te escuchamos"
                    VoiceCheckStep.CONNECTING -> "Preparando micrófono…"
                    VoiceCheckStep.LISTENING -> "Di «Listo»"
                    VoiceCheckStep.FAILED -> "Probemos otra vez"
                    else -> "Di una palabra"
                }, style = KpknType.title, color = WizardColors.text)
                Text(if (selected == VoiceCaptureMode.HANDS_FREE) "Usa el micrófono de tus audífonos. La música puede perder calidad mientras escucha."
                    else "Usa el micrófono del teléfono y conserva la calidad de la música. Acerca el teléfono al hablar.", style = KpknType.body, color = WizardColors.textMuted)
                if (step == VoiceCheckStep.INTRO) Text("Listo", style = KpknType.metricM, color = WizardColors.text)
                if (heard.isNotBlank()) Text("«" + heard + "»", style = KpknType.bodyStrong, color = WizardColors.text)
                route?.takeIf { ownsCapture && it.isNotBlank() }?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
                message?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
                HeaderSheetAction("Cambiar modo", { stopBefore { selected = null; step = VoiceCheckStep.CHOICES; message = null; heard = "" } }, Modifier.fillMaxWidth(), enabled = !transitioning)
            }
        }
    }
}

@Composable
private fun VoiceFirstUseArt(listening: Boolean, passed: Boolean, rms: Float) {
    val reduced = wizardReducedMotion()
    val visibility = rememberFeedbackAnimationVisibility()
    val clock = rememberRestClock(listening && visibility.visible && !reduced)
    val level = if (rms.isFinite() && rms != 0f) ((rms + 70f) / 50f).coerceIn(0f, 1f) else 0f
    val ink = WizardColors.text
    Canvas(Modifier.fillMaxWidth().height(138.dp).then(visibility.modifier)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = 42.dp.toPx()
        drawCircle(ink.copy(alpha = .06f), radius, center)
        drawCircle(ink.copy(alpha = .18f), radius, center, style = Stroke(1.dp.toPx()))
        if (listening && !reduced) {
            val pulse = (clock.seconds % 1.8f) / 1.8f
            drawCircle(ink.copy(alpha = (1f - pulse) * .2f), radius + pulse * 14.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        }
        if (passed) {
            drawLine(ink, center + Offset(-13.dp.toPx(), 0f), center + Offset(-3.dp.toPx(), 10.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            drawLine(ink, center + Offset(-3.dp.toPx(), 10.dp.toPx()), center + Offset(16.dp.toPx(), -12.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        } else {
            repeat(9) { i ->
                val h = (5f + level * (if (i % 2 == 0) 22f else 14f)).dp.toPx()
                val x = center.x + (i - 4) * 5.dp.toPx()
                drawLine(ink, Offset(x, center.y - h / 2), Offset(x, center.y + h / 2), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun VoiceModeChoices(selected: VoiceCaptureMode?, onSelect: (VoiceCaptureMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(VoiceCaptureMode.HANDS_FREE, VoiceCaptureMode.MUSIC).forEach { mode ->
            val active = selected == mode
            KpknModule(modifier = Modifier.weight(1f).semantics { this.selected = active }, onClick = { onSelect(mode) },
                accent = if (active) WizardColors.text else null, contentPadding = PaddingValues(12.dp)) {
                val visibility = rememberFeedbackAnimationVisibility()
                val reduced = wizardReducedMotion()
                val clock = rememberRestClock(active && mode == VoiceCaptureMode.MUSIC && visibility.visible && !reduced)
                val ink = if (active) WizardColors.text else WizardColors.textMuted
                Canvas(Modifier.fillMaxWidth().height(66.dp).then(visibility.modifier)) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    if (mode == VoiceCaptureMode.HANDS_FREE) {
                        drawArc(ink, 180f, 180f, false, Offset(cx - 22.dp.toPx(), cy - 22.dp.toPx()), androidx.compose.ui.geometry.Size(44.dp.toPx(), 44.dp.toPx()), style = stroke)
                        listOf(-1, 1).forEach { side -> drawLine(ink, Offset(cx + side * 22.dp.toPx(), cy), Offset(cx + side * 22.dp.toPx(), cy + 16.dp.toPx()), 7.dp.toPx(), StrokeCap.Round) }
                    } else {
                        listOf(-14f, -5f, 5f, 14f).forEachIndexed { i, x ->
                            val wave = if (active && !reduced) .6f + .4f * kotlin.math.sin(clock.seconds * 2 + i) else .7f
                            val h = (if (i == 1 || i == 2) 20.dp.toPx() else 11.dp.toPx()) * wave
                            drawLine(ink, Offset(cx + x.dp.toPx(), cy - h), Offset(cx + x.dp.toPx(), cy + h), 4.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
                Text(if (mode == VoiceCaptureMode.HANDS_FREE) "Manos libres" else "Modo música", style = KpknType.bodyStrong, color = ink)
                Spacer(Modifier.height(6.dp))
                Text(if (mode == VoiceCaptureMode.HANDS_FREE) "Micrófono de auriculares; reduce la calidad de la música." else "Micrófono del teléfono; acércalo al hablar.", style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
    }
}
