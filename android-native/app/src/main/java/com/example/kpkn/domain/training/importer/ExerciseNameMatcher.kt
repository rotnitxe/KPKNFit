package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2

/**
 * Configuración del catálogo v2 propuesta para un nombre importado. [equipmentId] (barbell, dumbbells, cable…)
 * distingue las configuraciones de un mismo ejercicio en la lista de «Elegir».
 */
data class ExerciseNameCandidate(
    val configurationId: String,
    /** Nombre canónico de la definición, el mismo que muestra el catálogo. */
    val displayName: String,
    /** Parecido 0..1 con el nombre canónico o un alias del ejercicio (sin acentos, mayúsculas ni plurales). */
    val confidence: Double,
    val equipmentId: String? = null,
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

/** Otro material del mismo ejercicio queda este punto por debajo: nunca desplaza a la configuración elegida. */
private const val ALTERNATIVE_PENALTY = 0.2

/** El nombre pide un material que el ejercicio no tiene: se usa la sugerida o por defecto con esta bajada. */
private const val MISSING_MATERIAL_PENALTY = 0.15

/** Tope tras esa bajada: ese caso nunca es confiable, así sale «Elegir». */
private const val MISSING_MATERIAL_CEILING = CONFIDENT_MIN - 0.01

/** Palabras vacías que no cuentan al comparar nombres («press de banca» = «press banca»). */
private val NAME_STOP_WORDS = setOf("de", "del", "la", "el", "en", "con", "para", "por", "al", "a", "y", "o")

/** Palabra de material del nombre (sin tildes ni mayúsculas) → equipo del catálogo. */
private val MATERIAL_WORDS: Map<String, String> = mapOf(
    "mancuerna" to "dumbbells",
    "mancuernas" to "dumbbells",
    "dumbbell" to "dumbbells",
    "dumbbells" to "dumbbells",
    "db" to "dumbbells",
    "barra" to "barbell",
    "barras" to "barbell",
    "barbell" to "barbell",
    "barbells" to "barbell",
    "polea" to "cable",
    "poleas" to "cable",
    "cable" to "cable",
    "cables" to "cable",
    "crossover" to "cable",
    "maquina" to "machine",
    "maquinas" to "machine",
    "machine" to "machine",
    "machines" to "machine",
    "smith" to "smith_machine",
    "multipower" to "smith_machine",
    "kettlebell" to "kettlebell",
    "kettlebells" to "kettlebell",
    "banda" to "band",
    "bandas" to "band",
    "elastico" to "band",
    "elasticos" to "band",
    "elastica" to "band",
    "band" to "band",
    "bands" to "band",
    "bodyweight" to "bodyweight",
    "hexagonal" to "hex_bar",
    "hex" to "hex_bar",
)

/**
 * Empareja nombres importados con el catálogo v2. Reutiliza [ExerciseCatalogV2Resolver.search] (acentos, plurales,
 * sinónimos ES/EN y erratas) para proponer ejercicios y [ExerciseMatchLexicon] para medir la confianza.
 */
class ExerciseNameMatcher(catalog: ExerciseCatalogV2) {
    private val resolver = ExerciseCatalogV2Resolver(catalog)
    private val definitionsById: Map<String, ExerciseDefinitionV2> =
        catalog.families.flatMap { it.definitions }.associateBy { it.id }

    /**
     * Configuraciones para un nombre. Si el nombre dice el material («con mancuernas», «en polea», «hexagonal»),
     * elige la configuración de ese equipo; si el ejercicio no la tiene, propone la sugerida o por defecto con la
     * confianza bajada. Sin material, la sugerida o por defecto, y otros materiales del ejercicio por debajo.
     */
    fun match(rawName: String): ExerciseNameMatch {
        val name = rawName.trim()
        val query = comparable(name)
        if (query.isEmpty()) return ExerciseNameMatch(name, emptyList())
        val equipment = materialEquipment(name)
        val ranked = mutableListOf<Ranked>()
        resolver.search(name).forEachIndexed { order, hit ->
            val definition = definitionsById[hit.definitionId] ?: return@forEachIndexed
            val primaryId = hit.suggestedConfigurationId ?: definition.defaultConfigurationId
            val primary = definition.configurations.firstOrNull { it.id == primaryId } ?: return@forEachIndexed
            val base = namesOf(definition).maxOf { similarity(query, comparable(it)) }
            if (equipment == null) {
                ranked += Ranked(candidate(primary, definition, base), order, alternative = false)
                addAlternatives(ranked, definition, primary, base, ceiling = base, order = order)
                return@forEachIndexed
            }
            val chosen = primary.takeIf { it.profile.equipmentId == equipment }
                ?: definition.configurations.firstOrNull { it.profile.equipmentId == equipment }
            if (chosen != null) {
                ranked += Ranked(candidate(chosen, definition, base), order, alternative = false)
                return@forEachIndexed
            }
            // El ejercicio no tiene ese material: sugerida o por defecto, con la confianza bajada.
            val lowered = (base - MISSING_MATERIAL_PENALTY).coerceIn(0.0, MISSING_MATERIAL_CEILING)
            ranked += Ranked(candidate(primary, definition, lowered), order, alternative = false)
            addAlternatives(ranked, definition, primary, base, ceiling = lowered, order = order)
        }
        val candidates = ranked
            .sortedWith(
                compareByDescending<Ranked> { it.candidate.confidence }
                    .thenBy { it.order }
                    .thenBy { it.alternative },
            )
            .map { it.candidate }
            .distinctBy { it.configurationId }
            .take(MAX_CANDIDATES)
        return ExerciseNameMatch(name, candidates)
    }

    /** Empareja cada nombre distinto una sola vez (clave: el nombre sin espacios sobrantes). */
    fun matchAll(rawNames: Collection<String>): Map<String, ExerciseNameMatch> =
        rawNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().associateWith { match(it) }

    private class Ranked(val candidate: ExerciseNameCandidate, val order: Int, val alternative: Boolean)

    /**
     * Material que dice el nombre: la primera palabra de [MATERIAL_WORDS] en orden. «hexagonal» manda sobre «barra»,
     * porque la barra hexagonal es otro equipo. Null si el nombre no dice material.
     */
    private fun materialEquipment(rawName: String): String? {
        val tokens = ExerciseMatchLexicon.normalize(rawName).split(' ').filter { it.isNotEmpty() }
        if ("hexagonal" in tokens || "hex" in tokens || ("trap" in tokens && "bar" in tokens)) return "hex_bar"
        for ((index, token) in tokens.withIndex()) {
            val next = tokens.getOrNull(index + 1)
            when {
                token == "peso" && next == "corporal" -> return "bodyweight"
                token == "sin" && next == "peso" -> return "bodyweight"
                token == "pesa" && next == "rusa" -> return "kettlebell"
            }
            MATERIAL_WORDS[token]?.let { return it }
        }
        return null
    }

    /** Otros materiales del mismo ejercicio (uno por equipo, en orden del catálogo), nunca por encima de [ceiling]. */
    private fun addAlternatives(
        ranked: MutableList<Ranked>,
        definition: ExerciseDefinitionV2,
        primary: ExerciseConfigurationV2,
        base: Double,
        ceiling: Double,
        order: Int,
    ) {
        val confidence = minOf((base - ALTERNATIVE_PENALTY).coerceAtLeast(0.0), ceiling)
        val equipments = hashSetOf(primary.profile.equipmentId)
        for (config in definition.configurations) {
            if (config.id == primary.id || !equipments.add(config.profile.equipmentId)) continue
            ranked += Ranked(candidate(config, definition, confidence), order, alternative = true)
        }
    }

    private fun candidate(
        config: ExerciseConfigurationV2,
        definition: ExerciseDefinitionV2,
        confidence: Double,
    ): ExerciseNameCandidate = ExerciseNameCandidate(
        configurationId = config.id,
        displayName = definition.canonicalName,
        confidence = confidence,
        equipmentId = config.profile.equipmentId,
    )

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
