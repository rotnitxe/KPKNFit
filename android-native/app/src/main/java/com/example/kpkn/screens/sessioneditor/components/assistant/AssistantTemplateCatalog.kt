package com.example.kpkn.screens.sessioneditor.components.assistant

import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec

/** Los tres niveles del catálogo, con su nombre («Fácil», «Medio», «Exigente»). */
internal val AsLevelNames = listOf("Fácil", "Medio", "Exigente")

/** Una tarjeta del catálogo de plantillas: lo que se ve y lo que hace falta para filtrarla y agruparla. */
internal data class AsTemplateCard(
    val id: String,
    val name: String,
    /** La línea propia de la tarjeta (descripción corta real, o el dato de equipo si no tiene). */
    val line: String,
    val minutes: Int?,
    val exercises: Int,
    val sets: Int,
    /** 0 = Fácil, 1 = Medio, 2 = Exigente. */
    val level: Int,
    val muscles: String,
    /** Tipos de sesión a los que pertenece (Piernas, Torso, Brazos…). */
    val types: Set<String>,
    /** Grupo del catálogo (Pierna, Torso, Brazo, Full body) y rutina (split) para agrupar. */
    val group: String,
    val routine: String,
    val searchText: String,
    val cover: LineCoverSpec,
)

internal enum class AsGroupMode(val label: String) { GROUP("Grupo"), ROUTINE("Rutina"), LEVEL("Nivel"), DURATION("Duración") }

/** Los cortes de duración del filtro: corta ≤ 45 min, media 46–75, larga ≥ 76 (los de `SessionTemplateDurationBucket`). */
internal enum class AsDuration(val label: String) { SHORT("Corta"), MEDIUM("Media"), LONG("Larga") }

internal data class AsTemplateFilters(
    val query: String = "",
    val type: String? = null,
    val level: Int? = null,
    val duration: AsDuration? = null,
    val group: AsGroupMode = AsGroupMode.GROUP,
) {
    val activeCount: Int get() = (if (level != null) 1 else 0) + (if (duration != null) 1 else 0)
    val anyFilter: Boolean get() = query.isNotBlank() || type != null || level != null || duration != null
}

internal data class AsTemplateSection(val key: String, val cards: List<AsTemplateCard>)

/** Filtrado y agrupación del catálogo (la lógica del lienzo sobre las tarjetas): pura y comprobable. */
internal object AsTemplateCatalog {

    fun matches(card: AsTemplateCard, filters: AsTemplateFilters): Boolean {
        val q = filters.query.trim().lowercase()
        if (q.isNotEmpty() && !card.searchText.contains(q)) return false
        if (filters.type != null && filters.type !in card.types) return false
        if (filters.level != null && card.level != filters.level) return false
        if (filters.duration != null && !durationOf(card).let { it == filters.duration }) return false
        return true
    }

    fun filter(cards: List<AsTemplateCard>, filters: AsTemplateFilters): List<AsTemplateCard> = cards.filter { matches(it, filters) }

    fun durationOf(card: AsTemplateCard): AsDuration {
        val minutes = card.minutes ?: 0
        return when {
            minutes <= 45 -> AsDuration.SHORT
            minutes <= 75 -> AsDuration.MEDIUM
            else -> AsDuration.LONG
        }
    }

    /** Secciones en el orden en que aparece cada clave por primera vez en [cards]. */
    fun sections(cards: List<AsTemplateCard>, mode: AsGroupMode): List<AsTemplateSection> {
        val by = linkedMapOf<String, MutableList<AsTemplateCard>>()
        cards.forEach { by.getOrPut(keyOf(it, mode)) { mutableListOf() }.add(it) }
        return by.map { (key, list) -> AsTemplateSection(key, list) }
    }

    fun keyOf(card: AsTemplateCard, mode: AsGroupMode): String = when (mode) {
        AsGroupMode.GROUP -> card.group
        AsGroupMode.ROUTINE -> card.routine
        AsGroupMode.LEVEL -> AsLevelNames[card.level.coerceIn(0, 2)]
        AsGroupMode.DURATION -> when (durationOf(card)) {
            AsDuration.SHORT -> "Corta (≤45 min)"
            AsDuration.MEDIUM -> "Media (46–75 min)"
            AsDuration.LONG -> "Larga (≥76 min)"
        }
    }

    /** «N plantillas» sin filtros; con filtros, «N resultado(s)» (vacío si no hay ninguno). */
    fun resultLabel(total: Int, shown: Int, filters: AsTemplateFilters): String = when {
        !filters.anyFilter -> "$total plantillas"
        shown == 0 -> ""
        else -> "$shown resultado${if (shown == 1) "" else "s"}"
    }
}
