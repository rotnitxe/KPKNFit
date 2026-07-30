package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.ExerciseCatalogSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePickerCatalogLogicTest {

    private val curlFemoral = ExerciseMuscleInfo(
        id = "hams_curl_femoral_maquina",
        name = "Curl Femoral en Máquina",
        alias = "leg curl sentado",
        description = "Aislamiento de isquiosurales",
        equipment = "Máquina",
    )
    private val curlBiceps = ExerciseMuscleInfo(
        id = "biceps_curl_barra",
        name = "Curl de Bíceps con Barra",
        description = "Curl clásico de bíceps",
        equipment = "Barra",
    )
    private val squat = ExerciseMuscleInfo(
        id = "quads_sentadilla",
        name = "Sentadilla trasera con barra",
        description = "Patrón de sentadilla; el recto femoral asiste",
        equipment = "Barra",
    )
    private val abs = ExerciseMuscleInfo(
        id = "core_crunch",
        name = "Crunch abdominal",
        description = "Trabajo de abs",
        equipment = "Peso corporal",
    )

    private val catalog = listOf(curlFemoral, curlBiceps, squat, abs)

    @Test
    fun multi_word_query_requires_all_terms_and_keeps_relevance_over_alphabetical() {
        val results = filterAndSortExerciseCatalog(
            fullCatalog = catalog,
            normalizedQuery = "Curl Femoral",
            sortMode = ExerciseCatalogSort.NAME,
            ascending = true,
        )

        assertEquals(listOf(curlFemoral.id), results.map { it.id })
        assertFalse(results.any { it.id == squat.id })
        assertFalse(results.any { it.id == curlBiceps.id })
        assertFalse(results.any { it.id == abs.id })
    }

    @Test
    fun single_word_query_still_returns_matching_family() {
        val results = filterAndSortExerciseCatalog(
            fullCatalog = catalog,
            normalizedQuery = "Curl",
            sortMode = ExerciseCatalogSort.NAME,
            ascending = true,
        )

        assertTrue(results.any { it.id == curlFemoral.id })
        assertTrue(results.any { it.id == curlBiceps.id })
        assertFalse(results.any { it.id == abs.id })
        assertFalse(results.any { it.id == squat.id })
        // Both start with "Curl"; relevance keeps curl family first (not alphabetical catalog).
        assertTrue(results.first().name.startsWith("Curl", ignoreCase = true))
    }

    @Test
    fun blank_query_uses_alphabetical_sort() {
        val results = filterAndSortExerciseCatalog(
            fullCatalog = catalog,
            normalizedQuery = "",
            sortMode = ExerciseCatalogSort.NAME,
            ascending = true,
        )

        assertEquals(
            catalog.map { it.name.lowercase() }.sorted(),
            results.map { it.name.lowercase() },
        )
    }

    @Test
    fun partial_second_token_still_matches_prefix() {
        val results = filterAndSortExerciseCatalog(
            fullCatalog = catalog,
            normalizedQuery = "Curl Fem",
            sortMode = ExerciseCatalogSort.NAME,
            ascending = true,
        )

        assertEquals(listOf(curlFemoral.id), results.map { it.id })
    }
}
