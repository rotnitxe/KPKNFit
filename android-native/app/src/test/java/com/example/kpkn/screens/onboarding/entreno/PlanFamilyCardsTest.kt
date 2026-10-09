package com.example.kpkn.screens.onboarding.entreno

import com.example.kpkn.screens.onboarding.SetupPlanReveal
import org.junit.Assert.assertEquals
import org.junit.Test

/** D1 (punto 12) · PLAN muestra una ficha por familia de autor (PHUL) y la otra versión se alcanza desde el detalle. */
class PlanFamilyCardsTest {

    private val originalPhul = "original:phul-ms-2021-r1"
    private val adaptedPhul = "adapted:phul-kpkn-r1"
    private val otherPlan = "native:muscle-foundation-v2"

    @Test
    fun a_family_shows_the_adapted_version_only_when_it_changes_an_exercise() {
        val reveals = listOf(reveal(originalPhul, listOf("Sentadilla")), reveal(adaptedPhul, listOf("Prensa")))
        assertEquals(listOf(adaptedPhul), planCardRevealsOf(reveals).map { it.planId })
    }

    @Test
    fun a_family_shows_the_original_when_the_adapted_version_changes_nothing() {
        val reveals = listOf(reveal(originalPhul, listOf("Sentadilla")), reveal(adaptedPhul, listOf("Sentadilla")))
        assertEquals(listOf(originalPhul), planCardRevealsOf(reveals).map { it.planId })
    }

    @Test
    fun a_family_with_only_one_viable_version_shows_that_version() {
        assertEquals(listOf(adaptedPhul), planCardRevealsOf(listOf(reveal(adaptedPhul))).map { it.planId })
        assertEquals(listOf(originalPhul), planCardRevealsOf(listOf(reveal(originalPhul))).map { it.planId })
    }

    @Test
    fun plans_outside_a_family_pass_through_in_their_order() {
        val reveals = listOf(reveal(otherPlan), reveal(originalPhul), reveal("native:machine-muscle"))
        assertEquals(listOf(otherPlan, originalPhul, "native:machine-muscle"), planCardRevealsOf(reveals).map { it.planId })
    }

    @Test
    fun the_other_version_is_reached_from_the_detail_and_the_action_says_where_it_goes() {
        val reveals = listOf(reveal(originalPhul), reveal(adaptedPhul))
        assertEquals(adaptedPhul, siblingRevealOf(reveals, originalPhul)?.planId)
        assertEquals(originalPhul, siblingRevealOf(reveals, adaptedPhul)?.planId)
        assertEquals(null, siblingRevealOf(listOf(reveal(originalPhul)), originalPhul))
        assertEquals(SIBLING_TO_ADAPTED_LABEL, siblingActionLabelOf(originalPhul))
        assertEquals(SIBLING_TO_ORIGINAL_LABEL, siblingActionLabelOf(adaptedPhul))
    }

    @Test
    fun a_selection_on_the_version_that_is_not_shown_marks_its_family_card() {
        val shown = listOf(reveal(adaptedPhul, listOf("Prensa")))
        assertEquals(adaptedPhul, shownSelectedIdOf(originalPhul, shown))
        assertEquals(otherPlan, shownSelectedIdOf(otherPlan, shown))
        assertEquals(null, shownSelectedIdOf(null, shown))
    }

    private fun reveal(planId: String, mainExercises: List<String> = listOf("Sentadilla")) = SetupPlanReveal(
        planId = planId,
        title = planId,
        generated = false,
        profile = null,
        kicker = "Campbell",
        blurb = "",
        description = "",
        daysLabel = "4 días",
        minutesLabel = "~60 min",
        levelLabel = "Intermedio",
        badge = null,
        coverSeed = 0,
        mainExercises = mainExercises,
        blocks = emptyList(),
        week = emptyList(),
        reasons = emptyList(),
        notes = emptyList(),
        attribution = null,
    )
}
