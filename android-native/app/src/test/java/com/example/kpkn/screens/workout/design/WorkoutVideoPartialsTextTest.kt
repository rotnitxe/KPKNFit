package com.example.kpkn.screens.workout.design

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.screens.workout.CompletedSetPatch
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las parciales de M4 en los textos de la sugerencia, la corrección, el Modo visual, el descanso y el chip: cada nivel de confianza
 * propia de las parciales y cada zona, con una sola fuente de texto (`partialsPhrase` / `partialsShort`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutVideoPartialsTextTest {
    @get:Rule val rule = createComposeRule()

    private fun suggest(
        partials: Int,
        partialConfidence: CountConfidence,
        zone: PartialZone? = null,
        confidence: CountConfidence = CountConfidence.ALTA,
        reps: Int = 8,
    ) = SetVideoAnalysisState.Suggest(reps, partials, 0, confidence, partialConfidence = partialConfidence, partialZone = zone)

    private fun line(state: SetVideoAnalysisState) = visualModeAnalysisLine(state, 0).text

    @Test fun high_confidence_names_the_zone_of_the_partials() {
        assertEquals("El vídeo cuenta 8 · +2 parciales (en estiramiento) · ver análisis",
            line(suggest(2, CountConfidence.ALTA, PartialZone.STRETCHED)))
        assertEquals("El vídeo cuenta 8 · +2 parciales (acortada) · ver análisis",
            line(suggest(2, CountConfidence.ALTA, PartialZone.SHORTENED)))
        assertEquals("El vídeo cuenta 8 · +1 parcial (en estiramiento) · ver análisis",
            line(suggest(1, CountConfidence.ALTA, PartialZone.STRETCHED)))
        // Zonas distintas (o sin zona): solo el número.
        assertEquals("El vídeo cuenta 8 · +2 parciales · ver análisis", line(suggest(2, CountConfidence.ALTA, zone = null)))
    }

    @Test fun medium_confidence_is_an_estimate_without_a_zone() {
        assertEquals("El vídeo cuenta 8 · +≈ 2 parciales · ver análisis",
            line(suggest(2, CountConfidence.MEDIA, PartialZone.STRETCHED)))
        assertEquals("El vídeo cuenta 8 · +≈ 1 parcial · ver análisis", line(suggest(1, CountConfidence.MEDIA, PartialZone.SHORTENED)))
        // El número de repeticiones y el de parciales tienen cada uno su confianza.
        assertEquals("El vídeo cuenta ≈ 8 · +2 parciales (en estiramiento) · revisar",
            line(suggest(2, CountConfidence.ALTA, PartialZone.STRETCHED, confidence = CountConfidence.MEDIA)))
    }

    @Test fun low_confidence_never_mentions_the_partials_even_if_a_number_arrives() {
        assertEquals("El vídeo cuenta 8 · ver análisis", line(suggest(2, CountConfidence.BAJA, PartialZone.STRETCHED)))
        assertEquals("El vídeo cuenta 8 · ver análisis", line(suggest(0, CountConfidence.BAJA)))
        assertFalse(workoutVideoRepsText(suggest(3, CountConfidence.BAJA)).contains("parcial"))
        assertNull(partialsPhrase(3, CountConfidence.BAJA, PartialZone.STRETCHED))
        assertNull(partialsShort(3, CountConfidence.BAJA))
        assertNull(partialsPhrase(0, CountConfidence.ALTA, PartialZone.STRETCHED))
    }

    @Test fun the_correction_and_the_rest_line_use_the_same_phrase() {
        val mismatch = SetVideoAnalysisState.Mismatch(
            8, 7, 2, 0, CountConfidence.ALTA, loggedPartials = 1, partialConfidence = CountConfidence.ALTA, partialZone = PartialZone.STRETCHED,
        )
        assertEquals("Registraste 8 + 1 parcial; el vídeo cuenta 7 · +2 parciales (en estiramiento) · ver análisis", line(mismatch))
        assertEquals(
            "Registraste 8 + 1 parcial; el vídeo cuenta 7 · +2 parciales (en estiramiento) · corregir",
            workoutRestVideoLine(mismatch).text,
        )
        val medium = mismatch.copy(partialConfidence = CountConfidence.MEDIA)
        assertEquals("Registraste 8 + 1 parcial; el vídeo cuenta 7 · +≈ 2 parciales · corregir", workoutRestVideoLine(medium).text)
    }

    @Test fun the_chip_and_the_compact_rest_line_keep_the_short_format() {
        fun chip(state: SetVideoAnalysisState) = workoutVideoChipContent(state)!!.text
        assertEquals("8 ·2p", chip(suggest(2, CountConfidence.ALTA, PartialZone.STRETCHED)))
        assertEquals("8 ·≈ 2p", chip(suggest(2, CountConfidence.MEDIA, PartialZone.STRETCHED)))
        assertEquals("8", chip(suggest(2, CountConfidence.BAJA)))
        assertEquals("8", chip(suggest(0, CountConfidence.ALTA)))

        fun compact(state: SetVideoAnalysisState) = workoutRestVideoLine(state).compactDescription
        assertEquals("Vídeo 8 +2p", compact(suggest(2, CountConfidence.ALTA)))
        assertEquals("Vídeo 8 +≈ 2p", compact(suggest(2, CountConfidence.MEDIA)))
        assertEquals("Vídeo 8", compact(suggest(2, CountConfidence.BAJA)))
        val mismatch = SetVideoAnalysisState.Mismatch(8, 7, 2, 0, CountConfidence.ALTA, partialConfidence = CountConfidence.MEDIA)
        assertEquals("8→7+≈ 2p", compact(mismatch))
    }

    @Test fun the_correction_patch_fills_the_partials_only_with_high_or_medium_confidence() {
        val high = SetVideoAnalysisState.Mismatch(8, 7, 2, 0, CountConfidence.ALTA, loggedPartials = 1, partialConfidence = CountConfidence.ALTA)
        assertEquals(2, completedVideoCorrectionPatch(high).partialReps)
        assertEquals(2, completedVideoCorrectionPatch(high.copy(partialConfidence = CountConfidence.MEDIA)).partialReps)
        // Con BAJA el parche no toca las parciales (null): quedan las que había.
        val low = completedVideoCorrectionPatch(high.copy(partials = 1, partialConfidence = CountConfidence.BAJA))
        assertNull(low.partialReps)
        assertEquals(7, low.edit.metricValue)
    }

    @Test fun using_the_suggestion_hands_over_the_partials_only_when_they_are_offered() {
        val used = mutableListOf<SetVideoAnalysisState.Suggest>()
        val state = androidx.compose.runtime.mutableStateOf<SetVideoAnalysisState>(suggest(2, CountConfidence.ALTA, PartialZone.STRETCHED))
        rule.setContent {
            WorkoutVideoRepsSuggestion(SetVideoAnalysisUi("video", state.value), onUse = { used += it }, onCorrect = {}, onMarkFailure = {}, onOpenAnalysis = {})
        }
        rule.onNodeWithText("Usar").performClick()
        // El anfitrión rellena con lo que recibe: 2 parciales con ALTA…
        rule.runOnIdle {
            assertEquals(2, used.last().partials)
            // …y la zona no se pierde por el camino.
            assertEquals(PartialZone.STRETCHED, used.last().partialZone)
            state.value = suggest(0, CountConfidence.BAJA)
        }
        rule.onNodeWithText("Usar").performClick()
        // …y con BAJA no hay nada que aplicar: la sugerencia llega sin parciales y sin mencionarlas.
        rule.runOnIdle {
            assertEquals(0, used.last().partials)
            assertTrue(used.size == 2)
        }
        rule.onNodeWithText("El vídeo cuenta 8", substring = true).assertExists()
        assertFalse(workoutVideoRepsText(state.value).contains("parcial"))
    }

    @Test fun correcting_with_low_partial_confidence_leaves_the_logged_partials_alone() {
        val patches = mutableListOf<CompletedSetPatch>()
        val low = SetVideoAnalysisState.Mismatch(8, 7, 1, 0, CountConfidence.ALTA, loggedPartials = 1, partialConfidence = CountConfidence.BAJA)
        assertFalse("las parciales ya registradas no se cuentan como diferencia", low.copy(counted = 8).countsDiffer)
        rule.setContent {
            WorkoutCompletedVideoSuggestion(SetVideoAnalysisUi("video", low), onPatch = { patches += it }, onOpenAnalysis = {})
        }
        rule.onNodeWithText("Corregir").performClick()
        rule.runOnIdle {
            assertEquals(7, patches.single().edit.metricValue)
            assertNull(patches.single().partialReps)
        }
    }
}
