package com.example.kpkn.domain.training.split

import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.NativeProfileSplitWitness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las opciones de reparto del tablero: visibles, con el número de días justo y los de powerlifting solo para fuerza. */
class SplitCatalogRulesTest {

    private val powerliftingIds = SPLIT_TEMPLATES.filter { SplitTag.POWERLIFTING in it.tags && it.isVisibleForApplication }.map { it.id }.toSet()

    @Test
    fun there_are_thirty_five_applicable_splits_and_none_for_one_or_seven_days() {
        assertEquals(35, (1..7).sumOf { SplitRedistributor.optionsFor(it).size })
        assertTrue(SplitRedistributor.optionsFor(1).isEmpty())
        assertTrue(SplitRedistributor.optionsFor(7).isEmpty())
        assertTrue(SplitRedistributor.optionsFor(0).isEmpty())
    }

    @Test
    fun options_have_exactly_the_requested_number_of_training_days() {
        (2..6).forEach { days ->
            val options = SplitRedistributor.optionsFor(days)
            assertTrue("hay repartos de $days días", options.isNotEmpty())
            options.forEach { option ->
                assertEquals(option.id, days, option.dayTitles.size)
                assertEquals(option.id, days, option.pattern.count { !SplitCatalogRules.isRest(it) })
                assertEquals(option.id, 7, option.pattern.size)
                assertTrue(option.id, option.name.isNotBlank())
                assertTrue(option.id, option.summary.startsWith("$days días"))
                assertTrue(option.id, option.dayTitles.all { it.isNotBlank() })
            }
        }
    }

    @Test
    fun the_counts_per_day_match_the_catalog() {
        assertEquals(setOf("minimalist_x2", "weekend_warrior"), SplitRedistributor.optionsFor(2).map { it.id }.toSet())
        assertEquals(9, SplitRedistributor.optionsFor(3).size)
        assertEquals(12, SplitRedistributor.optionsFor(4).size)
        assertEquals(8, SplitRedistributor.optionsFor(5).size)
        assertEquals(
            setOf("ppl_x6", "ul_x6", "ppl_arnold", "ant_post_x6"),
            SplitRedistributor.optionsFor(6).map { it.id }.toSet(),
        )
    }

    @Test
    fun power_splits_are_only_offered_to_the_strength_profile() {
        val withoutProfile = SplitRedistributor.optionsFor(4).map { it.id }.toSet()
        val strength = SplitRedistributor.optionsFor(4, TrainingGoalProfile.POWERLIFTING).map { it.id }.toSet()
        assertEquals(withoutProfile, strength)
        assertTrue(powerliftingIds.any { it in withoutProfile })

        TrainingGoalProfile.entries.filter { it != TrainingGoalProfile.POWERLIFTING }.forEach { profile ->
            val offered = SplitRedistributor.optionsFor(4, profile).map { it.id }.toSet()
            assertTrue("$profile no ve repartos de powerlifting", offered.none { it in powerliftingIds })
            assertTrue("$profile sigue viendo los generales", "ul_x4" in offered && "ant_post_x4" in offered)
        }
    }

    @Test
    fun the_profile_only_takes_away_the_powerlifting_splits_and_nothing_else() {
        (listOf<Int?>(null) + (1..7)).forEach { days ->
            val unfiltered = SplitCatalogRules.compatible(days, null)
            TrainingGoalProfile.entries.forEach { profile ->
                val expected = if (profile == TrainingGoalProfile.POWERLIFTING) {
                    unfiltered
                } else {
                    unfiltered.filter { SplitTag.POWERLIFTING !in it.tags }
                }
                assertEquals("$profile con $days días", expected.map { it.id }, SplitCatalogRules.compatible(days, profile).map { it.id })
            }
        }
    }

