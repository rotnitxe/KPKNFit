package com.example.kpkn.screens.programs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Textos de [TrainingMaxWizard] (C.P12–P13): el TM se nombra «Máximo de entrenamiento (TM)», los
 * levantamientos van con su nombre completo (nada de SQ, BP o DL) y la nota de D7 sobre el 87 %
 * solo aparece en los planes cuyo TM es ese 87 % (Texas Method y Madcow).
 */
class TrainingMaxWizardCopyTest {
    @Test
    fun theIntroSaysWhichPercentageOfTheOneRepMaxTheTrainingMaxIs() {
        assertEquals("Introduce 1RM de competición. El TM se calcula al 90 % de tu 1RM.", trainingMaxIntro(0.90))
        assertEquals("Introduce 1RM de competición. El TM se calcula al 87 % de tu 1RM.", trainingMaxIntro(0.87))
    }

    @Test
    fun theFiveRepNoteAppearsOnlyWhenTheTrainingMaxIsEightySevenPercent() {
        assertEquals(
            "Texas Method y Madcow usan el 87 % de tu 1RM como TM (parecido a un 5RM).",
            trainingMaxFiveRepNote(0.87),
        )
        assertNull("el 90 % no es de Texas ni de Madcow", trainingMaxFiveRepNote(0.90))
        assertNull("el 100 % tampoco", trainingMaxFiveRepNote(1.0))
    }

    @Test
    fun thePreviewNamesTheLiftsInFullAndShowsOnlyTheLiftsWithAValue() {
        assertEquals(
            listOf("Sentadilla · TM 85 kg", "Press de banca · TM 60 kg", "Peso muerto · TM 100 kg"),
            trainingMaxPreviewRows(squatTM = 85.0, benchTM = 60.0, deadliftTM = 100.0, unit = "kg"),
        )
        // Sin marca no hay fila: ni «—» huérfanos ni una línea vacía cuando no hay ningún TM.
        assertEquals(listOf("Peso muerto · TM 100 kg"), trainingMaxPreviewRows(null, null, 100.0, "kg"))
        assertTrue(trainingMaxPreviewRows(null, null, null, "kg").isEmpty())
        val all = trainingMaxPreviewRows(85.0, 60.0, 100.0, "kg").joinToString(" ")
        listOf("SQ", "BP", "DL").forEach { abbreviation ->
            assertFalse("«$all» conserva la sigla $abbreviation", all.contains(abbreviation))
        }
    }

    @Test fun thePreviewIncludesOverheadWithoutRequiringCompetitionLifts() {
        assertEquals(listOf("Press militar · TM 72 kg"),
            trainingMaxPreviewRows(null, null, null, "kg", overheadTM = 72.0))
        assertEquals(listOf("Sentadilla · TM 180 kg", "Press de banca · TM 108 kg",
            "Peso muerto · TM 198 kg", "Press militar · TM 72 kg"),
            trainingMaxPreviewRows(180.0, 108.0, 198.0, "kg", overheadTM = 72.0))
    }

}
