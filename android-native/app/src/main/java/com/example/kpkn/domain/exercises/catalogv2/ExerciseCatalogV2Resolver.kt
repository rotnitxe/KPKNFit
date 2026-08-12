package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon

/**
 * Pure resolver for the v2 contract. It never falls back to a default when a
 * definition/configuration pair is invalid and it never creates combinations
 * that were not materialized by the compiler.
 *
 * Search uses the shared match lexicon: accent folding, singular/plural,
 * synonyms ES/EN, prefix/substring matching, muscle aliases and typo
 * tolerance, keeping AND semantics across query terms.
 */
class ExerciseCatalogV2Resolver(
    private val catalog: ExerciseCatalogV2,
) {
    private val definitionsById: Map<String, ExerciseDefinitionV2> =
        catalog.families
            .flatMap { it.definitions }
            .associateBy { it.id }

    private data class SearchIndex(
        val text: String,
        val name: String,
        val nameTokens: List<String>,
        val rawTokens: Set<String>,
        val keys: Set<String>,
        val familyName: String,
    )

    private data class ConfigurationMatch(
        val configuration: ExerciseConfigurationV2?,
        val score: Int,
        val order: Int = 0,
    )

    private val definitionIndex: Map<String, SearchIndex> =
        catalog.families.flatMap { family ->
            family.definitions.map { definition ->
                definition.id to buildIndex(definition, family.canonicalName)
            }
        }.toMap()

    init {
        require(catalog.schemaVersion == 2) { "Unsupported catalog schema: ${catalog.schemaVersion}" }
        require(definitionsById.size == catalog.families.sumOf { it.definitions.size }) {
            "Duplicate definition id in catalog"
        }
        catalog.families.forEach { family ->
            require(family.definitions.all { it.familyId == family.id }) {
                "Family mismatch in ${family.id}"
            }
            family.definitions.forEach { definition ->
                require(definition.configurations.map { it.id }.distinct().size == definition.configurations.size) {
                    "Duplicate configuration id in ${definition.id}"
                }
                require(definition.defaultConfigurationId in definition.configurations.map { it.id }) {
                    "Invalid default configuration in ${definition.id}"
                }
                require(definition.configurations.all { configuration ->
                    val expected = definition.optionAxes.toMutableSet()
                    if ("pulley_height" in expected) {
                        if (configuration.selectedOptions["implement"] == "cable") {
                            if ("pulley_height" !in configuration.selectedOptions) return@all false
                        } else {
                            if ("pulley_height" in configuration.selectedOptions) return@all false
                            expected.remove("pulley_height")
                        }
                    }
                    configuration.selectedOptions.keys == expected
                }) {
                    "Configuration options do not match axes in ${definition.id}"
                }
            }
        }
    }

    fun validate(selection: ExerciseSelectionV2): ExerciseSelectionValidationV2 {
        if (selection.catalogRevision != catalog.catalogRevision) {
            return ExerciseSelectionValidationV2.Invalid(
                "catalog_revision_mismatch:${selection.catalogRevision}:${catalog.catalogRevision}",
            )
        }
        val definition = definitionsById[selection.definitionId]
            ?: return ExerciseSelectionValidationV2.Invalid("unknown_definition:${selection.definitionId}")
        if (definition.configurations.none { it.id == selection.configurationId }) {
            return ExerciseSelectionValidationV2.Invalid(
                "unknown_configuration:${selection.definitionId}:${selection.configurationId}",
            )
        }
        return ExerciseSelectionValidationV2.Valid(selection)
    }

    fun resolve(selection: ExerciseSelectionV2): ResolvedExerciseProfileV2? =
        when (validate(selection)) {
            is ExerciseSelectionValidationV2.Invalid -> null
            is ExerciseSelectionValidationV2.Valid -> definitionsById[selection.definitionId]
                ?.configurations
                ?.firstOrNull { it.id == selection.configurationId }
                ?.profile
        }

    fun search(
        query: String,
        filters: ExerciseSearchFiltersV2 = ExerciseSearchFiltersV2(),
    ): List<ExerciseSearchHitV2> {
        val normalizedQuery = ExerciseMatchLexicon.normalize(query)
        val queryTerms = normalizedQuery.split(' ').filter(String::isNotBlank)
        val rawTerms = queryTerms
            .filterNot { it in SEARCH_STOP_WORDS }
            .ifEmpty { queryTerms }
        if (rawTerms.isEmpty()) return emptyList()
        val openQuery = isOpenQuery(normalizedQuery)

        return definitionsById.values
            .filter { definition ->
                val configurations = definition.configurations
                (filters.familyIds.isEmpty() || definition.familyId in filters.familyIds) &&
                    (filters.kinds.isEmpty() || definition.kind in filters.kinds) &&
                    (filters.bodyRegions.isEmpty() || configurations.any { it.profile.bodyRegion in filters.bodyRegions }) &&
                    (filters.equipmentIds.isEmpty() || configurations.any { it.profile.equipmentId in filters.equipmentIds }) &&
                    (filters.movementPatternIds.isEmpty() || configurations.any { it.profile.movementPatternId in filters.movementPatternIds }) &&
                    (filters.muscleIds.isEmpty() || configurations.any { config ->
                        config.profile.primaryMuscles.any { it in filters.muscleIds }
                    })
            }
            .mapNotNull { definition ->
                val index = definitionIndex.getValue(definition.id)
                if (!rawTerms.all { termMatchesIndex(index, it) }) return@mapNotNull null

                // A query may contain both the parent name and a configuration
                // term ("curl bayesiano"). The parent satisfies the full query,
                // while the configuration term selects one explicit config. It
                // must never synthesize a configuration from the remaining axes.
                val configurationMatch = bestConfigurationMatch(
                    definition = definition,
                    normalizedQuery = normalizedQuery,
                    terms = rawTerms,
                )
                val exactNameBonus = if (index.name == normalizedQuery) 180 else 0
                val exactAliasBonus = if (
                    definition.searchTerms.any { ExerciseMatchLexicon.normalize(it) == normalizedQuery }
                ) {
                    150
                } else {
                    0
                }
                val phraseBonus = if (normalizedQuery.length >= 4 && index.name.contains(normalizedQuery)) 60 else 0
                val allNameTermsBonus = if (rawTerms.all { term ->
                    index.nameTokens.any { token -> token == term || token.startsWith(term) }
                }) {
                    40
                } else {
                    0
                }
                val score = rawTerms.sumOf { term ->
                    val name = index.name
                    val nameTokens = index.nameTokens
                    val stemmed = ExerciseMatchLexicon.stem(term)
                    when {
                        name == term -> 100
                        name.startsWith(term) -> 48
                        nameTokens.any { it.startsWith(term) } -> 40
                        name.contains(term) -> 30
                        ExerciseMatchLexicon.synonymKey(term) in index.keys -> 26
                        stemmed.length >= 2 && nameTokens.any { it == stemmed || it.startsWith(stemmed) } -> 22
                        index.familyName.contains(term) -> 18
                        index.text.contains(term) -> 12
                        else -> 6
                    }
                } + exactNameBonus + exactAliasBonus + phraseBonus + allNameTermsBonus +
                    configurationMatch.score + if (openQuery) openQueryPriorityBonus(definition, normalizedQuery) else 0

                ExerciseSearchHitV2(
                    definitionId = definition.id,
                    suggestedConfigurationId = configurationMatch.configuration?.id,
                    matchedTerm = normalizedQuery,
                    score = score,
                )
            }
            .sortedWith(compareByDescending<ExerciseSearchHitV2> { it.score }
                .thenBy { definitionsById.getValue(it.definitionId).canonicalName.length }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { definitionsById.getValue(it.definitionId).canonicalName })
    }

    private fun buildIndex(definition: ExerciseDefinitionV2, familyName: String): SearchIndex {
        val raw = buildString {
            append(definition.canonicalName)
            append(' ')
            append(definition.description)
            append(' ')
            append(definition.searchTerms.joinToString(" "))
            append(' ')
            append(familyName)
            definition.configurations.forEach { configuration ->
                append(' ')
                append(configuration.displaySummary)
                append(' ')
                append(configuration.selectedOptions.values.joinToString(" ") { localizedCatalogTerms(it) })
                append(' ')
                append(localizedCatalogTerms(configuration.profile.equipmentId))
                append(' ')
                append(localizedMuscleTerms(configuration.profile.primaryMuscles))
                append(' ')
                append(localizedMuscleTerms(configuration.profile.secondaryMuscles))
                append(' ')
                append(localizedMuscleTerms(configuration.profile.stabilizerMuscles))
                append(' ')
                append(configuration.profile.setupCues.joinToString(" "))
                append(' ')
                append(configuration.profile.executionCues.joinToString(" "))
            }
        }
        val text = ExerciseMatchLexicon.normalize(raw.toString())
        return SearchIndex(
            text = text,
            name = ExerciseMatchLexicon.normalize(definition.canonicalName),
            nameTokens = ExerciseMatchLexicon.normalize(definition.canonicalName)
                .split(' ').filter(String::isNotBlank),
            rawTokens = ExerciseMatchLexicon.stemTokens(text),
            keys = ExerciseMatchLexicon.tokenKeys(text),
            familyName = ExerciseMatchLexicon.normalize(familyName),
        )
    }

    private fun buildConfigText(configuration: ExerciseConfigurationV2): String =
        buildString {
            append(configuration.displaySummary)
            append(' ')
            append(configuration.selectedOptions.values.joinToString(" ") { localizedCatalogTerms(it) })
            append(' ')
            append(localizedCatalogTerms(configuration.profile.equipmentId))
        }

    private fun termMatchesIndex(index: SearchIndex, term: String): Boolean {
        val key = ExerciseMatchLexicon.synonymKey(term)
        if (key in index.keys) return true
        val stemmed = ExerciseMatchLexicon.stem(term)
        if (stemmed.length >= 2 && index.rawTokens.any { it == stemmed || it.startsWith(stemmed) }) {
            return true
        }
        if (term.length >= 3 && index.text.contains(term)) return true
        if (term.length >= 5 && index.rawTokens.any { ExerciseMatchLexicon.fuzzyMatch(term, it) }) {
            return true
        }
        return false
    }

    private fun textMatchesTerm(text: String, term: String): Boolean {
        val stemmed = ExerciseMatchLexicon.stem(term)
        if (stemmed.length >= 2) {
            val tokens = ExerciseMatchLexicon.stemTokens(text)
            if (tokens.any { it == stemmed || it.startsWith(stemmed) }) return true
        }
        return term.length >= 3 && text.contains(term)
    }

    private fun bestConfigurationMatch(
        definition: ExerciseDefinitionV2,
        normalizedQuery: String,
        terms: List<String>,
    ): ConfigurationMatch {
        val candidates = definition.configurations.mapIndexedNotNull { order, configuration ->
            val configText = ExerciseMatchLexicon.normalize(buildConfigText(configuration))
            val matchedTerms = terms.count { term ->
                term.length >= 3 && textMatchesTerm(configText, term)
            }
            if (matchedTerms == 0) return@mapIndexedNotNull null
            val phraseBonus = if (normalizedQuery.length >= 4 && configText.contains(normalizedQuery)) 48 else 0
            ConfigurationMatch(
                configuration = configuration,
                score = matchedTerms * 36 + phraseBonus,
                order = order,
            )
        }
        return candidates.maxWithOrNull(
            compareBy<ConfigurationMatch> { it.score }
                .thenBy { it.configuration?.id == definition.defaultConfigurationId }
                .thenBy { -it.order },
        ) ?: ConfigurationMatch(null, score = 0)
    }

    /**
     * A curated ordering is only appropriate for a family-level query. Once a
     * user adds a variant, implement, stance, grip or machine term, the normal
     * name/alias/configuration match score must decide the order.
     */
    private fun isOpenQuery(normalizedQuery: String): Boolean =
        normalizedQuery in OPEN_QUERY_ALIASES || definitionsById.values.any {
            ExerciseMatchLexicon.normalize(it.canonicalName) == normalizedQuery
        }

    private fun openQueryPriorityBonus(
        definition: ExerciseDefinitionV2,
        normalizedQuery: String,
    ): Int {
        val curatedOrder = OPEN_QUERY_PRIORITY[normalizedQuery]
        val rank = curatedOrder?.indexOf(definition.id) ?: -1
        return when {
            rank >= 0 -> 10_000 - rank * 100
            definition.kind == ExerciseDefinitionKindV2.PARENT -> 500
            else -> 0
        }
    }

    private fun localizedMuscleTerms(ids: List<String>): String = ids.joinToString(" ") { id ->
        when (id) {
            "pectoralis" -> "pectoral pectorales pecho chest"
            "deltoid" -> "deltoides hombro shoulder"
            "triceps" -> "triceps tricep"
            "biceps" -> "biceps bicep"
            "forearm" -> "antebrazo forearm"
            "latissimus_dorsi" -> "dorsal dorsales espalda back lat"
            "erector_spinae" -> "erectores espinales erector spine"
            "hamstrings" -> "isquiosurales isquio isquios hamstring"
            "gluteus_maximus" -> "gluteos gluteo glute glutes"
            "gluteus_medius" -> "gluteo medio"
            "quadriceps" -> "cuadriceps quad quads"
            "calves" -> "pantorrillas pantorrilla calf calves"
            "tibialis_anterior" -> "tibial anterior"
            "hip_flexors" -> "flexores cadera hip flexor"
            "neck" -> "cuello neck"
            "adductors" -> "aductores adductor"
            "tensor_fasciae_latae" -> "tensor fascia lata"
            "trapezius" -> "trapecio trapezius traps"
            "rhomboids" -> "romboides"
            "abdominals" -> "abdominal abdominales abs"
            "core" -> "core"
            else -> id
        }
    }

    private fun localizedCatalogTerms(value: String): String = when (value.lowercase()) {
        "barbell" -> "barra barra libre barra recta"
        "band" -> "banda banda elastica"
        "cable" -> "polea cable"
        "dumbbells" -> "mancuerna mancuernas"
        "machine" -> "maquina máquina"
        "bodyweight" -> "peso corporal"
        "plate" -> "disco"
        "safety_bar" -> "barra de seguridad safety bar"
        "smith_machine" -> "smith maquina máquina"
        "hex_bar" -> "barra hexagonal trap bar hex bar"
        "kettlebell" -> "kettlebell pesa rusa"
        "standing" -> "de pie parado"
        "seated" -> "sentado"
        "pec_deck" -> "pec deck"
        "pec_deck_reverse" -> "pec deck inverso reverse pec fly"
        "supinated" -> "supino"
        "pronated" -> "prono"
        "neutral" -> "neutro"
        else -> value
    }

    private companion object {
        private val SEARCH_STOP_WORDS = setOf("de", "del", "la", "el", "en", "con", "para", "por")

        private val OPEN_QUERY_ALIASES = setOf(
            "peso muerto",
            "deadlift",
            "sentadilla",
            "sentadillas",
            "squat",
            "press",
            "push",
            "press banca",
            "press de banca",
            "remo",
            "row",
            "curl",
            "apertura",
            "aperturas",
            "fly",
            "elevacion",
            "elevaciones",
            "jalon",
            "jalones",
            "pulldown",
            "dominada",
            "dominadas",
            "pull up",
            "zancada",
            "zancadas",
            "lunge",
            "prensa",
            "leg press",
            "hip thrust",
        )

        private val OPEN_QUERY_PRIORITY = mapOf(
            "peso muerto" to listOf(
                "conventional_deadlift",
                "romanian_deadlift",
                "sumo_deadlift",
                "stiff_leg_deadlift",
                "romanian_sumo_deadlift",
            ),
            "deadlift" to listOf(
                "conventional_deadlift",
                "romanian_deadlift",
                "sumo_deadlift",
                "stiff_leg_deadlift",
                "romanian_sumo_deadlift",
            ),
            "sentadilla" to listOf(
                "high_bar_back_squat",
                "low_bar_back_squat",
                "front_squat",
                "sumo_squat",
                "belt_squat",
                "pendulum_squat",
                "quads_sentadilla_hack",
                "sissy_squat",
                "bulgarian_split_squat",
            ),
            "sentadillas" to listOf(
                "high_bar_back_squat",
                "low_bar_back_squat",
                "front_squat",
                "sumo_squat",
                "belt_squat",
                "pendulum_squat",
                "quads_sentadilla_hack",
                "sissy_squat",
                "bulgarian_split_squat",
            ),
            "squat" to listOf(
                "high_bar_back_squat",
                "low_bar_back_squat",
                "front_squat",
                "sumo_squat",
                "belt_squat",
                "pendulum_squat",
                "quads_sentadilla_hack",
                "sissy_squat",
                "bulgarian_split_squat",
            ),
            "press" to listOf(
                "bench_press",
                "incline_bench_press",
                "decline_bench_press",
                "military_press",
                "seated_shoulder_press",
                "floor_press",
                "arnold_press",
                "z_press",
                "california_press",
                "triceps_press_frances",
            ),
            "push" to listOf(
                "bench_press",
                "incline_bench_press",
                "decline_bench_press",
                "military_press",
                "seated_shoulder_press",
                "floor_press",
                "arnold_press",
                "z_press",
            ),
            "press banca" to listOf(
                "bench_press",
                "incline_bench_press",
                "decline_bench_press",
            ),
            "press de banca" to listOf(
                "bench_press",
                "incline_bench_press",
                "decline_bench_press",
            ),
            "remo" to listOf(
                "back_conventional_row",
                "back_pendlay_row",
                "back_chest_supported_row",
                "back_t_bar_row",
                "back_seal_row",
                "back_gironda_row",
            ),
            "row" to listOf(
                "back_conventional_row",
                "back_pendlay_row",
                "back_chest_supported_row",
                "back_t_bar_row",
                "back_seal_row",
                "back_gironda_row",
            ),
        )
    }
}
