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
        val byName = catalog().associateBy { normalizeCatalogSearchValue(it.name) }

        assertNotNull(byName["press inclinado en maquina convergente"])
        assertNotNull(byName["press inclinado en smith"])
        assertNotNull(byName["sentadilla sissy"])
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
        assertEquals("tren_superior_press_inclinado_maquina_convergente", firstResult("press inclinado maquina convergente")?.id)
        assertEquals("tren_superior_press_inclinado_smith", firstResult("press inclinado smith")?.id)
        assertEquals("tren_inferior_sentadilla_sissy", firstResult("sentadilla sissy")?.id)
        assertTrue(catalog().none { it.id == "nuevo_extension_cuadriceps_unilateral" })
    }
}
