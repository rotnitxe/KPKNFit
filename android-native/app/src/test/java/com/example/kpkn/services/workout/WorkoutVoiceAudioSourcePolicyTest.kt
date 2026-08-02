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
}
