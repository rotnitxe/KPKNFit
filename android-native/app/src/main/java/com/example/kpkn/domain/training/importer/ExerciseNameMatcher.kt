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

/** Confianza de un acierto exacto con un alias curado de [ImportExerciseAliases]. */
private const val ALIAS_CONFIDENCE = 0.95

/** Los demás ejercicios frente a un alias quedan así de bajos: el alias siempre saca el margen de confianza. */
private const val RIVAL_CEILING = ALIAS_CONFIDENCE - ALTERNATIVE_PENALTY

/** Respaldo difuso: cada calificativo que el ejercicio tiene y el nombre no dice baja esto, hasta el tope. */
private const val VARIANT_PENALTY = 0.10
private const val VARIANT_PENALTY_CAP = 0.30

/** Palabras vacías que no cuentan al comparar nombres («press de banca» = «press banca»). */
private val NAME_STOP_WORDS = setOf("de", "del", "la", "el", "en", "con", "para", "por", "al", "a", "y", "o")

/** Palabra de material del nombre (sin tildes ni mayúsculas) → equipo del catálogo. */
internal val MATERIAL_WORDS: Map<String, String> = mapOf(
    "mancuerna" to "dumbbells",
    "mancuernas" to "dumbbells",
    "dumbbell" to "dumbbells",
    "dumbbells" to "dumbbells",
    "db" to "dumbbells",
    "barra" to "barbell",
    "barras" to "barbell",
    "barbell" to "barbell",
    "barbells" to "barbell",
    "bb" to "barbell",
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
    "ez" to "ez_bar",
    "ezbar" to "ez_bar",
)

/** Palabras inglesas del respaldo y su equivalente del catálogo en español (solo las que aparecen en los nombres). */
private val TRANSLATIONS: Map<String, String> = mapOf(
    "squat" to "sentadilla",
    "bench" to "banca",
    "row" to "remo",
    "raise" to "elevacion",
    "calf" to "talon",
    "fly" to "apertura",
    "pulldown" to "jalon",
    "pullup" to "dominada",
    "dip" to "fondo",
    "lunge" to "zancada",
    "incline" to "inclinado",
    "decline" to "declinado",
    "seated" to "sentado",
    "standing" to "pie",
    "front" to "frontal",
    "close" to "cerrado",
    "grip" to "agarre",
    "reverse" to "inverso",
    "deadlift" to "muerto",
    "romanian" to "rumano",
    "bulgarian" to "bulgara",
    "pause" to "pausa",
    "paused" to "pausa",
    "high" to "alta",
    "low" to "baja",
    "flat" to "plano",
    "chest" to "pecho",
    "back" to "espalda",
    "shoulder" to "hombro",
    "shoulders" to "hombros",
    "leg" to "pierna",
    "legs" to "piernas",
    "hip" to "cadera",
    "thrust" to "empuje",
    "machine" to "maquina",
    "chain" to "cadena",
    "band" to "banda",
    "block" to "bloque",
    "isometric" to "isometrico",
    "gorilla" to "gorila",
    "rear" to "posterior",
    "hammer" to "martillo",
    "preacher" to "predicador",
    "spider" to "arana",
    "concentration" to "concentrado",
    "lying" to "tumbado",
    "kickback" to "patada",
    "wrist" to "muneca",
    "neck" to "cuello",
    "knee" to "rodilla",
    "shrug" to "encogimiento",
    "shrugs" to "encogimiento",
    "farmer" to "granjero",
    "carry" to "acarreo",
    "plank" to "plancha",
    "hyperextension" to "hiperextension",
    "dumbbell" to "mancuerna",
    "barbell" to "barra",
    "tricep" to "triceps",
    "pec" to "pecho",
    "abs" to "abdominal",
)

/**
 * Calificativos que cambian la variante (pausa, déficit, sentado…). En el respaldo, el ejercicio que los tiene y el
 * nombre no dice baja su confianza. Se comparan por raíz sin género ni número («inclinada» = «inclinado»).
 */
