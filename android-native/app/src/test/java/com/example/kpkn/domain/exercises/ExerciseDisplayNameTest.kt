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
        // "Bilateral" se omite del display para ahorrar espacio; "Barra EZ" se conserva.
        assertEquals(listOf("Barra EZ"), parts.chips)
    }

    @Test
    fun v2_chips_keep_unilateral_variant() {
        val info = ExerciseMuscleInfo(
            id = "cfg",
            name = "Extension de Cuadriceps",
            catalogVariantChips = listOf("Máquina", "Unilateral"),
        )
        val parts = exerciseDisplayParts(exercise("cfg5"), info)
        assertEquals(listOf("Máquina", "Unilateral"), parts.chips)
    }

    @Test
    fun v2_chips_hide_bilateral_and_posture_labels() {
        val info = ExerciseMuscleInfo(
            id = "cfg",
            name = "Remo",
            catalogVariantChips = listOf("Bilateral", "Sentado", "De pie", "Polea Alta"),
        )
        assertEquals(listOf("Polea Alta"), exerciseDisplayParts(exercise("cfg-posture"), info).chips)
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

    @Test
    fun recipe_technique_surfaces_as_chip_beside_verbatim_name() {
        val exercise = Exercise(
            id = "ex-recipe",
            name = "Press de Banca Plano",
            exerciseDbId = "bench_press__barbell",
            catalogRevision = "v2-approved-2026-08-12-a",
            catalogDefinitionId = "bench_press",
            catalogConfigurationId = "bench_press__barbell",
            variantName = "Velocidad",
            techniqueModifier = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
            relationshipNotes = "Velocidad",
        )
        val info = ExerciseMuscleInfo(
            id = "bench_press__barbell",
            name = "Press de Banca Plano",
            catalogVariantChips = listOf("Barra"),
        )
        val parts = exerciseDisplayParts(exercise, info)
        assertEquals("Press de Banca Plano", parts.parentName)
        assertEquals(listOf("Velocidad", "Barra"), parts.chips)
        assertEquals("Press de Banca Plano · Velocidad · Barra", parts.text)
    }

    @Test
    fun legacy_variant_name_without_catalog_identity_stays_as_chip() {
        val exercise = Exercise(
            id = "ex-legacy",
            name = "Press de Banca",
            variantName = "Smith",
        )
        val parts = exerciseDisplayParts(exercise, null)
        assertEquals("Press de Banca", parts.parentName)
        assertEquals(listOf("Smith"), parts.chips)
    }

    @Test
    fun catalog_configuration_id_resolves_to_exact_config_not_default() {
        val index = mapOf(
            "incline_chest_fly" to ExerciseMuscleInfo(
                id = "incline_chest_fly",
                name = "Apertura en Banco Inclinado",
                catalogVariantChips = listOf("Mancuerna", "Bilateral"),
            ),
            "incline_chest_fly__cable" to ExerciseMuscleInfo(
                id = "incline_chest_fly__cable",
                name = "Apertura en Banco Inclinado",
                catalogVariantChips = listOf("Polea"),
            ),
        )
        val exercise = Exercise(
            id = "ex-incline-cable",
            name = "Apertura en Banco Inclinado",
            exerciseDbId = "incline_chest_fly",
            catalogRevision = "v2-approved-2026-08-02-c",
            catalogDefinitionId = "incline_chest_fly",
            catalogConfigurationId = "incline_chest_fly__cable",
        )
        val info = resolveCatalogInfoForDisplay(exercise, index)
        val parts = exerciseDisplayParts(exercise, info)
        assertEquals("incline_chest_fly__cable", info?.id)
        assertEquals(listOf("Polea"), parts.chips)
    }
}
