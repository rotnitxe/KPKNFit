package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La modalidad en el borrador de «Editar programa»: se copia del programa, cuenta como cambio y se ve en español. */
class ProgramEditDraftModeTest {

    private val program = Program(
        id = "generated:strength-muscle",
        name = "Fuerza de base",
        description = "Para empezar",
        mode = ProgramMode.POWERLIFTING,
    )

    @Test
    fun `the draft starts with the mode of the program`() {
        val draft = programEditDraftOf(program.copy(mode = ProgramMode.POWERBUILDING))
        assertEquals(ProgramMode.POWERBUILDING, draft.mode)
    }

    @Test
    fun `changing only the mode is a change that can be saved`() {
        val draft = programEditDraftOf(program).copy(mode = ProgramMode.HYPERTROPHY)
        assertTrue(draft.isChangedFrom(program))
        assertTrue(draft.canSave(program))
    }

    @Test
    fun `the same mode and nothing else changed is not a change`() {
        val draft = programEditDraftOf(program)
        assertFalse(draft.isChangedFrom(program))
        assertFalse(draft.canSave(program))
    }

    @Test
    fun `each mode has its Spanish label`() {
        assertEquals("Hipertrofia", programModeLabel(ProgramMode.HYPERTROPHY))
        assertEquals("Powerbuilding", programModeLabel(ProgramMode.POWERBUILDING))
        assertEquals("Powerlifting", programModeLabel(ProgramMode.POWERLIFTING))
    }
}
