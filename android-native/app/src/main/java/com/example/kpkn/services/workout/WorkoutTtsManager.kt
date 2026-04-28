package com.example.kpkn.services.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class WorkoutTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var _isInitialized = false
    private var _initError: String? = null
    private var _onUtteranceComplete: (() -> Unit)? = null

    val isInitialized: Boolean get() = _isInitialized
    val initError: String? get() = _initError

    fun initialize(onReady: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        tts?.stop()
        tts?.shutdown()

        tts = TextToSpeech(appContext) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    val ttsEngine = tts ?: return@TextToSpeech
                    ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            _onUtteranceComplete?.invoke()
                            _onUtteranceComplete = null
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _onUtteranceComplete?.invoke()
                            _onUtteranceComplete = null
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _onUtteranceComplete?.invoke()
                            _onUtteranceComplete = null
                        }
                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (interrupted) {
                                _onUtteranceComplete?.invoke()
                                _onUtteranceComplete = null
                            }
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
                    onReady?.invoke()
                }
                else -> {
                    _isInitialized = false
                    _initError = "TTS init error: $status"
                    onError?.invoke(_initError!!)
                }
            }
        }
    }

    fun setOnUtteranceComplete(callback: (() -> Unit)?) {
        _onUtteranceComplete = callback
    }

    fun speakSuggestedWeight(weightKg: Double, exerciseName: String) {
        val rounded = formatWeight(weightKg)
        speak("Carga sugerida para $exerciseName: $rounded.", queueFlush = true)
    }

    fun speakSetConfirmation(weightKg: Double?, reps: Int?, rpe: Double?, rir: Int?, isTimeMode: Boolean) {
        val parts = mutableListOf<String>()
        if (weightKg != null) parts.add("${formatWeight(weightKg)}")
        if (reps != null) parts.add(if (isTimeMode) "$reps segundos" else "$reps repeticiones")
        if (rpe != null) parts.add("RPE ${formatDecimal(rpe)}")
        if (rir != null) parts.add("RIR $rir")
        val summary = parts.joinToString(", ")
        speak("$summary. ¿Confirmar?", queueFlush = true)
    }

    fun speakSetRegistered(weightKg: Double?, reps: Int?, isTimeMode: Boolean) {
        val summary = buildString {
            if (weightKg != null) append("${formatWeight(weightKg)}, ")
            if (reps != null) append(if (isTimeMode) "$reps segundos. " else "$reps repeticiones. ")
        }
        speak("Serie registrada${if (summary.isNotBlank()) ": $summary" else "."}", queueFlush = true)
    }

    fun speakAutoConfirmed() {
        speak("Serie confirmada automáticamente.", queueFlush = false)
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

    fun speakCurrentExercise(name: String, setNumber: Int, totalSets: Int) {
        speak("$name, serie $setNumber de $totalSets.", queueFlush = true)
    }

    fun speakError(message: String) {
        speak(message, queueFlush = true)
    }

    fun speakVoiceOn() {
        speak("Voz activada. Di la palabra de activación para comandos.", queueFlush = true)
    }

    fun speakVoiceOff() {
        speak("Voz desactivada.", queueFlush = true)
    }

    fun speak(text: String, queueFlush: Boolean = false) {
        val engine = tts ?: return
        if (!_isInitialized) return

        val queueMode = if (queueFlush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = System.currentTimeMillis().toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.speak(text, queueMode, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            engine.speak(text, queueMode, hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId))
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        _onUtteranceComplete = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isInitialized = false
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
}
