package com.example.kpkn.services.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

object SystemAudioHelper {

    data class TransientDuckHandle(
        val audioManager: AudioManager,
        val request: AudioFocusRequest? = null,
    )

    fun getRingerModeVolume(context: Context): Float {
        return when (getRingerMode(context)) {
            AudioManager.RINGER_MODE_SILENT -> 0f
            AudioManager.RINGER_MODE_VIBRATE -> 0f
            AudioManager.RINGER_MODE_NORMAL -> 0.6f
            else -> 1f
        }
    }

    fun getRingerMode(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioManager.RINGER_MODE_NORMAL
        return audioManager.ringerMode
    }

    fun isNormalRinger(context: Context): Boolean {
        return getRingerMode(context) == AudioManager.RINGER_MODE_NORMAL
    }

    fun shouldPlaySound(context: Context, soundsEnabled: Boolean): Boolean {
        return soundsEnabled && isNormalRinger(context)
    }

    fun shouldVibrate(context: Context, hapticEnabled: Boolean): Boolean {
        return hapticEnabled && getRingerMode(context) != AudioManager.RINGER_MODE_SILENT
    }

    fun requestTransientDuckFocus(context: Context): TransientDuckHandle? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
            if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                TransientDuckHandle(audioManager = audioManager, request = request)
            } else {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val granted = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (granted) {
                TransientDuckHandle(audioManager = audioManager)
            } else {
                null
            }
        }
    }

    fun abandonTransientDuckFocus(handle: TransientDuckHandle?) {
        val safeHandle = handle ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeHandle.request?.let { safeHandle.audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            safeHandle.audioManager.abandonAudioFocus(null)
        }
    }

    fun requestTransientDuckForVoice(context: Context): TransientDuckHandle? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
            if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                TransientDuckHandle(audioManager = audioManager, request = request)
            } else {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val granted = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (granted) {
                TransientDuckHandle(audioManager = audioManager)
            } else {
                null
            }
        }
    }
}
