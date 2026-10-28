package com.example.kpkn.screens.programdetail.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lo que dice la cabecera de Program Detail: la meta, la acción principal, el progreso, el estado y las filas del ⋯ (lienzo v6). */
class ProgramHeaderModelTest {

    @Test
    fun headlineSaysModeWeeksAndBlocksInSpanish() {
        // El modo va primero; la cifra va pegada a su unidad, y los bloques solo se cuentan si hay más de uno.
        assertEquals(
            "Fuerza · 12 semanas · 3 bloques",
            programHeadlineMeta(goalLabel = "Fuerza", weekCount = 12, blockCount = 3, isRoutine = false, daysPerWeek = 4),
        )
        assertEquals(
            "1 semana",
            programHeadlineMeta(goalLabel = null, weekCount = 1, blockCount = 1, isRoutine = false, daysPerWeek = null),
        )
    }

    @Test
    fun headlineOfARoutineSaysRutinaAndItsDays() {
        assertEquals(
            "Músculo · Rutina · 3 días",
            programHeadlineMeta(goalLabel = "Músculo", weekCount = 1, blockCount = 1, isRoutine = true, daysPerWeek = 3),
        )
        assertEquals("Rutina", programHeadlineMeta(goalLabel = null, weekCount = 1, blockCount = 1, isRoutine = true, daysPerWeek = null))
        // Lo que no se sabe se omite: nada de «0 semanas» ni de «—».
        assertEquals("", programHeadlineMeta(goalLabel = null, weekCount = 0, blockCount = 0, isRoutine = false, daysPerWeek = null))
    }

    @Test
    fun todayAlsoNamesTheOtherSessionAndItsMinutes() {
        assertEquals("Hoy también: Cardio y potencia · ≈ 32 min", programTodayAlsoLine("Cardio y potencia", 32))
    }

    @Test
    fun primaryActionIsStartWhenTodayHasASession() {
        val primary = programHeaderPrimaryOf(isActive = true, sessionName = "Torso A", sessionOngoing = false)
        assertEquals(ProgramHeaderPrimary.Start("Torso A"), primary)
        assertEquals("Empezar · Torso A", programHeaderPrimaryLabel(primary))
    }

    @Test
    fun primaryActionIsContinueWhenTheSessionIsOngoing() {
        val primary = programHeaderPrimaryOf(isActive = true, sessionName = "Torso A", sessionOngoing = true)
        assertEquals(ProgramHeaderPrimary.Continue("Torso A"), primary)
        assertEquals("Continuar · Torso A", programHeaderPrimaryLabel(primary))
    }

    @Test
    fun primaryActionIsResumeWhenTheProgramIsPaused() {
        val primary = programHeaderPrimaryOf(isActive = false, sessionName = "Torso A", sessionOngoing = false, isPaused = true)
        assertEquals(ProgramHeaderPrimary.Resume, primary)
        assertEquals("Reanudar", programHeaderPrimaryLabel(primary))
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
            "Semana 5",
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
    fun menuStatusLineSaysTheStateAndTheWeek() {
        assertEquals("Activo · semana 4 de 6", programMenuStatusLine(ProgramHeaderStatus.ACTIVE, weekIndex = 4, weekCount = 6))
        assertEquals("Activo · semana 7", programMenuStatusLine(ProgramHeaderStatus.ACTIVE, weekIndex = 7, weekCount = null))
        assertEquals("Pausado", programMenuStatusLine(ProgramHeaderStatus.PAUSED, weekIndex = null, weekCount = 6))
    }

    @Test
    fun pauseRowChangesWithTheState() {
        assertEquals(
            ProgramPauseRow("Pausar programa", "Se guarda en la semana 4"),
            programPauseRow(ProgramHeaderStatus.ACTIVE, weekIndex = 4),
        )
        assertEquals(
            ProgramPauseRow("Reanudar programa", "Sigue en la semana 4"),
            programPauseRow(ProgramHeaderStatus.PAUSED, weekIndex = 4),
        )
        assertEquals(
            ProgramPauseRow("Activar programa", "Pasa a ser tu programa activo"),
            programPauseRow(ProgramHeaderStatus.DRAFT, weekIndex = null),
        )
        assertEquals(ProgramPauseRow("Pausar programa", null), programPauseRow(ProgramHeaderStatus.ACTIVE, weekIndex = null))
    }
}
