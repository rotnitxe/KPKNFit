package com.example.kpkn.screens.nutrition.design.logger

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.voice.VoiceNutritionRecognizer
import com.example.kpkn.data.voice.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Une lo dictado a lo que ya había escrito: con un espacio, o con «, » si lo anterior no acaba en coma. */
fun loggerAppendDictation(before: String, dictated: String): String {
    val spoken = dictated.trim()
    if (spoken.isEmpty()) return before
    val base = before.trimEnd()
    if (base.isEmpty()) return spoken.replaceFirstChar { it.uppercase() }
    return if (base.endsWith(",")) "$base $spoken" else "$base, $spoken"
}

/** Lo que se le dice a la persona cuando el reconocedor falla con [code] (un `SpeechRecognizer.ERROR_*`). */
fun loggerDictationErrorMessage(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> "No te oí. Prueba otra vez o escríbelo."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    VoiceNutritionRecognizer.ERROR_SERVER_DISCONNECTED,
    -> "El servicio de voz no responde ahora. Prueba otra vez o escríbelo."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El dictado está ocupado. Espera un momento y prueba otra vez."
    SpeechRecognizer.ERROR_AUDIO -> "No pude usar el micrófono. Mira que otra app no lo esté usando."
    VoiceNutritionRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    VoiceNutritionRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    -> "Este teléfono no tiene el dictado en español disponible. Escríbelo."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permiso del micrófono no puedo dictar."
    else -> "No pude dictar. Prueba otra vez o escríbelo."
}

/** Errores en los que no se oyó nada: si la persona acaba de pulsar «parar» no es un fallo que avisar. */
private fun isSilentError(code: Int): Boolean =
    code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

/**
 * El micrófono «Dictar» (f-empty, f-home): escucha, lo que oye va al texto y [error] dice por qué no pudo.
 *
 * Estados para pintar: [listening] (desde que se pulsa hasta que acaba), [ready] (el reconocedor ya está oyendo; antes, «preparando») y
 * [level] (volumen de la voz, 0..1, para el pulso del botón).
 */
@Stable
class LoggerDictation internal constructor() {
    var listening by mutableStateOf(false)
        internal set
    var ready by mutableStateOf(false)
        internal set
    var level by mutableFloatStateOf(0f)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    /** El [error] actual se resuelve en los ajustes del sistema (permiso denegado para siempre): el aviso ofrece «Ajustes». */
    var errorOpensSettings by mutableStateOf(false)
        internal set
    internal var stopRequested = false
    internal var toggleAction: () -> Unit = {}
    internal var openSettingsAction: () -> Unit = {}

    /** Empieza a escuchar (pide el permiso del micrófono la primera vez), o para y deja lo oído en el texto. */
    fun toggle() = toggleAction()

    /** Abre la ficha de la app en los ajustes del sistema, para conceder el micrófono. */
    fun openSettings() = openSettingsAction()

    fun clearError() {
        error = null
        errorOpensSettings = false
    }

    internal fun fail(message: String, opensSettings: Boolean = false) {
        errorOpensSettings = opensSettings
        error = message
    }
}

/**
 * El dictado con el reconocedor del sistema (decisión F-D6), a través de [VoiceNutritionRecognizer] en modo dictado: español del
 * dispositivo, el reconocedor del dispositivo primero y, si no puede, el del sistema. [current] da el texto de antes de empezar,
 * [onText] recibe el texto entero mientras se dicta (parciales incluidos) y [onFinal] el texto definitivo cuando acaba de oír
 * (ahí el registro lanza el mismo análisis que «Analizar»). Se libera al salir de la pantalla y al irse la app a segundo plano.
 */
@Composable
fun rememberLoggerDictation(
    current: () -> String,
    onText: (String) -> Unit,
    onFinal: (String) -> Unit = {},
): LoggerDictation {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictation = remember { LoggerDictation() }
    val recognizer = remember { VoiceNutritionRecognizer(context) }
    val latestCurrent by rememberUpdatedState(current)
    val latestOnText by rememberUpdatedState(onText)
    val latestOnFinal by rememberUpdatedState(onFinal)
    var job by remember { mutableStateOf<Job?>(null) }

    fun reset() {
        dictation.listening = false
        dictation.ready = false
        dictation.level = 0f
        dictation.stopRequested = false
    }

    fun start() {
        val before = latestCurrent()
        dictation.clearError()
        dictation.stopRequested = false
        dictation.ready = false
        dictation.level = 0f
        dictation.listening = true
        job = scope.launch {
            var finalText: String? = null
            try {
                recognizer.recognize(
                    maxDurationMs = DICTATION_MAX_MS,
                    onLevel = { dictation.level = it },
                    dictation = true,
                ).collect { state ->
                    when (state) {
                        VoiceState.Ready -> dictation.ready = true
                        is VoiceState.Partial -> latestOnText(loggerAppendDictation(before, state.text))
                        is VoiceState.Final -> {
                            val text = loggerAppendDictation(before, state.text)
                            latestOnText(text)
                            finalText = text
                        }
                        VoiceState.Unavailable -> dictation.fail("Tu teléfono no tiene dictado por voz. Escríbelo.")
                        is VoiceState.Error -> {
                            runCatching {
                                KpknDiagnosticLogger.event("nutrition", "dictation_error", mapOf("code" to state.code))
                            }
                            if (!(dictation.stopRequested && isSilentError(state.code))) {
                                dictation.fail(loggerDictationErrorMessage(state.code))
                            }
                        }
                        VoiceState.Done -> Unit
                    }
                }
            } finally {
                reset()
            }
            finalText?.let { latestOnFinal(it) }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            start()
        } else {
            val activity = context.loggerFindActivity()
            // Tras negarse, si Android ya no ofrece la explicación es que se negó «para siempre»: solo se arregla en Ajustes.
            val permanent = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            if (permanent) {
                dictation.fail("El micrófono está desactivado para KPKN. Actívalo en Ajustes para dictar.", opensSettings = true)
            } else {
                dictation.fail("Sin permiso del micrófono no puedo dictar.")
            }
        }
    }

    dictation.toggleAction = {
        when {
            dictation.listening -> {
                // Parar no tira lo dicho: el reconocedor cierra lo que oyó y entrega el final (con red de seguridad por si no contesta).
                dictation.stopRequested = true
                recognizer.stop()
            }
            !VoiceNutritionRecognizer.canDictate(context) ->
                dictation.fail("Tu teléfono no tiene dictado por voz. Escríbelo.")
            VoiceNutritionRecognizer.hasPermission(context) -> start()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    dictation.openSettingsAction = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // Al salir de la pantalla o irse la app a segundo plano se suelta el micrófono (cancelar el trabajo cierra y destruye el reconocedor).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                job?.cancel()
                reset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            job?.cancel()
            reset()
        }
    }
    return dictation
}

/** Tope de una dictación: una comida contada con calma cabe de sobra. */
private const val DICTATION_MAX_MS = 25_000

private tailrec fun Context.loggerFindActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.loggerFindActivity()
    else -> null
}
