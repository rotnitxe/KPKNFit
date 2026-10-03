package com.example.kpkn.data.programs

import com.example.kpkn.domain.text.SpanishPlurals

/** Nivel en español llano: la UI nunca muestra `BEGINNER` ni `level.name`. */
val CatalogLevel.label: String
    get() = when (this) {
        CatalogLevel.BEGINNER -> "Principiante"
        CatalogLevel.INTERMEDIATE -> "Intermedio"
        CatalogLevel.ADVANCED -> "Avanzado"
    }

/**
 * Etiquetas derivadas de una entrada del catálogo (§1.1 del diseño editorial).
 * Funciones puras: no hay ningún texto escrito a mano fuera de estas reglas, de
 * modo que la concordancia («1 día», «2–3 días») y el vocabulario son los mismos
 * en el wizard, la biblioteca y la revisión.
 */
object PlanLabels {
    /** Separador de las líneas compuestas («Semana que se repite · Todos los niveles»). */
    const val SEPARATOR = " · "

    const val ALL_LEVELS = "Todos los niveles"

    /**
     * «Todos los niveles» si son los tres; «Principiante a intermedio» o
     * «Intermedio a avanzado» si son dos contiguos; el nombre del nivel si es uno.
     */
    fun levelLabel(levels: Set<CatalogLevel>): String {
        val ordered = levels.sorted()
        return when {
            ordered.isEmpty() || ordered.size == CatalogLevel.entries.size -> ALL_LEVELS
            ordered.size == 1 -> ordered.first().label
            ordered.size == 2 && ordered[1].ordinal - ordered[0].ordinal == 1 ->
                "${ordered[0].label} a ${ordered[1].label.lowercase()}"
            // Dos niveles no contiguos no existen en el catálogo; se nombran tal cual.
            else -> "${ordered[0].label} y ${ordered[1].label.lowercase()}"
        }
    }

    /**
     * «Semana que se repite», «Ciclo de N semanas que se repite» o «N semanas»,
     * con la concordancia de [SpanishPlurals].
     */
    fun durationLabel(duration: CatalogDuration, weeks: Int): String = when (duration) {
        CatalogDuration.REPEATING_WEEK -> "Semana que se repite"
        CatalogDuration.REPEATING_CYCLE -> "Ciclo de ${SpanishPlurals.weeks(weeks)} que se repite"
        CatalogDuration.FINITE_CYCLE -> SpanishPlurals.weeks(weeks)
    }

    /** «1 día por semana», «4 días por semana»; los rangos llevan raya (U+2013): «2–3 días por semana». */
    fun frequencyLabel(range: IntRange): String =
        if (range.first == range.last) {
            "${SpanishPlurals.days(range.first)} por semana"
        } else {
            "${range.first}–${range.last} días por semana"
        }

    /** `{duración} · {nivel}`; los días van en el motivo del wizard, no aquí. */
    fun subtitle(duration: CatalogDuration, weeks: Int, levels: Set<CatalogLevel>): String =
        durationLabel(duration, weeks) + SEPARATOR + levelLabel(levels)

    fun subtitle(entry: CatalogEntry): String = subtitle(entry.duration, weeksOf(entry), entry.levels)

    /** `{frecuencia} · {duración} · {nivel}`, la línea de metadatos de la biblioteca. */
    fun metaLine(entry: CatalogEntry): String =
        frequencyLabel(entry.supportedFrequencies) + SEPARATOR + subtitle(entry)

    /**
     * Procedencia en palabras: «Plan KPKN», «Original fiel · {autor}»,
     * «Adaptación KPKN · desde {autor}», «Versión KPKN del método de {autor}» o
     * «Versión anterior». El autor sale de la procedencia declarada (autoradas)
     * o, si no existe, de `entry.sourceAuthor`.
     */
    fun provenanceLabel(entry: CatalogEntry): String {
        val author = (entry.provenance?.sourceAuthor ?: entry.sourceAuthor)?.takeIf { it.isNotBlank() }
        return when (entry.origin) {
            PlanOrigin.KPKN -> "Plan KPKN"
            PlanOrigin.ORIGINAL -> author?.let { "Original fiel · $it" } ?: "Original fiel"
            PlanOrigin.ADAPTED -> author?.let { "Adaptación KPKN · desde $it" } ?: "Adaptación KPKN"
            PlanOrigin.KPKN_VERSION -> author?.let { "Versión KPKN del método de $it" } ?: "Versión KPKN de un método publicado"
            PlanOrigin.LEGACY_VERSION -> PersonalizedPlanCatalog.LEGACY_VERSION_LABEL
        }
    }

    /** Semanas del ciclo: lo declarado por la entrada o, en su defecto, lo que dicen la receta o la plantilla. */
    private fun weeksOf(entry: CatalogEntry): Int =
        entry.durationWeeks ?: entry.recipe?.weeks?.size ?: entry.template?.weeks ?: 1
}
