package com.example.kpkn.domain.exercises

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogAuditTest {
    private val catalog: ExerciseCatalogV2 by lazy {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }

    private val definitions
        get() = catalog.families.flatMap { it.definitions }

    @Test
    fun canonical_names_are_unique_and_malformed_bulgaria_label_is_absent() {
        val names = definitions.map { it.canonicalName.lowercase() }
        assertEquals(names.size, names.distinct().size)
        assertFalse(names.any { it == "bulgaria en máquina" })
        assertTrue(names.any { it == "sentadilla búlgara" })
    }

    @Test
    fun screenshot_families_have_parent_and_specialty_identities() {
        val byId = definitions.associateBy { it.id }
        assertNotNull(byId["good_morning"])
        assertNotNull(byId["hip_abduction"])
        assertNotNull(byId["hip_adduction"])
        assertNotNull(byId["chest_fly"])
        assertNotNull(byId["bulgarian_split_squat"])
        assertNotNull(byId["biceps_curl"])
        assertNotNull(byId["lateral_raise"])
        assertEquals("SPECIALTY", byId.getValue("biceps_curl_zottman").kind.name)
        assertEquals("SPECIALTY", byId.getValue("lateral_raise_super_rom").kind.name)
    }

    @Test
    fun exact_search_suggests_configuration_without_name_resolution() {
        val resolver = ExerciseCatalogV2Resolver(catalog)
        val bayesian = resolver.search("curl bayesian")
        assertTrue(bayesian.any { it.definitionId == "biceps_curl" && it.suggestedConfigurationId?.contains("bayesian") == true })
        val bulgarian = resolver.search("bulgaria en maquina")
        assertTrue(bulgarian.any { it.definitionId == "bulgarian_split_squat" })
    }

    @Test
    fun specialties_remain_outside_parent_chip_matrices() {
        val byId = definitions.associateBy { it.id }
        assertTrue(byId.getValue("biceps_curl_zottman").optionAxes.isEmpty())
        assertTrue(byId.getValue("biceps_curl_waiter").optionAxes.isEmpty())
        assertTrue(byId.getValue("lateral_raise_super_rom").optionAxes.isNotEmpty())
        assertTrue(byId.getValue("reverse_pec_fly").optionAxes.isEmpty())
    }
}
