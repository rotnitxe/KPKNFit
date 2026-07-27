package com.example.kpkn.domain.training

import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun applyProtocol_uses_real_exerciseDbIds_from_catalog() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "531-base" }
        val applied = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())

        val allExercises = applied.macrocycles.flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
            .flatMap { it.parts }
            .flatMap { it.exercises }

        assertTrue(allExercises.isNotEmpty())
        assertTrue(allExercises.all { it.exerciseDbId != null })
    }

    @Test
    fun applyProtocol_scales_volume_and_intensity_by_block_goal() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "gzcl-base" }
        val applied = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())

        val blocks = applied.macrocycles.first().blocks
        val accumulationBlock = blocks.first { it.mesocycles.first().goal == MesocycleGoal.ACCUMULATION }
        val deloadBlock = blocks.first { it.mesocycles.first().goal == MesocycleGoal.DELOAD }

        fun totalSetsInFirstWeek(block: com.example.kpkn.data.models.Block) =
            block.mesocycles.first().weeks.first().sessions
                .flatMap { it.parts }
                .flatMap { it.exercises }
                .sumOf { it.sets.size }

        val accumulationSets = totalSetsInFirstWeek(accumulationBlock)
        val deloadSets = totalSetsInFirstWeek(deloadBlock)
        assertNotEquals(accumulationSets, deloadSets)
        assertTrue(accumulationSets > deloadSets)

        // La intensidad (%1RM) también debe ondular dentro de un mismo bloque multi-semana.
        val firstWeekPct = accumulationBlock.mesocycles.first().weeks.first().sessions
            .flatMap { it.parts }.flatMap { it.exercises }.flatMap { it.sets }
            .mapNotNull { it.targetPercentageRM }.average()
        val lastWeekPct = accumulationBlock.mesocycles.first().weeks.last().sessions
            .flatMap { it.parts }.flatMap { it.exercises }.flatMap { it.sets }
            .mapNotNull { it.targetPercentageRM }.average()
        assertNotEquals(firstWeekPct, lastWeekPct, 0.0001)
    }

    @Test
    fun applyProtocol_resolves_defaultSplit_to_a_real_split_template() {
        PROTOCOL_LIBRARY.filter { it.defaultSplit != null }.forEach { protocol ->
            val applied = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())
            assertNotNull("selectedSplitId debe resolverse para ${protocol.id}", applied.selectedSplitId)
            assertTrue(
                "selectedSplitId de ${protocol.id} debe existir en SPLIT_TEMPLATES",
                SPLIT_TEMPLATES.any { it.id == applied.selectedSplitId },
            )
        }
    }
}
