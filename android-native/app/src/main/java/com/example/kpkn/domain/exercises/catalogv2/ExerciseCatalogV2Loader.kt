package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.serialization.json.Json

/** Runtime gate: draft or partially reviewed catalog data is never loadable. */
object ExerciseCatalogV2Loader {
    private val json = Json { ignoreUnknownKeys = false }

    private fun requireNonBlank(value: String, label: String) {
        require(value.isNotBlank()) { "$label must not be blank" }
    }

    private fun requireNonBlankList(values: List<String>, label: String, allowEmpty: Boolean = false) {
        if (!allowEmpty) require(values.isNotEmpty()) { "$label must not be empty" }
        require(values.all { it.isNotBlank() }) { "$label contains blank text" }
    }
    fun decodeApproved(payload: String): ExerciseCatalogV2 {
        val catalog = json.decodeFromString(ExerciseCatalogV2.serializer(), payload)
        require(catalog.schemaVersion == 2) { "Unsupported catalog schema: ${catalog.schemaVersion}" }
        requireNonBlank(catalog.catalogRevision, "catalogRevision")
        requireNonBlank(catalog.ontologyRevision, "ontologyRevision")
        require(catalog.families.isNotEmpty()) { "Catalog must contain at least one family" }
        require(catalog.families.all { it.evidence.reviewStatus == CatalogReviewStatusV2.APPROVED }) { "Catalog family is not approved" }
        require(catalog.families.all { family ->
            family.definitions.all { definition ->
                definition.evidence.reviewStatus == CatalogReviewStatusV2.APPROVED &&
                    definition.configurations.all { configuration ->
                        configuration.evidence.reviewStatus == CatalogReviewStatusV2.APPROVED &&
                            configuration.profile.automationEligible &&
                            configuration.profile.richMetadata != null &&
                            configuration.profile.richMetadata.evidenceConfidence != CatalogConfidenceV2.LOW
                    }
            }
        }) { "Catalog contains unapproved definitions, configurations, or profiles" }
        validateApprovedStructure(catalog)
        return catalog
    }

