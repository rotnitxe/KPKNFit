package com.example.kpkn.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class VoiceState {
    object Ready : VoiceState()
    data class Partial(val text: String) : VoiceState()
    data class Final(val text: String) : VoiceState()
    object Done : VoiceState()
    data class Error(val message: String, val code: Int = -1) : VoiceState()
    object Unavailable : VoiceState()
}

class VoiceNutritionRecognizer(private val context: Context) {

    fun recognize(maxDurationMs: Int = 8000): Flow<VoiceState> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(VoiceState.Unavailable)
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceState.Ready)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!partial.isNullOrBlank()) {
                    trySend(VoiceState.Partial(partial))
                }
            }

            override fun onResults(results: Bundle?) {
                val alternatives = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: emptyList<String>()

                val best = pickBestTranscription(alternatives)

                if (best.isNotBlank()) {
                    trySend(VoiceState.Final(best))
                } else {
                    trySend(VoiceState.Error("No se reconoció ningún texto"))
                }
                trySend(VoiceState.Done)
                close()
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH         -> "No se entendió lo que dijiste"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT   -> "No se detectó voz"
                    SpeechRecognizer.ERROR_AUDIO            -> "Error de audio"
                    SpeechRecognizer.ERROR_NETWORK          -> "Sin conexión (usa reconocimiento offline)"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT  -> "Tiempo de red agotado"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permiso de micrófono"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY  -> "Reconocedor ocupado, intenta de nuevo"
                    else -> "Error de reconocimiento ($error)"
                }
                trySend(VoiceState.Error(message, error))
                trySend(VoiceState.Done)
                close()
            }

            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }

    private fun pickBestTranscription(candidates: List<String>): String {
        if (candidates.isEmpty()) return ""
        if (candidates.size == 1) return candidates[0].trim()

        val scored = candidates.map { candidate ->
            val lower = candidate.lowercase()
            val foodScore = FOOD_SIGNAL_WORDS.count { lower.contains(it) }
            val gymScore = GYM_SIGNAL_WORDS.count { lower.contains(it) }
            Pair(candidate.trim(), (foodScore * 2) + (gymScore * 3))
        }

        return scored.maxByOrNull { it.second }?.first ?: candidates[0].trim()
    }

    companion object {
        private val FOOD_SIGNAL_WORDS = setOf(
            "gramos", "gramo", "g de", "ml de", "taza", "cucharada", "porción",
            "pollo", "arroz", "huevo", "pan", "leche", "yogurt", "carne", "palta",
            "avena", "quinoa", "lentejas", "frejoles", "salmon", "atún",
            "cocido", "plancha", "frito", "horno", "crudo",
            "desayuno", "almuerzo", "cena", "snack", "colación",
            "proteína", "proteina", "carbohidrato", "grasa", "calorías",
        )

        private val GYM_SIGNAL_WORDS = setOf(
            "kilo", "kilos", "rep", "reps", "repeticion", "repeticiones",
            "rpe", "rir", "fallo", "falla", "serie", "series",
            "peso", "carga", "descanso", "ejercicio", "confirmar",
            "cancelar", "siguiente", "anterior", "izquierda", "derecha",
            "segundo", "segundos", "minuto", "minutos",
            "kpkn", "capecén", "capekén", "americana", "americano",
        )

        fun hasPermission(context: Context): Boolean {
            return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }
}
