package com.example.kpkn.screens.onboarding

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kpkn.services.workout.SystemAudioHelper

class WizChatFeedbackController(private val context: android.content.Context, @RawRes soundRes: Int = 0) {
    private val soundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    /** Bundled deterministic mono pluck; it is written to cache only because the source tree has no raw assets. */
    private val soundId = runCatching {
        if (soundRes != 0) soundPool.load(context, soundRes, 1) else soundPool.load(createPluckWav(context), 1)
    }.getOrDefault(0)
    private val played = mutableSetOf<String>()
    private var appForeground = false
    fun setAppForeground(value: Boolean) { appForeground = value }
    fun playOnce(messageId: String, enabled: Boolean) {
        if (!enabled || messageId in played || soundId == 0) return
        if (!appForeground) return
        if (!SystemAudioHelper.shouldPlaySound(context, soundsEnabled = true)) return
        played += messageId
        soundPool.play(soundId, .28f, .28f, 1, 0, 1f)
    }
    fun release() { soundPool.release() }

    private fun createPluckWav(context: android.content.Context): String {
        val file = java.io.File(context.cacheDir, "wizchat-response.wav")
        if (file.exists()) return file.absolutePath
        val sampleRate = 44_100
        val frames = (sampleRate * 0.12).toInt()
        val pcm = ByteArray(frames * 2)
        for (index in 0 until frames) {
            val t = index.toDouble() / sampleRate
            val envelope = kotlin.math.exp(-28.0 * t)
            val sample = (kotlin.math.sin(2.0 * Math.PI * (520.0 + 180.0 * t) * t) * envelope * 0.34 * Short.MAX_VALUE).toInt().toShort()
            pcm[index * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[index * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
        }
        val bytes = java.io.ByteArrayOutputStream()
        fun ascii(value: String) { bytes.write(value.toByteArray(Charsets.US_ASCII)) }
        fun littleInt(value: Int) { bytes.write(byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())) }
        fun littleShort(value: Int) { bytes.write(byteArrayOf(value.toByte(), (value ushr 8).toByte())) }
        ascii("RIFF"); littleInt(36 + pcm.size); ascii("WAVEfmt "); littleInt(16); littleShort(1); littleShort(1); littleInt(sampleRate); littleInt(sampleRate * 2); littleShort(2); littleShort(16); ascii("data"); littleInt(pcm.size); bytes.write(pcm)
        file.writeBytes(bytes.toByteArray())
        return file.absolutePath
    }
}

@Composable
fun rememberWizChatFeedbackController(): WizChatFeedbackController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { WizChatFeedbackController(context) }
    DisposableEffect(controller, lifecycleOwner) {
        controller.setAppForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.setAppForeground(true)
                Lifecycle.Event.ON_STOP -> controller.setAppForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.setAppForeground(false)
            controller.release()
        }
    }
    return controller
}
