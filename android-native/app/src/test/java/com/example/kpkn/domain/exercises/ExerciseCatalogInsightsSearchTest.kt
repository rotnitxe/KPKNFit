package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.TechnicalAspect
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseCatalogInsightsSearchTest {

    @Test
    fun exact_normalized_name_wins_over_broad_semantic_match() {
        val exact = ExerciseMuscleInfo(
            id = "press-inclinado-smith",
            name = "Press inclinado en Smith",
            alias = "press inclinado smith",
            description = "Press para pectoral superior",
            equipment = "Smith",
        )
        val broad = ExerciseMuscleInfo(
            id = "press-banca",
            name = "Press banca con barra",
            alias = "press inclinado smith variante historica",
            description = "Press inclinado en smith mencionado como alternativa",
            equipment = "Barra",
        )

        assertTrue(
            calculateSearchScore(exact, "press inclinado en smith") >
                calculateSearchScore(broad, "press inclinado en smith")
        )
    }

    @Test
    fun connector_words_do_not_hide_literal_name_match() {
        val exact = ExerciseMuscleInfo(
            id = "press-banca-plano",
            name = "Press de Banca Plano con Barra",
            description = "Press plano clásico de powerlifting",
            equipment = "Barra",
        )
        val partial = ExerciseMuscleInfo(
            id = "press-banca-unilateral",
            name = "Press de Banca con Mancuerna Unilateral",
            description = "Press plano unilateral",
            equipment = "Mancuerna",
        )

        assertTrue(
            calculateSearchScore(exact, "press banca plano") >
                calculateSearchScore(partial, "press banca plano")
        )
    }

    @Test
    fun shorter_literal_sissy_match_wins_over_weighted_variant() {
        val base = ExerciseMuscleInfo(
            id = "sentadilla-sissy",
            name = "Sentadilla Sissy",
            alias = "pendulo sissy",
            equipment = "Peso corporal",
        )
        val weighted = ExerciseMuscleInfo(
            id = "pendulo-sissy-disco",
            name = "Péndulo Sissy con Disco",
            equipment = "Disco",
        )

        assertTrue(
            calculateSearchScore(base, "sentadilla sissy") >
                calculateSearchScore(weighted, "sentadilla sissy")
        )
    }

    @Test
    fun multi_word_search_requires_every_term_not_or() {
        val curlFemoral = ExerciseMuscleInfo(
            id = "curl-femoral",
            name = "Curl Femoral en Máquina",
            description = "Aislamiento de isquios",
        )
        val onlyFemoralInDescription = ExerciseMuscleInfo(
            id = "sentadilla",
            name = "Sentadilla trasera",
            description = "El recto femoral participa en la extensión",
        )
        val curlBiceps = ExerciseMuscleInfo(
            id = "curl-biceps",
            name = "Curl de Bíceps",
            description = "Curl de brazo",
        )

        assertTrue(calculateSearchScore(curlFemoral, "Curl Femoral") > 0)
        assertEquals(0, calculateSearchScore(onlyFemoralInDescription, "Curl Femoral"))
        assertEquals(0, calculateSearchScore(curlBiceps, "Curl Femoral"))
        assertTrue(calculateSearchScore(curlBiceps, "Curl") > 0)
    }

    @Test
    fun partial_token_prefix_matches_while_typing() {
        val curlFemoral = ExerciseMuscleInfo(
            id = "curl-femoral",
            name = "Curl Femoral Mentonera",
        )
        assertTrue(calculateSearchScore(curlFemoral, "Curl Fem") > 0)
        assertTrue(calculateSearchScore(curlFemoral, "curl femora") > 0)
    }

    @Test
    fun technical_chip_name_matches_and_returns_the_option_to_preselect() {
        val curl = ExerciseMuscleInfo(
            id = "biceps_curl_de_pie",
            name = "Curl de Bíceps de Pie",
            technicalAspects = listOf(
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

        assertTrue(calculateSearchScore(curl, "Curl Martillo") > 0)
        assertEquals(mapOf("grip_type" to "martillo"), matchingTechnicalAspectOptions(curl, "Curl Martillo"))
    }

    @Test
    fun visual_dedup_groups_custom_duplicate_by_normalized_name() {
        val system = ExerciseMuscleInfo(
            id = "system-sissy",
            name = "Sentadilla Sissy",
            isCustom = false,
        )
        val custom = ExerciseMuscleInfo(
            id = "custom-sissy",
            name = "sentadilla sissy",
            isCustom = true,
        )

        val results = deduplicateCatalogVisualResults(listOf(system, custom))

        assertEquals(listOf(system), results)
    }
}
