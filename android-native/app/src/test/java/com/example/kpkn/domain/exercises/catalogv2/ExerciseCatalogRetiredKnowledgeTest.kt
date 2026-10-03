package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the F1 catalog cleanup on the Android side.
 *
 * The compiler, the gate and the backend reject retired fields; this test scans
 * the asset the app actually ships, because [ExerciseCatalogV2Loader] ignores
 * unknown keys on purpose and would otherwise let a retired key slip through.
 *
 * [RETIRED_KEYS] mirrors `scripts/catalog_v2_retired_fields.py` (24 paths).
 */
class ExerciseCatalogRetiredKnowledgeTest {
    private val payload: String by lazy {
        val resource = java.io.File("src/main/assets/exercise_catalog_v2.json")
            .takeIf { it.exists() }
            ?: java.io.File("app/src/main/assets/exercise_catalog_v2.json")
        resource.readText()
    }

    @Test
    fun runtime_asset_carries_no_retired_field() {
        val found = retiredFieldsIn(Json.parseToJsonElement(payload).jsonObject)
        assertTrue("retired fields still shipped: ${found.take(5)}", found.isEmpty())
    }

    @Test
    fun scanner_flags_each_retired_path_so_the_guard_cannot_go_blind() {
        assertEquals(24, RETIRED_KEYS.size)
        val clean = Json.parseToJsonElement(payload).jsonObject
        RETIRED_KEYS.forEach { path ->
            val found = retiredFieldsIn(inject(clean, path))
            assertEquals("injected $path must be reported exactly once: $found", 1, found.size)
        }
    }

    @Test
    fun runtime_asset_decodes_and_materializes_without_retired_knowledge() {
        val catalog = ExerciseCatalogV2Loader.decodeApproved(payload)
        val selection = catalog.families.first().definitions.first().let { definition ->
            ExerciseSelectionV2(definition.id, definition.configurations.first().id, catalog.catalogRevision)
        }
        val legacy = catalog.toLegacySelection(selection)
        assertTrue(legacy != null)
        assertTrue(legacy!!.involvedMuscles.all { it.biomechanicalReason == null })
        // The picker shows the configuration description and one cue of each kind: they must survive.
        assertTrue(!legacy.description.isNullOrBlank())
        assertTrue(!legacy.setupCues.isNullOrEmpty())
        assertTrue(!legacy.executionCues.isNullOrEmpty())
    }

    // ---- scanner -------------------------------------------------------------------------------

    private fun retiredFieldsIn(root: JsonObject): List<String> {
        val found = mutableListOf<String>()
        (root["families"] as? JsonArray).orEmpty().forEach { familyElement ->
            val family = familyElement.jsonObject
            val familyId = family["id"]?.toString() ?: "<family>"
            if ("description" in family) found += "$familyId.description"
            if (family.objectAt("evidence")?.containsKey("rationale") == true) found += "$familyId.evidence.rationale"
            (family["definitions"] as? JsonArray).orEmpty().forEach { definitionElement ->
                val definition = definitionElement.jsonObject
                val definitionId = definition["id"]?.toString() ?: "<definition>"
                if (definition.objectAt("evidence")?.containsKey("rationale") == true) {
                    found += "$definitionId.evidence.rationale"
                }
                (definition["configurations"] as? JsonArray).orEmpty().forEach { configurationElement ->
                    val configuration = configurationElement.jsonObject
                    val configurationId = configuration["id"]?.toString() ?: "<configuration>"
                    if (configuration.objectAt("evidence")?.containsKey("rationale") == true) {
                        found += "$configurationId.evidence.rationale"
                    }
                    val profile = configuration.objectAt("profile") ?: return@forEach
                    PROFILE_KEYS.filter { it in profile }.forEach { found += "$configurationId.profile.$it" }
                    found += jointNotes(profile["jointInvolvement"]).map { "$configurationId.profile$it" }
                    val rich = profile.objectAt("richMetadata") ?: return@forEach
                    RICH_BLOCKS.filter { it in rich }.forEach { found += "$configurationId.richMetadata.$it" }
                    RICH_FIELDS.forEach { (section, keys) ->
                        val body = rich.objectAt(section) ?: return@forEach
                        keys.filter { it in body }.forEach { found += "$configurationId.richMetadata.$section.$it" }
                    }
                    found += jointNotes(rich.objectAt("anatomy")?.get("jointInvolvement"))
                        .map { "$configurationId.richMetadata.anatomy$it" }
                }
            }
        }
        return found
    }

