package com.example.kpkn.screens.programs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Textos de [TrainingMaxWizard] según la escena pr-tm del lienzo v6: el subtítulo dice «Escribe tu 1RM. El TM es el N % y con él se
 * calculan las cargas.» con el porcentaje de la receta (90 % o el 87 % de Texas Method y Madcow), y lo escrito en cada campo se lee
 * con coma o punto. La vista previa aparte y la nota de Texas/Madcow ya no existen: el TM va en la fila de cada levantamiento.
 */
class TrainingMaxWizardCopyTest {
    @Test
    fun theIntroSaysWhichPercentageOfTheOneRepMaxTheTrainingMaxIs() {
        assertEquals("Escribe tu 1RM. El TM es el 90 % y con él se calculan las cargas.", trainingMaxIntro(0.90))
        assertEquals("Escribe tu 1RM. El TM es el 87 % y con él se calculan las cargas.", trainingMaxIntro(0.87))
    }

    @Test
    fun aMarkIsReadWithACommaOrADotAndOnlyWhenItIsAPositiveNumber() {
        assertEquals(142.5, parseMark("142,5")!!, 0.0)
        assertEquals(142.5, parseMark("142.5")!!, 0.0)
        assertEquals(160.0, parseMark(" 160 ")!!, 0.0)
        assertNull(parseMark(""))
        assertNull(parseMark("abc"))
        assertNull(parseMark("0"))
        assertNull(parseMark("-5"))
    }
}
