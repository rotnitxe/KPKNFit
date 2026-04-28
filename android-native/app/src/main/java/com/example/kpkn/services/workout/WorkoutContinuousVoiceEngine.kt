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

class WorkoutContinuousVoiceEngine(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var scope: CoroutineScope? = null
    private var active = false
    private var restarting = false

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

    fun start(scope: CoroutineScope) {
        this.scope = scope
        if (active) return
        active = true
        startListening()
    }

    fun pause() {
        active = false
        restarting = false
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    fun stop() {
        pause()
    }

    private fun startListening() {
        if (!active) return

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            scope?.launch { _errors.emit("Reconocimiento no disponible en este dispositivo") }
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            val intent = buildRecognizerIntent()

            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
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
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        ?: ""

                    if (text.isNotBlank()) {
                        _finalResults.tryEmit(text)
                    }

                    if (active && !restarting) {
                        restarting = true
                        scope?.launch {
                            delay(300)
                            restarting = false
                            if (active) startListening()
                        }
                    }
                }

                override fun onError(error: Int) {
                    if (!active) return
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        if (active && !restarting) {
                            restarting = true
                            scope?.launch {
                                delay(400)
                                restarting = false
                                if (active) startListening()
                            }
                        }
                        return
                    }

                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_CLIENT
                    ) {
                        if (active && !restarting) {
                            restarting = true
                            scope?.launch {
                                delay(800)
                                restarting = false
                                if (active) startListening()
                            }
                        }
                        return
                    }

                    scope?.launch {
                        _errors.emit("Error de reconocimiento: $error")
                    }

                    if (active && !restarting) {
                        restarting = true
                        scope?.launch {
                            delay(1000)
                            restarting = false
                            if (active) startListening()
                        }
                    }
                }
            })

            startListening(intent)
        }
    }

    companion object {
        fun buildRecognizerIntent(): Intent {
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL")
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
