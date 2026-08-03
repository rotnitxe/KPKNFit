package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseDisplayNameTest {

    private fun exercise(catalogConfigurationId: String? = "cfg") = Exercise(
        id = "ex1",
        name = "Cruce de Poleas",
        exerciseDbId = "tren_superior_cruce_poleas",
        catalogRevision = "v2-approved-2026-08-02-c",
        catalogDefinitionId = "tren_superior_cruce_poleas",
        catalogConfigurationId = catalogConfigurationId,
    )

    @Test
    fun v2_chips_collapse_contained_duplicates() {
        val info = ExerciseMuscleInfo(
            id = "cfg",
            name = "Cruce de Poleas",
            catalogVariantChips = listOf("Polea", "Polea Alta"),
        )
        val parts = exerciseDisplayParts(exercise(), info)
        assertEquals("Cruce de Poleas", parts.parentName)
        assertEquals(listOf("Polea Alta"), parts.chips)
    }

    @Test
    fun v2_chips_keep_non_overlapping_values() {
        val info = ExerciseMuscleInfo(
            id = "cfg",
            name = "Press Francés",
            catalogVariantChips = listOf("Barra EZ", "Bilateral"),
        )
        val parts = exerciseDisplayParts(exercise("cfg2"), info)
        assertEquals(listOf("Barra EZ", "Bilateral"), parts.chips)
    }

    @Test
    fun v2_chips_dedupe_only_longer_specific_chip() {
        val info = ExerciseMuscleInfo(
            id = "cfg",
            name = "Remo",
            catalogVariantChips = listOf("Polea", "Polea Alta", "Amplio"),
        )
        val parts = exerciseDisplayParts(exercise("cfg3"), info)
        assertEquals(listOf("Polea Alta", "Amplio"), parts.chips)
    }

    @Test
    fun empty_v2_chips_falls_back_to_legacy_behavior() {
        val info = ExerciseMuscleInfo(id = "cfg", name = "Cruce de Poleas")
        val parts = exerciseDisplayParts(exercise("cfg4"), info)
        assertEquals(emptyList<String>(), parts.chips)
    }
}
