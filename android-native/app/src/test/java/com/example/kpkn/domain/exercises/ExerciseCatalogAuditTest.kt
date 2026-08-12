package com.example.kpkn.domain.exercises

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
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
        assertNotNull(byId["flat_chest_fly"])
        assertNotNull(byId["bulgarian_split_squat"])
        assertNotNull(byId["standing_biceps_curl"])
        assertNotNull(byId["standing_lateral_raise"])
        assertEquals("SPECIALTY", byId.getValue("biceps_curl_zottman").kind.name)
        assertEquals("PARENT", byId.getValue("lateral_raise_super_rom").kind.name)
    }

    @Test
    fun exact_search_suggests_configuration_without_name_resolution() {
        val resolver = ExerciseCatalogV2Resolver(catalog)
        val bayesian = resolver.search("curl bayesian")
        assertTrue(bayesian.any { it.definitionId == "biceps_curl_bayesian" })
        val bulgarian = resolver.search("bulgaria en maquina")
        assertTrue(bulgarian.any { it.definitionId == "bulgarian_split_squat" })
        val reverseCable = resolver.search("aperturas inversas polea")
        assertTrue(
            reverseCable.any {
                it.definitionId == "reverse_pec_fly" &&
                    it.suggestedConfigurationId == "reverse_pec_fly__bilateral__cable"
            },
        )
    }

    @Test
    fun open_family_queries_prioritize_base_movements_but_specific_queries_prioritize_exact_variant() {
        val resolver = ExerciseCatalogV2Resolver(catalog)

        assertEquals(
            listOf("conventional_deadlift", "romanian_deadlift", "sumo_deadlift"),
            resolver.search("peso muerto").take(3).map { it.definitionId },
        )
        assertEquals(
            "romanian_deadlift",
            resolver.search("peso muerto rumano").first().definitionId,
        )
        assertEquals(
            "conventional_deadlift",
            resolver.search("peso muerto convencional smith").first().definitionId,
        )
        assertEquals(
            listOf("high_bar_back_squat", "low_bar_back_squat", "front_squat"),
            resolver.search("sentadilla").take(3).map { it.definitionId },
        )
        assertEquals(
            listOf("bench_press", "incline_bench_press", "decline_bench_press"),
            resolver.search("press").take(3).map { it.definitionId },
        )
    }

    @Test
    fun specialties_remain_outside_parent_chip_matrices() {
        val byId = definitions.associateBy { it.id }
        assertTrue(byId.getValue("biceps_curl_zottman").optionAxes.isEmpty())
        assertTrue(byId.getValue("biceps_curl_waiter").optionAxes.isEmpty())
        assertTrue(byId.getValue("lateral_raise_super_rom").optionAxes.isNotEmpty())
        assertEquals("PARENT", byId.getValue("reverse_pec_fly").kind.name)
        assertEquals(
            listOf("implement", "laterality"),
            byId.getValue("reverse_pec_fly").optionAxes,
        )
    }

    @Test
    fun configuration_descriptions_are_factual_and_change_with_chips() {
        val reverse = definitions.first { it.id == "reverse_pec_fly" }
        val machine = reverse.configurations.first { it.selectedOptions["implement"] == "machine" }
        val cable = reverse.configurations.first { it.selectedOptions["implement"] == "cable" }

        assertTrue(machine.profile.description.length >= 40)
        assertTrue(cable.profile.description.length >= 40)
        assertFalse(machine.profile.description.contains("Ejecuta", ignoreCase = true))
        assertFalse(cable.profile.description.contains("Ejecuta", ignoreCase = true))
        assertNotEquals(machine.profile.description, cable.profile.description)
        assertTrue(machine.profile.description.contains("máquina", ignoreCase = true))
        assertTrue(cable.profile.description.contains("polea", ignoreCase = true))
    }

    @Test
    fun grouped_families_follow_general_to_particular_axis_order() {
        val byId = definitions.associateBy { it.id }

        assertEquals(listOf("implement", "stance"), byId.getValue("romanian_deadlift").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("conventional_deadlift").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("sumo_deadlift").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("stiff_leg_deadlift").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("seated_leg_curl").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("lying_leg_curl").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("standing_leg_curl").optionAxes)
        assertTrue(byId.getValue("glute_ham_raise").optionAxes.isEmpty())
        assertEquals(listOf("laterality"), byId.getValue("belt_squat").optionAxes)
        assertEquals(listOf("laterality"), byId.getValue("pendulum_squat").optionAxes)
        assertEquals(listOf("support_angle"), byId.getValue("push_up").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("lat_pulldown").optionAxes)
        assertEquals(listOf("grip_type", "grip_width"), byId.getValue("pull_up").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("calf_raise").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("overhead_triceps_extension").optionAxes)
        assertEquals(listOf("laterality"), byId.getValue("crossbody_triceps_extension").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("good_morning").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("flat_chest_fly").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("incline_chest_fly").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("decline_chest_fly").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("standing_lateral_raise").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("seated_lateral_raise").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("rear_delt_raise").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("katana_extension").optionAxes)

        assertEquals(listOf("implement", "laterality"), byId.getValue("pullover").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("lying_pullover").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("hip_thrust").optionAxes)
        assertEquals(listOf("implement", "laterality"), byId.getValue("triceps_patada").optionAxes)
        assertEquals(listOf("laterality"), byId.getValue("quads_extension_cuadriceps").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("military_press").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("seated_shoulder_press").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("standing_biceps_curl").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("preacher_curl").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("conventional_row").optionAxes)
        assertEquals(listOf("implement"), byId.getValue("pendlay_row").optionAxes)
        assertEquals(listOf("implement", "grip_width"), byId.getValue("t_bar_row").optionAxes)
        assertEquals(listOf("grip_width"), byId.getValue("gironda_row").optionAxes)
        assertEquals(
            listOf("implement", "pulley_height", "grip_width"),
            byId.getValue("chest_supported_row").optionAxes,
        )

        assertEquals(8, byId.getValue("romanian_deadlift").configurations.size)
        assertEquals(8, byId.getValue("romanian_sumo_deadlift").configurations.size)
        assertEquals(18, byId.getValue("chest_supported_row").configurations.size)
        assertEquals(9, byId.getValue("pull_up").configurations.size)
        assertTrue(byId.getValue("bench_press").configurations.map { it.profile.equipmentId }.containsAll(listOf("barbell", "dumbbells")))
        assertFalse(definitions.any { it.id in setOf(
            "triceps_press_california_barra_recta",
            "back_remo_seal_barra_recta",
            "quads_sentadilla_sissy",
            "hams_peso_muerto_convencional",
            "hams_curl_femoral_sentado_unilateral_maquina",
            "tren_superior_flexiones_clasicas",
            "deadlift",
            "leg_curl",
            "lateral_raise",
            "biceps_curl",
            "chest_fly",
        ) })
    }
}
