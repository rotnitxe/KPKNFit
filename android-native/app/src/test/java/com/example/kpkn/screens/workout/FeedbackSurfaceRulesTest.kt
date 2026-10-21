package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dónde vive el feedback pendiente y qué dice su cabecera (LV6, B1 y B3): reglas puras. */
class FeedbackSurfaceRulesTest {

    @Test
    fun withTheRestRunningAndMinimizedTheFeedbackLivesOnTheCard() {
        assertTrue(
            feedbackLivesOnCard(
                feedbackShowing = true,
                isRestTimerRunning = true,
                isRestMinimized = true,
                restKind = RestTimerKind.STANDARD,
            ),
        )
        assertTrue(feedbackLivesOnCard(true, true, true, RestTimerKind.SUPERSET_ROUND))

    }

    @Test
    fun otherwiseTheFeedbackStaysFullScreen() {
        // Descanso a pantalla completa: ya está a la vista.
        assertFalse(feedbackLivesOnCard(true, true, false, RestTimerKind.STANDARD))
        // Sin estado del descanso no hay página REST donde alojarlo, aunque el tick aún diga que corre.
        assertFalse(feedbackLivesOnCard(true, true, true, null))
        // Sin descanso en marcha (acabó o se saltó) no hay tarjeta del descanso que lo lleve.
        assertFalse(feedbackLivesOnCard(true, false, true, null))
        assertFalse(feedbackLivesOnCard(true, false, false, null))
        // El descanso de una aproximación no hospeda el feedback.
        assertFalse(feedbackLivesOnCard(true, true, true, RestTimerKind.WARMUP))
    }

    @Test
    fun withoutPendingFeedbackThereIsNothingToCarry() {
        assertFalse(feedbackLivesOnCard(false, true, true, RestTimerKind.STANDARD))
        assertFalse(feedbackLivesOnCard(false, true, false, RestTimerKind.STANDARD))
    }

    private val press = Exercise(id = "press", name = "Press militar")
    private val rowA = Exercise(id = "a", name = "Remo", supersetGroupRef = "g1")
    private val rowB = Exercise(id = "b", name = "Curl", supersetGroupRef = "g1")
    private val all = listOf(press, rowA, rowB)

    @Test
    fun theHeaderSaysWhatComesAfter() {
        assertEquals("Press militar", feedbackAfterText(0, all, { it.name }))
        assertEquals("la superserie", feedbackAfterText(1, all, { it.name }))
        assertEquals("la superserie", feedbackAfterText(2, all, { it.name }))
    }

    @Test
    fun theLastExerciseOfTheSessionAnnouncesTheClosing() {
        assertEquals("el cierre de la sesión", feedbackAfterText(-2, all, { it.name }))
    }

    @Test
    fun withoutAnExerciseAfterItSaysNothing() {
        assertNull(feedbackAfterText(-1, all, { it.name }))
        assertNull(feedbackAfterText(7, all, { it.name }))
        assertNull(feedbackAfterText(0, emptyList(), { it.name }))
    }
}
