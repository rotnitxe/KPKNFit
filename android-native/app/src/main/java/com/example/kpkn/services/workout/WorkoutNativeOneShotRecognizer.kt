package com.example.kpkn.services.workout

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * One-shot **estrictamente on-device**. Nunca crea el SpeechRecognizer genérico
 * (posible ruta remota). Si no hay on-device, falla en local.
 */
class WorkoutNativeOneShotRecognizer(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val onDeviceAvailable: (Context) -> Boolean = { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        } else {
            false
        }
    },
    private val createOnDevice: (Context) -> SpeechRecognizer = { ctx ->
        SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
    },
) {
    /** Prevalidación barata: llamar antes del prompt TTS del fallback. */
    fun isAvailable(): Boolean =
        WorkoutNativeOnDevicePolicy.isStrictlyLocalFallbackAllowed(
            sdkInt = sdkInt,
            onDeviceRecognitionAvailable = onDeviceAvailable(context),
        )

    fun unavailableReason(): String? =
        WorkoutNativeOnDevicePolicy.unavailableReason(
            sdkInt = sdkInt,
            onDeviceRecognitionAvailable = onDeviceAvailable(context),
        )

    suspend fun recognizeOnce(maxResults: Int = 3): NativeRecognitionResult {
        unavailableReason()?.let { return NativeRecognitionResult.Error(it) }
        return suspendCancellableCoroutine { continuation ->
            val recognizer = try {
                createOnDevice(context)
            } catch (e: Exception) {
                continuation.resume(
                    NativeRecognitionResult.Error(
                        "No se pudo crear recognizer on-device: ${e.message ?: "error"}",
                    ),
                )
                return@suspendCancellableCoroutine
            }
            var completed = false
            fun finish(result: NativeRecognitionResult) {
                if (completed) return
                completed = true
                runCatching { recognizer.destroy() }
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation {
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }
            try {
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onResults(results: Bundle?) {
                        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        val hypotheses = texts.mapIndexedNotNull { index, value ->
                            value?.trim()?.takeIf { it.isNotBlank() }?.let {
                                val conf = confidences?.getOrNull(index)
                                VoiceHypothesis(
                                    text = it,
                                    confidence = conf ?: 0f,
                                    confidenceKnown = conf != null,
                                )
                            }
                        }
                        finish(NativeRecognitionResult.Success(hypotheses))
                    }

                    override fun onError(error: Int) {
                        finish(NativeRecognitionResult.Error("Error de reconocimiento nativo: $error"))
                    }
                })
                recognizer.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 450L)
                    },
                )
            } catch (e: Exception) {
                finish(
                    NativeRecognitionResult.Error(
                        "Fallo al iniciar reconocimiento on-device: ${e.message ?: "error"}",
                    ),
                )
            }
        }
    }
}

sealed interface NativeRecognitionResult {
    data class Success(val hypotheses: List<VoiceHypothesis>) : NativeRecognitionResult
    data class Error(val message: String) : NativeRecognitionResult
}
