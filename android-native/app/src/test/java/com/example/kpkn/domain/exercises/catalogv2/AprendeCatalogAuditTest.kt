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
        // El hash fija el CONTENIDO del asset, no sus bytes de disco: se
        // normaliza CRLF→LF para que el checkout de Windows (w/crlf) no rompa
        // la comparación contra el blob LF que git almacena.
        val normalized = catalogSource.readText().replace("\r\n", "\n")
        MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
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

        // Drift de conteos reconciliado con honestidad (T-002b, paquete E): la
        // expectativa previa (197/510 y `v2-approved-2026-08-12-a`) ya no
        // coincidía con el asset de HEAD (e520812e6: 199/512) — drift
        // PREEXISTENTE. Las altas curadas de cuerpo (T-041/T-043, 2026-09-28)
        // llevaron el asset a 200/522 y la alta curada `skullcrusher` de §13.5
        // (paquete E) lo lleva a 201/523 bajo `v2-approved-2026-09-29-a`. Se
        // fija la expectativa al conteo REAL aprobado y al hash REAL del asset;
        // ninguna otra aserción de esta suite se elimina ni se debilita. El retiro
        // de `sissy_squat__barbell` (decisión del usuario, 2026-10-02) deja 201/522. Las
        // cinco altas M1-M5 del 2026-10-03 (`close_grip_bench_press`, `paused_back_squat`,
        // `deadlift_to_knees`, `close_grip_lat_pulldown` e `incline_biceps_curl`, cada una
        // una especialidad de una sola configuración) lo llevan a 206/527 con la misma revisión.
        // El retiro autorizado de las cuatro unilaterales de rumano sumo (2026-10-04) deja 206/523.
        // El retiro de walking_lunge en Smith y polea (lote 6) deja 206/521. El lote BW-1 de peso corporal
        // (2026-10-07: 9 definiciones nuevas y 8 configuraciones `__bodyweight` en definiciones existentes)
        // lo lleva a 215/539 con la misma revisión. El lote OL-1 de halterofilia y acarreos (2026-10-07: 13 definiciones
        // nuevas con 14 configuraciones) lo lleva a 228/553, también con la misma revisión.
        assertEquals("v2-approved-2026-09-29-a", report.catalogRevision)
        assertEquals("wikilab-v3-2026-08-08", report.ontologyRevision)
        assertEquals(96, report.familyCount)
        assertEquals(228, report.definitionCount)
        assertEquals(553, report.configurationCount)
        assertEquals(553, report.richMetadataCount)
        assertEquals(553, report.editorialCoverageCount)
        assertEquals(553, report.jointCoverageCount)
        assertEquals(0, report.shortDescriptionCount)
        assertEquals(0, report.duplicateDescriptionCount)
        assertEquals(0, report.desynchronizedMetadataCount)
        assertEquals(0, report.reverseLinkConsistencyIssueCount)
        assertEquals("8e70937474a217c8314eb53f17a94456fee0ec3d93f9587695ec4727dd8f33f9", report.sourceSha256)
        assertTrue("músculos sin puente: ${report.unmappedMuscleIds}", report.unmappedMuscleIds.isEmpty())
        assertTrue("patrones sin puente: ${report.unmappedPatternIds}", report.unmappedPatternIds.isEmpty())
        assertTrue(report.unknownJointIds.isEmpty())
        assertTrue(report.invalidLegacyMappings.isEmpty())
        assertEquals(21, AprendeOntology.catalogMuscleToWikiLab.size)
        assertEquals(63, AprendeOntology.catalogPatternToWikiLab.size)
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

        // 228 definiciones / 553 configuraciones tras el lote OL-1 (ver comentario de
        // `approved_catalog_has_complete_aprende_ontology_and_editorial_coverage`).
        assertEquals(553, runtime.size)
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
        assertEquals(553, reverse.exerciseIdsByPattern.values.sumOf { it.size })
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

        assertTrue(prepopulate.contains("APRENDE_CONTENT_REVISION = \"conceptos-clave-v2-2026-08-23\""))
        assertTrue(prepopulate.contains("currentRevision != APRENDE_CONTENT_REVISION"))
        assertTrue(prepopulate.contains("putString(APRENDE_CONTENT_PREF_KEY, APRENDE_CONTENT_REVISION)"))

        // El refresco estático no se acopla a una versión exacta de Room (27 cuando se
        // escribió este test, 28 con las asociaciones de media del entrenamiento): lo
        // que debe seguir cumpliéndose es (a) que la base declare al menos esa versión
        // y (b) que ninguna migración posterior toque las tablas de WikiLab.
        val declaredVersion = Regex("""version\s*=\s*(\d+)\s*,""").find(database)?.groupValues?.get(1)?.toInt()
        assertTrue("KpknDatabase debe declarar una versión de Room >= 27 (era $declaredVersion)",
            declaredVersion != null && declaredVersion >= 27)
        val migrationsSinceRefreshBaseline = database
            .substringAfter("val MIGRATION_26_27", missingDelimiterValue = "")
            .substringBefore("fun getInstance(")
        assertTrue("No se encontró MIGRATION_26_27 en KpknDatabase", migrationsSinceRefreshBaseline.isNotBlank())
        val wikiLabTables = listOf("muscle_groups", "joints", "tendons", "movement_patterns", "kinetic_chains")
        wikiLabTables.forEach { table ->
            assertTrue(
                "Una migración >= 26->27 toca la tabla WikiLab '$table'; el refresco estático no debe necesitar migración",
                !Regex("""\b${Regex.escape(table)}\b""").containsMatchIn(migrationsSinceRefreshBaseline),
            )
        }
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
