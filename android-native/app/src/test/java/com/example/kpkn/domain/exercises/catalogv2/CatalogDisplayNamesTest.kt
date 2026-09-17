package com.example.kpkn.domain.exercises.catalogv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class CatalogDisplayNamesTest {

    companion object {
        private lateinit var catalog: ExerciseCatalogV2

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val resource = CatalogDisplayNamesTest::class.java.classLoader?.getResource("exercise_catalog_v2.json")
            val file = if (resource != null) {
                File(resource.toURI())
            } else {
                listOf(
                    "../../android-native/app/src/main/assets/exercise_catalog_v2.json",
                    "../android-native/app/src/main/assets/exercise_catalog_v2.json",
                    "android-native/app/src/main/assets/exercise_catalog_v2.json",
                ).map(::File).first { it.exists() }
            }
            catalog = ExerciseCatalogV2Loader.decodeApproved(file.readText())
        }
    }

    @Test
    fun all_configurations_resolve_to_a_verbatim_catalog_name() {
        val index = CatalogDisplayNames.buildDisplayNameIndex(catalog)
        val total = catalog.families.sumOf { family ->
            family.definitions.sumOf { it.configurations.size }
        }
        assertEquals(total, index.size)
        index.values.forEach { name ->
            assertTrue("Nombre derivado vacío", name.isNotBlank())
        }
        assertTrue("El índice no debe contener ids crudos como nombre", index.values.none { it.contains("__") })
    }

    @Test
    fun name_is_verbatim_definition_canonical_name() {
        assertEquals(
            "Press de Banca Plano",
            CatalogDisplayNames.configurationDisplayName(catalog, "bench_press__barbell"),
        )
        assertEquals(
            "Jalón al Pecho",
            CatalogDisplayNames.configurationDisplayName(catalog, "lat_pulldown__bilateral__cable"),
        )
        assertEquals(
            "Plancha Abdominal",
            CatalogDisplayNames.configurationDisplayName(catalog, "core_plancha__default"),
        )
    }

    @Test
    fun different_configurations_share_the_definition_name() {
        // La desambiguación vive en los chips de display, no en el nombre:
        // todas las configs de una definición comparten el canonical verbatim.
        val first = CatalogDisplayNames.configurationDisplayName(catalog, "bench_press__barbell")
        val second = CatalogDisplayNames.configurationDisplayName(catalog, "bench_press__dumbbells")
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first, second)
    }

    @Test
    fun unknown_configuration_returns_null_instead_of_inventing() {
        assertNull(CatalogDisplayNames.configurationDisplayName(catalog, "invented__id"))
        assertNull(CatalogDisplayNames.configurationDisplayName(catalog, "  "))
    }
}