    @Test
    fun a_muscle_list_with_three_days_keeps_the_general_splits_and_drops_every_powerlifting_one() {
        val muscle = SplitCatalogRules.compatible(3, TrainingGoalProfile.BODYBUILDING).map { it.id }
        listOf("fullbody_x3", "heavy_light", "ppl_x3", "ul_fb_x3").forEach { assertTrue("Culturismo ofrece $it", it in muscle) }
        listOf("pl_sbd_x3", "texas_method", "madcow_5x5", "sheiko_3day", "korte_3x3").forEach {
            assertFalse("Culturismo no debe ofrecer $it", it in muscle)
        }
        val strength = SplitCatalogRules.compatible(3, TrainingGoalProfile.POWERLIFTING).map { it.id }
        listOf("fullbody_x3", "pl_sbd_x3", "texas_method", "madcow_5x5", "sheiko_3day", "korte_3x3").forEach {
            assertTrue("Powerlifting ofrece $it", it in strength)
        }
    }

    @Test
    fun the_profile_filter_never_hides_the_split_the_own_plan_of_that_profile_accepts() {
        // El perfil que sirve a cada plan propio en Entreno v2: Fuerza → Powerlifting, Músculo → Culturismo, Fuerza y músculo →
        // Powerbuilding y Atleta completo → Fuerza y cardio.
        val profileOf = mapOf(
            NativeProfileKind.STRENGTH to TrainingGoalProfile.POWERLIFTING,
            NativeProfileKind.MUSCLE to TrainingGoalProfile.BODYBUILDING,
            NativeProfileKind.POWERBUILDING to TrainingGoalProfile.POWERBUILDING,
            NativeProfileKind.COMPLETE_ATHLETE to TrainingGoalProfile.STRENGTH_CARDIO,
        )
        NativeProfileSplitWitness.allWitnesses().forEach { witness ->
            val profile = profileOf.getValue(witness.profile)
            assertTrue(
                "$profile con ${witness.days} días debe ofrecer ${witness.splitId}",
                witness.splitId in SplitCatalogRules.compatible(witness.days, profile).map { it.id },
            )
        }
    }

    @Test
    fun recommended_splits_come_first_and_the_rest_keep_the_catalog_order() {
        val options = SplitRedistributor.optionsFor(4)
        val recommended = options.takeWhile { it.recommended }
        assertTrue(recommended.isNotEmpty())
        assertTrue(options.drop(recommended.size).none { it.recommended })
        assertEquals("ul_x4", options.first().id)
        val catalogOrder = SPLIT_TEMPLATES.map { it.id }
        val others = options.drop(recommended.size).map { catalogOrder.indexOf(it.id) }
        assertEquals(others.sorted(), others)
    }

    @Test
    fun names_and_titles_read_in_plain_spanish() {
        val ul = SplitRedistributor.optionsFor(4).first { it.id == "ul_x4" }
        assertEquals("Torso y pierna, 4 días", ul.name)
        assertEquals(listOf("Torso A", "Pierna A", "Torso B", "Pierna B"), ul.dayTitles)
        assertEquals(listOf("Torso", "Pierna", "Descanso", "Torso", "Pierna", "Descanso", "Descanso"), ul.pattern)
        assertEquals("4 días · Frecuencia 2x/semana óptima", ul.summary)

        val fullBody = SplitRedistributor.optionsFor(3).first { it.id == "fullbody_x3" }
        assertEquals(listOf("Cuerpo Completo A", "Cuerpo Completo B", "Cuerpo Completo C"), fullBody.dayTitles)

        val ppl = SplitRedistributor.optionsFor(6).first { it.id == "ppl_x6" }
        assertEquals(listOf("Empuje A", "Tirón A", "Pierna A", "Empuje B", "Tirón B", "Pierna B"), ppl.dayTitles)

        // Un reparto sin nombre propio conserva el de su plantilla.
        val texas = SplitRedistributor.optionsFor(3, TrainingGoalProfile.POWERLIFTING).first { it.id == "texas_method" }
        assertEquals("Estilo Texas", texas.name)
        assertEquals("Fin de semana", SplitCatalogRules.displayName("weekend_warrior"))
        assertEquals(null, SplitCatalogRules.displayName("no_existe"))
    }

    @Test
    fun the_blank_canvas_and_hidden_definitions_never_appear() {
        val ids = (1..7).flatMap { SplitRedistributor.optionsFor(it) }.map { it.id }
        assertFalse("custom" in ids)
        SPLIT_TEMPLATES.filter { !it.isVisibleForApplication }.forEach { assertFalse(it.id, it.id in ids) }
    }
}
