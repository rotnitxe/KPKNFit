package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverArtMode
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverArtModeOf
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

    @Test
    fun `opening a saved studio cover restores its visible style and palette`() {
        val saved = program.copy(coverImage = "kpkn-studio://WAVES/NOCHE")

        val draft = programEditDraftOf(saved)

        assertEquals(LineCoverStyle.ONDAS, draft.lineStyle)
        assertEquals(LineCoverBackground.NOCHE, draft.background)
        assertFalse(draft.isChangedFrom(saved))
        assertEquals(CoverStudioStyle.WAVES, coverIdentityForProgram(saved).style)
        assertEquals(CoverPaletteKind.NOCHE, coverIdentityForProgram(saved).palette)
    }

    @Test
    fun `editing a line cover keeps the chosen background through save and reopen`() {
        val saved = program.copy(coverImage = "kpkn-lines://PERFIL/CARBON")
        val edited = programEditDraftOf(saved).withStyle(LineCoverStyle.ONDAS).withBackground(LineCoverBackground.VIOLETA)

        assertEquals("kpkn-lines://ONDAS/VIOLETA", edited.cover)
        assertEquals(LineCoverStyle.ONDAS, coverIdentityForProgram(saved.copy(coverImage = edited.cover)).lines?.style)
        assertEquals(LineCoverBackground.VIOLETA, coverIdentityForProgram(saved.copy(coverImage = edited.cover)).lines?.background)
        assertEquals(LineCoverStyle.ONDAS, programEditDraftOf(saved.copy(coverImage = edited.cover)).lineStyle)
        assertEquals(LineCoverBackground.VIOLETA, programEditDraftOf(saved.copy(coverImage = edited.cover)).background)
    }

    @Test
    fun `a personal photo remains the visible cover until a cover choice is explicitly made`() {
        val photo = "content://media/external/images/media/42"
        val saved = program.copy(coverImage = photo)

        val draft = programEditDraftOf(saved)
        val identity = coverIdentityForProgram(saved)

        assertTrue(draft.hasPhoto)
        assertEquals(photo, draft.cover)
        assertEquals(CoverArtMode.PHOTO, coverArtModeOf(hasPhoto = draft.hasPhoto, identity = identity))
        assertFalse(draft.isChangedFrom(saved))
    }
}
