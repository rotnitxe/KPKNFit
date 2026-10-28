package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramDecision

/**
 * Respuesta de la persona (revisión semanal, sesiones perdidas) que ya no se puede aplicar. El motor la registra como
 * una [ProgramDecision] con este título, y la pantalla enseña el motivo en lugar de un «sin cambios» mudo.
 */
object UnappliedResponse {
    const val TITLE = "Respuesta no aplicada"

    fun decision(id: String, atMs: Long, source: String, reason: String, weekId: String?): ProgramDecision =
        ProgramDecision(id = id, atMs = atMs, source = source, title = TITLE, detail = reason, weekId = weekId)

    /** Motivo en español si la decisión es de este tipo; null si no lo es. */
    fun reason(decision: ProgramDecision?): String? =
        decision?.takeIf { it.title == TITLE }?.detail?.takeIf { it.isNotBlank() }

    /** Motivo de la primera decisión «no aplicada» que [result] añadió respecto a [original]; null si no hay. */
    fun newReason(original: Program, result: Program): String? =
        result.programDecisions.filter { it !in original.programDecisions }.firstNotNullOfOrNull { reason(it) }
}
