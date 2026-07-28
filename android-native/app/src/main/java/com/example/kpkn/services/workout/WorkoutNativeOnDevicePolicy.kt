package com.example.kpkn.services.workout

/**
 * Política estricta: el fallback nativo sólo puede usar reconocimiento on-device.
 * Nunca [android.speech.SpeechRecognizer.createSpeechRecognizer] (ruta remota posible).
 */
object WorkoutNativeOnDevicePolicy {
    /** API 31 = Android 12 (S), donde existe createOnDeviceSpeechRecognizer. */
    const val MIN_SDK_ON_DEVICE = 31

    fun isStrictlyLocalFallbackAllowed(
        sdkInt: Int,
        onDeviceRecognitionAvailable: Boolean,
    ): Boolean = sdkInt >= MIN_SDK_ON_DEVICE && onDeviceRecognitionAvailable

    fun unavailableReason(sdkInt: Int, onDeviceRecognitionAvailable: Boolean): String? {
        if (sdkInt < MIN_SDK_ON_DEVICE) {
            return "Fallback nativo local no disponible en este Android (requiere 12+)"
        }
        if (!onDeviceRecognitionAvailable) {
            return "Reconocimiento on-device no disponible; no se usará ruta remota"
        }
        return null
    }
}
