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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WizChatFeedbackController(private val context: android.content.Context, @RawRes soundRes: Int = 0) {
    private val soundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loadingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var soundId = 0
    init {
        // Synthesizing the short sound and writing its cache file must not block composition.
        loadingScope.launch {
            val path = if (soundRes == 0) runCatching { createPluckWav(context) }.getOrNull() else null
            if (!isActive) return@launch
            soundId = runCatching { if (soundRes != 0) soundPool.load(context, soundRes, 1)
                else if (path != null) soundPool.load(path, 1) else 0 }.getOrDefault(0)
        }
    }
    private val played = mutableSetOf<String>()
    private var appForeground = false
    fun setAppForeground(value: Boolean) { appForeground = value }
    fun playOnce(messageId: String, enabled: Boolean) {
        if (!enabled || messageId in played || soundId == 0) return
        if (!appForeground) return
        if (!SystemAudioHelper.shouldPlaySound(context, soundsEnabled = true)) return
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (notificationManager?.currentInterruptionFilter in setOf(
                android.app.NotificationManager.INTERRUPTION_FILTER_NONE,
                android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS,
            )) return
        played += messageId
        soundPool.play(soundId, .28f, .28f, 1, 0, 1f)
    }
    fun release() { loadingScope.cancel(); soundPool.release() }

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
