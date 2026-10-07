package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La animación «estamos preparando tu programa»: el guion en el tiempo (funciones puras) y el overlay (movimiento reducido
 * para poder medir el aviso sin esperar fotogramas).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanPreparingTest {

    @get:Rule
    val rule = createComposeRule()

    // ------------------------------------------------------------ guion en el tiempo

    @Test
    fun theMinimumAnimationIsAtLeast2point6Seconds() {
        assertTrue(PreparingTimeline.MIN_SECONDS >= 2.6f)
        // Y la última etapa se enciende antes de que se cumpla.
        assertTrue(PreparingTimeline.allLitAt < PreparingTimeline.MIN_SECONDS)
    }

    @Test
    fun theGuideLightsTheFiveStagesOneAfterAnother() {
        assertEquals(0, PreparingTimeline.litCount(0f))
        assertEquals(0, PreparingTimeline.litCount(PreparingTimeline.LEAD - 0.01f))
        assertEquals(1, PreparingTimeline.litCount(PreparingTimeline.LEAD))
        for (i in 0 until PreparingTimeline.STAGE_COUNT) {
            assertEquals(i + 1, PreparingTimeline.litCount(PreparingTimeline.reach(i) + 0.001f))
        }
        assertEquals(5, PreparingTimeline.litCount(10f))
        // El instante de cada etapa crece.
        for (i in 1 until PreparingTimeline.STAGE_COUNT) assertTrue(PreparingTimeline.reach(i) > PreparingTimeline.reach(i - 1))
    }

    @Test
    fun theInitialGuideTravelsTheRailOnceAndStops() {
        assertEquals(0f, PreparingTimeline.guide(0f), 0f)
        assertEquals(0f, PreparingTimeline.guide(PreparingTimeline.LEAD), 0.0001f)
        assertEquals(4f, PreparingTimeline.guide(PreparingTimeline.allLitAt), 0.0001f)
        assertEquals(4f, PreparingTimeline.guide(99f), 0f)
        // Con otro número de etapas llega a la última de esas.
        assertEquals(2f, PreparingTimeline.guide(99f, stages = 3), 0f)
    }

    @Test
    fun whileWaitingTheGuideKeepsCrossingTheRailForever() {
        // Antes de acabar el recorrido inicial no hay destello de espera.
        assertEquals(-1f, PreparingTimeline.waitGuide(0f), 0f)
        assertEquals(-1f, PreparingTimeline.waitGuide(PreparingTimeline.allLitAt), 0f)
        // Después cruza el riel una y otra vez, siempre dentro de [0, 4].
        var maxSeen = 0f
        for (i in 0..4000) {
            val g = PreparingTimeline.waitGuide(3f + i * 0.01f)
            assertTrue("destello fuera del riel: $g", g in 0f..4f)
            maxSeen = maxOf(maxSeen, g)
        }
        assertTrue("llega casi al final del riel", maxSeen > 3.9f)
        // Y a mucho tiempo vista sigue vivo: la espera no se acaba.
        assertTrue(PreparingTimeline.waitGuide(3600f) >= 0f)
    }

    @Test
    fun itOnlyFinishesWhenTheEngineIsReadyAndTheMinimumIsDone() {
        assertFalse(PreparingTimeline.canFinish(0f, ready = true))
        assertFalse(PreparingTimeline.canFinish(PreparingTimeline.MIN_SECONDS - 0.01f, ready = true))
        assertFalse(PreparingTimeline.canFinish(99f, ready = false))
        assertTrue(PreparingTimeline.canFinish(PreparingTimeline.MIN_SECONDS, ready = true))
        assertTrue(PreparingTimeline.canFinish(99f, ready = true))
    }

    @Test
    fun theFlashRunsFromZeroToOne() {
        assertEquals(0f, PreparingTimeline.flash(5f, Float.NaN), 0f)
        assertEquals(0f, PreparingTimeline.flash(3f, 3f), 0f)
        assertEquals(0.5f, PreparingTimeline.flash(3f + PreparingTimeline.FLASH_SECONDS / 2f, 3f), 0.0001f)
        assertEquals(1f, PreparingTimeline.flash(9f, 3f), 0f)
    }

    @Test
    fun bothVariantsHaveFiveStagesAndTheirOwnTitle() {
        for (variant in PlanPreparingVariant.entries) {
            assertEquals(PreparingTimeline.STAGE_COUNT, preparingStages(variant).size)
        }
        assertEquals("Estamos preparando tu programa personalizado", preparingTitle(PlanPreparingVariant.GENERAL))
        assertEquals("Seleccionando programas para tu disciplina", preparingTitle(PlanPreparingVariant.DISCIPLINE))
        assertEquals(listOf("Tu material", "Tus días", "Tu tiempo", "Tus músculos", "Tus ejercicios"), preparingStages(PlanPreparingVariant.GENERAL))
        assertEquals(
            listOf("Tu disciplina", "Tu material", "Tus días", "Tu nivel", "Los mejores programas"),
            preparingStages(PlanPreparingVariant.DISCIPLINE),
        )
    }

    @Test
    fun theRailSwitchesToTheVerticalLayoutOnlyWhenALabelDoesNotFit() {
        val labels = preparingStages(PlanPreparingVariant.DISCIPLINE)
        // Columnas holgadas: ninguna etiqueta pasa de dos líneas ni parte una palabra.
        assertFalse(railNeedsVertical(labels) { _, _ -> 1 })
        // Una etiqueta que necesita tres líneas obliga a la lista.
        assertTrue(railNeedsVertical(labels) { text, maxLines -> if (text == "Los mejores programas" && maxLines == 4) 3 else 1 })
        // Una palabra que se parte a media palabra también.
        assertTrue(railNeedsVertical(labels) { text, maxLines -> if (text == "programas" && maxLines == 2) 2 else 1 })
    }

    // ------------------------------------------------------------ overlay

    @Test
    fun theOverlayCarriesItsTagTitleAndStages() {
        rule.setContent {
            PlanPreparingOverlay(PlanPreparingVariant.GENERAL, null, ready = false, onAnimationDone = {}, reduced = true)
        }
        rule.onNodeWithTag("setup-plan-preparing").assertContentDescriptionContains("Estamos preparando tu programa personalizado", substring = true)
        rule.onNodeWithTag("setup-plan-preparing").assertContentDescriptionContains("Tus músculos", substring = true)
        assertEquals("setup-plan-preparing", PLAN_PREPARING_TAG)
    }

    @Test
    fun theDisciplineOverlayNamesItsOwnStages() {
        rule.setContent {
            PlanPreparingOverlay(PlanPreparingVariant.DISCIPLINE, TrainingGoalProfile.POWERLIFTING, ready = false, onAnimationDone = {}, reduced = true)
        }
        rule.onNodeWithTag("setup-plan-preparing").assertContentDescriptionContains("Seleccionando programas para tu disciplina", substring = true)
        rule.onNodeWithTag("setup-plan-preparing").assertContentDescriptionContains("Los mejores programas", substring = true)
    }

    @Test
    fun withReducedMotionItAnnouncesOnceAShortWhileAfterBeingReady() {
        var done = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PlanPreparingOverlay(PlanPreparingVariant.GENERAL, null, ready = true, onAnimationDone = { done++ }, reduced = true)
        }
        rule.mainClock.advanceTimeBy(PreparingTimeline.REDUCED_HOLD_MS / 2)
        assertEquals(0, done)
        rule.mainClock.advanceTimeBy(PreparingTimeline.REDUCED_HOLD_MS)
        rule.mainClock.advanceTimeBy(2000)
        assertEquals(1, done)
    }

    @Test
    fun aFrozenOverlayNeverAnnounces() {
        var done = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PlanPreparingOverlay(PlanPreparingVariant.GENERAL, null, ready = true, onAnimationDone = { done++ }, reduced = false, frozenAt = 1.6f)
        }
        rule.mainClock.advanceTimeBy(20_000)
        assertEquals(0, done)
        rule.onNodeWithTag("setup-plan-preparing").assertContentDescriptionContains("Tus días", substring = true)
    }

    @Test
    fun itNeverAnnouncesWhileTheEngineIsNotReady() {
        var done = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PlanPreparingOverlay(PlanPreparingVariant.GENERAL, null, ready = false, onAnimationDone = { done++ }, reduced = true)
        }
        rule.mainClock.advanceTimeBy(20_000)
        assertEquals(0, done)
    }
}
