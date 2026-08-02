package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.TechnicalAspect
import com.example.kpkn.domain.exercises.ExerciseCatalogSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePickerCatalogSearchRegressionTest {

    private val curlFemoral = ExerciseMuscleInfo(
        id = "curl-femoral",
        name = "Curl Femoral en Máquina",
        alias = "leg curl sentado",
    )

    @Test
    fun current_query_cannot_be_replaced_by_an_older_query_result() {
        val oldQuery = filterAndSortExerciseCatalog(
            fullCatalog = listOf(curlFemoral),
            normalizedQuery = "Abducción",
            sortMode = ExerciseCatalogSort.NAME,
        )
        val currentQuery = filterAndSortExerciseCatalog(
            fullCatalog = listOf(curlFemoral),
            normalizedQuery = "Curl Femoral",
            sortMode = ExerciseCatalogSort.NAME,
        )

        assertTrue(oldQuery.isEmpty())
        assertEquals(listOf(curlFemoral.id), currentQuery.map { it.id })
    }

    @Test
    fun multi_term_search_excludes_an_unrelated_abduccion_exercise() {
        val abduction = ExerciseMuscleInfo(
            id = "abduction",
            name = "Abducción de cadera",
        )

        val results = filterAndSortExerciseCatalog(
            fullCatalog = listOf(curlFemoral, abduction),
            normalizedQuery = "Curl Femoral",
            sortMode = ExerciseCatalogSort.NAME,
        )

        assertEquals(listOf(curlFemoral.id), results.map { it.id })
    }

    @Test
    fun variant_search_returns_the_canonical_parent() {
        val canonical = curlFemoral.copy(
            id = "biceps_curl_de_pie",
            name = "Curl de Bíceps de Pie",
            catalogOptionAxes = listOf(
                TechnicalAspect(
                    id = "grip_type",
                    name = "Tipo de agarre",
                    defaultOptionId = "supino",
                    options = listOf(
                        AspectOption("supino", "Supino"),
                        AspectOption("martillo", "Martillo"),
                    ),
                ),
            ),
        )
        val results = filterAndSortExerciseCatalog(
            fullCatalog = listOf(canonical),
            normalizedQuery = "Curl Martillo",
            sortMode = ExerciseCatalogSort.NAME,
        )

        assertEquals(listOf(canonical.id), results.map { it.id })
    }
}
