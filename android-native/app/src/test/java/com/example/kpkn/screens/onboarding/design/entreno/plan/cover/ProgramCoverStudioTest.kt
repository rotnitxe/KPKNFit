package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import com.example.kpkn.data.models.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La portada de estudio de un programa («Editar programa»): su esquema, su identidad y el respaldo de la de siempre. */
class ProgramCoverStudioTest {

    private val program = Program(id = "generated:strength-muscle", name = "Fuerza de base")

    @Test
    fun coverInfoCarriesTheStyleAndThePaletteOfTheStudio() {
        assertEquals("kpkn-studio://PLATES/NOCHE", programStudioCoverValue(CoverStudioStyle.PLATES, CoverPaletteKind.NOCHE))
        assertEquals(
            ProgramStudioChoice(CoverStudioStyle.GRID, CoverPaletteKind.PAPEL),
            parseProgramStudioCover("kpkn-studio://GRID/PAPEL"),
        )
        assertNull(parseProgramStudioCover("kpkn-studio://GRID/ROSA"))
        assertNull(parseProgramStudioCover("gradient://ember"))
    }

    @Test
    fun coverIdentityForProgramTakesStyleAndPaletteButKeepsAccentsAndSeed() {
        val base = coverIdentityFor(program.id, null)
        val identity = coverIdentityForProgram(program.copy(coverImage = "kpkn-studio://GRID/PAPEL"))
        assertEquals(CoverStudioStyle.GRID, identity.style)
        assertEquals(CoverPaletteKind.PAPEL, identity.palette)
        assertEquals(base.primary, identity.primary)
        assertEquals(base.secondary, identity.secondary)
        assertEquals(base.seed, identity.seed)
    }

    @Test
    fun keepingTheStyleKeepsItsMotifAsAMethodNumeral() {
        // «531» es un numeral de método: si el estilo no cambia, el dato de la base se conserva.
        val base = coverIdentityFor("protocol:wendler-531-bbb", null)
        val identity = coverIdentityForProgram(
            Program(id = "protocol:wendler-531-bbb", name = "531", coverImage = "kpkn-studio://NUMERAL/NOCHE"),
        )
        assertEquals(base.motif, identity.motif)
        assertEquals(base.numeralIsWeeks, identity.numeralIsWeeks)
        assertEquals(CoverPaletteKind.NOCHE, identity.palette)
    }

    @Test
    fun aMissingOrInvalidStudioValueGivesTheIdentityOfToday() {
        val base = coverIdentityFor(program.id, null)
        assertEquals(base, coverIdentityForProgram(program))
        assertEquals(base, coverIdentityForProgram(program.copy(coverImage = "kpkn-studio://FOO/BAR")))
        assertEquals(base, coverIdentityForProgram(program.copy(coverImage = "gradient://lagoon")))
    }
}
