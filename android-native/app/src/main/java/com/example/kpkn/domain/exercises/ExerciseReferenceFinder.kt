package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionKindV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.OPEN_QUERY_PRIORITY_TABLE

enum class ReferenceOutcome {
    /** Una definición clara, con la variante pedida materializada en el catálogo: de ella se pueden copiar los datos. */
    CONFIRMED,

    /** Hay parecidos, pero el nombre es genérico/ambiguo, la variante no existe o la lectura es incierta: se enseñan, no se copian. */
    SIMILAR,

    /** Nada se parece lo bastante: el ejercicio se crea con los datos por completar. */
    NONE,
}

/** Un parecido del catálogo para un nombre escrito, con el porqué. */
data class ReferenceCandidate(
    val definitionId: String,
    val name: String,
    /** Configuración exacta que cumple TODA la variante pedida; null si ninguna la cumple (variante no materializada). */
    val configurationId: String?,
    /** Puntuación interna de orden: no es una probabilidad ni un porcentaje de coincidencia. */
    val score: Double,
    /** Qué tanto del nombre explica esta definición (0–1). */
    val identityScore: Double,
    val reasons: List<String>,
    val satisfiesVariant: Boolean,
    val isCustom: Boolean = false,
)

data class ReferenceResult(
    val parsed: ParsedExerciseName,
    val outcome: ReferenceOutcome,
    /**
     * Los tres primeros parecidos, en orden (vacío si el nombre no se parece a nada). Con [ReferenceOutcome.NONE] se conservan para
     * medir el orden, pero la interfaz no debe ofrecerlos: ninguno se parece lo bastante.
     */
    val candidates: List<ReferenceCandidate>,
    /** Por qué se confirma o se abstiene: texto explicable para la interfaz. */
    val reason: String,
) {
    val confirmed: ReferenceCandidate? get() = if (outcome == ReferenceOutcome.CONFIRMED) candidates.firstOrNull() else null
}

/**
 * Motor único de parecidos para «Crear ejercicio» (MOT · E1/E2). Lee el mismo catálogo y el mismo léxico que la búsqueda, pero decide
 * con la identidad del nombre (no con el material por defecto): corrige erratas, reconoce alias, separa la variante pedida, ordena
 * por cuánto del nombre explica cada definición y solo CONFIRMA una referencia cuando es clara y su variante existe. El material
 * escrito en el nombre prevalece sobre cualquier respaldo; nunca se inventa una configuración.
 */
