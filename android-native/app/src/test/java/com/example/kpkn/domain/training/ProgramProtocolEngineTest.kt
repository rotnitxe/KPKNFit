package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramProtocolEngineTest {

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    @Test
    fun applyProtocol_builds_sessions_parts_sets_for_both_surfaces() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "gzcl-base" }
        val base = Program(id = "p", name = "Base", structure = ProgramStructure.SIMPLE)
        val applied = ProgramProtocolEngine.applyProtocol(base, protocol, SeqIds())

        assertEquals(ProgramStructure.COMPLEX, applied.structure)
        assertEquals(protocol.id, applied.structureTemplateId)
        assertEquals(protocol.blocks.size, applied.macrocycles.first().blocks.size)

        val firstWeek = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        assertTrue(firstWeek.sessions.isNotEmpty())
        assertTrue(firstWeek.sessions.all { it.parts.isNotEmpty() })
        assertTrue(firstWeek.sessions.all { session ->
            session.parts.any { part -> part.exercises.isNotEmpty() && part.exercises.any { it.sets.isNotEmpty() } }
        })
        assertTrue(firstWeek.sessions.any { it.isMainSession })
        assertTrue(firstWeek.sessions.first().parts.first().exercises.first().sets.any { it.targetPercentageRM != null })
    }

    @Test
    fun applyProtocol_single_block_stays_simple_cyclic() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "531-base" }.let { p ->
            // Force single block for structure contract
            p.copy(blocks = p.blocks.take(1))
        }
        val applied = ProgramProtocolEngine.applyProtocol(
            Program(id = "p", name = "Base"),
            protocol,
            SeqIds(),
        )
        assertEquals(ProgramStructure.SIMPLE, applied.structure)
        assertEquals(SimpleProgramKind.CYCLIC, applied.simpleProgramKind)
        assertTrue(applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions.isNotEmpty())
    }

    @Test
    fun applyProtocol_is_deterministic_for_same_id_provider_sequence() {
        val protocol = PROTOCOL_LIBRARY.first()
        val a = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())
        val b = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())
        assertEquals(a.macrocycles, b.macrocycles)
        assertEquals(a.structureTemplateId, b.structureTemplateId)
    }
}
