package com.example.kpkn.domain.exercises.catalogv2

import java.text.Normalizer

/**
 * Pure resolver for the v2 contract. It never falls back to a default when a
 * definition/configuration pair is invalid and it never creates combinations
 * that were not materialized by the compiler.
 */
class ExerciseCatalogV2Resolver(
    private val catalog: ExerciseCatalogV2,
) {
    private val definitionsById: Map<String, ExerciseDefinitionV2> =
        catalog.families
            .flatMap { it.definitions }
            .associateBy { it.id }

    init {
        require(catalog.schemaVersion == 2) { "Unsupported catalog schema: ${catalog.schemaVersion}" }
        require(definitionsById.size == catalog.families.sumOf { it.definitions.size }) {
            "Duplicate definition id in catalog"
        }
        catalog.families.forEach { family ->
            require(family.definitions.all { it.familyId == family.id }) {
                "Definition family mismatch in ${family.id}"
            }
            family.definitions.forEach { definition ->
                require(definition.configurations.map { it.id }.distinct().size == definition.configurations.size) {
                    "Duplicate configuration id in ${definition.id}"
                }
                require(definition.defaultConfigurationId in definition.configurations.map { it.id }) {
                    "Invalid default configuration in ${definition.id}"
                }
                require(definition.configurations.all { it.selectedOptions.keys == definition.optionAxes.toSet() }) {
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
        val normalizedTerms = normalize(query).split(' ').filter(String::isNotBlank)
        if (normalizedTerms.isEmpty()) return emptyList()

        return definitionsById.values
            .filter { definition ->
                val configurations = definition.configurations
                (filters.familyIds.isEmpty() || definition.familyId in filters.familyIds) &&
                    (filters.kinds.isEmpty() || definition.kind in filters.kinds) &&
                    (filters.bodyRegions.isEmpty() || configurations.any { it.profile.bodyRegion in filters.bodyRegions }) &&
                    (filters.equipmentIds.isEmpty() || configurations.any { it.profile.equipmentId in filters.equipmentIds }) &&
                    (filters.movementPatternIds.isEmpty() || configurations.any { it.profile.movementPatternId in filters.movementPatternIds })
            }
            .mapNotNull { definition ->
            val definitionText = normalize(
                buildString {
                    append(definition.canonicalName)
                    append(' ')
                    append(definition.description)
                    append(' ')
                    append(definition.searchTerms.joinToString(" "))
                    definition.configurations.forEach { configuration ->
                        append(' ')
                        append(configuration.displaySummary)
                        append(' ')
                        append(configuration.selectedOptions.values.joinToString(" "))
                    }
                },
            )
            if (!normalizedTerms.all { definitionText.contains(it) }) return@mapNotNull null

            // A query may contain both the parent name and a configuration
            // term ("curl bayesiano"). The parent satisfies the full query,
            // while the configuration term selects one explicit config. It
            // must never synthesize a configuration from the remaining axes.
            val configuration = definition.configurations
                .firstOrNull { config ->
                    val configText = normalize(
                        "${config.displaySummary} ${config.selectedOptions.values.joinToString(" ")}",
                    )
                    normalizedTerms.any { term -> term.length >= 4 && configText.contains(term) }
                }
            ExerciseSearchHitV2(
                definitionId = definition.id,
                suggestedConfigurationId = configuration?.id,
                matchedTerm = normalizedTerms.joinToString(" "),
                score = normalizedTerms.sumOf { term ->
                    when {
                        normalize(definition.canonicalName).contains(term) -> 100
                        definition.searchTerms.any { normalize(it).contains(term) } -> 50
                        else -> 10
                    }
                },
            )
            }.sortedWith(compareByDescending<ExerciseSearchHitV2> { it.score }.thenBy { it.definitionId })
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
}
