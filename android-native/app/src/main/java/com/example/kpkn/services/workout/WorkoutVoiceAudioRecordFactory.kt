package com.example.kpkn.services.workout

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build

/**
 * Puerto pequeño alrededor de [AudioRecord].
 *
 * El motor depende de esta interfaz para poder probar la exclusión mutua y la
 * recuperación sin crear micrófonos reales en tests JVM.
 */
internal interface WorkoutVoiceAudioRecord {
    val platformRecord: AudioRecord?
    val state: Int
    val recordingState: Int
    val audioSessionId: Int

    fun startRecording()

    fun read(buffer: ShortArray, offset: Int, size: Int): Int

    fun stop()

    fun release()
}

internal fun interface WorkoutVoiceAudioRecordFactory {
    fun create(bufferBytes: Int, audioSource: Int): WorkoutVoiceAudioRecord
}

/**
 * Fuente de AudioRecord según la ruta activa.
 * Con micrófono Bluetooth (SCO/BLE comunicación), la vía de comunicación es la
 * única que varios HAL/OEM (Samsung incluido) alimentan con PCM real; con el
 * mic interno, VOICE_RECOGNITION evita procesamiento de llamada.
 */
internal object WorkoutVoiceAudioSourcePolicy {
    /**
     * [musicAecEnabled]: experimento Fase 4.4. En el mic del teléfono (sin BT) la
     * vía de comunicación habilita el cancelador de eco (AEC) para no auto-escucharse
     * el TTS, a costa del modo de audio. Default OFF hasta validación física.
     */
    fun select(sdkInt: Int, externalCommunicationRouteActive: Boolean, musicAecEnabled: Boolean = false): Int =
        when {
            externalCommunicationRouteActive -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            musicAecEnabled && sdkInt >= Build.VERSION_CODES.N -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            sdkInt >= Build.VERSION_CODES.N -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            else -> MediaRecorder.AudioSource.MIC
        }

    fun nameOf(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "OTHER($source)"
    }
}

internal object AndroidWorkoutVoiceAudioRecordFactory : WorkoutVoiceAudioRecordFactory {
    override fun create(bufferBytes: Int, audioSource: Int): WorkoutVoiceAudioRecord =
        AndroidWorkoutVoiceAudioRecord(
            AudioRecord(
                audioSource,
                WorkoutContinuousVoiceEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            ),
        )
}

private class AndroidWorkoutVoiceAudioRecord(
    private val delegate: AudioRecord,
) : WorkoutVoiceAudioRecord {
    override val platformRecord: AudioRecord get() = delegate
    override val state: Int get() = delegate.state
    override val recordingState: Int get() = delegate.recordingState
    override val audioSessionId: Int get() = delegate.audioSessionId

    override fun startRecording() = delegate.startRecording()

    override fun read(buffer: ShortArray, offset: Int, size: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            delegate.read(buffer, offset, size, AudioRecord.READ_BLOCKING)
        } else {
            @Suppress("DEPRECATION")
            delegate.read(buffer, offset, size)
        }

    override fun stop() = delegate.stop()

    override fun release() = delegate.release()
}
