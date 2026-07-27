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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Continuous ASR for live workouts.
 *
 * STREAM_SYSTEM is muted for the whole voice session (start→stop) so Android's
 * mic-activation beeps stay silent across listen cycles. Session beeps/TTS use
 * other streams and are unaffected.
 */
class WorkoutContinuousVoiceEngine(
    private val context: Context,
    private var noiseProfile: com.example.kpkn.data.models.VoiceNoiseProfile =
        com.example.kpkn.data.models.VoiceNoiseProfile.GYM,
) {

    private var recognizer: SpeechRecognizer? = null
    private var scope: CoroutineScope? = null
    private var active = false
    private var restarting = false
    private var sessionMuted = false
    private var originalSystemVolume: Int = -1
    private var usingOnDevice = false

    private val _partialResults = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val partialResults: Flow<String> = _partialResults

    private val _finalResults = MutableSharedFlow<List<VoiceHypothesis>>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val finalResults: Flow<List<VoiceHypothesis>> = _finalResults

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: Flow<String> = _errors

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _usingOnDeviceRecognizer = MutableStateFlow(false)
    val usingOnDeviceRecognizer: StateFlow<Boolean> = _usingOnDeviceRecognizer.asStateFlow()

    val isActive: Boolean get() = active

    fun setNoiseProfile(profile: com.example.kpkn.data.models.VoiceNoiseProfile) {
        noiseProfile = profile
    }

    private fun muteSystemVolume() {
        if (sessionMuted) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            if (originalSystemVolume == -1) {
                originalSystemVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_SYSTEM)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    android.media.AudioManager.STREAM_SYSTEM,
                    android.media.AudioManager.ADJUST_MUTE,
                    0,
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(android.media.AudioManager.STREAM_SYSTEM, true)
            }
            sessionMuted = true
        } catch (_: Exception) {
            // Ignore security or permission exceptions gracefully
        }
    }

    private fun unmuteSystemVolume() {
        if (!sessionMuted) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    android.media.AudioManager.STREAM_SYSTEM,
                    android.media.AudioManager.ADJUST_UNMUTE,
                    0,
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(android.media.AudioManager.STREAM_SYSTEM, false)
            }
            if (originalSystemVolume != -1) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                originalSystemVolume = -1
            }
            sessionMuted = false
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        if (active) return
        active = true
        muteSystemVolume()
        scope.launch(Dispatchers.Main) {
            startListening()
        }
    }

    /** Pause listening for TTS without unmuting STREAM_SYSTEM (session mute stays). */
    fun pause() {
        active = false
        restarting = false
        _rmsLevel.value = 0f
        val currentScope = scope
        if (currentScope != null && currentScope.isActive) {
            currentScope.launch(Dispatchers.Main) {
                destroyRecognizer()
            }
        } else {
            destroyRecognizer()
        }
    }

    /** End the voice session: unmute system stream and tear down recognizer. */
    fun stop() {
        active = false
        restarting = false
        _rmsLevel.value = 0f
        unmuteSystemVolume()
        val currentScope = scope
        if (currentScope != null && currentScope.isActive) {
            currentScope.launch(Dispatchers.Main) {
                destroyRecognizer()
            }
        } else {
            destroyRecognizer()
        }
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
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
            val newRecognizer = createRecognizerInstance()
            newRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    _rmsLevel.value = rmsdB
                }
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
                    val texts = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val hypotheses = texts.mapIndexedNotNull { index, raw ->
                        val text = raw.trim()
                        if (text.isBlank()) null
                        else VoiceHypothesis(
                            text = text,
                            confidence = confidences?.getOrNull(index) ?: 0f,
                        )
                    }

                    if (hypotheses.isNotEmpty()) {
                        _finalResults.tryEmit(hypotheses)
                    }

                    restartListeningDelayed(300)
                }

                override fun onError(error: Int) {
                    if (!active) return

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> {
                            softRestartListening(400)
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER,
                        -> {
                            scope?.launch {
                                _errors.emit("Error de reconocimiento: $error")
                            }
                        }
                        else -> {
                            destroyRecognizer()
                            scope?.launch {
                                _errors.emit("Error de reconocimiento: $error")
                            }
                        }
                    }
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

    private fun createRecognizerInstance(): SpeechRecognizer {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val onDevice = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                usingOnDevice = true
                _usingOnDeviceRecognizer.value = true
                return onDevice
            } catch (_: Exception) {
                // Fall through to standard recognizer.
            }
        }
        usingOnDevice = false
        _usingOnDeviceRecognizer.value = false
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun softRestartListening(delayMs: Long) {
        if (!active || restarting) return
        restarting = true
        scope?.launch(Dispatchers.Main) {
            delay(delayMs)
            restarting = false
            if (!active) return@launch
            val rec = recognizer ?: getOrCreateRecognizer() ?: return@launch
            val intent = buildRecognizerIntent(noiseProfile)
            try {
                rec.cancel()
            } catch (_: Exception) {
            }
            try {
                rec.startListening(intent)
            } catch (_: Exception) {
                destroyRecognizer()
                scope?.launch {
                    _errors.emit("Error al reiniciar reconocimiento")
                }
            }
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
            val intent = buildRecognizerIntent(noiseProfile)

            try {
                rec.cancel()
            } catch (_: Exception) {
            }

            try {
                rec.startListening(intent)
            } catch (e: Exception) {
                destroyRecognizer()
                scope?.launch {
                    _errors.emit("Error al iniciar reconocimiento: ${e.message}")
                }
            }
        }
    }

    companion object {
        fun buildRecognizerIntent(
            noiseProfile: com.example.kpkn.data.models.VoiceNoiseProfile =
                com.example.kpkn.data.models.VoiceNoiseProfile.GYM,
        ): Intent {
            val deviceLocale = java.util.Locale.getDefault()
            val lang = if (deviceLocale.language == "es") deviceLocale.toLanguageTag() else "es-CL"
            val (minLen, completeSilence, possibleSilence) = when (noiseProfile) {
                com.example.kpkn.data.models.VoiceNoiseProfile.GYM -> Triple(450L, 1500L, 1000L)
                com.example.kpkn.data.models.VoiceNoiseProfile.QUIET -> Triple(300L, 1200L, 800L)
            }
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilence)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possibleSilence)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, minLen)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
        }
    }
}
