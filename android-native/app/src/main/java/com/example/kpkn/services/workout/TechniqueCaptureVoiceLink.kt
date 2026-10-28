package com.example.kpkn.services.workout

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Resultado de una orden de inicio, sin prometer un cambio que el grabador no confirmó. */
enum class TechniqueCaptureStartResult {
    STARTED,
    ALREADY_RECORDING,
    CHECKING,
    NOT_READY,
    BUSY,
    UNAVAILABLE,
}

enum class TechniqueCaptureStopResult {
    STOPPED,
    NOT_RECORDING,
    BUSY,
    UNAVAILABLE,
}

/**
 * Puente de proceso entre el controlador de voz y el grabador de técnica abierto.
 *
 * Los comandos de vídeo sólo se aceptan con el grabador abierto. El grabador registra sus
 * manejadores y el controlador consulta el resultado real: repetir una orden no duplica la captura.
 * También arbitra la guía hablada: una frase recién pronunciada por la guía se ignora al reconocer.
 */
object TechniqueCaptureVoiceLink {
    data class Handlers(
        val start: () -> TechniqueCaptureStartResult,
        val stop: () -> TechniqueCaptureStopResult,
    )

    private val handlers = AtomicReference<Handlers?>(null)

    private val _recorderOpen = MutableStateFlow(false)
    val recorderOpen: StateFlow<Boolean> = _recorderOpen.asStateFlow()

    private val _voiceEnabled = MutableStateFlow(false)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _voiceToggleRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val voiceToggleRequests: SharedFlow<Unit> = _voiceToggleRequests.asSharedFlow()

    private val _guideSpeech = MutableStateFlow<String?>(null)
    /** La frase que la guía de encuadre acaba de decir (la cápsula enseña «KPKN habla» con ella). */
    val guideSpeech: StateFlow<String?> = _guideSpeech.asStateFlow()

    private val _stopGuideRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** «Detener» de la cápsula: el grabador calla la guía. */
    val stopGuideRequests: SharedFlow<Unit> = _stopGuideRequests.asSharedFlow()

    @Volatile
    private var lastGuideText: String? = null

    @Volatile
    private var lastGuideAtMs: Long = 0L

    fun attach(value: Handlers) {
        handlers.set(value)
        _recorderOpen.value = true
    }

    fun detach(value: Handlers) {
        handlers.compareAndSet(value, null)
        _recorderOpen.value = false
        clearGuideSpeech()
    }

    fun setRecorderOpen(open: Boolean) {
        _recorderOpen.value = open
        if (!open) clearGuideSpeech()
    }

    fun setVoiceEnabled(enabled: Boolean) {
        _voiceEnabled.value = enabled
    }

    fun requestVoiceToggle() {
        _voiceToggleRequests.tryEmit(Unit)
    }

    fun tryStart(): TechniqueCaptureStartResult =
        handlers.get()?.start() ?: TechniqueCaptureStartResult.UNAVAILABLE

    fun tryStop(): TechniqueCaptureStopResult =
        handlers.get()?.stop() ?: TechniqueCaptureStopResult.UNAVAILABLE

    fun noteGuideSpoken(text: String, nowMs: Long = monotonicMs()) {
        lastGuideText = text
        lastGuideAtMs = nowMs
        _guideSpeech.value = text
    }

    fun clearGuideSpeech() {
        lastGuideText = null
        lastGuideAtMs = 0L
        _guideSpeech.value = null
    }

    /** La frase ya se dijo: la cápsula deja de enseñarla (el eco sigue filtrándose con su ventana). */
    fun guideSpeechShown() {
        _guideSpeech.value = null
    }

    fun requestStopGuide() {
        _stopGuideRequests.tryEmit(Unit)
        clearGuideSpeech()
    }

    /** Ignora el eco de la propia guía para que la síntesis de voz no active comandos. */
    fun isGuideEcho(
        transcript: String,
        nowMs: Long = monotonicMs(),
        windowMs: Long = 8_000L,
    ): Boolean {
        if (!_recorderOpen.value) return false
        val spoken = lastGuideText ?: return false
        if (nowMs - lastGuideAtMs > windowMs) return false
        val heard = normalize(transcript)
        val guide = normalize(spoken)
        if (heard.isBlank() || guide.isBlank()) return false
        return heard == guide || guide.contains(heard) || heard.contains(guide)
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000L

    private fun normalize(text: String): String = Normalizer.normalize(
        text.lowercase(Locale.ROOT),
        Normalizer.Form.NFD,
    ).replace("\\p{Mn}+".toRegex(), "")
        .replace(Regex("[^a-z0-9ñ ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