    private fun validateApprovedStructure(catalog: ExerciseCatalogV2) {
        val familyIds = mutableSetOf<String>()
        val definitionIds = mutableSetOf<String>()
        val configurationIds = mutableSetOf<String>()

        catalog.families.forEach { family ->
            require(familyIds.add(family.id)) { "Duplicate family id: ${family.id}" }
            require(family.id.isNotBlank()) { "Family id must not be blank" }
            requireNonBlank(family.canonicalName, "family.canonicalName:${family.id}")
            require(family.description.length >= 40) { "family.description is too short: ${family.id}" }
            family.definitions.forEach { definition ->
                require(definitionIds.add(definition.id)) {
                    "Duplicate definition id: ${definition.id}"
                }
                require(definition.familyId == family.id) {
                    "Definition family mismatch: ${definition.id}"
                }
                require(definition.configurations.isNotEmpty()) {
                    "Definition must have configurations: ${definition.id}"
                }
                requireNonBlank(definition.id, "definition.id")
                requireNonBlank(definition.canonicalName, "definition.canonicalName")
                require(definition.description.length >= 40) { "definition.description is too short" }
                requireNonBlankList(definition.searchTerms, "definition.searchTerms", allowEmpty = true)
                require(definition.defaultConfigurationId in definition.configurations.map { it.id }) {
                    "Default configuration missing: ${definition.id}"
                }
                require(definition.optionAxes.distinct().size == definition.optionAxes.size) {
                    "Duplicate option axis: ${definition.id}"
                }

                val valuesByAxis = definition.optionAxes.associateWith { axis ->
                    definition.configurations.map { configuration ->
                        configuration.selectedOptions[axis]
                    }.toSet()
                }
                valuesByAxis.forEach { (axis, values) ->
                    if (axis == "pulley_height") return@forEach
                    if (axis == "implement" && "pulley_height" in definition.optionAxes) {
                        // Cable-fixed definition: implement is implicitly cable.
                        return@forEach
                    }
                    require(values.none { it.isNullOrBlank() }) {
                        "Configuration option missing: ${definition.id}:$axis"
                    }
                    require(values.size > 1) {
                        "Singleton option axis is not allowed: ${definition.id}:$axis"
                    }
                }

                val signatures = mutableSetOf<String>()
                definition.configurations.forEach { configuration ->
                    require(configurationIds.add(configuration.id)) {
                        "Duplicate configuration id: ${configuration.id}"
                    }
                    val expectedOptions = definition.optionAxes.toMutableSet()
                    if ("pulley_height" in expectedOptions) {
                        if ("implement" in expectedOptions) {
                            val implement = configuration.selectedOptions["implement"]
                            if (implement == "cable") {
                                require("pulley_height" in configuration.selectedOptions) {
                                    "Cable configuration must include pulley_height: ${configuration.id}"
                                }
                            } else {
                                require("pulley_height" !in configuration.selectedOptions) {
                                    "pulley_height is only allowed for cable: ${configuration.id}"
                                }
                                expectedOptions.remove("pulley_height")
                            }
                        } else {
                            require("pulley_height" in configuration.selectedOptions) {
                                "Cable-fixed configuration must include pulley_height: ${configuration.id}"
                            }
                        }
                    }
                    require(configuration.selectedOptions.keys == expectedOptions) {
                        "Configuration axes mismatch: ${configuration.id}"
                    }
                    require(signatures.add(
                        definition.optionAxes.joinToString("|") { axis ->
                            "$axis=${configuration.selectedOptions[axis]}"
                        },
                    )) {
                        "Duplicate configuration option signature: ${configuration.id}"
                    }

                    val profile = configuration.profile
                    requireNonBlank(configuration.id, "configuration.id")
                    requireNonBlank(configuration.displaySummary, "configuration.displaySummary")
                    requireNonBlank(profile.movementPatternId, "profile.movementPatternId")
                    requireNonBlank(profile.equipmentId, "profile.equipmentId")
                    requireNonBlank(profile.loadMode, "profile.loadMode")
                    requireNonBlank(profile.resistanceProfile, "profile.resistanceProfile")
                    require(profile.description.trim().length >= 40) {
                        "Configuration description is too short: ${configuration.id}"
                    }
                    requireNonBlank(profile.performanceProfileId, "profile.performanceProfileId")
                    requireNonBlankList(profile.primaryMuscles, "profile.primaryMuscles")
                    requireNonBlankList(profile.setupCues, "profile.setupCues")
                    requireNonBlankList(profile.executionCues, "profile.executionCues")
                    requireNonBlankList(profile.commonMistakes, "profile.commonMistakes")
                    val listedMuscles = profile.primaryMuscles + profile.secondaryMuscles + profile.stabilizerMuscles
                    require(listedMuscles.size == listedMuscles.toSet().size) {
                        "Muscle listed in more than one role: ${configuration.id}"
                    }
                    require(profile.muscleNotes.isNotEmpty()) {
                        "muscleNotes cannot be empty: ${configuration.id}"
                    }
                    require(profile.muscleNotes.size == profile.muscleNotes.map { it.muscleId }.toSet().size) {
                        "Duplicate muscle note: ${configuration.id}"
                    }
                    val notedMuscles = profile.muscleNotes.map { it.muscleId }.toSet()
                    require(notedMuscles == listedMuscles.toSet()) {
                        "muscleNotes must cover listed muscles exactly: ${configuration.id}"
                    }
                    require(profile.muscleNotes.all { it.note.trim().length >= 40 }) {
                        "muscle note is too short: ${configuration.id}"
                    }
                    require(profile.efc.isFinite() && profile.efc >= 0.0)
                    require(profile.cnc.isFinite() && profile.cnc >= 0.0)
                    require(profile.ssc.isFinite() && profile.ssc >= 0.0)
                    require(profile.ttc.isFinite() && profile.ttc >= 0.0)
                    require(profile.axialLoadFactor.isFinite() && profile.axialLoadFactor >= 0.0)
                    require(profile.technicalDifficulty in 1.0..10.0)
                    require(profile.automationEligible) {
                        "Automation is not eligible: ${configuration.id}"
                    }
                    val rich = profile.richMetadata
                        ?: error("Rich metadata missing: ${configuration.id}")
                    require(rich.identity.catalogRevision == catalog.catalogRevision)
                    require(rich.identity.familyId == family.id)
                    require(rich.identity.definitionId == definition.id)
                    require(rich.identity.configurationId == configuration.id)
                    require(rich.identity.canonicalName == definition.canonicalName)
                    require(rich.identity.searchTerms == definition.searchTerms)
                    require(rich.identity.kind == definition.kind)
                    require(rich.identity.performanceProfileId == profile.performanceProfileId)
                    require(rich.anatomy.primaryMuscles == profile.primaryMuscles)
                    require(rich.anatomy.secondaryMuscles == profile.secondaryMuscles)
                    require(rich.anatomy.stabilizerMuscles == profile.stabilizerMuscles)
                    require(rich.biomechanics.movementPatternId == profile.movementPatternId)
                    require(rich.biomechanics.bodyRegion == profile.bodyRegion)
                    require(rich.biomechanics.kineticChain == profile.kineticChain)
                    require(rich.biomechanics.laterality == profile.laterality)
                    require(rich.biomechanics.equipmentId == profile.equipmentId)
                    require(rich.biomechanics.loadMode == profile.loadMode)
                    require(rich.biomechanics.resistanceProfile == profile.resistanceProfile)
                    require(rich.fatigue.efc == profile.efc)
                    require(rich.fatigue.cnc == profile.cnc)
                    require(rich.fatigue.ssc == profile.ssc)
                    require(rich.fatigue.ttc == profile.ttc)
                    require(rich.fatigue.axialLoadFactor == profile.axialLoadFactor)
                    require(rich.fatigue.technicalDifficulty == profile.technicalDifficulty)
                    require(rich.replacement.replacementGroup == profile.replacementGroup)
                    require(rich.replacement.replacementPriority == profile.replacementPriority)
                    require(rich.coaching.setup == profile.setupCues)
                    require(rich.coaching.execution == profile.executionCues)
                    require(rich.coaching.commonMistakes == profile.commonMistakes)
                    require(rich.display.displayName == definition.canonicalName)
                    require(rich.display.displaySummary == configuration.displaySummary)
                    require(rich.display.selectedOptions == configuration.selectedOptions)
                    require(rich.evidenceConfidence == configuration.evidence.confidence)
                    requireNonBlankList(rich.anatomy.targetRegions, "rich.anatomy.targetRegions")
                    requireNonBlankList(rich.anatomy.jointActions, "rich.anatomy.jointActions")
                    requireNonBlank(rich.anatomy.muscleLengthBias ?: "", "rich.anatomy.muscleLengthBias")
                    requireNonBlank(rich.anatomy.volumeContribution ?: "", "rich.anatomy.volumeContribution")
                    requireNonBlank(rich.anatomy.stabilizationDemand ?: "", "rich.anatomy.stabilizationDemand")
                    requireNonBlank(rich.biomechanics.rangeOfMotion ?: "", "rich.biomechanics.rangeOfMotion")
                    requireNonBlank(rich.biomechanics.stability ?: "", "rich.biomechanics.stability")
                    requireNonBlankList(rich.biomechanics.relevantJoints, "rich.biomechanics.relevantJoints")
                    requireNonBlankList(rich.biomechanics.relevantTendons, "rich.biomechanics.relevantTendons", allowEmpty = true)
                    requireNonBlank(rich.programming.role ?: "", "rich.programming.role")
                    requireNonBlankList(rich.programming.objectives, "rich.programming.objectives")
                    requireNonBlankList(rich.programming.suitableRepRanges, "rich.programming.suitableRepRanges")
                    require(rich.programming.indicativeRestSeconds != null)
                    requireNonBlank(rich.programming.fatigueCost ?: "", "rich.programming.fatigueCost")
                    requireNonBlank(rich.programming.recoveryCost ?: "", "rich.programming.recoveryCost")
                    requireNonBlankList(rich.programming.requiredEquipment, "rich.programming.requiredEquipment")
                    requireNonBlank(rich.programming.setupTransitionCost ?: "", "rich.programming.setupTransitionCost")
                    requireNonBlankList(rich.programming.splitSuitability, "rich.programming.splitSuitability")
                    requireNonBlankList(rich.replacement.compatibleEquipmentIds, "rich.replacement.compatibleEquipmentIds", allowEmpty = true)
                    requireNonBlankList(rich.replacement.preservesIntent, "rich.replacement.preservesIntent")
                    requireNonBlankList(rich.coaching.cues, "rich.coaching.cues")
                    requireNonBlankList(rich.coaching.progressions, "rich.coaching.progressions")
                    requireNonBlankList(rich.coaching.regressions, "rich.coaching.regressions")
                    requireNonBlankList(rich.coaching.relevantMobility, "rich.coaching.relevantMobility", allowEmpty = true)
                    requireNonBlankList(rich.safety.risks, "rich.safety.risks", allowEmpty = true)
                    requireNonBlankList(rich.safety.precautions, "rich.safety.precautions", allowEmpty = true)
                    require(rich.evidenceConfidence != CatalogConfidenceV2.LOW)
                }
            }
        }
    }
}
