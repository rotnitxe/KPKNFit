package com.example.kpkn.services.workout

import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVoiceAudioSourcePolicyTest {
    @Test
    fun bluetoothRoute_forcesCommunicationSource() {
        assertEquals(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            WorkoutVoiceAudioSourcePolicy.select(sdkInt = 34, externalCommunicationRouteActive = true),
        )
        assertEquals(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            WorkoutVoiceAudioSourcePolicy.select(sdkInt = 28, externalCommunicationRouteActive = true),
        )
    }

    @Test
    fun phoneMic_keepsVoiceRecognition() {
        assertEquals(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            WorkoutVoiceAudioSourcePolicy.select(sdkInt = 34, externalCommunicationRouteActive = false),
        )
        assertEquals(
            MediaRecorder.AudioSource.MIC,
            WorkoutVoiceAudioSourcePolicy.select(sdkInt = 23, externalCommunicationRouteActive = false),
        )
    }

    @Test
    fun phoneMic_withMusicAec_usesCommunicationSource() {
        // Fase 4.4: AEC opt-in en el mic interno se traduce a VOICE_COMMUNICATION.
        assertEquals(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            WorkoutVoiceAudioSourcePolicy.select(
                sdkInt = 34,
                externalCommunicationRouteActive = false,
                musicAecEnabled = true,
            ),
        )
    }

    @Test
    fun off_is_identical_to_previous_behavior() {
        // Default OFF: comportamiento histórico (sin cambio de comportamiento).
        assertEquals(
            WorkoutVoiceAudioSourcePolicy.select(34, externalCommunicationRouteActive = false, musicAecEnabled = false),
            WorkoutVoiceAudioSourcePolicy.select(34, externalCommunicationRouteActive = false),
        )
        assertEquals(
            WorkoutVoiceAudioSourcePolicy.select(23, externalCommunicationRouteActive = false, musicAecEnabled = false),
            WorkoutVoiceAudioSourcePolicy.select(23, externalCommunicationRouteActive = false),
        )
    }
}
