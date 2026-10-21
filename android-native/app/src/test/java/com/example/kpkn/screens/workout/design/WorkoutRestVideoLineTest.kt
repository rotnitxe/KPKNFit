package com.example.kpkn.screens.workout.design

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import org.junit.Assert.*
import org.junit.Test

class WorkoutRestVideoLineTest {
    @Test fun every_rest_phrase_and_its_explicit_actions_match_the_table() {
        val cases = listOf(
            SetVideoAnalysisState.Analyzing(42) to ("Analizando el vídeo… 42\u00A0%" to emptyList()),
            SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.ALTA) to
                ("Registraste 8; el vídeo cuenta 7 · corregir" to listOf(RestVideoAction.CORRECT)),
            SetVideoAnalysisState.Mismatch(8, 7, 0, 1, CountConfidence.ALTA) to
                ("Registraste 8; el vídeo cuenta 7 · 1 al fallo · corregir · marcar fallo" to listOf(RestVideoAction.CORRECT, RestVideoAction.MARK_FAILURE)),
            SetVideoAnalysisState.Agrees(8) to ("El vídeo confirma 8 · ver análisis" to listOf(RestVideoAction.OPEN)),
            SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.MEDIA) to
                ("Registraste 8; el vídeo cuenta ≈\u00A07 · corregir · ver análisis" to listOf(RestVideoAction.CORRECT, RestVideoAction.OPEN)),
            SetVideoAnalysisState.Mismatch(8, 8, 0, 0, CountConfidence.MEDIA) to
                ("El vídeo cuenta ≈\u00A08 · ver análisis" to listOf(RestVideoAction.OPEN)),
            SetVideoAnalysisState.Inconclusive to ("Vídeo no concluyente · ver por qué" to listOf(RestVideoAction.WHY)),
            SetVideoAnalysisState.Failed("decode") to ("No se pudo analizar el vídeo · reintentar" to listOf(RestVideoAction.RETRY)),
        )
        cases.forEach { (state, expected) ->
            val line = workoutRestVideoLine(state)
            assertEquals(state.toString(), expected.first, line.text)
            assertEquals(expected.second, line.actions)
        }
    }

    @Test fun partials_failure_and_low_confidence_keep_the_visual_mode_meaning() {
        listOf(CountConfidence.ALTA, CountConfidence.MEDIA).forEach { confidence ->
            val state = SetVideoAnalysisState.Mismatch(8, 7, 2, 1, confidence, loggedPartials = 1)
            val line = workoutRestVideoLine(state)
            assertTrue(line.text.contains(workoutVideoRepsText(state).replaceFirst("El vídeo", "el vídeo")))
            assertTrue(line.compactDescription.contains("8+1p"))
            assertTrue(line.compactDescription.contains("+2p"))
            assertTrue(line.compactDescription.contains("1 fallo"))
        }
        listOf(
            SetVideoAnalysisState.Suggest(27, 4, 2, CountConfidence.BAJA),
            SetVideoAnalysisState.Mismatch(8, 27, 4, 2, CountConfidence.BAJA),
        ).forEach { state ->
            assertEquals("Vídeo no concluyente · ver por qué", workoutRestVideoLine(state).text)
            assertEquals(listOf(RestVideoAction.WHY), workoutRestVideoLine(state).actions)
        }
    }

    @Test fun zero_full_reps_with_partials_is_correctable_but_failure_only_never_replaces_the_count() {
        listOf(CountConfidence.ALTA, CountConfidence.MEDIA).forEach { confidence ->
            val state = SetVideoAnalysisState.Mismatch(8, 0, 0, 2, confidence)
            assertFalse(RestVideoAction.CORRECT in workoutRestVideoLine(state).actions)
            assertTrue(RestVideoAction.CORRECT in workoutRestVideoLine(state.copy(partials = 2)).actions)
            assertEquals(confidence == CountConfidence.ALTA, RestVideoAction.MARK_FAILURE in workoutRestVideoLine(state).actions)
        }
        assertEquals(emptyList<RestVideoAction>(), workoutRestVideoLine(SetVideoAnalysisState.Unsupported("Por tiempo")).actions)
        assertEquals("Análisis no disponible", workoutRestVideoLine(SetVideoAnalysisState.Unsupported("Por tiempo")).compactDescription)
        assertEquals("1 al fallo", workoutRestVideoLine(SetVideoAnalysisState.Suggest(0, 0, 1, CountConfidence.ALTA)).compactDescription)
        assertEquals("Analizando el vídeo… 100\u00A0%", workoutRestVideoLine(SetVideoAnalysisState.Analyzing(140)).text)
    }
}
