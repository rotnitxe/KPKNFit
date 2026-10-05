package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.programs.CatalogClassification
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.domain.training.ATHLETE_SIX_DAY_BRIDGE_NOTE
import com.example.kpkn.domain.training.CatalogProvenance
import com.example.kpkn.domain.training.GLUTE_BRIDGE_ONCE_NOTE
import com.example.kpkn.domain.training.HighVolumeNotice
import com.example.kpkn.domain.training.PersonalizationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W2 · Revisión final del asistente (C3 restos, dato previo de grasa, B-03) y
 * línea de medición del barrido de candidatos (C4). Funciones puras de texto.
 */
class WizardReviewAndSweepTest {

    private val provenance = CatalogProvenance("native:muscle-foundation-v2", "rev", CatalogSource.NATIVE, "muscle")

    private fun report(highVolume: List<HighVolumeNotice> = emptyList(), planNotes: List<String> = emptyList()) =
        PersonalizationReport(
            executable = true,
            classification = CatalogClassification.SIMPLE,
            limitations = emptyList(),
            muscles = emptyList(),
            provenance = provenance,
            highVolume = highVolume,
            planNotes = planNotes,
        )

    // ─── C3: restos de la revisión ───────────────────────────────────────────

    @Test
    fun reviewSummariesAgreeWithOne() {
        assertEquals("1 sesión · 1 ejercicio", previewSessionsSummary(sessions = 1, exercises = 1))
        assertEquals("4 sesiones · 12 ejercicios", previewSessionsSummary(sessions = 4, exercises = 12))
        assertEquals("1 sesión en 2 semanas", allSessionsSummary(sessions = 1, weeks = 2))
        assertEquals("8 sesiones en 4 semanas", allSessionsSummary(sessions = 8, weeks = 4))
    }

    // ─── Dato previo de grasa corporal ───────────────────────────────────────

    @Test
    fun reviewShowsThePreviousBodyFatHonestlyInsteadOfNotDeclared() {
        val returning = SetupWizardDraft(importedBodyFatPercent = 21.0)
        assertEquals("Guardada en Ajustes: 21 %", bodyFatReviewValue(returning))
        assertEquals("Guardada en Ajustes: 21,5 %", bodyFatReviewValue(SetupWizardDraft(importedBodyFatPercent = 21.5)))
        // No es una respuesta nueva: nada declarado en el borrador.
        assertNull(returning.bodyFatSource)
        assertNull(returning.bodyFatPercent)
    }

    @Test
    fun reviewKeepsTheOtherBodyFatCases() {
        assertNull("sin nada: «Sin declarar»", bodyFatReviewValue(SetupWizardDraft()))
        assertNull("dato previo increíble no se enseña", bodyFatReviewValue(SetupWizardDraft(importedBodyFatPercent = 80.0)))
        assertEquals("No lo sé", bodyFatReviewValue(SetupWizardDraft(bodyFatSource = SetupBodyFatSource.UNKNOWN, importedBodyFatPercent = 21.0)))
        assertEquals(
            "18 % · estimación visual",
            bodyFatReviewValue(SetupWizardDraft(bodyFatSource = SetupBodyFatSource.VISUAL_ESTIMATE, bodyFatPercent = 18.0)),
        )
        assertEquals(
            "17,5 % · medido",
            bodyFatReviewValue(SetupWizardDraft(bodyFatSource = SetupBodyFatSource.MEASURED, bodyFatPercent = 17.5)),
        )
    }

    // ─── B-03: aviso de volumen alto y notas del plan ────────────────────────

    @Test
    fun highVolumeNoticeIsWrittenInPlainLanguage() {
        assertEquals(
            "Volumen alto en Glúteos: 17,5 series principales por semana (recomendado 16). Es un exceso pequeño y aceptable.",
            highVolumeReviewText(HighVolumeNotice("Glúteos", 17.5, 16, 17.5)),
        )
        assertEquals(
            "Volumen alto en Glúteos: 17 series principales por semana (recomendado 16). Es un exceso pequeño y aceptable.",
            highVolumeReviewText(HighVolumeNotice("Glúteos", 17.0, 16, 17.5)),
        )
    }

    @Test
    fun reviewNotesListTheWarningFirstAndThenThePlanNotes() {
        val notes = planReviewNotes(
            report(
                highVolume = listOf(HighVolumeNotice("Glúteos", 17.0, 16, 17.5)),
                planNotes = listOf(GLUTE_BRIDGE_ONCE_NOTE),
            ),
        )
        assertEquals(2, notes.size)
        assertTrue(notes[0].startsWith("Volumen alto en Glúteos"))
        assertEquals(GLUTE_BRIDGE_ONCE_NOTE, notes[1])
    }

    @Test
    fun noWarningWhenThePlanStaysWithinTheLimitOrHasNoReport() {
        assertTrue("sin exceso ni notas no hay nada que enseñar", planReviewNotes(report()).isEmpty())
        assertTrue("PHUL/PHAT no traen informe", planReviewNotes(null).isEmpty())
    }

    @Test
    fun planNotesNoLongerUseInternalJargon() {
        listOf(GLUTE_BRIDGE_ONCE_NOTE, ATHLETE_SIX_DAY_BRIDGE_NOTE).forEach { note ->
            listOf("MRV", "BL", "XH", "D6", "puente H", "flexión H", "SBD").forEach { jargon ->
                assertTrue("«$note» contiene jerga «$jargon»", !Regex("\\b${Regex.escape(jargon)}\\b").containsMatchIn(note))
            }
        }
        assertTrue(GLUTE_BRIDGE_ONCE_NOTE.startsWith("Hacemos el puente de glúteo 1 vez por semana para no pasar de 16 series de glúteo"))
    }

    // ─── C4: medición del barrido ────────────────────────────────────────────

    @Test
    fun sweepLogLineCarriesEveryMeasurementAndNoPersonalData() {
        val line = candidateSweepLogLine(
            totalMs = 9_500, catalogMs = 3_100, sweepMs = 6_400,
            catalogWasLoaded = false, firstSweep = true,
            published = 12, evaluated = 10, cacheHits = 2, passes = 1, viable = 7, useAdapted = false,
        )
        assertEquals(
            "barrido de candidatos: totalMs=9500 catalogMs=3100 sweepMs=6400 catalogoYaCargado=false " +
                "primerBarrido=true publicados=12 evaluados=10 aciertosCache=2 pases=1 viables=7 adaptado=false",
            line,
        )
    }
}
