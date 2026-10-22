package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.SimpleProgramKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramScopeTest {

    private fun program(
        structure: ProgramStructure,
        kind: SimpleProgramKind,
        blocks: Int,
        weeksPerBlock: Int,
    ): Program = Program(
        id = "p",
        name = "P",
        structure = structure,
        simpleProgramKind = kind,
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = (1..blocks).map { b ->
                    Block(
                        id = "block$b",
                        name = "Bloque $b",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso$b",
                                name = "Meso $b",
                                weeks = (1..weeksPerBlock).map { w ->
                                    ProgramWeek(id = "b${b}w$w", name = "Semana $w")
                                },
                            ),
                        ),
                    )
                },
            ),
        ),
    )

    @Test
    fun `simple cyclic one week is one repeating week`() {
        val program = program(ProgramStructure.SIMPLE, SimpleProgramKind.CYCLIC, blocks = 1, weeksPerBlock = 1)
        assertEquals(ProgramScope.RepeatingWeeks(1), ProgramScopes.of(program))
        assertEquals("Una semana que se repite", ProgramScopes.label(ProgramScopes.of(program)))
    }

    @Test
    fun `simple cyclic two weeks are two repeating weeks`() {
        val program = program(ProgramStructure.SIMPLE, SimpleProgramKind.CYCLIC, blocks = 1, weeksPerBlock = 2)
        assertEquals(ProgramScope.RepeatingWeeks(2), ProgramScopes.of(program))
        assertEquals("2 semanas que se repiten", ProgramScopes.label(ProgramScopes.of(program)))
    }

    @Test
    fun `simple linear four weeks are fixed weeks`() {
        val program = program(ProgramStructure.SIMPLE, SimpleProgramKind.LINEAR, blocks = 1, weeksPerBlock = 4)
        assertEquals(ProgramScope.FixedWeeks(4), ProgramScopes.of(program))
        assertEquals("4 semanas", ProgramScopes.label(ProgramScopes.of(program)))
    }

    @Test
    fun `complex three blocks of four weeks`() {
        val program = program(ProgramStructure.COMPLEX, SimpleProgramKind.LINEAR, blocks = 3, weeksPerBlock = 4)
        assertEquals(ProgramScope.Blocks(3, 12), ProgramScopes.of(program))
        assertEquals("Por bloques · 3 bloques · 12 semanas", ProgramScopes.label(ProgramScopes.of(program)))
    }

    @Test
    fun `complex one block of six weeks is fixed weeks`() {
        val program = program(ProgramStructure.COMPLEX, SimpleProgramKind.LINEAR, blocks = 1, weeksPerBlock = 6)
        assertEquals(ProgramScope.FixedWeeks(6), ProgramScopes.of(program))
        assertEquals("6 semanas", ProgramScopes.label(ProgramScopes.of(program)))
    }
}
