package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2

/** Configuración del catálogo v2 propuesta para un nombre importado. */
data class ExerciseNameCandidate(
    val configurationId: String,
    /** Nombre canónico de la definición, el mismo que muestra el catálogo. */
    val displayName: String,
    /** Parecido 0..1 con el nombre canónico o un alias del ejercicio (sin acentos, mayúsculas ni plurales). */
    val confidence: Double,
)

/** Hasta tres configuraciones para un nombre importado, de mayor a menor confianza. */
data class ExerciseNameMatch(
    val rawName: String,
    val candidates: List<ExerciseNameCandidate>,
) {
    val best: ExerciseNameCandidate?
        get() = candidates.firstOrNull()

    /** Confianza alta: la mejor llega a 0,8 y saca al menos 0,15 a la segunda (sin segunda, cuenta como 0). */
    val isConfident: Boolean
        get() {
            val first = best ?: return false
            val second = candidates.getOrNull(1)?.confidence ?: 0.0
            return first.confidence >= CONFIDENT_MIN && first.confidence - second >= CONFIDENT_MARGIN
        }
}

private const val CONFIDENT_MIN = 0.8
private const val CONFIDENT_MARGIN = 0.15
private const val MAX_CANDIDATES = 3
private const val SEARCH_POOL = 10

/** Palabras vacías que no cuentan al comparar nombres («press de banca» = «press banca»). */
private val NAME_STOP_WORDS = setOf("de", "del", "la", "el", "en", "con", "para", "por", "al", "a", "y", "o")

/**
 * Empareja nombres importados con el catálogo v2. Reutiliza [ExerciseCatalogV2Resolver.search] (acentos, plurales,
 * sinónimos ES/EN y erratas) para proponer ejercicios y [ExerciseMatchLexicon] para medir la confianza.
 */
class ExerciseNameMatcher(catalog: ExerciseCatalogV2) {
    private val resolver = ExerciseCatalogV2Resolver(catalog)
    private val definitionsById: Map<String, ExerciseDefinitionV2> =
        catalog.families.flatMap { it.definitions }.associateBy { it.id }

    /** Las mejores configuraciones para un nombre; vacío si el catálogo no reconoce nada. */
    fun match(rawName: String): ExerciseNameMatch {
        val name = rawName.trim()
        val query = comparable(name)
        if (query.isEmpty()) return ExerciseNameMatch(name, emptyList())
        val ranked = resolver.search(name).take(SEARCH_POOL).mapIndexedNotNull { order, hit ->
            val definition = definitionsById[hit.definitionId] ?: return@mapIndexedNotNull null
            val configurationId = hit.suggestedConfigurationId ?: definition.defaultConfigurationId
            val confidence = namesOf(definition).maxOf { similarity(query, comparable(it)) }
            Ranked(ExerciseNameCandidate(configurationId, definition.canonicalName, confidence), order)
        }
        val candidates = ranked
            .sortedWith(compareByDescending<Ranked> { it.candidate.confidence }.thenBy { it.order })
            .map { it.candidate }
            .distinctBy { it.configurationId }
            .take(MAX_CANDIDATES)
        return ExerciseNameMatch(name, candidates)
    }

    /** Empareja cada nombre distinto una sola vez (clave: el nombre sin espacios sobrantes). */
    fun matchAll(rawNames: Collection<String>): Map<String, ExerciseNameMatch> =
        rawNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().associateWith { match(it) }

    private class Ranked(val candidate: ExerciseNameCandidate, val order: Int)

    private fun namesOf(definition: ExerciseDefinitionV2): List<String> =
        listOf(definition.canonicalName) + definition.searchTerms

    private fun comparable(text: String): String =
        ExerciseMatchLexicon.normalize(text)
            .split(' ')
            .filter { it.isNotEmpty() && it !in NAME_STOP_WORDS }
            .joinToString(" ") { ExerciseMatchLexicon.stem(it) }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val distance = ExerciseMatchLexicon.levenshtein(a, b)
        return (1.0 - distance.toDouble() / maxOf(a.length, b.length)).coerceIn(0.0, 1.0)
    }
}
