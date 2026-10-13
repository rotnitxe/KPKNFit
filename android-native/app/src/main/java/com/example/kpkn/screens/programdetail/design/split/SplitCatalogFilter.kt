package com.example.kpkn.screens.programdetail.design.split

import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.training.SplitApplicationEngine

/**
 * Repartos del catálogo que cumplen la búsqueda y la etiqueta (P5). Busca en nombre, descripción, ventajas y precauciones. Con
 * «Personalizado» no lista ninguno: ese caso lo pinta su propio módulo.
 */
internal fun filterSplitCatalog(splits: List<SplitTemplate>, query: String, tag: SplitTag?): List<SplitTemplate> {
    if (tag == SplitTag.PERSONALIZADO) return emptyList()
    val needle = query.trim().lowercase()
    return splits.filter { split ->
        split.isVisibleForApplication &&
            split.id != "custom" &&
            (tag == null || tag in split.tags) &&
            (
                needle.isEmpty() ||
                    split.name.lowercase().contains(needle) ||
                    split.description.lowercase().contains(needle) ||
                    split.pros.any { it.lowercase().contains(needle) } ||
                    split.cons.any { it.lowercase().contains(needle) }
                )
    }
}

/** Nombre de una etiqueta en la app, en español y tipo frase. */
internal fun splitTagLabel(tag: SplitTag): String = when (tag) {
    SplitTag.RECOMENDADO_KPKN -> "Recomendado KPKN"
    SplitTag.ALTA_FRECUENCIA -> "Alta frecuencia"
    SplitTag.BAJA_FRECUENCIA -> "Baja frecuencia"
    SplitTag.BALANCEADO -> "Balanceado"
    SplitTag.ALTO_VOLUMEN -> "Alto volumen"
    SplitTag.ALTA_TOLERANCIA -> "Alta tolerancia"
    SplitTag.PERSONALIZADO -> "Personalizado"
    SplitTag.POWERLIFTING -> "Powerlifting"
}

/**
 * Motivo por el que un reparto no se puede crear con sesiones pre-creadas, o null si cada día tiene plantilla compatible. Es la
 * misma comprobación que muestra la hoja de aplicar, pero para el catálogo.
 */
internal fun splitUnavailableReason(split: SplitTemplate, templates: List<SessionTemplate>): String? {
    val days = SplitApplicationEngine.prebuiltWeekPreview(split = split, templates = templates).days
    val blocked = days.firstOrNull { !it.isAvailable } ?: return null
    return "${blocked.dayLabel}: ${blocked.unavailabilityReason ?: "sin plantilla compatible"}"
}
