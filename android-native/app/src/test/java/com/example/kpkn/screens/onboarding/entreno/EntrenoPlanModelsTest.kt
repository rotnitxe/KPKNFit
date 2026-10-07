package com.example.kpkn.screens.onboarding.entreno

import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupPlanReveal
import com.example.kpkn.screens.onboarding.SetupPlanRevealBlock
import com.example.kpkn.screens.onboarding.SetupPlanRevealDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Entreno v2 · el revelado del ViewModel llega intacto a la portada, al carrusel y al detalle. */
class EntrenoPlanModelsTest {

    private val reveal = SetupPlanReveal(
        planId = "generated:powerlifting",
        title = "Powerlifting a medida",
        generated = true,
        profile = TrainingGoalProfile.POWERLIFTING,
        kicker = "Hecho a tu medida",
        blurb = "4 días de sentadilla, banca y peso muerto, unos 75 min por sesión.",
        description = "Descripción completa.",
        daysLabel = "4 días",
        minutesLabel = "~75 min",
        levelLabel = "Intermedio",
        badge = null,
        coverSeed = 3,
        mainExercises = listOf("Sentadilla", "Press de banca"),
        blocks = listOf(SetupPlanRevealBlock("Semana que se repite", "Cada semana", "Detalle")),
        week = listOf(
            SetupPlanRevealDay(day = 1, title = "Sentadilla", minutes = 74, exerciseCount = 6, isMain = true, place = TrainingPlace.GYM),
            SetupPlanRevealDay(day = 3, title = "Banca", minutes = 70, exerciseCount = 6, isMain = false),
        ),
        reasons = listOf("Razón 1", "Razón 2", "Razón 3"),
        notes = listOf("Nota"),
        attribution = null,
    )

    @Test
    fun the_card_keeps_every_field_of_the_reveal() {
        val card = reveal.toCardModel()
        assertEquals(reveal.planId, card.id)
        assertEquals(reveal.title, card.title)
        assertEquals(reveal.kicker, card.kicker)
        assertEquals(reveal.blurb, card.blurb)
        assertEquals(reveal.profile, card.profile)
        assertEquals(reveal.daysLabel, card.daysLabel)
        assertEquals(reveal.minutesLabel, card.minutesLabel)
        assertEquals(reveal.levelLabel, card.levelLabel)
        assertEquals(reveal.badge, card.badge)
        assertEquals(reveal.coverSeed, card.coverSeed)
    }

    @Test
    fun the_detail_carries_the_real_week_structure_reasons_and_notes() {
        val detail = reveal.toDetailModel()
        assertEquals(reveal.toCardModel(), detail.card)
        assertEquals(reveal.description, detail.description)
        assertEquals(reveal.mainExercises, detail.mainExercises)
        assertEquals(listOf("Semana que se repite" to "Cada semana"), detail.blocks.map { it.label to it.weeksLabel })
        assertEquals(listOf(1 to true, 3 to false), detail.week.map { it.day to it.isMain })
        assertEquals(listOf(74, 70), detail.week.map { it.minutes })
        assertEquals(reveal.reasons, detail.reasons)
        assertEquals(reveal.notes, detail.notes)
        assertEquals(reveal.attribution, detail.attribution)
    }

    @Test
    fun the_carousel_never_receives_two_cards_with_the_same_id() {
        val other = reveal.copy(planId = "native:powerlifting-own", title = "Powerlifting KPKN", generated = false)
        val cards = cardModelsOf(listOf(reveal, other, reveal.copy(title = "Duplicado")))
        assertEquals(listOf("generated:powerlifting", "native:powerlifting-own"), cards.map { it.id })
        assertEquals("Powerlifting a medida", cards.first().title)
    }

    @Test
    fun the_reveal_key_is_stable_and_changes_with_what_the_person_sees() {
        val key = revealKeyOf(listOf(reveal))
        // Estable: el mismo resultado (otra instancia igual) da la misma huella, también tras recrear la lista.
        assertEquals(key, revealKeyOf(listOf(reveal.copy())))
        // «Otra versión»: otra semilla de portada u otros ejercicios dan otra huella.
        assertNotEquals(key, revealKeyOf(listOf(reveal.copy(coverSeed = 4))))
        assertNotEquals(key, revealKeyOf(listOf(reveal.copy(mainExercises = listOf("Sentadilla frontal", "Press de banca")))))
        // Otras respuestas: otros minutos o la sesión en otro día.
        assertNotEquals(key, revealKeyOf(listOf(reveal.copy(minutesLabel = "~90 min"))))
        assertNotEquals(
            key,
            revealKeyOf(listOf(reveal.copy(week = reveal.week.map { if (it.day == 3) it.copy(day = 4) else it }))),
        )
        // Otra lista de programas (uno más en el carrusel).
        assertNotEquals(key, revealKeyOf(listOf(reveal, reveal.copy(planId = "native:powerlifting-own"))))
        // Sin programas, una huella vacía (nunca igual a la de un resultado).
        assertEquals("", revealKeyOf(emptyList()))
    }
}
