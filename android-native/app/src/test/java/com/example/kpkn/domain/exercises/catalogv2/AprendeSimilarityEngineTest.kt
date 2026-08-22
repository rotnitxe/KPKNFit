package com.example.kpkn.domain.exercises.catalogv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AprendeSimilarityEngineTest {
    private val current = input(
        id = "current",
        name = "Remo base",
        definition = "row",
        replacementGroup = "horizontal_pull",
        preservesIntent = setOf("tirón horizontal"),
        pattern = "horizontal-pull",
        muscles = setOf("latissimus_dorsi", "biceps"),
        joints = setOf("glenohumeral", "codo"),
    )

    @Test
    fun bands_use_only_explicit_intent_pattern_and_anatomy_metadata() {
        val matches = AprendeSimilarityEngine.rank(
            current = current,
            candidates = listOf(
                input("equivalent", "Remo equivalente", "other", "horizontal_pull", pattern = "horizontal-pull"),
                input("variant", "Jalón variante", "vertical_pull", replacementGroup = null, pattern = "horizontal-pull"),
                input("transfer", "Curl con transferencia", "curl", replacementGroup = null, pattern = "elbow-flexion", muscles = setOf("biceps"), joints = setOf("codo")),
                input("unrelated", "Salto sin relación", "jump", null, pattern = "jump", muscles = setOf("quadriceps"), joints = setOf("rodilla")),
            ),
            limit = 10,
        )

        assertEquals(AprendeSimilarityBand.EQUIVALENT, matches.single { it.candidate.id == "equivalent" }.band)
        assertEquals(AprendeSimilarityBand.PATTERN_VARIANT, matches.single { it.candidate.id == "variant" }.band)
        assertEquals(AprendeSimilarityBand.ANATOMICAL_TRANSFER, matches.single { it.candidate.id == "transfer" }.band)
        assertTrue(matches.none { it.candidate.id == "unrelated" })
    }

    @Test
    fun ordering_is_deterministic_by_score_then_display_name() {
        val candidates = listOf(
            input("b", "B", "other", null, pattern = "horizontal-pull"),
            input("a", "A", "other", null, pattern = "horizontal-pull"),
        )
        assertEquals(listOf("A", "B"), AprendeSimilarityEngine.rank(current, candidates, 2).map { it.candidate.displayName })
    }

    @Test
    fun role_aware_anatomy_prioritizes_primary_matches_over_stabilizer_matches() {
        val roleAwareCurrent = current.copy(
            musclesByRole = mapOf(
                AprendeMuscleRole.PRIMARY to setOf("latissimus_dorsi"),
                AprendeMuscleRole.STABILIZER to setOf("biceps"),
            ),
        )
        val matches = AprendeSimilarityEngine.rank(
            current = roleAwareCurrent,
            candidates = listOf(
                input("primary", "Principal", "other", null, pattern = "horizontal-pull", muscles = setOf("latissimus_dorsi"))
                    .copy(musclesByRole = mapOf(AprendeMuscleRole.PRIMARY to setOf("latissimus_dorsi"))),
                input("stabilizer", "Estabilizador", "other", null, pattern = "horizontal-pull", muscles = setOf("biceps"))
                    .copy(musclesByRole = mapOf(AprendeMuscleRole.STABILIZER to setOf("biceps"))),
            ),
            limit = 2,
        )

        assertEquals(listOf("primary", "stabilizer"), matches.map { it.candidate.id })
    }

    private fun input(
        id: String,
        name: String,
        definition: String,
        replacementGroup: String?,
        preservesIntent: Set<String> = emptySet(),
        pattern: String,
        muscles: Set<String> = setOf("latissimus_dorsi"),
        joints: Set<String> = setOf("glenohumeral"),
    ) = AprendeSimilarityInput(
        id = id,
        displayName = name,
        definitionId = definition,
        replacementGroup = replacementGroup,
        preservesIntent = preservesIntent,
        movementPatternId = pattern,
        muscles = muscles,
        joints = joints,
        bodyRegion = ExerciseBodyRegionV2.UPPER,
        kineticChain = ExerciseKineticChainV2.POSTERIOR,
        laterality = ExerciseLateralityV2.BILATERAL,
        equipmentId = "cable",
    )
}
