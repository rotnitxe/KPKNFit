package com.example.kpkn.services.workout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

    /**
     * Chequeo apto para Main: no lista assets, no toca cacheDir y no extrae el modelo.
     *
     * La integridad real del modelo se valida en [WorkoutVoskModelStore.prepare] dentro
     * del actor IO. Vosk continúa siendo utilizable aunque no exista SpeechRecognizer.
     */
    fun checkVoiceCapability(context: Context): VoiceCapabilityResult {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        val recognizerAvailable = runCatching {
            SpeechRecognizer.isRecognitionAvailable(context)
        }.getOrDefault(false)

        val ttsAvailable = runCatching {
            context.packageManager.queryIntentServices(
                Intent("android.intent.action.TTS_SERVICE"),
                PackageManager.MATCH_DEFAULT_ONLY,
            ).isNotEmpty()
        }.getOrDefault(false)

        return VoiceCapabilityResult(
            hasAudioPermission = hasAudioPermission,
            speechRecognizerAvailable = recognizerAvailable,
            ttsAvailable = ttsAvailable,
            canUseVoice = hasAudioPermission,
            blockingReason = if (hasAudioPermission) {
                null
            } else {
                "Permiso de micrófono no concedido."
            },
        )
    }

    fun needsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) != PackageManager.PERMISSION_GRANTED

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * BLUETOOTH_CONNECT se solicita para seleccionar el micrófono Bluetooth.
     * Si se deniega, Vosk sigue usable con el micrófono del teléfono.
     * POST_NOTIFICATIONS es independiente de la captura.
     */
    fun permissionsToRequestForVoiceEnable(
        context: Context,
        includeNotifications: Boolean,
    ): Array<String> {
        val needed = mutableListOf<String>()
        if (needsPermission(context)) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasBluetoothConnectPermission(context)
        ) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (
            includeNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        return needed.toTypedArray()
    }
}
