package com.example.kpkn.services.workout

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class WorkoutTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var _isInitialized = false
    private var _initError: String? = null
    private var pendingCompletion: (() -> Unit)? = null
    private val completions = ConcurrentHashMap<String, () -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicLong(0)
    private var initializationStarted = false
    private val readyCallbacks = mutableListOf<() -> Unit>()
    private val errorCallbacks = mutableListOf<(String) -> Unit>()

    val isInitialized: Boolean get() = _isInitialized
    val initError: String? get() = _initError

    fun initialize(onReady: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        if (_isInitialized) {
            onReady?.let { mainHandler.post(it) }
            return
        }
        onReady?.let(readyCallbacks::add)
        onError?.let(errorCallbacks::add)
        if (initializationStarted) return
        initializationStarted = true

        tts = TextToSpeech(appContext) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    val ttsEngine = tts ?: return@TextToSpeech
                    ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            complete(utteranceId)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            complete(utteranceId)
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            complete(utteranceId)
                        }
                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (interrupted) complete(utteranceId)
                        }
                    })
                    ttsEngine.language = Locale("es", "CL")
                    val langResult = ttsEngine.setLanguage(Locale("es", "CL"))
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        ttsEngine.language = Locale("es", "ES")
                        ttsEngine.setLanguage(Locale("es", "ES"))
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ttsEngine.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                    }
                    _isInitialized = true
                    _initError = null
                    initializationStarted = false
                    val callbacks = readyCallbacks.toList()
                    readyCallbacks.clear()
                    errorCallbacks.clear()
                    callbacks.forEach { callback -> mainHandler.post(callback) }
                }
                else -> {
                    _isInitialized = false
                    _initError = "TTS init error: $status"
                    initializationStarted = false
                    val message = _initError!!
                    val callbacks = errorCallbacks.toList()
                    readyCallbacks.clear()
                    errorCallbacks.clear()
                    callbacks.forEach { callback -> mainHandler.post { callback(message) } }
                }
            }
        }
    }

    fun setOnUtteranceComplete(callback: (() -> Unit)?) {
        pendingCompletion = callback
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.7f, 1.3f))
    }

    fun speakSuggestedWeight(weightKg: Double, exerciseName: String) {
        val rounded = formatWeight(weightKg)
        speak("Carga sugerida para $exerciseName: $rounded.", queueFlush = true)
    }

    fun speakSetConfirmation(
        weightKg: Double?,
        metricValue: Double?,
        metricLabel: String,
        rpe: Double?,
        rir: Int?,
        reachedFailure: Boolean,
        romPercent: Int?,
        tagName: String?,
    ) {
        val parts = mutableListOf<String>()
        if (weightKg != null) parts.add(formatWeight(weightKg))
        if (metricValue != null) parts.add("${formatDecimal(metricValue)} $metricLabel")
        if (rpe != null) parts.add("RPE ${formatDecimal(rpe)}")
        if (rir != null) parts.add("RIR $rir")
        if (reachedFailure) parts.add("al fallo")
        if (romPercent != null) parts.add("ROM $romPercent por ciento")
        if (!tagName.isNullOrBlank()) parts.add("etiqueta $tagName")
        speak("${parts.joinToString(", ")}. ¿Confirmar?", queueFlush = true)
    }
    fun speakSetRegistered(
        weightKg: Double?,
        reps: Double?,
        metricLabel: String,
        trailingText: String? = null,
    ) {
        val summary = buildString {
            if (weightKg != null) append("${formatWeight(weightKg)}, ")
            if (reps != null) append("${formatDecimal(reps)} $metricLabel. ")
        }
        val base = "Serie registrada${if (summary.isNotBlank()) ": $summary" else "."}"
        speak(listOfNotNull(base, trailingText?.trim()?.takeIf(String::isNotBlank)).joinToString(" "), queueFlush = true)
    }

    fun speakAutoConfirmed() {
        speak("Serie confirmada automáticamente.", queueFlush = false)
    }

    fun speakAutoConfirmedWithUndo(weightKg: Double?, reps: Int?, isTimeMode: Boolean) {
        val summary = buildString {
            if (weightKg != null) append(formatWeight(weightKg).replace(" kilos", ""))
            if (weightKg != null && reps != null) append(" por ")
            if (reps != null) append(if (isTimeMode) "$reps segundos" else "$reps")
        }
        val body = if (summary.isNotBlank()) "Serie registrada: $summary." else "Serie registrada."
        speak("$body Di corregir para deshacer.", queueFlush = true)
    }

    fun speakRestAdaptiveSuggestion(plannedSeconds: Int, suggestedSeconds: Int) {
        speak(
            "Descanso programado: ${formatRestSecondsLabel(plannedSeconds)}. " +
                "Sugerido: ${formatRestSecondsLabel(suggestedSeconds)}. Di usar sugerido.",
            queueFlush = false,
        )
    }

    fun speakTenSecondsLeft() {
        speak("Diez segundos.", queueFlush = true)
    }

    fun speakRecoveredReady() {
        speak("Recuperado. Di listo para continuar.", queueFlush = true)
    }

    fun speakRestRemaining(minutes: Int, seconds: Int) {
        val text = when {
            minutes > 0 && seconds > 0 -> "Descansas $minutes minutos $seconds segundos."
            minutes > 0 -> "Descansas $minutes minutos."
            else -> "Descansas $seconds segundos."
        }
        speak(text, queueFlush = true)
    }

    fun speakNextExercise(name: String, restSeconds: Int?) {
        val restText = restSeconds?.let { formatRestTime(it) }.orEmpty()
        val prefix = if (restText.isNotBlank()) ", $restText" else ""
        speak("Siguiente: $name$prefix.", queueFlush = false)
    }

    fun speakRestComplete(exerciseName: String, suggestedWeight: Double?) {
        val weightText = suggestedWeight?.let { w -> " Carga sugerida: ${formatWeight(w)}." }.orEmpty()
        speak("Descanso completo. $exerciseName.$weightText", queueFlush = true)
    }

    fun speakCurrentExercise(name: String, setNumber: Int, totalSets: Int, round: Int? = null) {
        val prefix = if (round != null) "Superserie ronda $round. " else ""
        speak("$prefix$name, serie $setNumber de $totalSets.", queueFlush = true)
    }

    fun speakRestStarted(totalSeconds: Int) {
        val restText = formatRestTime(totalSeconds)
        speak("Descanso iniciado por $restText.", queueFlush = false)
    }

    fun speakRestStartedContextual(seconds: Int, isTransition: Boolean) {
        val restText = formatRestTime(seconds)
        val text = if (isTransition) {
            "Descanso de transición por $restText."
        } else {
            "Ronda completada. Descanso de ronda por $restText."
        }
        speak(text, queueFlush = false)
    }

    fun speakUnilateralSideRegistered(completedSide: String, pendingSide: String) {
        val comp = if (completedSide == "left") "izquierdo" else "derecho"
        val pend = if (pendingSide == "left") "izquierdo" else "derecho"
        speak("Lado $comp registrado. Siguiente: Lado $pend.", queueFlush = true)
    }

    fun speakSessionSaved() {
        speak("Entrenamiento guardado con éxito. ¡Felicitaciones por completar tu sesión!", queueFlush = true)
    }

    fun speakError(message: String) {
        speak(message, queueFlush = true)
    }

    fun speakVoiceOn() {
        speak("Voz activada. Puedes dictar series y comandos.", queueFlush = true)
    }

    fun speakVoiceOff() {
        speak("Voz desactivada.", queueFlush = true)
    }

    fun speak(text: String, queueFlush: Boolean = false) {
        val engine = tts ?: return
        if (!_isInitialized) return

        val queueMode = if (queueFlush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "kpkn-tts-${utteranceCounter.incrementAndGet()}"
        pendingCompletion?.let { callback ->
            completions[utteranceId] = callback
            pendingCompletion = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            engine.speak(text, queueMode, params, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            engine.speak(
                text,
                queueMode,
                hashMapOf(
                    TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId,
                    TextToSpeech.Engine.KEY_PARAM_VOLUME to "1.0",
                ),
            )
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        pendingCompletion = null
        completions.clear()
        readyCallbacks.clear()
        errorCallbacks.clear()
        initializationStarted = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isInitialized = false
    }

    private fun complete(utteranceId: String?) {
        val id = utteranceId ?: return
        val callback = completions.remove(id) ?: return
        mainHandler.post(callback)
    }

    private fun formatWeight(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) {
            "${kg.toLong()} kilos"
        } else {
            "${"%.1f".format(kg).replace(',', '.')} kilos"
        }
    }

    private fun formatDecimal(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            "%.1f".format(value).replace(',', '.')
        }
    }

    private fun formatRestTime(totalSeconds: Int): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return when {
            mins > 0 && secs > 0 -> "descansas $mins minutos $secs segundos"
            mins > 0 -> "descansas $mins minutos"
            else -> "descansas $secs segundos"
        }
    }

    private fun formatRestSecondsLabel(totalSeconds: Int): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return when {
            mins > 0 && secs == 0 -> if (mins == 1) "1 minuto" else "$mins minutos"
            mins > 0 -> "$mins minutos $secs segundos"
            else -> "$secs segundos"
        }
    }
}
