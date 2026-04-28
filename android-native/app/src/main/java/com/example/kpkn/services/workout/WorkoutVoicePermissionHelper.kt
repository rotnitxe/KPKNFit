package com.example.kpkn.services.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat

object WorkoutVoicePermissionHelper {

    data class VoiceCapabilityResult(
        val hasAudioPermission: Boolean,
        val speechRecognizerAvailable: Boolean,
        val ttsAvailable: Boolean,
        val canUseVoice: Boolean,
        val blockingReason: String? = null,
    )

    fun checkVoiceCapability(context: Context): VoiceCapabilityResult {
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context)

        val ttsAvailable = try {
            var available = false
            val tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) available = true
            }
            Thread.sleep(100)
            tts.stop()
            tts.shutdown()
            available
        } catch (_: Exception) {
            false
        }

        val blockingReason = buildString {
            if (!hasAudioPerm) append("Permiso de micrófono no concedido. ")
            if (!recognizerAvailable) append("Reconocimiento de voz no disponible. ")
            if (!ttsAvailable) append("Texto a voz no disponible. ")
        }.trim().ifBlank { null }

        return VoiceCapabilityResult(
            hasAudioPermission = hasAudioPerm,
            speechRecognizerAvailable = recognizerAvailable,
            ttsAvailable = ttsAvailable,
            canUseVoice = hasAudioPerm && recognizerAvailable,
            blockingReason = blockingReason,
        )
    }

    fun needsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
    }
}