    private fun jointNotes(joints: JsonElement?): List<String> =
        (joints as? JsonArray).orEmpty().mapIndexedNotNull { index, joint ->
            if ((joint as? JsonObject)?.containsKey("note") == true) ".jointInvolvement[$index].note" else null
        }

    // ---- injection (proves the scanner sees every retired path) ----------------------------------

    private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.with(key: String, value: JsonElement): JsonObject =
        JsonObject(toMutableMap().apply { put(key, value) })

    private fun JsonObject.update(key: String, transform: (JsonObject) -> JsonObject): JsonObject =
        with(key, transform(getValue(key).jsonObject))

    private fun JsonObject.updateFirst(key: String, transform: (JsonObject) -> JsonObject): JsonObject =
        with(
            key,
            JsonArray(
                (getValue(key) as JsonArray).mapIndexed { index, element ->
                    if (index == 0) transform(element.jsonObject) else element
                },
            ),
        )

    private fun inject(root: JsonObject, path: String): JsonObject = root.updateFirst("families") { family ->
        when (path) {
            "family.description" -> family.with("description", MARKER)
            "family.evidence.rationale" -> family.update("evidence") { it.with("rationale", MARKER) }
            else -> family.updateFirst("definitions") { definition ->
                if (path == "definition.evidence.rationale") {
                    definition.update("evidence") { it.with("rationale", MARKER) }
                } else {
                    definition.updateFirst("configurations") { configuration ->
                        injectIntoConfiguration(configuration, path)
                    }
                }
            }
        }
    }

    private fun injectIntoConfiguration(configuration: JsonObject, path: String): JsonObject {
        fun withNote(joints: JsonElement): JsonElement = JsonArray(
            (joints as JsonArray).mapIndexed { index, joint ->
                if (index == 0) joint.jsonObject.with("note", MARKER) else joint
            },
        )
        return when {
            path == "configuration.evidence.rationale" ->
                configuration.update("evidence") { it.with("rationale", MARKER) }
            path == "profile.jointInvolvement[].note" -> configuration.update("profile") {
                it.with("jointInvolvement", withNote(it.getValue("jointInvolvement")))
            }
            path == "richMetadata.anatomy.jointInvolvement[].note" -> configuration.update("profile") { profile ->
                profile.update("richMetadata") { rich ->
                    rich.update("anatomy") { it.with("jointInvolvement", withNote(it.getValue("jointInvolvement"))) }
                }
            }
            path.startsWith("profile.") ->
                configuration.update("profile") { it.with(path.removePrefix("profile."), MARKER) }
            else -> {
                val parts = path.removePrefix("richMetadata.").split('.')
                configuration.update("profile") { profile ->
                    profile.update("richMetadata") { rich ->
                        if (parts.size == 1) {
                            rich.with(parts[0], MARKER)
                        } else {
                            rich.update(parts[0]) { it.with(parts[1], MARKER) }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val MARKER = JsonPrimitive("x")
        val PROFILE_KEYS = listOf("benefits", "techniqueSummary", "variantRationale", "commonMistakes", "muscleNotes")
        val RICH_BLOCKS = listOf("editorial", "coaching", "safety")
        val RICH_FIELDS = mapOf(
            "anatomy" to listOf("targetRegions", "muscleLengthBias", "stabilizationDemand"),
            "biomechanics" to listOf("rangeOfMotion", "relevantTendons"),
            "programming" to listOf(
                "objectives",
                "suitableRepRanges",
                "recoveryCost",
                "setupTransitionCost",
                "splitSuitability",
            ),
        )
        val RETIRED_KEYS = listOf(
            "family.description",
            "family.evidence.rationale",
            "definition.evidence.rationale",
            "configuration.evidence.rationale",
            "profile.benefits",
            "profile.techniqueSummary",
            "profile.variantRationale",
            "profile.commonMistakes",
            "profile.muscleNotes",
            "profile.jointInvolvement[].note",
            "richMetadata.editorial",
            "richMetadata.coaching",
            "richMetadata.safety",
            "richMetadata.anatomy.targetRegions",
            "richMetadata.anatomy.muscleLengthBias",
            "richMetadata.anatomy.stabilizationDemand",
            "richMetadata.anatomy.jointInvolvement[].note",
            "richMetadata.biomechanics.rangeOfMotion",
            "richMetadata.biomechanics.relevantTendons",
            "richMetadata.programming.objectives",
            "richMetadata.programming.suitableRepRanges",
            "richMetadata.programming.recoveryCost",
            "richMetadata.programming.setupTransitionCost",
            "richMetadata.programming.splitSuitability",
        )
    }
}
