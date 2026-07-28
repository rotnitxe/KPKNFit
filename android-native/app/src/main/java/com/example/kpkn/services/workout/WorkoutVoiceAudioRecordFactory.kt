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
    fun create(bufferBytes: Int): WorkoutVoiceAudioRecord
}

internal object AndroidWorkoutVoiceAudioRecordFactory : WorkoutVoiceAudioRecordFactory {
    override fun create(bufferBytes: Int): WorkoutVoiceAudioRecord {
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.MIC
        }
        return AndroidWorkoutVoiceAudioRecord(
            AudioRecord(
                source,
                WorkoutContinuousVoiceEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            ),
        )
    }
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
            delegate.read(buffer, offset, size, AudioRecord.READ_NON_BLOCKING)
        } else {
            @Suppress("DEPRECATION")
            delegate.read(buffer, offset, size)
        }

    override fun stop() = delegate.stop()

    override fun release() = delegate.release()
}
