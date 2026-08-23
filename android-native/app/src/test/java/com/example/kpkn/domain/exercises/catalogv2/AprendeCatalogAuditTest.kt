package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.data.exercises.catalogv2.decodeCatalogRichMetadata
import com.example.kpkn.data.exercises.catalogv2.toLegacyDefaultCatalog
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AprendeCatalogAuditTest {
    private val staticExerciseRefs: Set<String> by lazy {
        val directory = listOf(
            File("src/main/assets/wikilab"),
            File("app/src/main/assets/wikilab"),
        ).first { it.isDirectory }
        directory.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .flatMap { file -> collectExerciseRefs(Json.parseToJsonElement(file.readText())) }
            .toSet()
    }

    private val catalogSource: File by lazy {
        listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
    }

    private val catalog: ExerciseCatalogV2 by lazy {
        ExerciseCatalogV2Loader.decodeApproved(catalogSource.readText())
    }

    private val catalogSha256: String by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest(catalogSource.readBytes())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    @Test
    fun approved_catalog_has_complete_aprende_ontology_and_editorial_coverage() {
        val report = auditAprendeCatalog(
            catalog = catalog,
            sourceSha256 = catalogSha256,
            wikiLabMuscleIds = staticIds("muscles.json"),
            wikiLabPatternIds = staticIds("movement_patterns.json"),
            wikiLabJointIds = staticIds("joints.json"),
        )

        assertEquals("v2-approved-2026-08-12-a", report.catalogRevision)
        assertEquals("wikilab-v3-2026-08-08", report.ontologyRevision)
        assertEquals(96, report.familyCount)
        assertEquals(196, report.definitionCount)
        assertEquals(521, report.configurationCount)
        assertEquals(521, report.richMetadataCount)
        assertEquals(521, report.editorialCoverageCount)
        assertEquals(521, report.jointCoverageCount)
        assertEquals(0, report.shortDescriptionCount)
        assertEquals(0, report.shortBenefitCount)
        assertEquals(0, report.shortTechniqueCount)
        assertEquals(0, report.shortVariantRationaleCount)
        assertEquals(0, report.duplicateDescriptionCount)
        assertEquals(0, report.duplicateTechniqueCount)
        assertEquals(0, report.duplicateVariantRationaleCount)
        assertEquals(0, report.desynchronizedMetadataCount)
        assertEquals(0, report.reverseLinkConsistencyIssueCount)
        assertEquals("d4ea20ed8fcce76c6b621f0b6f1ec3713270328ce44914a1a57931bb51ee453f", report.sourceSha256)
        assertTrue(report.unmappedMuscleIds.isEmpty())
        assertTrue(report.unmappedPatternIds.isEmpty())
        assertTrue(report.unknownJointIds.isEmpty())
        assertTrue(report.invalidLegacyMappings.isEmpty())
        assertEquals(21, AprendeOntology.catalogMuscleToWikiLab.size)
        assertEquals(64, AprendeOntology.catalogPatternToWikiLab.size)
        assertEquals(
            catalogMuscleIds(),
            AprendeOntology.catalogMuscleToWikiLab.keys,
        )
        assertEquals(
            catalogPatternIds(),
            AprendeOntology.catalogPatternToWikiLab.keys,
        )
        assertEquals(66, AprendeOntology.legacyExerciseDecisions.size)
        assertEquals(19, AprendeOntology.legacyExerciseNameDecisions.size)
        assertEquals(85, AprendeOntology.allLegacyExerciseDecisions.size)
        assertEquals(10, report.explicitlyRemovedLegacyIds.size)
        assertTrue(staticExerciseRefs.isEmpty())
        assertTrue(report.passes)
    }

    @Test
    fun exercise_detail_does_not_reintroduce_drain_or_rpe_surfaces() {
        val source = File("src/main/java/com/example/kpkn/screens/home/ConceptosClaveScreen.kt").readText()
        listOf("Drenaje", "Fatiga General", "RPE", "Wikipedia").forEach { forbidden ->
            assertTrue("surface contains $forbidden", !source.contains(forbidden, ignoreCase = true))
        }
        assertTrue(source.contains("BasicTextField"))
        assertTrue(!source.contains("OutlinedTextField"))
        assertTrue(!source.contains("ConceptoClaveDetailScreen"))
        val accordion = File("src/main/java/com/example/kpkn/screens/home/ConceptoClaveAccordion.kt").readText()
        assertTrue(accordion.contains("Leer más"))
        assertTrue(accordion.contains("Leer menos"))
    }

    @Test
    fun runtime_materializes_the_same_explicit_configuration_set_used_by_the_editor() {
        val runtime = catalog.toLegacyConfigurationLookup()
        val sourceConfigurationIds = catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .map { it.id }
            .toSet()

        assertEquals(521, runtime.size)
        assertEquals(sourceConfigurationIds, runtime.keys)
        assertTrue(runtime.values.all {
            val configurationId = it.catalogConfigurationId
            configurationId != null && configurationId in sourceConfigurationIds
        })
        assertTrue(runtime.values.all { it.decodeCatalogRichMetadata() != null })
    }

    @Test
    fun reverse_index_contains_every_configuration_on_each_exact_relation() {
        val reverse = buildAprendeCatalogReverseIndex(catalog)
        // A configuration contributes to exactly one movement-pattern bucket;
        // this also guards against parent-name deduplication.
        assertEquals(521, reverse.exerciseIdsByPattern.values.sumOf { it.size })
        catalog.families.flatMap { it.definitions }.flatMap { it.configurations }.forEach { configuration ->
            val profile = configuration.profile
            assertTrue(configuration.id in reverse.exerciseIdsByPattern[profile.movementPatternId].orEmpty())
            profile.jointInvolvement.forEach { joint ->
                assertTrue(configuration.id in reverse.exerciseIdsByJoint[joint.jointId].orEmpty())
            }
            (profile.primaryMuscles + profile.secondaryMuscles + profile.stabilizerMuscles).forEach { muscle ->
                assertTrue(configuration.id in reverse.exerciseIdsByMuscle[muscle].orEmpty())
            }
        }
    }

    @Test
    fun aprende_route_resolves_definition_and_exact_non_default_configuration_ids() {
        val defaults = catalog.toLegacyDefaultCatalog()
        val configurations = catalog.toLegacyConfigurationLookup()
        val index = defaults.associateBy { it.id.lowercase() } + configurations
        val definition = catalog.families
            .asSequence()
            .flatMap { it.definitions.asSequence() }
            .first { it.configurations.size > 1 }
        val defaultConfiguration = definition.configurations.first { it.id == definition.defaultConfigurationId }
        val nonDefault = definition.configurations.first { it.id != definition.defaultConfigurationId }

        val fromDefinition = resolveCatalogExerciseInfoInIndex(
            index = index,
            catalogConfigurationId = null,
            exerciseDbId = null,
            exerciseId = definition.id,
            exerciseName = null,
        )
        val fromConfiguration = resolveCatalogExerciseInfoInIndex(
            index = index,
            catalogConfigurationId = nonDefault.id,
            exerciseDbId = null,
            exerciseId = null,
            exerciseName = null,
        )

        assertEquals(defaultConfiguration.id, fromDefinition?.catalogConfigurationId)
        assertEquals(nonDefault.id, fromConfiguration?.catalogConfigurationId)
        assertEquals(
            nonDefault.selectedOptions,
            fromConfiguration?.decodeCatalogRichMetadata()?.display?.selectedOptions,
        )
        assertTrue(definition.optionAxes.isNotEmpty())
        assertTrue(definition.configurations.all { configuration ->
            configuration.selectedOptions.keys.all { it in definition.optionAxes }
        })
    }

    @Test
    fun concepts_surface_keeps_deterministic_editorial_lens_and_branding() {
        val home = listOf(
            File("src/main/java/com/example/kpkn/screens/home/HomeWikiLabSection.kt"),
            File("app/src/main/java/com/example/kpkn/screens/home/HomeWikiLabSection.kt"),
        ).first { it.exists() }.readText()
        assertTrue(home.contains("\"CONCEPTOS CLAVE\""))
        assertTrue(!home.contains(".shuffled()"))
    }

    @Test
    fun static_anatomy_refresh_is_revision_gated_without_a_room_migration() {
        val prepopulate = listOf(
            File("src/main/java/com/example/kpkn/data/WikiLabPrepopulate.kt"),
            File("app/src/main/java/com/example/kpkn/data/WikiLabPrepopulate.kt"),
        ).first { it.exists() }.readText()
        val database = listOf(
            File("src/main/java/com/example/kpkn/data/db/KpknDatabase.kt"),
            File("app/src/main/java/com/example/kpkn/data/db/KpknDatabase.kt"),
        ).first { it.exists() }.readText()

        assertTrue(prepopulate.contains("APRENDE_CONTENT_REVISION = \"conceptos-clave-v1-2026-08-22\""))
        assertTrue(prepopulate.contains("currentRevision != APRENDE_CONTENT_REVISION"))
        assertTrue(prepopulate.contains("putString(APRENDE_CONTENT_PREF_KEY, APRENDE_CONTENT_REVISION)"))
        assertTrue(database.contains("version = 23"))
    }

    private fun collectExerciseRefs(element: JsonElement): List<String> = when (element) {
        is JsonArray -> element.flatMap(::collectExerciseRefs)
        is JsonObject -> element.entries.flatMap { (key, value) ->
            val direct = if (key in EXERCISE_REFERENCE_KEYS && value is JsonArray) {
                value.mapNotNull { item ->
                    (item as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
            } else {
                emptyList()
            }
            direct + collectExerciseRefs(value)
        }
        else -> emptyList()
    }

    private fun staticIds(fileName: String): Set<String> {
        val directory = staticExerciseRefsDirectory()
        val element = Json.parseToJsonElement(File(directory, fileName).readText())
        return (element as? JsonArray).orEmpty().mapNotNull { item ->
            (item as? JsonObject)?.get("id")?.let { id ->
                (id as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
        }.toSet()
    }

    private fun catalogMuscleIds(): Set<String> = catalog.families
        .flatMap { it.definitions }
        .flatMap { it.configurations }
        .flatMap { configuration ->
            configuration.profile.primaryMuscles +
                configuration.profile.secondaryMuscles +
                configuration.profile.stabilizerMuscles
        }
        .toSet()

    private fun catalogPatternIds(): Set<String> = catalog.families
        .flatMap { it.definitions }
        .flatMap { it.configurations }
        .map { it.profile.movementPatternId }
        .toSet()

    private fun staticExerciseRefsDirectory(): File = listOf(
        File("src/main/assets/wikilab"),
        File("app/src/main/assets/wikilab"),
    ).first { it.isDirectory }

    private companion object {
        val EXERCISE_REFERENCE_KEYS = setOf(
            "recommendedExercises",
            "protectiveExercises",
            "exampleExercises",
            "riskExercises",
        )
    }
}
