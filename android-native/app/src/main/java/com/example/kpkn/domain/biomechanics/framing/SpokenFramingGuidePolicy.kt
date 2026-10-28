package com.example.kpkn.domain.biomechanics.framing

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind

/**
 * Política inicial de la guía hablada: guía antes de grabar y silencio no urgente durante la serie.
 *
 * El evaluador congela el veredicto mientras se graba, así que la política sólo recibe
 * evaluaciones previas. Deduplica el mismo problema, limita la frecuencia entre mensajes
 * distintos y anuncia «Encuadre listo» una sola vez por racha estable.
 */
class SpokenFramingGuidePolicy(
    private val repeatAfterMs: Long = 12_000L,
    private val minChangeIntervalMs: Long = 2_500L,
) {
    private var lastText: String? = null
    private var lastSpokenAtMs: Long? = null
    private var readyAnnounced = false
    private var unavailableAnnounced = false

    fun reset() {
        lastText = null
        lastSpokenAtMs = null
        readyAnnounced = false
        unavailableAnnounced = false
    }

    /** Registra una locución manual (botón de repetir) para no duplicarla al evaluar. */
    fun noteSpoken(text: String, nowMs: Long) {
        lastText = text
        lastSpokenAtMs = nowMs
    }

    fun onEvaluation(
        status: FramingCheckStatus,
        verdict: FramingVerdict,
        preferredView: ViewKind = ViewKind.SIDE,
        nowMs: Long,
    ): String? {
        if (status == FramingCheckStatus.UNAVAILABLE) {
            if (unavailableAnnounced) return null
            unavailableAnnounced = true
            readyAnnounced = false
            return accept(FramingInstruction.UNAVAILABLE_SPEECH, nowMs)
        }
        unavailableAnnounced = false
        if (status == FramingCheckStatus.CHECKING) return null

        if (verdict.ok) {
            if (readyAnnounced) return null
            readyAnnounced = true
            return accept(FramingInstruction.READY_SPEECH, nowMs)
        }

        readyAnnounced = false
        val issue = verdict.issues.firstOrNull() ?: return null
        val text = FramingInstruction.spoken(issue, preferredView)
        val elapsed = lastSpokenAtMs?.let { nowMs - it }
        if (text == lastText && elapsed != null && elapsed < repeatAfterMs) return null
        if (text != lastText && elapsed != null && elapsed < minChangeIntervalMs) return null
        return accept(text, nowMs)
    }

    private fun accept(text: String, nowMs: Long): String {
        lastText = text
        lastSpokenAtMs = nowMs
        return text
    }
}
