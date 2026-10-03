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
    fun thePreviewNamesTheLiftsInFullAndUsesADashWhereThereIsNoMarkYet() {
        assertEquals(
            "TM: sentadilla 85 · press de banca 60 · peso muerto 100",
            trainingMaxPreviewLine(squatTM = 85.5, benchTM = 60.2, deadliftTM = 100.0),
        )
        val empty = trainingMaxPreviewLine(squatTM = null, benchTM = null, deadliftTM = null)
        assertEquals("TM: sentadilla — · press de banca — · peso muerto —", empty)
        listOf("SQ", "BP", "DL").forEach { abbreviation ->
            assertFalse("«$empty» conserva la sigla $abbreviation", empty.contains(abbreviation))
        }
        assertTrue(trainingMaxPreviewLine(1.0, null, null).startsWith("TM: sentadilla 1 "))
    }
}
