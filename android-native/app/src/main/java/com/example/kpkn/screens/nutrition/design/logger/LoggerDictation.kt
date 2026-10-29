package com.example.kpkn.screens.nutrition.design.logger

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

/** El micrófono «Dictar» (f-empty, f-home): escucha, lo que oye va al texto y [error] dice por qué no pudo. */
@Stable
class LoggerDictation internal constructor() {
    var listening by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    internal var toggleAction: () -> Unit = {}

    /** Empieza a escuchar (pide el permiso del micrófono la primera vez) o deja de hacerlo. */
    fun toggle() = toggleAction()

    fun clearError() {
        error = null
    }
}

/**
 * El dictado con el reconocedor del sistema (decisión F-D6: se acepta el del sistema y se le pide el modo sin conexión cuando lo tiene,
 * `EXTRA_PREFER_OFFLINE` en [VoiceNutritionRecognizer]). [current] da el texto de antes de empezar y [onText] recibe el texto entero
 * mientras se dicta.
 */
@Composable
fun rememberLoggerDictation(current: () -> String, onText: (String) -> Unit): LoggerDictation {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictation = remember { LoggerDictation() }
    val latestCurrent by rememberUpdatedState(current)
    val latestOnText by rememberUpdatedState(onText)
    var job by remember { mutableStateOf<Job?>(null) }
    fun start() {
        val before = latestCurrent()
        dictation.listening = true
        dictation.error = null
        job = scope.launch {
            try {
                VoiceNutritionRecognizer(context).recognize(maxDurationMs = 15_000).collect { state ->
                    when (state) {
                        is VoiceState.Partial -> latestOnText(loggerAppendDictation(before, state.text))
                        is VoiceState.Final -> latestOnText(loggerAppendDictation(before, state.text))
                        is VoiceState.Unavailable -> dictation.error = "Este teléfono no tiene dictado por voz."
                        is VoiceState.Error -> dictation.error = "No te entendí. Prueba otra vez o escríbelo."
                        else -> Unit
                    }
                }
            } finally {
                dictation.listening = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else dictation.error = "Sin permiso del micrófono no puedo dictar."
    }
    dictation.toggleAction = {
        if (dictation.listening) {
            job?.cancel()
            dictation.listening = false
        } else if (VoiceNutritionRecognizer.hasPermission(context)) {
            start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return dictation
}