private val VARIANT_ROOTS: Set<String> = listOf(
    "pausa", "deficit", "tempo", "cadena", "banda", "bloque", "pin", "sumo", "rumano", "bulgara", "frontal", "hack", "v",
    "zercher", "landmine", "unilateral", "inclinado", "declinado", "cerrado", "drag", "gorila", "gorilla", "isometrico",
    "sentado", "pie", "alta", "baja",
).map(::genderRoot).toSet()

/**
 * Empareja nombres importados con el catálogo v2. Primero busca el alias curado de [ImportExerciseAliases] (confianza
 * alta). Si no hay alias, usa [ExerciseCatalogV2Resolver.search] como candidatos y mide el parecido con el nombre
 * original y con su traducción sin material, por caracteres o por palabras, quedándose con el mejor.
 */
class ExerciseNameMatcher(catalog: ExerciseCatalogV2) {
    private val resolver = ExerciseCatalogV2Resolver(catalog)
    private val definitionsById: Map<String, ExerciseDefinitionV2> =
        catalog.families.flatMap { it.definitions }.associateBy { it.id }

    /**
     * Configuraciones para un nombre. Si el nombre dice el material («con mancuernas», «en polea», «hexagonal»),
     * elige la configuración de ese equipo; si el ejercicio no la tiene, propone la sugerida o por defecto con la
     * confianza bajada. Sin material, la del alias, la sugerida o por defecto, y otros materiales por debajo.
     */
    fun match(rawName: String): ExerciseNameMatch {
        val name = rawName.trim()
        val original = comparable(name)
        if (original.isEmpty()) return ExerciseNameMatch(name, emptyList())
        val equipment = materialEquipment(name)
        val alias = ImportExerciseAliases.lookup(name)?.takeIf { it.definitionId in definitionsById }
        val translated = comparable(translatedCore(name))
        val said = saidRoots(name)
        val ranked = mutableListOf<Ranked>()
        if (alias != null) {
            val definition = definitionsById.getValue(alias.definitionId)
            addCandidates(ranked, definition, aliasConfiguration(definition, alias), equipment, ALIAS_CONFIDENCE, -1)
        }
        resolver.search(name).forEachIndexed { order, hit ->
            val definition = definitionsById[hit.definitionId] ?: return@forEachIndexed
            if (definition.id == alias?.definitionId) return@forEachIndexed
            val primary = definition.configurations
                .firstOrNull { it.id == (hit.suggestedConfigurationId ?: definition.defaultConfigurationId) }
                ?: return@forEachIndexed
            val base = fallbackConfidence(definition, original, translated, said)
            val confidence = if (alias != null) minOf(base, RIVAL_CEILING) else base
            addCandidates(ranked, definition, primary, equipment, confidence, order)
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
     * Configuración de un alias: la exacta si la tiene; si no, la de su material por defecto; si no, la por defecto
     * del ejercicio.
     */
    private fun aliasConfiguration(definition: ExerciseDefinitionV2, alias: ImportExerciseAlias): ExerciseConfigurationV2 {
        val defaultConfig = definition.configurations.first { it.id == definition.defaultConfigurationId }
        val exact = alias.configurationId?.let { id -> definition.configurations.firstOrNull { it.id == id } }
        if (exact != null) return exact
        val equipment = alias.equipmentId ?: return defaultConfig
        return defaultConfig.takeIf { it.profile.equipmentId == equipment }
            ?: definition.configurations.firstOrNull { it.profile.equipmentId == equipment }
            ?: defaultConfig
    }

    /**
     * Añade la configuración elegida de un ejercicio y, si el nombre no dice material, sus otros materiales.
     * [primary] es la sugerida, la del alias o la por defecto; [base] la confianza del nombre para ese ejercicio.
     */
    private fun addCandidates(
        ranked: MutableList<Ranked>,
        definition: ExerciseDefinitionV2,
        primary: ExerciseConfigurationV2,
        equipment: String?,
        base: Double,
        order: Int,
    ) {
        if (equipment == null) {
            ranked += Ranked(candidate(primary, definition, base), order, alternative = false)
            addAlternatives(ranked, definition, primary, base, ceiling = base, order = order)
            return
        }
        val chosen = primary.takeIf { it.profile.equipmentId == equipment }
            ?: definition.configurations.firstOrNull { it.profile.equipmentId == equipment }
        if (chosen != null) {
            ranked += Ranked(candidate(chosen, definition, base), order, alternative = false)
            return
        }
        // El ejercicio no tiene ese material: sugerida o por defecto, con la confianza bajada.
        val lowered = (base - MISSING_MATERIAL_PENALTY).coerceIn(0.0, MISSING_MATERIAL_CEILING)
        ranked += Ranked(candidate(primary, definition, lowered), order, alternative = false)
        addAlternatives(ranked, definition, primary, base, ceiling = lowered, order = order)
    }

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

    /**
     * Confianza del respaldo para un ejercicio: el mejor parecido entre el nombre original y su traducción sin material,
     * con su nombre canónico o sus alias, menos la penalización de las variantes que el nombre no dice.
     */
    private fun fallbackConfidence(
        definition: ExerciseDefinitionV2,
        original: String,
        translated: String,
        said: Set<String>,
    ): Double {
        val best = namesOf(definition).maxOf { candidateName ->
            val target = comparable(candidateName)
            maxOf(
                similarity(original, target),
                wordSimilarity(original, target),
                similarity(translated, target),
                wordSimilarity(translated, target),
            )
        }
        return (best - variantPenalty(definition, said)).coerceIn(0.0, 1.0)
    }

    private fun variantPenalty(definition: ExerciseDefinitionV2, said: Set<String>): Double {
        val missing = tokensOf(definition.canonicalName).map(::genderRoot).toSet()
            .count { it in VARIANT_ROOTS && it !in said }
        return minOf(missing * VARIANT_PENALTY, VARIANT_PENALTY_CAP)
    }

    /** Raíces de las palabras del nombre y de sus traducciones: lo que el nombre dice. */
    private fun saidRoots(name: String): Set<String> {
        val words = tokensOf(name)
        return (words + words.map { TRANSLATIONS[it] ?: it }).map(::genderRoot).toSet()
    }

    /** Nombre sin palabras vacías ni de material, con las palabras inglesas traducidas («Barbell Row» → «remo»). */
    private fun translatedCore(name: String): String =
        tokensOf(name)
            .filter { it !in IMPORT_STOP_WORDS && it !in MATERIAL_WORDS }
            .joinToString(" ") { TRANSLATIONS[it] ?: it }

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

    private fun tokensOf(text: String): List<String> =
        ExerciseMatchLexicon.normalize(text).split(' ').filter { it.isNotEmpty() }

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

    /** Parecido por palabras: Dice sobre raíces, con palabras de 5 o más letras iguales salvo una letra como iguales. */
    private fun wordSimilarity(a: String, b: String): Double {
        val left = a.split(' ').filter { it.isNotEmpty() }
        val right = b.split(' ').filter { it.isNotEmpty() }
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val matchedLeft = left.count { x -> right.any { y -> sameRoot(x, y) } }
        val matchedRight = right.count { y -> left.any { x -> sameRoot(x, y) } }
        return ((matchedLeft + matchedRight).toDouble() / (left.size + right.size)).coerceIn(0.0, 1.0)
    }

    private fun sameRoot(x: String, y: String): Boolean =
        x == y || (x.length >= 5 && y.length >= 5 && ExerciseMatchLexicon.levenshtein(x, y) <= 1)
}

/**
 * Raíz sin género: quita la última «a» o «o» de la raíz («inclinada», «inclinado» e «inclinados» dan «inclinad»).
 */
private fun genderRoot(word: String): String {
    val stemmed = ExerciseMatchLexicon.stem(word)
    return if (stemmed.length > 2 && (stemmed.endsWith("a") || stemmed.endsWith("o"))) stemmed.dropLast(1) else stemmed
}
