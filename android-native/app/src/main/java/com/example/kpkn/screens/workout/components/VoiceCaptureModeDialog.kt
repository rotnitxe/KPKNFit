package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.screens.onboarding.design.WizardFonts
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
    // Prototipo 02 (`voice`): «Elige cómo entrenar con voz.», Música → Manos libres con su icono y radio, «Entrada de audio»,
    // «Probar este modo»; la prueba con la onda, «Di «Listo»» / «Te escuchamos.» y «Activar modo de voz»; «Ahora no» al pie.
    LiveSheet(title = "Modo de voz", onDismiss = { dismiss() }, modifier = modifier, tag = "voice-mode-sheet", footer = null,
        showClose = false, dismissible = false) {
        LiveSheetSubtitle("Elige cómo entrenar con voz.")
        if (step == VoiceCheckStep.CHOICES || step == VoiceCheckStep.INTRO) {
            VoiceModeChoices(selected) { mode ->
                if (verified != null && !transitioning) {
                    selected = mode; message = null
                    step = if (mode in verified.orEmpty()) VoiceCheckStep.CHOICES else VoiceCheckStep.INTRO
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Entrada de audio", Modifier.weight(1f), style = VoiceBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
                Text(route?.takeIf { it.isNotBlank() } ?: when (selected) {
                    VoiceCaptureMode.HANDS_FREE -> "Micrófono de auriculares"
                    VoiceCaptureMode.MUSIC -> "Micrófono del teléfono"
                    null -> "—"
                }, style = VoiceBody.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
            }
            if (verified == null) LiveSheetShortCopy("Preparando controles de voz…")
            val alreadyVerified = selected?.let { it in verified.orEmpty() } == true
            LiveSheetPrimary(if (alreadyVerified) "Activar modo de voz" else "Probar este modo",
                { if (alreadyVerified) activate() else requestTest() }, Modifier.padding(top = 10.dp),
                enabled = selected != null && verified != null && !transitioning, tag = "voice-mode-test")
        } else {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                VoiceWaveform(listening = step == VoiceCheckStep.LISTENING, passed = step == VoiceCheckStep.PASSED, rms = rms)
                Text(when (step) {
                    VoiceCheckStep.PASSED -> "Te escuchamos."
                    VoiceCheckStep.CONNECTING -> "Preparando micrófono…"
                    VoiceCheckStep.FAILED -> "Probemos otra vez"
                    else -> "Di «Listo»"
                }, Modifier.padding(top = 14.dp), style = VoiceSyne.copy(fontSize = 22.sp, lineHeight = 29.sp), color = WizardColors.text,
                    textAlign = TextAlign.Center)
                Text(message ?: "Comprueba el modo antes de entrenar.", Modifier.padding(top = 8.dp, bottom = 14.dp),
                    style = VoiceBody.copy(fontSize = 12.sp, lineHeight = 19.sp), color = LiveSheetTokens.Muted, textAlign = TextAlign.Center)
                if (heard.isNotBlank()) Text("«$heard»", style = VoiceBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
                route?.takeIf { ownsCapture && it.isNotBlank() }?.let { Text(it, style = VoiceBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint) }
            }
            when (step) {
                VoiceCheckStep.PASSED -> LiveSheetPrimary("Activar modo de voz", ::activate, enabled = !transitioning, tag = "voice-mode-activate")
                VoiceCheckStep.FAILED -> LiveSheetPrimary("Reintentar", ::requestTest, enabled = !transitioning, tag = "voice-mode-retry")
                else -> LiveSheetPrimary("Escuchando…", {}, enabled = false)
            }
            LiveSheetTextAction("Cambiar modo", { stopBefore { selected = null; step = VoiceCheckStep.CHOICES; message = null; heard = "" } })
        }
        if (step == VoiceCheckStep.INTRO && message != null) LiveSheetShortCopy(message.orEmpty(), error = true)
        LiveSheetTextAction("Ahora no", { if (onDismissRequest != null && !transitioning) dismiss() }, Modifier.testTag("voice-mode-later"))
    }
}

private val VoiceBody = TextStyle(fontFamily = WizardFonts.body)
private val VoiceSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)

/** `.v2-voice-check` del prototipo: 25 barras que siguen el nivel del micrófono; al pasar la prueba, la marca. */
@Composable
private fun VoiceWaveform(listening: Boolean, passed: Boolean, rms: Float) {
    val reduced = wizardReducedMotion()
    val visibility = rememberFeedbackAnimationVisibility()
    val clock = rememberRestClock(listening && visibility.visible && !reduced)
    val level = if (rms.isFinite() && rms != 0f) ((rms + 70f) / 50f).coerceIn(0f, 1f) else 0f
    val ink = WizardColors.text
    Canvas(Modifier.fillMaxWidth().height(84.dp).then(visibility.modifier)) {
        val center = Offset(size.width / 2, size.height / 2)
        if (passed) {
            drawLine(ink, center + Offset(-13.dp.toPx(), 0f), center + Offset(-3.dp.toPx(), 10.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            drawLine(ink, center + Offset(-3.dp.toPx(), 10.dp.toPx()), center + Offset(16.dp.toPx(), -12.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        val gap = 6.dp.toPx()
        repeat(25) { i ->
            val wave = if (listening && !reduced) .5f + .5f * kotlin.math.sin(clock.seconds * 5f + i * .55f) else .35f
            val shape = 1f - kotlin.math.abs(i - 12) / 14f
            val h = (4f + (6f + level * 40f) * shape * wave).dp.toPx()
            val x = center.x + (i - 12) * gap
            drawLine(ink.copy(alpha = if (listening) 1f else .45f), Offset(x, center.y - h / 2), Offset(x, center.y + h / 2), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** `.v2-voice-choices`: Música y Manos libres, cada una con su icono, título, explicación y radio. */
@Composable
internal fun VoiceModeChoices(selected: VoiceCaptureMode?, onSelect: (VoiceCaptureMode) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        listOf(
            Triple(VoiceCaptureMode.MUSIC, "Música", "Tu audio, con captura de voz."),
            Triple(VoiceCaptureMode.HANDS_FREE, "Manos libres", "Escucha continua de tus comandos."),
        ).forEach { (mode, title, hint) ->
            val active = selected == mode
            Row(
                Modifier.fillMaxWidth().heightIn(min = 70.dp).drawBehind {
                    drawLine(LiveSheetTokens.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }.clickable(role = Role.RadioButton) { onSelect(mode) }.semantics { this.selected = active }
                    .testTag("voice-mode-${mode.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(if (mode == VoiceCaptureMode.MUSIC) KpknLineIcons.Play else KpknLineIcons.Mic, null, Modifier.size(22.dp),
                    tint = if (active) WizardColors.text else LiveSheetTokens.Muted)
                Column(Modifier.weight(1f)) {
                    Text(title, style = VoiceBody.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
                    Text(hint, Modifier.padding(top = 5.dp), style = VoiceBody.copy(fontSize = 10.sp, lineHeight = 14.sp), color = LiveSheetTokens.Faint)
                }
                Box(Modifier.size(17.dp).clip(CircleShape).border(1.dp, if (active) WizardColors.text else Color(0x38FFFFFF), CircleShape)
                    .padding(4.dp)) {
                    if (active) Box(Modifier.fillMaxSize().clip(CircleShape).background(WizardColors.text))
                }
            }
        }
    }
}
