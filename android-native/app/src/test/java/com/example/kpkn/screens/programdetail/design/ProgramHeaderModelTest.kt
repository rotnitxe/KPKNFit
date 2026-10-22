package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lo que dice la cabecera de Program Detail: la meta, la acción principal, el progreso y el estado (P1). */
class ProgramHeaderModelTest {

    @Test
    fun metaSaysWeeksDaysAndModeInSpanish() {
        // La cifra va pegada a su unidad con un espacio sin corte: nunca queda sola al final de línea.
        assertEquals("12 semanas · 4 días por semana · Fuerza", programMetaLine(12, false, 4, ProgramMode.POWERLIFTING))
    }

    @Test
    fun metaOmitsWhatIsNotKnownInsteadOfInventingIt() {
        assertEquals("12 semanas · 4 días por semana", programMetaLine(12, false, 4, null))
        assertEquals("Semana que se repite · Músculo", programMetaLine(1, true, null, ProgramMode.HYPERTROPHY))
    }

    @Test
    fun primaryActionIsStartWhenTodayHasASession() {
        val primary = programHeaderPrimaryOf(isActive = true, sessionName = "Torso A", sessionOngoing = false)
        assertEquals(ProgramHeaderPrimary.Start("Torso A"), primary)
        assertEquals("Empezar Torso A", programHeaderPrimaryLabel(primary))
    }

    @Test
    fun primaryActionIsContinueWhenTheSessionIsOngoing() {
        val primary = programHeaderPrimaryOf(isActive = true, sessionName = "Torso A", sessionOngoing = true)
        assertEquals(ProgramHeaderPrimary.Continue, primary)
        assertEquals("Continuar sesión", programHeaderPrimaryLabel(primary))
    }

    @Test
    fun primaryActionIsActivateWhenTheProgramIsNotActive() {
        val primary = programHeaderPrimaryOf(isActive = false, sessionName = "Torso A", sessionOngoing = false)
        assertEquals(ProgramHeaderPrimary.Activate, primary)
        assertEquals("Activar programa", programHeaderPrimaryLabel(primary))
    }

    @Test
    fun activeProgramWithNothingToDoTodayHasNoPrimaryAction() {
        val primary = programHeaderPrimaryOf(isActive = true, sessionName = null, sessionOngoing = false)
        assertEquals(ProgramHeaderPrimary.None, primary)
        assertNull(programHeaderPrimaryLabel(primary))
    }

    @Test
    fun progressSaysTheWeekAndThePhaseOrTheRepeatingCycle() {
        assertEquals(
            "Semana 3 de 12 · Acumulación",
            programProgressLabel(weekIndex = 3, weekCount = 12, phase = "Fase de acumulación", repeatingWeek = false, cycle = null),
        )
        assertEquals(
            "Semana que se repite · ciclo 5",
            programProgressLabel(weekIndex = 1, weekCount = 1, phase = null, repeatingWeek = true, cycle = 5),
        )
        assertEquals(
            "Semana 2 de 8",
            programProgressLabel(weekIndex = 2, weekCount = 8, phase = "Fase personalizada", repeatingWeek = false, cycle = null),
        )
        assertNull(programProgressLabel(weekIndex = null, weekCount = null, phase = null, repeatingWeek = false, cycle = null))
    }

    @Test
    fun statusFollowsActiveAndPaused() {
        assertEquals(ProgramHeaderStatus.ACTIVE, programHeaderStatusOf(isActive = true, isPaused = false))
        assertEquals(ProgramHeaderStatus.PAUSED, programHeaderStatusOf(isActive = false, isPaused = true))
        assertEquals(ProgramHeaderStatus.DRAFT, programHeaderStatusOf(isActive = false, isPaused = false))
    }

    @Test
    fun scopeLineNamesTheBlocksOfAComplexProgram() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = (1..3).map { b ->
                        Block(
                            id = "block$b",
                            name = "Bloque $b",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso$b",
                                    name = "Meso $b",
                                    weeks = (1..4).map { w -> ProgramWeek(id = "b${b}w$w", name = "Semana $w") },
                                ),
                            ),
                        )
                    },
                ),
            ),
        )
        assertEquals("Por bloques · 3 bloques · 12 semanas", programScopeLine(program))
    }
}
