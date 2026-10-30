package com.example.kpkn.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

sealed class VoiceState {
    object Ready : VoiceState()
    data class Partial(val text: String) : VoiceState()
    data class Final(val text: String) : VoiceState()
    object Done : VoiceState()
    data class Error(val message: String, val code: Int = -1) : VoiceState()
    object Unavailable : VoiceState()
}

class VoiceNutritionRecognizer(private val context: Context) {

    /** La sesión que está escuchando ahora (null si ninguna): la usa [stop]. Solo se toca desde el hilo principal. */
    private var activeSession: Session? = null

    /**
     * Pide al reconocedor que cierre lo que ya oyó y entregue el resultado final (el botón «parar» del dictado).
     * Si no contesta en [STOP_GRACE_MS] se entrega lo último parcial (o un error de «sin voz») para no quedarse colgado.
     * Llamar desde el hilo principal.
     */
    fun stop() {
        activeSession?.requestStop()
    }

    /**
     * @param maxDurationMs tope de escucha; solo se aplica con [dictation] (el flujo de la sesión en vivo conserva su comportamiento).
     * @param onLevel nivel de voz normalizado 0..1 (`onRmsChanged`), en el hilo principal; para pintar el pulso del micrófono.
     * @param dictation modo dictado de «Registrar comida»: español del dispositivo, reconocedor del dispositivo primero con salto al del
     * sistema si no puede, tope de escucha, y lo último parcial se entrega como final si el servicio acaba con «sin coincidencia».
     */
    fun recognize(
        maxDurationMs: Int = 8000,
        onLevel: ((Float) -> Unit)? = null,
        dictation: Boolean = false,
    ): Flow<VoiceState> = callbackFlow {
        val genericAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        val onDeviceAvailable = dictation &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        val engines = buildList {
            if (onDeviceAvailable) add(Engine.ON_DEVICE)
            if (genericAvailable) add(Engine.SYSTEM)
        }
        if (engines.isEmpty()) {
            trySend(VoiceState.Unavailable)
            close()
            return@callbackFlow
        }

        val session = Session(
            context = context,
            scope = this,
            engines = engines,
            dictation = dictation,
            maxDurationMs = maxDurationMs,
            onLevel = onLevel,
        )
        activeSession = session
        session.start()

        awaitClose {
            if (activeSession === session) activeSession = null
            session.release()
        }
    }

    private enum class Engine { ON_DEVICE, SYSTEM }

