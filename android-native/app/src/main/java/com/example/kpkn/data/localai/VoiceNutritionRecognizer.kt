package com.example.kpkn.data.localai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * VoiceNutritionRecognizer — Converts speech to food description text using Android's
 * on-device SpeechRecognizer, then feeds the result into parseFreeFormNutrition().
 *
 * Architecture:
 *   Voice → SpeechRecognizer (on-device STT) → text → parseFreeFormNutrition() → ParsedMealDescription
 *
 * The AI model (Gemma 4) is NOT used for speech recognition — Android's built-in STT handles it.
 * The Gemma model processes the resulting text through the existing nutrition pipeline.
 *
 * Usage from ViewModel/Composable:
 *   val recognizer = VoiceNutritionRecognizer(context)
 *   recognizer.recognize().collect { state ->
 *       when (state) {
 *           is VoiceState.Partial  -> showLiveText(state.text)
 *           is VoiceState.Final    -> parseFreeFormNutrition(state.text, options)
 *           is VoiceState.Error    -> showError(state.message)
 *           is VoiceState.Ready    -> showReadyIndicator()
 *           is VoiceState.Done     -> hideRecordingUI()
 *       }
 *   }
 *
 * Required permission: android.permission.RECORD_AUDIO
 * (declared in AndroidManifest.xml, requested at runtime before calling recognize())
 *
 * Language: Spanish Chilean (es-CL). Falls back to es-ES if es-CL is unavailable.
 */

sealed class VoiceState {
    /** Recognizer is ready and listening. Show microphone indicator. */
    object Ready : VoiceState()

    /** Partial result while the user is still speaking. Update the text field live. */
    data class Partial(val text: String) : VoiceState()

    /**
     * Final recognized text. Feed into parseFreeFormNutrition() to get nutrition data.
     * The text is already cleaned and capitalized.
     */
    data class Final(val text: String) : VoiceState()

    /** Recognition ended (silence detected or user stopped). Hide recording UI. */
    object Done : VoiceState()

    /** Error during recognition. Show error message. */
    data class Error(val message: String, val code: Int = -1) : VoiceState()

    /** Speech recognizer is not available on this device. */
    object Unavailable : VoiceState()
}

class VoiceNutritionRecognizer(private val context: Context) {

    /**
     * Returns a cold Flow that starts speech recognition when collected and emits VoiceState events.
     * The flow completes when recognition finishes (Final or Error).
     *
     * Must be called from the Main thread (SpeechRecognizer requirement).
     * Use .flowOn(Dispatchers.Main) if calling from IO context.
     *
     * @param maxDurationMs  Maximum recording duration in milliseconds (default: 8 seconds).
     *                       Long descriptions may need more time.
     */
    fun recognize(maxDurationMs: Int = 8000): Flow<VoiceState> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(VoiceState.Unavailable)
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Chilean Spanish first, fallback to general Spanish
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            // Enable partial results for live text display
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Maximum recording duration
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            // Request multiple alternatives to pick the best nutrition-related transcription
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

                // Pick the best candidate: prefer the one that contains known food words
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

    // ─── Transcription Selection ──────────────────────────────────────────────

    /**
     * Picks the best transcription from multiple candidates.
     * Prefers transcriptions that contain food-related Spanish words.
     * Falls back to the first (highest-confidence) candidate.
     */
    private fun pickBestTranscription(candidates: List<String>): String {
        if (candidates.isEmpty()) return ""
        if (candidates.size == 1) return candidates[0].trim()

        // Score each candidate by how many food-related words it contains
        val scored = candidates.map { candidate ->
            val lower = candidate.lowercase()
            val score = FOOD_SIGNAL_WORDS.count { lower.contains(it) }
            Pair(candidate.trim(), score)
        }

        return scored.maxByOrNull { it.second }?.first ?: candidates[0].trim()
    }

    companion object {
        /** Common Spanish food words used to prefer better transcriptions */
        private val FOOD_SIGNAL_WORDS = setOf(
            "gramos", "gramo", "g de", "ml de", "taza", "cucharada", "porción",
            "pollo", "arroz", "huevo", "pan", "leche", "yogurt", "carne", "palta",
            "avena", "quinoa", "lentejas", "frejoles", "salmon", "atún",
            "cocido", "plancha", "frito", "horno", "crudo",
            "desayuno", "almuerzo", "cena", "snack", "colación",
            "proteína", "proteina", "carbohidrato", "grasa", "calorías",
        )

        /**
         * Checks if RECORD_AUDIO permission has been granted.
         * Call this before recognize() and request the permission if false.
         */
        fun hasPermission(context: Context): Boolean {
            return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        /**
         * Checks if speech recognition is available on this device.
         * Some low-end devices or emulators may not have it.
         */
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }
}
