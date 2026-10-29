package com.example.kpkn.screens.programs

import com.example.kpkn.screens.onboarding.ProgramReadyChoice
import org.junit.Assert.assertEquals
import org.junit.Test

/** Los textos y la opción por defecto de «Listo para empezar» (Registros v4, cr-listo, CR-F1). */
class ProgramReadyCopyTest {

    private val current = ProgramReadyCurrent(name = "Motor y fuerza", weekIndex = 4, weekCount = 6)

    @Test
    fun conUnoEnCursoPorDefectoVaEnColaYSinNingunoEmpiezaAhora() {
        assertEquals(ProgramReadyChoice.QUEUE, ProgramReadyCopy.defaultChoice(current))
        assertEquals(ProgramReadyChoice.START_NOW, ProgramReadyCopy.defaultChoice(null))
    }

    @Test
    fun elCuerpoNombraElActualYSuSemana() {
        assertEquals(
            "Ya tienes «Motor y fuerza» en curso (semana 4 de 6). ¿Qué hacemos con el nuevo?",
            ProgramReadyCopy.body(current),
        )
        assertEquals(
            "Ya tienes «Motor y fuerza» en pausa. ¿Qué hacemos con el nuevo?",
            ProgramReadyCopy.body(ProgramReadyCurrent(name = "Motor y fuerza", paused = true)),
        )
        assertEquals("No tienes ningún programa en curso. ¿Qué hacemos con el nuevo?", ProgramReadyCopy.body(null))
    }

    @Test
    fun lasTresOpcionesDicenLoDelLienzo() {
        assertEquals("Empezar ahora", ProgramReadyCopy.title(ProgramReadyChoice.START_NOW))
        assertEquals("Ponerlo en cola", ProgramReadyCopy.title(ProgramReadyChoice.QUEUE))
        assertEquals("Guardarlo sin activar", ProgramReadyCopy.title(ProgramReadyChoice.SAVE))
        assertEquals("Pausa «Motor y fuerza»; podrás reanudarlo", ProgramReadyCopy.subtitle(ProgramReadyChoice.START_NOW, current))
        assertEquals("Empieza cuando termine el actual", ProgramReadyCopy.subtitle(ProgramReadyChoice.QUEUE, current))
        assertEquals("Queda en «Tus programas»", ProgramReadyCopy.subtitle(ProgramReadyChoice.SAVE, current))
        assertEquals("Al confirmar: guardado en cola, sin tocar el actual.", ProgramReadyCopy.note(ProgramReadyChoice.QUEUE, current))
    }

    @Test
    fun lasFilasPlegadasDicenProgramaYSemana() {
        assertEquals("Fuerza y masa en bloques · 6 sem.", ProgramReadyCopy.programValue("Fuerza y masa en bloques", 6))
        assertEquals("Vacío", ProgramReadyCopy.programValue("Vacío", 0))
        // La semana va del día de inicio al anterior: miércoles a martes.
        assertEquals("5 días · ~60 min · Mié a Mar", ProgramReadyCopy.weekValue(5, 60, 3))
        assertEquals("1 día · Lun a Dom", ProgramReadyCopy.weekValue(1, null, 1))
    }
}
