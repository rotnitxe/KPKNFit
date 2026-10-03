package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.CatalogConfidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogEvidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionKindV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseFamilyV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseMetadataV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseSnapshotV2
import com.example.kpkn.domain.exercises.catalogv2.toRichMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rows persisted by builds that predate the F1 catalog cleanup still carry the
 * retired keys (`commonMistakes`, `benefits`, `richMetadata.coaching`, ...).
 *
 * Completed history must keep decoding: the retired keys are ignored, never
 * the reason a whole snapshot turns into `null`.
 */
class ExerciseCatalogV2LegacyPersistedDataTest {
    private val selection = ExerciseSelectionV2("parent", "config", "v2")
    private val evidence = CatalogEvidenceV2(
        reviewStatus = CatalogReviewStatusV2.APPROVED,
        confidence = CatalogConfidenceV2.HIGH,
        evidenceRefs = listOf("fixture"),
    )
    private val baseProfile = ResolvedExerciseProfileV2(
        movementPatternId = "elbow_flexion",
        bodyRegion = ExerciseBodyRegionV2.UPPER,
        kineticChain = ExerciseKineticChainV2.ANTERIOR,
        laterality = ExerciseLateralityV2.BILATERAL,
        equipmentId = "barbell",
        loadMode = "free_external_load",
        primaryMuscles = listOf("biceps"),
        efc = 2.0,
        cnc = 1.0,
        ssc = 0.0,
        ttc = 1.0,
        axialLoadFactor = 0.0,
        technicalDifficulty = 2.0,
        resistanceProfile = "gravity_arc",
        setupCues = listOf("Setup."),
        executionCues = listOf("Execute."),
        performanceProfileId = "profile",
    )
    private val family = ExerciseFamilyV2(
        id = "family",
        canonicalName = "Familia",
        definitions = emptyList(),
        evidence = evidence,
    )
    private val definition = ExerciseDefinitionV2(
        id = "parent",
        familyId = family.id,
        kind = ExerciseDefinitionKindV2.PARENT,
        canonicalName = "Padre",
        description = "Fixture de prueba suficientemente descriptivo.",
        configurations = emptyList(),
        defaultConfigurationId = "config",
        evidence = evidence,
    )
    private val configuration = ExerciseConfigurationV2(
        id = "config",
        selectedOptions = mapOf("implement" to "barbell"),
        displaySummary = "Barra",
        profile = baseProfile,
        evidence = evidence,
    )
    private val metadata = baseProfile.toRichMetadata("v2", family, definition, configuration)
    private val profile = baseProfile.copy(richMetadata = metadata)

    @Test
    fun completed_snapshot_persisted_before_f1_still_decodes() {
        val legacyJson = legacySnapshotJson()

        // Control: the retired keys are really there and a strict decoder rejects them.
        assertTrue(
            runCatching {
                Json.decodeFromString(ResolvedExerciseSnapshotV2.serializer(), legacyJson)
            }.isFailure,
        )
        assertTrue("\"commonMistakes\"" in legacyJson && "\"coaching\"" in legacyJson)

        val completed = CompletedExercise(exerciseId = "occ-1", exerciseName = "Padre")
            .copy(resolvedProfileSnapshotJson = legacyJson)
        val snapshot = completed.decodeResolvedCatalogSnapshot()

        assertNotNull(snapshot)
        assertEquals(selection, snapshot!!.selection)
        assertEquals("profile", snapshot.resolvedProfile.performanceProfileId)
        assertEquals(listOf("Setup."), snapshot.resolvedProfile.setupCues)
        assertEquals(listOf("Execute."), snapshot.resolvedProfile.executionCues)
        assertEquals(listOf("biceps"), snapshot.resolvedProfile.richMetadata!!.anatomy.primaryMuscles)
    }

    @Test
    fun rich_metadata_persisted_before_f1_still_rebuilds_the_profile() {
        val legacyJson = legacyRichMetadataJson()

        assertTrue(
            runCatching {
                Json.decodeFromString(ResolvedExerciseMetadataV2.serializer(), legacyJson)
            }.isFailure,
        )

        val info = ExerciseMuscleInfo(
            id = "config",
            name = "Padre",
            catalogRevision = "v2",
            catalogDefinitionId = "parent",
            catalogConfigurationId = "config",
            performanceProfileId = "profile",
            catalogReviewStatus = "APPROVED",
            catalogRichMetadataJson = legacyJson,
            setupCues = listOf("Setup."),
            executionCues = listOf("Execute."),
        )

        assertNotNull(info.decodeCatalogRichMetadata())
        val rebuilt = info.toResolvedExerciseProfileV2()
        assertNotNull(rebuilt)
        assertEquals("profile", rebuilt!!.performanceProfileId)
        assertEquals(listOf("Setup."), rebuilt.setupCues)
        assertEquals(listOf("Execute."), rebuilt.executionCues)
    }

    // ---- fixtures: write what the pre-F1 builds persisted ---------------------------------------

    private fun legacySnapshotJson(): String {
        val current = Json.parseToJsonElement(
            encodeResolvedCatalogSnapshot(
                ResolvedExerciseSnapshotV2(
                    selection = selection,
                    resolvedProfile = profile,
                    catalogRevision = "v2",
                    capturedAtEpochMs = 10L,
                ),
            ),
        ).jsonObject
        val legacy = current.update("resolvedProfile") { resolved ->
            resolved
                .with("commonMistakes", strings("x"))
                .with("benefits", strings("x", "y"))
                .with("techniqueSummary", JsonPrimitive("x"))
                .with("variantRationale", JsonPrimitive("x"))
                .update("richMetadata") { addRetiredRichKeys(it) }
        }
        return legacy.toString()
    }

    private fun legacyRichMetadataJson(): String {
        val current = Json { encodeDefaults = true }
            .encodeToJsonElement(ResolvedExerciseMetadataV2.serializer(), metadata)
            .jsonObject
        return addRetiredRichKeys(current).toString()
    }

    private fun addRetiredRichKeys(rich: JsonObject): JsonObject = rich
        .with("editorial", JsonObject(mapOf("description" to JsonPrimitive("x"))))
        .with("coaching", JsonObject(mapOf("setup" to strings("x"), "execution" to strings("x"))))
        .with("safety", JsonObject(mapOf("contraindications" to strings("x"))))
        .update("anatomy") {
            it.with("targetRegions", strings("x"))
                .with("muscleLengthBias", JsonPrimitive("x"))
                .with("stabilizationDemand", JsonPrimitive("x"))
        }
        .update("biomechanics") {
            it.with("rangeOfMotion", JsonPrimitive("x")).with("relevantTendons", strings("x"))
        }
        .update("programming") {
            it.with("objectives", strings("x"))
                .with("suitableRepRanges", strings("x"))
                .with("recoveryCost", JsonPrimitive(1))
                .with("setupTransitionCost", JsonPrimitive(1))
                .with("splitSuitability", strings("x"))
        }

    private fun strings(vararg values: String): JsonElement = JsonArray(values.map(::JsonPrimitive))

    private fun JsonObject.with(key: String, value: JsonElement): JsonObject =
        JsonObject(toMutableMap().apply { put(key, value) })

    private fun JsonObject.update(key: String, transform: (JsonObject) -> JsonObject): JsonObject =
        with(key, transform(getValue(key).jsonObject))
}