class ExerciseReferenceFinder private constructor(
    private val entries: List<Entry>,
    private val vocabulary: ExerciseNameVocabulary,
    /** Revisión del catálogo con que se construyó: la procedencia de una referencia confirmada. */
    val catalogRevision: String?,
) {
    private class Phrase(
        val tokens: List<String>,
        /** Palabras de identidad de la frase: sin material ni agarre. */
        val core: List<String>,
        val weight: Double,
        val implement: String?,
    )

    private class EntryConfig(val id: String?, val options: Map<String, String>)

    private class Entry(
        val definitionId: String,
        val name: String,
        val parent: Boolean,
        val phrases: List<Phrase>,
        val configs: List<EntryConfig>,
        val defaultConfigId: String?,
        /** false = el catálogo recibido solo trae una configuración por ejercicio: no se puede dar por inexistente una variante. */
        val verifiedConfigs: Boolean,
        val custom: Boolean,
        val nameWords: Set<String>,
        val nameKeys: Set<String>,
    )

    private class Scored(
        val entry: Entry,
        val score: Double,
        val identity: Double,
        val coverage: Double,
        val precision: Double,
        val alias: Boolean,
    )

    private val nameKeysByDefinition: Map<String, Set<String>> = entries.associate { it.definitionId to it.nameKeys }

    /**
     * Busca los parecidos de [raw]. [explicit] son las elecciones hechas a mano (material, estación, lateralidad) que prevalecen
     * sobre lo que diga el nombre.
     */
    fun find(raw: String, explicit: Map<String, String> = emptyMap()): ReferenceResult {
        val parsed = ExerciseNameAnalyzer.parse(raw, vocabulary) { id -> nameKeysByDefinition[id].orEmpty() }
        val restrictions = parsed.restrictions + explicit
        val strippedWords = parsed.strippedWords
        val scored = entries.mapNotNull { score(parsed, it, restrictions, strippedWords) }
            .sortedWith(
                compareByDescending<Scored> { it.score }
                    .thenBy { it.entry.name.length }
                    .thenBy { it.entry.name },
            )
        if (scored.isEmpty()) {
            return ReferenceResult(parsed, ReferenceOutcome.NONE, emptyList(), "No reconocemos este nombre en el catálogo.")
        }
        val candidates = scored.take(3).map { candidateOf(it, restrictions) }
        val top = scored.first()
        val second = scored.getOrNull(1)
        val topSatisfies = candidates.first().satisfiesVariant
        val complete = top.coverage >= 0.66 && top.precision >= 0.66
        val margin = top.alias || (top.identity - (second?.identity ?: 0.0)) >= 0.10
        val sure = parsed.maxTypoDistance < 2
        return when {
            top.identity >= 0.79 && complete && margin && topSatisfies && sure ->
                ReferenceResult(
                    parsed, ReferenceOutcome.CONFIRMED, candidates,
                    "«${top.entry.name}» explica el nombre y su variante existe en el catálogo.",
                )
            top.identity >= 0.45 -> ReferenceResult(parsed, ReferenceOutcome.SIMILAR, candidates, similarReason(top, candidates.first(), complete, margin, sure))
            // NONE conserva los candidatos ordenados (para medirlos), pero ninguno es lo bastante parecido para ofrecerse.
            else -> ReferenceResult(parsed, ReferenceOutcome.NONE, candidates, "Ningún ejercicio del catálogo se parece lo bastante a este nombre.")
        }
    }

    private fun similarReason(top: Scored, candidate: ReferenceCandidate, complete: Boolean, margin: Boolean, sure: Boolean): String = when {
        !candidate.satisfiesVariant && top.entry.verifiedConfigs ->
            "«${top.entry.name}» se parece, pero el catálogo no tiene la variante que pides; los datos quedan por completar."
        !sure -> "El nombre parece tener erratas y la lectura es incierta; revisa el parecido antes de usarlo."
        !complete -> "El nombre es más genérico o más específico que «${top.entry.name}»; elige el parecido que corresponda."
        !margin -> "Varios ejercicios encajan casi igual con este nombre; elige el parecido que corresponda."
        else -> "El parecido no es lo bastante claro para copiar sus datos."
    }

    // ─── Puntuación ──────────────────────────────────────────────────────────

    private fun score(
        p: ParsedExerciseName,
        e: Entry,
        restrictions: Map<String, String>,
        strippedWords: Set<String>,
    ): Scored? {
        val core = p.coreTokens
        if (core.isEmpty()) {
            if (e.definitionId in p.aliasDefinitionIds) {
                val rank = p.aliasDefinitionIds.indexOf(e.definitionId)
                val s = 0.97 - 0.01 * rank + (if (e.parent) 0.02 else 0.0)
                return Scored(e, s, 0.97, 1.0, 1.0, alias = true)
            }
            return null
        }
        var best = 0.0
        // La identidad se mide con la mejor frase POR SÍ MISMA (sin bonificaciones): un alias exacto vale lo que vale aunque el nombre
        // canónico puntúe más para ordenar.
        var bestF = 0.0
        var bestCov = 0.0
        var bestPrec = 0.0
        var namedBonus = 0.0
        val queryImplement = restrictions["implement"]
        for (ph in e.phrases) {
            if (ph.weight < 1.0 && queryImplement != null) {
                val phraseImplement = ph.implement
                if (phraseImplement != null && phraseImplement != queryImplement) continue
            }
            val m = matchCount(core, ph.core)
            if (m == 0) continue
            val cov = m.toDouble() / core.size
            val prec = m.toDouble() / ph.core.size
            var f1 = 2 * cov * prec / (cov + prec)
            if (cov == 1.0 && prec == 1.0) f1 = 1.0
            val s = ph.weight * f1 + (if (ph.weight == 1.0) 0.03 else 0.0) + (if (ph.weight == 1.0 && cov == 1.0) 0.20 else 0.0)
            if (ph.weight * f1 > bestF) {
                bestF = ph.weight * f1
                bestCov = cov
                bestPrec = prec
            }
            if (s > best) {
                best = s
                namedBonus = if (ph.weight == 1.0 && strippedWords.isNotEmpty() &&
                    ph.tokens.any { ExerciseMatchLexicon.stem(it) in strippedWords }
                ) 0.08 else 0.0
            }
        }
        var score = best + (if (best > 0) namedBonus else 0.0)
        var identity = bestF
        var cov = bestCov
        var prec = bestPrec
        var alias = false
        if (e.definitionId in p.aliasDefinitionIds) {
            val rank = p.aliasDefinitionIds.indexOf(e.definitionId)
            score = maxOf(score, 0.97 - 0.01 * rank)
            identity = 0.97
            cov = 1.0
            prec = 1.0
            alias = true
        }
        if (score <= 0) return null
        if (e.parent) score += 0.02

        // Orden editorial de las consultas abiertas («sentadilla», «peso muerto», «press»…): el mismo que la búsqueda del catálogo.
        val key = core.joinToString(" ")
        val implementSatisfiable = queryImplement == null || e.configs.any { it.options["implement"] == queryImplement }
        if (p.aliasDefinitionIds.isEmpty() && implementSatisfiable) {
            val order = OPEN_KEYS[key]
            val index = order?.indexOf(e.definitionId) ?: -1
            if (index >= 0) score += 2.0 - 0.2 * index
        }

        if (restrictions.isNotEmpty()) {
            val fit = bestConfigFit(e, restrictions)
            score += 0.10 * (fit.satisfied.toDouble() / restrictions.size)
            if (restrictions.containsKey("implement")) {
                val default = e.configs.firstOrNull { it.id != null && it.id == e.defaultConfigId }
                if (default != null && default.options["implement"] == restrictions["implement"]) score += 0.05
            }
            if (e.verifiedConfigs && restrictions.containsKey("implement") && !fit.implementOk) score -= 0.10
        }
        return Scored(e, score, identity, cov, prec, alias)
    }

    private class Fit(val satisfied: Int, val implementOk: Boolean, val bestConfig: EntryConfig?, val allSatisfied: Boolean)

    private fun bestConfigFit(e: Entry, restrictions: Map<String, String>): Fit {
        var bestSat = -1
        var bestConfig: EntryConfig? = null
        var implementOk = !restrictions.containsKey("implement")
        e.configs.forEach { c ->
            var sat = 0
            restrictions.forEach { (axis, value) -> if (c.options[axis] == value) sat += 1 }
            val isDefault = c.id != null && c.id == e.defaultConfigId
            val bestIsDefault = bestConfig?.let { it.id != null && it.id == e.defaultConfigId } == true
            if (sat > bestSat || (sat == bestSat && isDefault && !bestIsDefault)) {
                bestSat = sat
                bestConfig = c
            }
            if (restrictions["implement"] != null && c.options["implement"] == restrictions["implement"]) implementOk = true
        }
        // Una estación pedida que ya dice el nombre de la definición cuenta como cumplida.
        var extra = 0
        restrictions["station"]?.let { station ->
            val words = e.nameWords
            val named = (station == "seated" && "sentado" in words) || (station == "standing" && "pie" in words)
            val inConfig = bestConfig?.options?.get("station") == station
            if (named && !inConfig) extra = 1
        }
        val satisfied = minOf(restrictions.size, maxOf(bestSat, 0) + extra)
        val all = satisfied >= restrictions.size
        return Fit(satisfied, implementOk, bestConfig, all)
    }

    private fun candidateOf(s: Scored, restrictions: Map<String, String>): ReferenceCandidate {
        val e = s.entry
        val fit = bestConfigFit(e, restrictions)
        val satisfies = if (e.verifiedConfigs) fit.allSatisfied else true
        val reasons = buildList {
            if (s.alias) add("Alias reconocido de «${e.name}».")
            else if (s.coverage >= 0.999) add("Su nombre explica todas las palabras que escribiste.")
            else add("Coincide en parte con el nombre que escribiste.")
            if (restrictions.isNotEmpty() && e.verifiedConfigs) {
                if (satisfies) add("Existe en el catálogo con la variante pedida.")
                else add("El catálogo no tiene esa variante exacta.")
            }
            if (e.custom) add("Es un ejercicio tuyo.")
        }
        return ReferenceCandidate(
            definitionId = e.definitionId,
            name = e.name,
            configurationId = if (satisfies) fit.bestConfig?.id else null,
            score = s.score,
            identityScore = s.identity,
            reasons = reasons,
            satisfiesVariant = satisfies,
            isCustom = e.custom,
        )
    }

    private fun keysOf(token: String): Set<String> {
        val keys = mutableSetOf(ExerciseMatchLexicon.synonymKey(token))
        val t = ExerciseMatchLexicon.normalize(token)
        // El stem compartido mutila plurales como «raises» → «rais»: también se prueba el singular simple.
        if (t.endsWith("s") && t.length > 4) keys += ExerciseMatchLexicon.synonymKey(t.dropLast(1))
        return keys
    }

    private fun matchCount(core: List<String>, phraseCore: List<String>): Int {
        val used = mutableSetOf<Int>()
        var matches = 0
        for (c in core) {
            val ck = keysOf(c)
            for ((i, p) in phraseCore.withIndex()) {
                if (i in used) continue
                if (ck.any { it in keysOf(p) }) {
                    used += i
                    matches += 1
                    break
                }
            }
        }
        return matches
    }

    // ─── Construcción ────────────────────────────────────────────────────────

    companion object {
        private val OPEN_KEYS: Map<String, List<String>> by lazy {
            OPEN_QUERY_PRIORITY_TABLE.mapKeys { (key, _) ->
                ExerciseMatchLexicon.normalize(key).split(' ').filter { it.isNotBlank() && it !in NameLexicon.STOP }.joinToString(" ")
            }
        }

        private val PARENTHESES = Regex("\\([^)]*\\)")
        private val INNER_PARENTHESES = Regex("\\(([^)]*)\\)")

        private fun phraseTokens(text: String): List<String> =
            ExerciseMatchLexicon.normalize(text).split(' ').filter { it.isNotBlank() && it !in NameLexicon.STOP }

        private fun phraseOf(text: String, weight: Double): Phrase {
            val tokens = phraseTokens(text)
            val core = tokens.filter { ExerciseMatchLexicon.stem(it) !in NameLexicon.RESTRICTION_STEMS }.ifEmpty { tokens }
            val implement = ExerciseNameAnalyzer.extractPhrase(tokens, NameLexicon.IMPLEMENT_PHRASES)?.value
            return Phrase(tokens, core, weight, implement)
        }

        /** Nombre y alias del catálogo; el texto entre paréntesis es otro nombre del mismo ejercicio y cuenta como frase propia. */
        private fun expandedPhrases(name: String, terms: List<String>): List<Phrase> {
            val out = mutableListOf<Phrase>()
            (listOf(name to 1.0) + terms.map { it to 0.98 }).forEach { (text, weight) ->
                out += phraseOf(text, weight)
                if (text.contains('(')) {
                    out += phraseOf(text.replace(PARENTHESES, " "), weight)
                    INNER_PARENTHESES.findAll(text).forEach { out += phraseOf(it.groupValues[1], weight) }
                }
            }
            return out
        }

        private fun nameWordsOf(name: String): Set<String> = phraseTokens(name).map { ExerciseMatchLexicon.stem(it) }.toSet()
        private fun nameKeysOf(name: String): Set<String> = phraseTokens(name).map { ExerciseMatchLexicon.synonymKey(it) }.toSet()

        private fun entryOf(definition: ExerciseDefinitionV2): Entry = Entry(
            definitionId = definition.id,
            name = definition.canonicalName,
            parent = definition.kind == ExerciseDefinitionKindV2.PARENT,
            phrases = expandedPhrases(definition.canonicalName, definition.searchTerms),
            configs = definition.configurations.map { c ->
                EntryConfig(c.id, c.selectedOptions + ("implement" to (c.selectedOptions["implement"] ?: c.profile.equipmentId)))
            },
            defaultConfigId = definition.defaultConfigurationId,
            verifiedConfigs = true,
            custom = false,
            nameWords = nameWordsOf(definition.canonicalName),
            nameKeys = nameKeysOf(definition.canonicalName),
        )

        private fun entryOf(info: ExerciseMuscleInfo): Entry {
            val terms = info.alias.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }
            val implementId = info.equipment?.let { SmartExerciseCreator.implementoIdFromLabel(it) }
            return Entry(
                definitionId = info.catalogDefinitionId ?: info.id,
                name = info.name,
                parent = !info.isCustom,
                phrases = expandedPhrases(info.name, terms),
                configs = listOf(EntryConfig(info.catalogConfigurationId, listOfNotNull(implementId?.let { "implement" to it }).toMap())),
                defaultConfigId = info.catalogConfigurationId,
                verifiedConfigs = false,
                custom = info.isCustom,
                nameWords = nameWordsOf(info.name),
                nameKeys = nameKeysOf(info.name),
            )
        }

        private fun vocabularyOf(entries: List<Entry>): ExerciseNameVocabulary =
            ExerciseNameVocabulary.of(entries.flatMap { e -> e.phrases.map { it.tokens.joinToString(" ") } })

        /** Motor sobre el catálogo v2 completo (todas las configuraciones) más los ejercicios propios como candidatos del mismo contrato. */
        fun forCatalog(catalog: ExerciseCatalogV2, customs: List<ExerciseMuscleInfo> = emptyList()): ExerciseReferenceFinder {
            val entries = catalog.families.flatMap { it.definitions }.map { entryOf(it) } + customs.map { entryOf(it) }
            return ExerciseReferenceFinder(entries, vocabularyOf(entries), catalog.catalogRevision)
        }

        /** Motor sobre una instantánea plana (una configuración por ejercicio): sin catálogo v2 no se puede afirmar que una variante no exista. */
        fun forSnapshot(snapshot: List<ExerciseMuscleInfo>): ExerciseReferenceFinder {
            val entries = snapshot.filter { it.efc != null && it.cnc != null || it.isCustom || it.involvedMuscles.isNotEmpty() }.map { entryOf(it) }
            return ExerciseReferenceFinder(entries, vocabularyOf(entries), snapshot.firstOrNull { it.catalogRevision != null }?.catalogRevision)
        }
    }
}
