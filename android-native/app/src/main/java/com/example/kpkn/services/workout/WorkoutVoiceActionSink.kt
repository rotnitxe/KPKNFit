package com.example.kpkn.services.workout

/**
 * Puente desacoplado entre el runtime de voz y quien ejecuta acciones reales.
 */
fun interface WorkoutVoiceActionSink {
    fun onVoiceCommand(command: VoiceSessionCommand)
}
