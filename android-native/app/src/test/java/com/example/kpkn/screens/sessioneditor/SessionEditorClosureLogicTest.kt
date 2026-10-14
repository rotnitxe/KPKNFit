package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.sessioneditor.design.timelineSegmentWidths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cierre del editor de sesiones (SE8), lógica pura: la mini línea de cardio no baja de 4 dp por tramo y la lectura hablada de la dosis dice
 * lo mismo que la línea. Sin Robolectric ni arnés de Compose, para no heredar excepciones de otras pruebas del proceso.
 */
class SessionEditorClosureLogicTest {

    @Test
    fun timelineSegmentsNeverShrinkBelowFourDp() {
        // Un bloque largo con tramos de 30 s: los cortos se quedan en 4 dp y los largos reparten lo que sobra. La suma cabe en el ancho.
        val seconds = listOf(30, 1800, 30, 1800, 30)
        val widths = timelineSegmentWidths(seconds, availableDp = 320f, gapDp = 2f, minDp = 4f)
        assertTrue("ningún tramo baja de 4 dp: $widths", widths.all { it >= 4f - 0.001f })
        assertEquals(320f - 2f * 4f, widths.sum(), 0.01f)
        assertTrue(widths[1] > widths[0])
    }

    @Test
    fun spokenDoseReadsTheSameDataAsTheDoseLine() {
        val sets = listOf(
            SessionEditorSamples.workSet("a", 6, 8, 2),
            SessionEditorSamples.workSet("b", 6, 8, 2),
            SessionEditorSamples.workSet("c", 6, 8, 2),
        )
        assertEquals(
            "3 series de 6 a 8, RIR 2, descanso 2 minutos 30",
            sessionExerciseSpokenDose(sets, TrainingMode.REPS, restSeconds = 150),
        )
    }
}
