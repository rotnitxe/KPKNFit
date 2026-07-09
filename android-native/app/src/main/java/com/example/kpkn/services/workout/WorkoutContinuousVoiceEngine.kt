package com.example.kpkn.services.workout

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkoutContinuousVoiceEngine(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var scope: CoroutineScope? = null
    private var active = false
    private var restarting = false
    private var originalSystemVolume: Int = -1

    private val _partialResults = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val partialResults: Flow<String> = _partialResults

    private val _finalResults = MutableSharedFlow<String>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val finalResults: Flow<String> = _finalResults

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: Flow<String> = _errors

    val isActive: Boolean get() = active

    private fun muteSystemVolume() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            if (originalSystemVolume == -1) {
                originalSystemVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_SYSTEM)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(android.media.AudioManager.STREAM_SYSTEM, true)
            }
        } catch (e: Exception) {
            // Ignore security or permission exceptions gracefully
        }
    }

    private fun unmuteSystemVolume() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(android.media.AudioManager.STREAM_SYSTEM, false)
            }
            if (originalSystemVolume != -1) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                originalSystemVolume = -1
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        if (active) return
        active = true
        scope.launch(Dispatchers.Main) {
            startListening()
        }
    }

    fun pause() {
        active = false
        restarting = false
        unmuteSystemVolume()
        val currentScope = scope
        if (currentScope != null) {
            currentScope.launch(Dispatchers.Main) {
                destroyRecognizer()
            }
        } else {
            destroyRecognizer()
        }
    }

    fun stop() {
        pause()
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        val r = recognizer
        if (r != null) return r

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            scope?.launch {
                _errors.emit("Reconocimiento no disponible en este dispositivo")
            }
            return null
        }

        try {
            val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            newRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    unmuteSystemVolume()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        ?: return
                    if (text.isNotBlank()) {
                        _partialResults.tryEmit(text)
                    }
                }

                override fun onResults(results: Bundle?) {
                    unmuteSystemVolume()
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        ?: ""

                    if (text.isNotBlank()) {
                        _finalResults.tryEmit(text)
                    }

                    restartListeningDelayed(300)
                }

                override fun onError(error: Int) {
                    unmuteSystemVolume()
                    if (!active) return

                    destroyRecognizer()

                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        restartListeningDelayed(400)
                        return
                    }

                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_CLIENT
                    ) {
                        restartListeningDelayed(800)
                        return
                    }

                    scope?.launch {
                        _errors.emit("Error de reconocimiento: $error")
                    }

                    restartListeningDelayed(1000)
                }
            })
            recognizer = newRecognizer
            return newRecognizer
        } catch (e: Exception) {
            scope?.launch {
                _errors.emit("Error al inicializar reconocedor: ${e.message}")
            }
            return null
        }
    }

    private fun restartListeningDelayed(delayMs: Long) {
        if (!active || restarting) return
        restarting = true
        scope?.launch(Dispatchers.Main) {
            delay(delayMs)
            restarting = false
            if (active) startListening()
        }
    }

    private fun startListening() {
        if (!active) return
        val currentScope = scope ?: return

        currentScope.launch(Dispatchers.Main) {
            if (!active) return@launch

            val rec = getOrCreateRecognizer() ?: return@launch
            val intent = buildRecognizerIntent()

            try {
                rec.cancel()
            } catch (_: Exception) {}

            muteSystemVolume()
            try {
                rec.startListening(intent)
            } catch (e: Exception) {
                unmuteSystemVolume()
                destroyRecognizer()
                restartListeningDelayed(1000)
            }
        }
    }

    companion object {
        fun buildRecognizerIntent(): Intent {
            val deviceLocale = java.util.Locale.getDefault()
            val lang = if (deviceLocale.language == "es") deviceLocale.toLanguageTag() else "es-ES"
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
        }
    }
}