    /**
     * Una escucha completa. Prueba los motores de [engines] por orden: si uno falla de forma que no tiene que ver con la persona
     * (idioma sin paquete, servicio caído) y todavía no se oyó voz, pasa al siguiente. Todo ocurre en el hilo principal.
     */
    private class Session(
        private val context: Context,
        private val scope: ProducerScope<VoiceState>,
        private val engines: List<Engine>,
        private val dictation: Boolean,
        private val maxDurationMs: Int,
        private val onLevel: ((Float) -> Unit)?,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var engineIndex = 0
        private var current: SpeechRecognizer? = null
        private var lastPartial = ""
        private var heardSpeech = false
        private var finished = false

        fun start() {
            if (dictation) {
                handler.postDelayed({ requestStop() }, maxDurationMs.toLong())
            }
            launchEngine()
        }

        fun requestStop() {
            if (finished) return
            val engine = current
            if (engine == null) {
                giveUp()
                return
            }
            runCatching { engine.stopListening() }
            handler.postDelayed({ giveUp() }, STOP_GRACE_MS)
        }

        /** Cierra la escucha sin enviar nada más (la colección se canceló o el flujo ya terminó). */
        fun release() {
            finished = true
            handler.removeCallbacksAndMessages(null)
            releaseCurrent()
        }

        private fun giveUp() {
            if (finished) return
            if (lastPartial.isNotBlank()) {
                finish(VoiceState.Final(lastPartial))
            } else {
                finish(VoiceState.Error(errorMessage(SpeechRecognizer.ERROR_SPEECH_TIMEOUT), SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
            }
        }

        private fun releaseCurrent() {
            val engine = current ?: return
            current = null
            runCatching { engine.cancel() }
            runCatching { engine.destroy() }
        }

        private fun finish(last: VoiceState) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            releaseCurrent()
            scope.trySend(last)
            scope.trySend(VoiceState.Done)
            scope.close()
        }

        /** El motor falló sin que sirviera: pasa al siguiente si lo hay y la causa lo permite; si no, termina con [code]. */
        private fun engineFailed(code: Int) {
            releaseCurrent()
            val canTryNext = engineIndex < engines.lastIndex && !heardSpeech && code in NEXT_ENGINE_CODES
            if (canTryNext) {
                engineIndex += 1
                launchEngine()
                return
            }
            val silent = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (dictation && silent && lastPartial.isNotBlank()) {
                finish(VoiceState.Final(lastPartial))
            } else {
                finish(VoiceState.Error(errorMessage(code), code))
            }
        }

        private fun launchEngine() {
            if (finished) return
            val kind = engines[engineIndex]
            val engine = try {
                if (kind == Engine.ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                null
            }
            if (engine == null) {
                engineFailed(SpeechRecognizer.ERROR_CLIENT)
                return
            }
            current = engine
            engine.setRecognitionListener(listenerFor(engine))
            try {
                engine.startListening(buildIntent(kind))
            } catch (e: Exception) {
                if (current === engine) engineFailed(SpeechRecognizer.ERROR_CLIENT)
            }
        }

        private fun buildIntent(kind: Engine): Intent {
            val language = if (dictation) dictationLanguageTag() else LEGACY_LANGUAGE
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // En el dictado el motor del sistema es el último recurso: ahí sí se permite la red. Sin dictado, como antes.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !dictation || kind == Engine.ON_DEVICE)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (dictation) 2000L else 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, if (dictation) 1500L else 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
        }

        private fun listenerFor(engine: SpeechRecognizer) = object : RecognitionListener {
            // Un motor ya soltado (cambio de motor, parada, fin) puede mandar eventos tardíos: se ignoran.
            private fun stale() = finished || engine !== current

            override fun onReadyForSpeech(params: Bundle?) {
                if (stale()) return
                scope.trySend(VoiceState.Ready)
            }

            override fun onBeginningOfSpeech() {
                if (stale()) return
                heardSpeech = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (stale()) return
                onLevel?.invoke(normalizeLevel(rmsdB))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (stale()) return
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!partial.isNullOrBlank()) {
                    heardSpeech = true
                    lastPartial = partial
                    scope.trySend(VoiceState.Partial(partial))
                }
            }

            override fun onResults(results: Bundle?) {
                if (stale()) return
                val alternatives = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: emptyList<String>()
                val best = pickBestTranscription(alternatives).ifBlank { if (dictation) lastPartial else "" }
                if (best.isNotBlank()) {
                    finish(VoiceState.Final(best))
                } else {
                    finish(VoiceState.Error("No se reconoció ningún texto", SpeechRecognizer.ERROR_NO_MATCH))
                }
            }

            override fun onError(error: Int) {
                if (stale()) return
                engineFailed(error)
            }

            override fun onEndOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    companion object {
        private const val LEGACY_LANGUAGE = "es-CL"

        /** Margen que se le da al reconocedor, tras pedirle que pare, para entregar el resultado final. */
        const val STOP_GRACE_MS = 3_000L

        // Códigos de `SpeechRecognizer` que solo existen desde Android 12/13 (se dejan literales para no depender del nivel de la API).
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
        const val ERROR_CANNOT_CHECK_SUPPORT = 14

        /** Fallos que no dependen de lo que dijo la persona: con ellos se prueba el siguiente motor (del dispositivo → del sistema). */
        private val NEXT_ENGINE_CODES = setOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER,
            ERROR_SERVER_DISCONNECTED,
            ERROR_LANGUAGE_NOT_SUPPORTED,
            ERROR_LANGUAGE_UNAVAILABLE,
            ERROR_CANNOT_CHECK_SUPPORT,
        )

        /**
         * El español del dispositivo para el dictado: la variante de su región (`es-CL`, `es-MX`, `es-419`…); si el teléfono está en
         * otro idioma o sin región, `es-ES`.
         */
        fun dictationLanguageTag(locale: Locale = Locale.getDefault()): String =
            if (locale.language == "es" && locale.country.isNotEmpty()) "es-${locale.country}" else "es-ES"

        /** `onRmsChanged` va de unos −2 dB (silencio) a unos 10 dB (voz fuerte): lo lleva a 0..1. */
        fun normalizeLevel(rmsdB: Float): Float = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)

        fun errorMessage(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió lo que dijiste"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
            SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
            SpeechRecognizer.ERROR_NETWORK -> "Sin conexión (usa reconocimiento offline)"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de red agotado"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permiso de micrófono"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado, intenta de nuevo"
            else -> "Error de reconocimiento ($error)"
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

        /** Hay algún motor para dictar: el del sistema o, desde Android 12, el del dispositivo. */
        fun canDictate(context: Context): Boolean =
            runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false) ||
                (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
                    )
    }
}
