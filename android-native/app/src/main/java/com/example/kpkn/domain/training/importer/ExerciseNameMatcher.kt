package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSearchHitV2
import com.example.kpkn.domain.nutrition.RegexEs

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

/**
 * Hasta tres configuraciones para un nombre importado, de mayor a menor confianza. [alternative] es la segunda opción de
 * un nombre «X o Y» que se reconoce por X («Dominadas o jalón agarre cerrado» → «jalón agarre cerrado»); si no, null.
 */
data class ExerciseNameMatch(
    val rawName: String,
    val candidates: List<ExerciseNameCandidate>,
    val alternative: String? = null,
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

/** Sugerencias por debajo de esta confianza no se muestran (no cambian la confianza de un reconocimiento). */
private const val SUGGESTION_MIN = 0.3

/** Tope de un candidato que las guardas G1 o G3 no dejan reconocer solo (la sugerencia sigue visible). */
private const val GUARDED_CEILING = 0.79

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

/** Traducciones de la segunda pasada: las de siempre más «sitting» → «sentado». */
private val IMPORT_TRANSLATIONS: Map<String, String> = TRANSLATIONS + mapOf("sitting" to "sentado")

/** Quita «pull down» y «pull downs» (con guion o espacio) y lo deja como «pulldown», la forma del catálogo. */
// Sin bandera U: ICU (Android) la rechaza; los bordes de palabra vienen de RegexEs (SE9).
internal val PULL_DOWN = Regex(RegexEs.bounded("""pull[ -]?downs?"""), RegexOption.IGNORE_CASE)

/**
 * Calificativos que cambian la variante (pausa, déficit, sentado…). En el respaldo, el ejercicio que los tiene y el
 * nombre no dice baja su confianza. Se comparan por raíz sin género ni número («inclinada» = «inclinado»).
 */
private val VARIANT_ROOTS: Set<String> = listOf(
    "pausa", "deficit", "tempo", "cadena", "banda", "bloque", "pin", "sumo", "rumano", "bulgara", "frontal", "hack", "v",
    "zercher", "landmine", "unilateral", "inclinado", "declinado", "cerrado", "drag", "gorila", "gorilla", "isometrico",
    "sentado", "pie", "alta", "baja",
).map(::genderRoot).toSet()

/** Separador de dos opciones de un nombre: «X o Y», «X or Y» o «X u Y». */
private val ALTERNATIVE_SEPARATOR = Regex("""\s+(?:o|or|u)\s+""", RegexOption.IGNORE_CASE)

/** Parte un nombre con dos opciones en X e Y; null si no tiene esa forma o le falta una de las dos partes. */
internal fun splitAlternative(name: String): Pair<String, String>? {
    val separator = ALTERNATIVE_SEPARATOR.find(name) ?: return null
    val first = name.substring(0, separator.range.first).trim()
    val second = name.substring(separator.range.last + 1).trim()
    return if (first.isNotEmpty() && second.isNotEmpty()) first to second else null
}

/**
 * Empareja nombres importados con el catálogo v2 en dos pasadas. La primera es el emparejador de siempre: alias curados
 * de [ImportExerciseAliases] y búsqueda con parecido; si reconoce el nombre con confianza, su resultado no cambia. Si
 * no, la segunda aplica la propuesta v6 de H6b ([ImportNameNormalizer]: notación, plurales, abreviaturas y búsqueda
 * relajada), con guardas que no dejan reconocer solos los nombres con palabras que el catálogo no conoce.
 */
class ExerciseNameMatcher(catalog: ExerciseCatalogV2) {
    private val resolver = ExerciseCatalogV2Resolver(catalog)
    private val definitionsById: Map<String, ExerciseDefinitionV2> =
        catalog.families.flatMap { it.definitions }.associateBy { it.id }

    /** Reglas de la primera pasada: las de siempre, sin cambios. */
    private val currentRules = Rules(
        stem = { ExerciseMatchLexicon.stem(it) },
        translations = TRANSLATIONS,
        lookupAlias = { ImportExerciseAliases.lookup(it) },
        search = { resolver.search(it) },
    )

    /** Reglas de la segunda pasada: plurales, «sitting», alias nuevos y búsqueda relajada. */
    private val importRules = Rules(
        stem = { ImportNameNormalizer.pluralStem(it) },
        translations = IMPORT_TRANSLATIONS,
        lookupAlias = { ImportExerciseAliases.lookupForImport(it) },
        search = { relaxedHits(it) },
    )

    /** Palabras que el catálogo conoce (guarda G1). Se calculan la primera vez que hacen falta. */
    private val vocabulary: Set<String> by lazy {
        ImportNameNormalizer.vocabulary(catalog, TRANSLATIONS, ImportExerciseAliases.vocabularyPhrases)
    }

    /** Palabras de cada ejercicio (nombres, búsquedas y sus stems), para la guarda G3. */
    private val definitionTerms: Map<String, Set<String>> by lazy {
        definitionsById.values.associate { definition ->
            val terms = HashSet<String>()
            for (name in namesOf(definition)) {
                for (word in tokensOf(name)) {
                    terms += word
                    terms += ExerciseMatchLexicon.stem(word)
                    terms += ImportNameNormalizer.pluralStem(word)
                }
            }
            definition.id to terms
        }
    }

    /**
     * Configuraciones para un nombre. Si el nombre dice el material («con mancuernas», «en polea», «hexagonal»),
     * elige la configuración de ese equipo; si el ejercicio no la tiene, propone la sugerida o por defecto con la
     * confianza bajada. Sin material, la del alias, la sugerida o por defecto, y otros materiales por debajo.
     * «X o Y»: si el nombre completo no se reconoce y X sí, se reconoce X y Y queda como [ExerciseNameMatch.alternative].
     */
    fun match(rawName: String): ExerciseNameMatch {
        val name = rawName.trim()
        val current = matchCurrent(name)
        return if (current.isConfident) current else matchImported(name)
    }

    /** Empareja cada nombre distinto una sola vez (clave: el nombre sin espacios sobrantes). */
    fun matchAll(rawNames: Collection<String>): Map<String, ExerciseNameMatch> =
        rawNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().associateWith { match(it) }

    /** Primera pasada: el nombre completo y, si es «X o Y» y X se reconoce, X con Y como alternativa. */
    private fun matchCurrent(name: String): ExerciseNameMatch {
        val whole = ExerciseNameMatch(name, rank(name, currentRules).map { it.candidate })
        if (whole.isConfident) return whole
        val (first, second) = splitAlternative(name) ?: return whole
        val part = ExerciseNameMatch(first, rank(first, currentRules).map { it.candidate })
        return if (part.isConfident) ExerciseNameMatch(name, part.candidates, alternative = second) else whole
    }

    /**
     * Segunda pasada (propuesta v6): quita la notación, separa «X / Y» solo si X y Y se reconocen por separado, y
     * empareja con las reglas de importación. Sin reconocimiento, devuelve sugerencias con confianza ≤ 0,79.
     */
    private fun matchImported(raw: String): ExerciseNameMatch {
        val withoutPulldown = PULL_DOWN.replace(raw, "pulldown")
        val slash = '/' in withoutPulldown
        val text = withoutPulldown.replace("/", " or ")
        val prepared = ImportNameNormalizer.prepare(text)
        if (prepared.isNoise) return ExerciseNameMatch(raw, emptyList())
        val whole = guarded(rank(prepared.text, importRules), prepared.text)
        if (isConfident(whole)) return ExerciseNameMatch(raw, offered(whole, prepared.text))
        val parts = if (withoutPulldown.count { it == '/' } <= 1) splitAlternative(text) else null
        if (parts != null) {
            val (firstText, firstScored) = importPart(parts.first)
            if (isConfident(firstScored)) {
                val secondOk = !slash || isConfident(importPart(parts.second).second)
                if (secondOk) return ExerciseNameMatch(raw, offered(firstScored, firstText), alternative = parts.second)
            }
        }
        return ExerciseNameMatch(raw, offered(whole, prepared.text))
    }

    /** Un fragmento del nombre limpio con las reglas de importación y las guardas: su texto y sus candidatos. */
    private fun importPart(part: String): Pair<String, List<Scored>> {
        val prepared = ImportNameNormalizer.prepare(part)
        if (prepared.isNoise || prepared.text.isEmpty()) return "" to emptyList()
        return prepared.text to guarded(rank(prepared.text, importRules), prepared.text)
    }

    /**
     * Candidatos de un nombre con unas reglas: alias, búsqueda y parecido, con la configuración que pide su material.
     * Ordenados de mayor a menor confianza, sin configuraciones repetidas y como mucho [MAX_CANDIDATES].
     */
    private fun rank(name: String, rules: Rules): List<Scored> {
        val original = comparable(name, rules.stem)
        if (original.isEmpty()) return emptyList()
        val equipment = materialEquipment(name)
        val alias = rules.lookupAlias(name)?.takeIf { it.definitionId in definitionsById }
        val translated = comparable(translatedCore(name, rules.translations), rules.stem)
        val said = saidRoots(name, rules.translations)
        val ranked = mutableListOf<Ranked>()
        if (alias != null) {
            val definition = definitionsById.getValue(alias.definitionId)
            addCandidates(ranked, definition, aliasConfiguration(definition, alias), equipment, ALIAS_CONFIDENCE, -1)
        }
        rules.search(name).forEachIndexed { order, hit ->
            val definition = definitionsById[hit.definitionId] ?: return@forEachIndexed
            if (definition.id == alias?.definitionId) return@forEachIndexed
            val primary = definition.configurations
                .firstOrNull { it.id == (hit.suggestedConfigurationId ?: definition.defaultConfigurationId) }
                ?: return@forEachIndexed
            val base = fallbackConfidence(definition, original, translated, said, rules.stem)
            val confidence = if (alias != null) minOf(base, RIVAL_CEILING) else base
            addCandidates(ranked, definition, primary, equipment, confidence, order)
        }
        return ranked
            .sortedWith(
                compareByDescending<Ranked> { it.scored.candidate.confidence }
                    .thenBy { it.order }
                    .thenBy { it.alternative },
            )
            .distinctBy { it.scored.candidate.configurationId }
            .take(MAX_CANDIDATES)
            .map { it.scored }
    }

    /** Confianza alta de una lista ordenada (igual que [ExerciseNameMatch.isConfident]), antes de ocultar sugerencias. */
    private fun isConfident(scored: List<Scored>): Boolean {
        val first = scored.firstOrNull()?.candidate?.confidence ?: return false
        val second = scored.getOrNull(1)?.candidate?.confidence ?: 0.0
        return first >= CONFIDENT_MIN && first - second >= CONFIDENT_MARGIN
    }

    /**
     * Guardas de la segunda pasada: sin reconocimiento automático si sobra una palabra que el catálogo no conoce (G1)
     * o si el candidato no dice una palabra significativa del nombre (G3). Antes se descarta el movimiento contrario
     * ([isOppositeMovement]). Los alias curados no pasan por ellas.
     */
    private fun guarded(scored: List<Scored>, text: String): List<Scored> {
        if (scored.isEmpty() || ImportExerciseAliases.lookupForImport(text) != null) return scored
        val unknownWord = text.split(' ').filter { it.isNotEmpty() }.any { !isKnown(it) }
        return scored.filterNot { isOppositeMovement(text, it.definition) }.map { item ->
            if (!unknownWord && uncoveredWords(text, item.definition).isEmpty()) {
                item
            } else {
                item.copy(candidate = item.candidate.copy(confidence = minOf(item.candidate.confidence, GUARDED_CEILING)))
            }
        }
    }

    /** Sugerencias que se muestran: desde [SUGGESTION_MIN] y sin la versión tumbada si el nombre dice «de pie». */
    private fun offered(scored: List<Scored>, text: String): List<ExerciseNameCandidate> =
        scored.filter { it.candidate.confidence >= SUGGESTION_MIN && !contradictsStanding(text, it.definition) }
            .map { it.candidate }

    /** Palabra conocida por el catálogo, tal cual, sin plural o en su raíz de importación. Los dígitos cuentan. */
    private fun isKnown(token: String): Boolean =
        token.all { it.isDigit() } || token in vocabulary ||
            ImportNameNormalizer.vocabularyStem(token) in vocabulary || ExerciseMatchLexicon.stem(token) in vocabulary

    /**
     * Palabras significativas del nombre que el ejercicio no dice, ni en su forma, ni traducidas, ni por parecido de raíz
     * (G3). No cuentan las de material, las vacías y las neutras.
     */
    private fun uncoveredWords(text: String, definition: ExerciseDefinitionV2): List<String> {
        val terms = definitionTerms.getValue(definition.id)
        return text.split(' ').filter { it.isNotEmpty() }.filterNot { token ->
            token in MATERIAL_WORDS || token in IMPORT_STOP_WORDS || token in ImportNameNormalizer.NEUTRAL_WORDS ||
                token.all { it.isDigit() } || coveredBy(token, terms)
        }
    }

    private fun coveredBy(token: String, terms: Set<String>): Boolean {
        val stemmed = ImportNameNormalizer.pluralStem(token)
        val translated = ImportNameNormalizer.pluralStem(IMPORT_TRANSLATIONS[stemmed] ?: stemmed)
        return token in terms || stemmed in terms || translated in terms || ExerciseMatchLexicon.stem(token) in terms ||
            terms.any { term -> term.length >= 5 && translated.length >= 5 && sameRoot(translated, term) }
    }

    /**
     * Postura contraria: el nombre dice «de pie» (o «standing») y no dice tumbado, y el ejercicio es la versión tumbada.
     * Única regla de postura de H6b: evita sugerir «Extensión de Tríceps Tumbada» para «Standing Tricep Extensions».
     */
    private fun contradictsStanding(text: String, definition: ExerciseDefinitionV2): Boolean {
        val said = tokensOf(text)
        val standing = "standing" in said || "pie" in said
        if (!standing || said.any { isLyingWord(it) }) return false
        return tokensOf(definition.canonicalName).any { isLyingWord(it) }
    }

    private fun isLyingWord(token: String): Boolean = token == "lying" || token.startsWith("tumbad")

    /**
     * «Abductor» y «aductor» están a una letra de distancia y el parecido por raíz los une. Si el nombre dice un
     * movimiento y el ejercicio el contrario, no es el mismo ejercicio («Hip Abductor» no es «Aducciones de Pierna»).
     */
    private fun isOppositeMovement(text: String, definition: ExerciseDefinitionV2): Boolean {
        val said = movementsOf(tokensOf(text))
        val meant = movementsOf(tokensOf(definition.canonicalName))
        return said.isNotEmpty() && meant.isNotEmpty() && said.intersect(meant).isEmpty()
    }

    /** Movimiento de las palabras: abducción o aducción, con sus formas en inglés y español. */
    private fun movementsOf(tokens: List<String>): Set<String> = tokens.mapNotNullTo(HashSet<String>()) { token ->
        when {
            token.startsWith("abduc") -> "abduccion"
            token.startsWith("aduc") || token.startsWith("adduc") -> "aduccion"
            else -> null
        }
    }

    /**
     * Búsqueda de la segunda pasada: las palabras del nombre traducidas al español, sin las que el catálogo no conoce
     * (AND relajado). Solo amplía los candidatos.
     */
    private fun relaxedHits(text: String): List<ExerciseSearchHitV2> {
        val kept = ArrayList<String>()
        for (token in text.split(' ').filter { it.isNotEmpty() }) {
            val stemmed = ImportNameNormalizer.pluralStem(token)
            if (stemmed in MATERIAL_WORDS || stemmed in IMPORT_STOP_WORDS) continue
            val translated = IMPORT_TRANSLATIONS[stemmed] ?: stemmed
            if (resolver.search(translated).isNotEmpty()) kept += translated
        }
        if (kept.isEmpty()) return emptyList()
        return resolver.search(kept.joinToString(" "))
    }

    /**
     * Configuración de un alias: la exacta si la tiene; si no, la de su material por defecto; si no, la por defecto del
     * ejercicio.
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
            ranked += Ranked(scoredOf(primary, definition, base), order, alternative = false)
            addAlternatives(ranked, definition, primary, base, ceiling = base, order = order)
            return
        }
        val chosen = primary.takeIf { it.profile.equipmentId == equipment }
            ?: definition.configurations.firstOrNull { it.profile.equipmentId == equipment }
        if (chosen != null) {
            ranked += Ranked(scoredOf(chosen, definition, base), order, alternative = false)
            return
        }
        // El ejercicio no tiene ese material: sugerida o por defecto, con la confianza bajada.
        val lowered = (base - MISSING_MATERIAL_PENALTY).coerceIn(0.0, MISSING_MATERIAL_CEILING)
        ranked += Ranked(scoredOf(primary, definition, lowered), order, alternative = false)
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
            ranked += Ranked(scoredOf(config, definition, confidence), order, alternative = true)
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
        stem: (String) -> String,
    ): Double {
        val best = namesOf(definition).maxOf { candidateName ->
            val target = comparable(candidateName, stem)
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
    private fun saidRoots(name: String, translations: Map<String, String>): Set<String> {
        val words = tokensOf(name)
        return (words + words.map { translations[it] ?: it }).map(::genderRoot).toSet()
    }

    /** Nombre sin palabras vacías ni de material, con las palabras inglesas traducidas («Barbell Row» → «remo»). */
    private fun translatedCore(name: String, translations: Map<String, String>): String =
        tokensOf(name)
            .filter { it !in IMPORT_STOP_WORDS && it !in MATERIAL_WORDS }
            .joinToString(" ") { translations[it] ?: it }

    private fun scoredOf(
        config: ExerciseConfigurationV2,
        definition: ExerciseDefinitionV2,
        confidence: Double,
    ): Scored = Scored(
        definition = definition,
        candidate = ExerciseNameCandidate(
            configurationId = config.id,
            displayName = definition.canonicalName,
            confidence = confidence,
            equipmentId = config.profile.equipmentId,
        ),
    )

    private fun namesOf(definition: ExerciseDefinitionV2): List<String> =
        listOf(definition.canonicalName) + definition.searchTerms

    private fun tokensOf(text: String): List<String> =
        ExerciseMatchLexicon.normalize(text).split(' ').filter { it.isNotEmpty() }

    private fun comparable(text: String, stem: (String) -> String): String =
        tokensOf(text).filter { it !in NAME_STOP_WORDS }.joinToString(" ") { stem(it) }

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

    /** Candidato con la definición de la que sale; las guardas necesitan saber de qué ejercicio es. */
    private data class Scored(val definition: ExerciseDefinitionV2, val candidate: ExerciseNameCandidate)

    private class Ranked(val scored: Scored, val order: Int, val alternative: Boolean)

    /** Reglas de una pasada: cómo se compara, qué traducciones usa, qué alias conoce y cómo busca en el catálogo. */
    private class Rules(
        val stem: (String) -> String,
        val translations: Map<String, String>,
        val lookupAlias: (String) -> ImportExerciseAlias?,
        val search: (String) -> List<ExerciseSearchHitV2>,
    )
}

/**
 * Raíz sin género: quita la última «a» o «o» de la raíz («inclinada», «inclinado» e «inclinados» dan «inclinad»).
 */
private fun genderRoot(word: String): String {
    val stemmed = ExerciseMatchLexicon.stem(word)
    return if (stemmed.length > 2 && (stemmed.endsWith("a") || stemmed.endsWith("o"))) stemmed.dropLast(1) else stemmed
}
