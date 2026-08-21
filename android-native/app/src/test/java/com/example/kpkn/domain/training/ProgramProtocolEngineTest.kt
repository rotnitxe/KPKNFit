package com.example.kpkn.domain.training

import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.ProtocolPublicationStatus
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramProtocolEngineTest {

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    /** The legacy index is intentionally hidden; compiler tests use a local KPKN recipe contract. */
    private fun native(protocol: Protocol): Protocol = protocol.copy(
        publicationStatus = ProtocolPublicationStatus.KPKN_NATIVE,
    )

    @Test
    fun applyProtocol_builds_sessions_parts_sets_for_both_surfaces() {
        val protocol = native(PROTOCOL_LIBRARY.first { it.id == "gzcl-base" })
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
        val protocol = native(PROTOCOL_LIBRARY.first { it.id == "531-base" }).let { p ->
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
        val protocol = native(PROTOCOL_LIBRARY.first())
        val a = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())
        val b = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())
        assertEquals(a.macrocycles, b.macrocycles)
        assertEquals(a.structureTemplateId, b.structureTemplateId)
    }

    @Test
    fun applyProtocol_uses_real_exerciseDbIds_from_catalog() {
        val protocol = native(PROTOCOL_LIBRARY.first { it.id == "531-base" })
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
        val protocol = native(PROTOCOL_LIBRARY.first { it.id == "gzcl-base" })
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
    fun enhanced_day_differentiation_uses_focus_specific_accessory_recipes() {
        val protocol = native(PROTOCOL_LIBRARY.first { it.id == "gzcl-base" })
        val applied = ProgramProtocolEngine.applyProtocol(
            program = Program(id = "p", name = "A"),
            protocol = protocol,
            idProvider = SeqIds(),
            enhancedDayDifferentiation = true,
        )
        val sessions = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions
        fun exerciseCount(session: com.example.kpkn.data.models.Session): Int =
            session.parts.sumOf { part -> part.exercises.size }

        assertEquals(4, exerciseCount(sessions.first { it.name == "Torso" }))
        assertEquals(5, exerciseCount(sessions.first { it.name == "Pierna" }))
        assertTrue(sessions.first { it.name == "Torso" }.parts.any { it.exercises.size == 2 })
        assertTrue(sessions.first { it.name == "Pierna" }.parts.any { it.exercises.size == 3 })
    }

    @Test
    fun five_three_one_uses_real_main_lift_reps_by_cycle_week() {
        val protocol = native(PROTOCOL_LIBRARY.first { it.id == "531-base" })
        val applied = ProgramProtocolEngine.applyProtocol(
            program = Program(id = "p", name = "A"),
            protocol = protocol,
            idProvider = SeqIds(),
        )
        val reps = applied.macrocycles.first().blocks.map { block ->
            block.mesocycles.first().weeks.first().sessions.first()
                .parts.first().exercises.first().sets.first().targetReps
        }
        assertEquals(listOf(5, 3, 1, 5), reps)
    }

    @Test
    fun split_aliases_resolve_and_unknown_ids_fail_loudly() {
        assertEquals("ul_x4", ProgramProtocolEngine.resolveSplitId("UL"))
        try {
            ProgramProtocolEngine.resolveSplitId("split-no-existe")
            error("Se esperaba un error para un split desconocido")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("no existe"))
        }
    }

    @Test
    fun applyProtocol_resolves_defaultSplit_to_a_real_split_template() {
        PROTOCOL_LIBRARY.filter { it.defaultSplit != null }.map(::native).forEach { protocol ->
            val applied = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())
            assertNotNull("selectedSplitId debe resolverse para ${protocol.id}", applied.selectedSplitId)
            assertTrue(
                "selectedSplitId de ${protocol.id} debe existir en SPLIT_TEMPLATES",
                SPLIT_TEMPLATES.any { it.id == applied.selectedSplitId },
            )
        }
    }

    @Test
    fun applyProtocol_rejects_hidden_unverified_definitions() {
        val hidden = PROTOCOL_LIBRARY.first { it.publicationStatus == ProtocolPublicationStatus.HIDDEN_UNVERIFIED }
        try {
            ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), hidden, SeqIds())
            error("Se esperaba rechazo de protocolo oculto")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("no está publicado"))
        }
    }

    @Test
    fun nativeSbd_hasExplicitCompetitionRecipes_andStartDay() {
        val protocol = PROTOCOL_LIBRARY.single { it.id == "kpkn-native-sbd-4" }
        val applied = ProgramProtocolEngine.applyProtocol(
            Program(id = "p", name = "A", startDay = 3),
            protocol,
            SeqIds(),
        )
        val firstWeek = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        assertEquals(3, firstWeek.sessions.first().dayOfWeek)
        assertEquals(4, firstWeek.sessions.size)
        val mainIds = firstWeek.sessions.map { session ->
            session.parts.first().exercises.single().catalogConfigurationId
        }
        assertEquals(
            listOf(
                "low_bar_back_squat__barbell",
                "conventional_deadlift__bilateral__barbell",
                "bench_press__barbell",
                "low_bar_back_squat__barbell",
            ),
            mainIds,
        )
        firstWeek.sessions.flatMap { it.parts.first().exercises }.forEach { main ->
            assertTrue(main.isCompetitionLift)
            assertEquals(TrainingMode.RM, main.trainingMode)
            assertTrue((main.restTime ?: 0) >= 180)
            assertTrue(main.sets.all { it.targetPercentageRM != null })
            assertEquals(listOf(40.0, 60.0, 75.0), main.warmupSets.map { it.percentageOfWorkingWeight })
            assertEquals(listOf(5, 3, 1), main.warmupSets.map { it.targetReps })
        }
        val phases = applied.macrocycles.first().blocks.map { it.goal }
        assertEquals(
            listOf(
                com.example.kpkn.data.models.BlockGoal.ACCUMULATION,
                com.example.kpkn.data.models.BlockGoal.INTENSIFICATION,
                com.example.kpkn.data.models.BlockGoal.PEAK,
                com.example.kpkn.data.models.BlockGoal.TAPER,
            ),
            phases,
        )
        val baseSets = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.flatMap { it.parts }.flatMap { it.exercises }.sumOf { it.sets.size }
        val taperSets = applied.macrocycles.first().blocks.last().mesocycles.first().weeks.first()
            .sessions.flatMap { it.parts }.flatMap { it.exercises }.sumOf { it.sets.size }
        assertTrue("Taper debe reducir volumen", taperSets < baseSets)
        val peak = applied.macrocycles.first().blocks[2].mesocycles.first().weeks.first()
        val taper = applied.macrocycles.first().blocks.last().mesocycles.first().weeks.first()
        val peakMainPct = peak.sessions.flatMap { it.parts.first().exercises }
            .flatMap { it.sets }.mapNotNull { it.targetPercentageRM }.average()
        val taperMainPct = taper.sessions.flatMap { it.parts.first().exercises }
            .flatMap { it.sets }.mapNotNull { it.targetPercentageRM }.average()
        val peakAccessoryRpe = peak.sessions.flatMap { it.parts.drop(1) }
            .flatMap { it.exercises }.flatMap { it.sets }.mapNotNull { it.targetRPE }.average()
        val taperAccessoryRpe = taper.sessions.flatMap { it.parts.drop(1) }
            .flatMap { it.exercises }.flatMap { it.sets }.mapNotNull { it.targetRPE }.average()
        assertTrue("Taper debe reducir %RM respecto a Peak", taperMainPct < peakMainPct)
        assertTrue("Taper debe reducir RPE de accesorios", taperAccessoryRpe < peakAccessoryRpe)

        val anchoredMain = firstWeek.sessions.first().parts.first().exercises.single()
            .copy(reference1RM = 200.0)
        val anchoredSet = anchoredMain.sets.first()
        assertEquals(200.0 * (anchoredSet.targetPercentageRM ?: 0.0) / 100.0,
            calculateSuggestedLoad(anchoredMain, anchoredSet) ?: -1.0,
            0.0001)
    }

    @Test
    fun native_protocol_hydrates_recorded_program_goals_without_inventing_missing_rm() {
        val protocol = PROTOCOL_LIBRARY.single { it.id == "kpkn-native-sbd-4" }
        val applied = ProgramProtocolEngine.applyProtocol(
            Program(
                id = "goals",
                name = "SBD con referencias",
                goals = ProgramGoals(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            ),
            protocol,
            SeqIds(),
        )
        val main = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.flatMap { it.parts }.flatMap { it.exercises }
            .filter { it.isCompetitionLift }
        assertTrue(main.all { it.reference1RM != null && it.reference1RM in setOf(200.0, 120.0, 220.0) })
        val squat = main.first { it.catalogConfigurationId == "low_bar_back_squat__barbell" }
        assertEquals(200.0 * (squat.sets.first().targetPercentageRM ?: 0.0) / 100.0,
            calculateSuggestedLoad(squat, squat.sets.first()) ?: -1.0, 0.0001)

        val withoutGoals = ProgramProtocolEngine.applyProtocol(
            Program(id = "no-goals", name = "SBD sin referencias"), protocol, SeqIds(),
        )
        val unanchored = withoutGoals.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.flatMap { it.parts }.flatMap { it.exercises }
            .first { it.isCompetitionLift }
        assertNull(unanchored.reference1RM)
        assertNull(calculateSuggestedLoad(unanchored, unanchored.sets.first()))
    }

    @Test
    fun applyProtocol_respects_weekStart_and_keeps_rm_anchors_off_accessories() {
        val protocol = native(PROTOCOL_LIBRARY.first { it.id == "gzcl-base" })
        val applied = ProgramProtocolEngine.applyProtocol(
            Program(id = "p", name = "A", startDay = 5),
            protocol,
            SeqIds(),
        )
        val firstWeek = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        assertEquals(5, firstWeek.sessions.first().dayOfWeek)
        val accessory = firstWeek.sessions
            .flatMap { it.parts.drop(2) }
            .flatMap { it.exercises }
            .first()
        assertNull(accessory.sets.first().targetPercentageRM)
        assertEquals(TrainingMode.REPS, accessory.trainingMode)
        assertNotNull("El accesorio debe conservar RPE", accessory.sets.first().targetRPE)
    }
}
