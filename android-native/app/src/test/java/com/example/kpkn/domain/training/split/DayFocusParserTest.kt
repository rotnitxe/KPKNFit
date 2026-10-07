package com.example.kpkn.domain.training.split

import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Las etiquetas y los `foci` de los 35 repartos se traducen a qué músculos acoge cada día. */
class DayFocusParserTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun focus(label: String, foci: List<String> = emptyList()) = DayFocusParser.parse(label, foci)

    @Test
    fun upper_lower_push_and_pull_days_accept_what_their_names_say() {
        val torso = focus("Torso")
        assertEquals(1.0, torso.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(1.0, torso.weight(KpknMuscleGroup.BACK_LATS), 0.0)
        assertEquals(0.0, torso.weight(KpknMuscleGroup.QUADS), 0.0)
        assertEquals(setOf(SplitGroup.PUSH, SplitGroup.PULL), torso.acceptedGroups)

        val legs = focus("Pierna")
        assertEquals(1.0, legs.weight(KpknMuscleGroup.QUADS), 0.0)
        assertEquals(0.0, legs.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(setOf(SplitGroup.LEGS), legs.acceptedGroups)

        val push = focus("Empuje")
        assertEquals(1.0, push.weight(KpknMuscleGroup.TRICEPS), 0.0)
        assertEquals(0.0, push.weight(KpknMuscleGroup.BICEPS), 0.0)
        assertEquals(setOf(SplitGroup.PUSH), push.acceptedGroups)

        val pull = focus("Tirón")
        assertEquals(1.0, pull.weight(KpknMuscleGroup.BICEPS), 0.0)
        assertEquals(1.0, pull.weight(KpknMuscleGroup.DELT_REAR), 0.0)
        assertEquals(0.0, pull.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(setOf(SplitGroup.PULL), pull.acceptedGroups)
    }

    @Test
    fun full_body_days_accept_everything_and_a_mixed_label_keeps_a_floor_elsewhere() {
        listOf("Cuerpo Completo A", "Full Body A", "Cuerpo Completo Pesado").forEach { label ->
            val full = focus(label)
            assertFalse(label, full.isGeneric)
            assertTrue(label, KpknMuscleGroup.entries.all { full.weight(it) == 1.0 })
            assertEquals(setOf(SplitGroup.PUSH, SplitGroup.PULL, SplitGroup.LEGS), full.acceptedGroups)
        }
        val mixed = focus("Torso/Full Body")
        assertEquals(1.0, mixed.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(0.85, mixed.weight(KpknMuscleGroup.QUADS), 1e-9)
        val legsMixed = focus("Pierna/Full Body")
        assertEquals(1.0, legsMixed.weight(KpknMuscleGroup.HAMS), 0.0)
        assertEquals(0.85, legsMixed.weight(KpknMuscleGroup.CHEST), 1e-9)
        // El piso solo existe cuando la etiqueta mezcla un foco con «cuerpo completo» o «accesorios».
        assertEquals(0.85, mixed.floor, 1e-9)
        assertEquals(0.85, legsMixed.floor, 1e-9)
        assertEquals(0.0, focus("Torso").floor, 0.0)
        assertEquals(0.0, focus("Cuerpo Completo A").floor, 0.0)
        assertEquals(0.6, focus("Sentadilla/Accesorios", listOf("SQUAT")).floor, 1e-9)
        val accessoriesOnly = focus("Accesorios Hipertrofia")
        assertEquals(0.0, accessoriesOnly.floor, 0.0)
        assertTrue(KpknMuscleGroup.entries.all { accessoriesOnly.weight(it) == 1.0 })
    }

    @Test
    fun anterior_and_posterior_chain_days_split_the_body_front_and_back() {
        val front = focus("Cadena Anterior")
        assertEquals(1.0, front.weight(KpknMuscleGroup.QUADS), 0.0)
        assertEquals(1.0, front.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(0.0, front.weight(KpknMuscleGroup.HAMS), 0.0)
        val back = focus("Cadena Posterior")
        assertEquals(1.0, back.weight(KpknMuscleGroup.HAMS), 0.0)
        assertEquals(1.0, back.weight(KpknMuscleGroup.BACK_LATS), 0.0)
        assertEquals(0.0, back.weight(KpknMuscleGroup.QUADS), 0.0)
    }

    @Test
    fun muscle_named_days_follow_the_muscles_they_name() {
        val chestBack = focus("Pecho/Espalda")
        assertEquals(1.0, chestBack.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(1.0, chestBack.weight(KpknMuscleGroup.BACK_LATS), 0.0)
        assertEquals(0.0, chestBack.weight(KpknMuscleGroup.QUADS), 0.0)

        val shouldersArms = focus("Hombro/Brazo")
        assertEquals(1.0, shouldersArms.weight(KpknMuscleGroup.DELT_LATERAL), 0.0)
        assertEquals(1.0, shouldersArms.weight(KpknMuscleGroup.BICEPS), 0.0)
        assertEquals(0.0, shouldersArms.weight(KpknMuscleGroup.CHEST), 0.0)

        val glutes = focus("Glúteo/Isquios")
        assertEquals(1.0, glutes.weight(KpknMuscleGroup.GLUTES), 0.0)
        assertEquals(1.0, glutes.weight(KpknMuscleGroup.HAMS), 0.0)

        val pushQuads = focus("Empuje + Cuádriceps")
        assertEquals(1.0, pushQuads.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(1.0, pushQuads.weight(KpknMuscleGroup.QUADS), 0.0)
        assertEquals(0.0, pushQuads.weight(KpknMuscleGroup.HAMS), 0.0)

        val shouldersAbs = focus("Hombros/Abs")
        assertEquals(1.0, shouldersAbs.weight(KpknMuscleGroup.CORE), 0.0)
    }

    @Test
    fun power_lift_foci_bring_the_supporting_muscles_with_them() {
        val texas = focus("Día Volumen (5x5)", listOf("SQUAT", "BENCH"))
        assertEquals(1.0, texas.weight(KpknMuscleGroup.QUADS), 0.0)
        assertEquals(1.0, texas.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(0.4, texas.weight(KpknMuscleGroup.HAMS), 1e-9)
        assertEquals(listOf("SQUAT", "BENCH"), texas.foci)
        assertFalse(texas.isGeneric)

        val deadliftBench = focus("Peso Muerto/Banca", listOf("DEADLIFT", "BENCH"))
        assertEquals(1.0, deadliftBench.weight(KpknMuscleGroup.HAMS), 0.0)
        assertEquals(1.0, deadliftBench.weight(KpknMuscleGroup.CHEST), 0.0)

        val sbd = focus("SBD Día 1")
        assertEquals(1.0, sbd.weight(KpknMuscleGroup.QUADS), 0.0)
        assertEquals(1.0, sbd.weight(KpknMuscleGroup.CHEST), 0.0)
        assertEquals(1.0, sbd.weight(KpknMuscleGroup.ERECTORS), 0.0)

        val overhead = focus("Press Militar/Hombro")
        assertEquals(1.0, overhead.weight(KpknMuscleGroup.DELT_FRONT), 0.0)
    }

    @Test
    fun a_label_that_says_nothing_accepts_everything_and_is_flagged_generic() {
        val generic = focus("Sesión 1 (4x9)")
        assertTrue(generic.isGeneric)
        assertTrue(KpknMuscleGroup.entries.all { generic.weight(it) == 1.0 })
    }

    @Test
    fun light_days_are_recognized_from_their_label() {
        assertTrue(focus("Día Recuperación", listOf("SQUAT", "BENCH")).isLight)
        assertTrue(focus("Torso Liviano").isLight)
        assertTrue(focus("Glúteo Pump").isLight)
        assertFalse(focus("Torso").isLight)
    }

    @Test
    fun every_published_day_has_a_clear_focus_or_accepts_everything() {
        SPLIT_TEMPLATES.filter { it.isVisibleForApplication }.forEach { split ->
            SplitCatalogRules.trainingDefinitions(split).forEach { definition ->
                val parsed = DayFocusParser.parse(definition)
                assertTrue("${split.id}/${definition.label}", parsed.weights.values.maxOrNull()!! >= 0.9)
                assertTrue("${split.id}/${definition.label}", parsed.weights.values.all { it in 0.0..1.0 })
            }
        }
    }

    @Test
    fun affinity_matches_each_exercise_with_its_day() {
        fun affinity(configurationId: String, label: String): Double {
            val traits = requireNotNull(SplitTestSupport.resolver.traitsOf(SplitTestSupport.exercise("x", configurationId)))
            return SplitAffinity.of(traits.muscles, focus(label), traits.chain)
        }
        assertEquals(1.0, affinity(CatalogIds.BP, "Empuje"), 1e-9)
        assertEquals(1.0, affinity(CatalogIds.BP, "Torso"), 1e-9)
        assertEquals(0.0, affinity(CatalogIds.BP, "Tirón"), 0.2)
        assertEquals(0.0, affinity(CatalogIds.BP, "Pierna"), 1e-9)
        assertTrue(affinity(CatalogIds.SQ_HIGH, "Pierna") > 0.9)
        assertTrue(affinity(CatalogIds.SQ_HIGH, "Cadena Anterior") > affinity(CatalogIds.SQ_HIGH, "Cadena Posterior"))
        assertTrue(affinity(CatalogIds.RDL, "Cadena Posterior") > affinity(CatalogIds.RDL, "Cadena Anterior"))
        assertEquals(1.0, affinity(CatalogIds.CURL, "Tirón"), 0.2)
        assertEquals(0.5, affinity(CatalogIds.CRUNCH, "Pierna"), 1e-9)
        assertEquals(0.5, affinity(CatalogIds.CRUNCH, "Empuje"), 1e-9)
        assertEquals(SplitAffinity.NEUTRAL, SplitAffinity.of(emptyMap(), focus("Torso")), 0.0)
    }
}
