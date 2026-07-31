package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogAuditTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun catalog(): List<ExerciseMuscleInfo> {
        val file = listOf(
            File("src/main/assets/exercise_database.json"),
            File("app/src/main/assets/exercise_database.json"),
        ).first { it.exists() }
        return json.decodeFromString(file.readText())
    }

    @Test
    fun catalog_has_no_duplicate_display_names() {
        val duplicates = catalog()
            .groupBy { visualCatalogDuplicateNameKey(it) }
            .filterValues { it.size > 1 }
            .mapValues { (_, items) -> items.map { it.id to it.name } }

        assertEquals(emptyMap<String, List<Pair<String, String>>>(), duplicates)
    }

    @Test
    fun catalog_keeps_required_common_exercises() {
        val items = catalog()
        val allNames = items.flatMap { listOfNotNull(it.name, it.alias) }.map { normalizeCatalogSearchValue(it) }.toSet()
        val ids = items.map { it.id }.toSet()

        assertTrue(ids.contains("tren_superior_press_pecho_maquina_convergente"))
        assertTrue(ids.contains("tren_superior_press_banca_plano_barra"))
        assertTrue(
            allNames.any { it.contains("press") && it.contains("smith") } ||
                items.any { ex ->
                    ex.technicalAspects.orEmpty().any { aspect ->
                        aspect.id == "bar_path" && aspect.options.any { it.id == "smith" }
                    }
                },
        )
        assertTrue(allNames.any { it.contains("sentadilla sissy") })
    }

    @Test
    fun audited_search_examples_rank_expected_exercise_first() {
        val items = catalog()

        fun firstResult(query: String): ExerciseMuscleInfo? =
            items
                .map { it to calculateSearchScore(it, query) }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<ExerciseMuscleInfo, Int>> { it.second }
                        .thenBy { kotlin.math.abs(it.first.name.length - query.length) }
                        .thenBy { it.first.name }
                )
                .firstOrNull()
                ?.first

        assertEquals("tren_superior_press_banca_plano_barra", firstResult("press banca plano")?.id)
        assertEquals(
            "tren_superior_press_pecho_maquina_convergente",
            firstResult("press inclinado maquina convergente")?.id,
        )
        assertEquals("tren_superior_press_banca_plano_barra", firstResult("press inclinado smith")?.id)
        val sissyFirstId = firstResult("sentadilla sissy")?.id
        assertTrue(sissyFirstId == "quads_sentadilla_sissy_libre" || sissyFirstId == "tren_inferior_sentadilla_sissy")
        assertTrue(catalog().none { it.id == "nuevo_extension_cuadriceps_unilateral" })
    }

    @Test
    fun remo_t_is_single_canonical_with_station_chips() {
        val remoT = catalog().single { it.id == "back_remo_barra_t" }
        val station = remoT.technicalAspects.orEmpty().single { it.id == "station" }
        assertTrue(station.options.any { it.id == "libre" })
        assertTrue(station.options.any { it.id == "maquina" })
        assertTrue(catalog().none { it.id.startsWith("back_remo_barra_t_") })
    }

    @Test
    fun hammer_curl_on_straight_or_ez_bar_removed() {
        assertTrue(
            catalog().none {
                it.name.contains("Martillo", ignoreCase = true) &&
                    (it.equipment == "Barra" || it.equipment == "Barra EZ")
            },
        )
    }

    @Test
    fun press_specialties_remain_separate() {
        val ids = catalog().map { it.id }.toSet()
        assertTrue(ids.contains("tren_superior_press_spoto_barra"))
        assertTrue(ids.contains("tren_superior_floor_press_barra"))
        assertTrue(ids.contains("tren_superior_press_banca_cadenas"))
        val spoto = catalog().single { it.id == "tren_superior_press_spoto_barra" }
        assertTrue(spoto.technicalAspects.orEmpty().none { it.id == "chest_pause" })
        assertTrue(spoto.variantGroupId.isNullOrBlank())
    }
}
