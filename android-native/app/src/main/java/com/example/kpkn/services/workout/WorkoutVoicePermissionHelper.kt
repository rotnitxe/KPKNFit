package com.example.kpkn.services.workout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
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
            val ttsIntent = Intent("android.intent.action.TTS_SERVICE")
            val resolveInfo = context.packageManager.queryIntentServices(
                ttsIntent, PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfo.isNotEmpty()
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
