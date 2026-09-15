package com.example.kpkn.domain.training

import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProtocolPublicationStatus
import com.example.kpkn.data.protocols.isVisibleForApplication
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

    companion object {
        @org.junit.BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    @Test
    fun applyProtocol_builds_sessions_parts_sets_for_both_surfaces() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "gzclp" }
        val base = Program(id = "p", name = "Base", structure = ProgramStructure.SIMPLE)
        val applied = ProgramProtocolEngine.applyProtocol(base, protocol, SeqIds())

        assertEquals(ProgramStructure.SIMPLE, applied.structure)
        assertEquals(protocol.recipe!!.id, applied.structureTemplateId)
        assertEquals(1, applied.macrocycles.first().blocks.size)
        assertEquals(SimpleProgramKind.CYCLIC, applied.simpleProgramKind)

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
    fun applyProtocol_single_block_finite_stays_simple_linear() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "smolov-jr" }
        val applied = ProgramProtocolEngine.applyProtocol(
            Program(id = "p", name = "Base"),
            protocol,
            SeqIds(),
        )
        assertEquals(ProgramStructure.SIMPLE, applied.structure)
        assertEquals(SimpleProgramKind.LINEAR, applied.simpleProgramKind)
        assertTrue(applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions.isNotEmpty())
    }

    @Test
    fun applyProtocol_is_deterministic_for_same_id_provider_sequence() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "kpkn-native-sbd-4" }
        val a = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())
        val b = ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "A"), protocol, SeqIds())
        assertEquals(a.macrocycles, b.macrocycles)
        assertEquals(a.structureTemplateId, b.structureTemplateId)
    }

    @Test
    fun applyProtocol_uses_real_exerciseDbIds_from_catalog() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "wendler-531-bbb" }
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
        val protocol = PROTOCOL_LIBRARY.first { it.id == "kpkn-native-sbd-4" }
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
        val protocol = PROTOCOL_LIBRARY.first { it.id == "kpkn-native-sbd-4" }
        val applied = ProgramProtocolEngine.applyProtocol(
            program = Program(id = "p", name = "A"),
            protocol = protocol,
            idProvider = SeqIds(),
            enhancedDayDifferentiation = true,
        )
        val sessions = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions
        val mains = sessions.map { it.exercises.first().catalogConfigurationId }
        assertTrue(mains.contains("low_bar_back_squat__barbell"))
        assertTrue(mains.contains("conventional_deadlift__bilateral__barbell"))
        assertTrue(mains.contains("bench_press__barbell"))
        assertTrue(sessions.map { it.exercises.size }.distinct().size >= 2)
    }

    @Test
    fun five_three_one_uses_real_main_lift_reps_by_cycle_week() {
        val protocol = PROTOCOL_LIBRARY.first { it.id == "wendler-531-bbb" }
        val applied = ProgramProtocolEngine.applyProtocol(
            program = Program(id = "p", name = "A"),
            protocol = protocol,
            idProvider = SeqIds(),
        )
        val weeks = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.take(4)
        val reps = weeks.map { week ->
            week.sessions.first().parts.first().exercises.first().sets.last().targetReps
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
        PROTOCOL_LIBRARY.filter { it.isVisibleForApplication && it.defaultSplit != null }.forEach { protocol ->
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
            session.exercises.first().catalogConfigurationId
        }
        assertEquals("low_bar_back_squat__barbell", mainIds[0])
        assertEquals("conventional_deadlift__bilateral__barbell", mainIds[1])
        assertEquals("bench_press__barbell", mainIds[2])
        assertEquals("bench_press__barbell", mainIds[3])
        firstWeek.sessions.forEach { session ->
            val main = session.exercises.first()
            assertTrue((main.restTime ?: 0) >= 180)
            assertTrue(main.sets.any { it.targetPercentageRM != null })
        }
        val squat = firstWeek.sessions.first().exercises.first()
        assertTrue(squat.isCompetitionLift)
        assertEquals(listOf(40.0, 55.0, 65.0), squat.warmupSets.map { it.percentageOfWorkingWeight })
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
            .sessions.flatMap { it.exercises }.sumOf { it.sets.size }
        val taperSets = applied.macrocycles.first().blocks.last().mesocycles.first().weeks.first()
            .sessions.flatMap { it.exercises }.sumOf { it.sets.size }
        assertTrue("Taper debe reducir volumen", taperSets <= baseSets)
        val peak = applied.macrocycles.first().blocks[2].mesocycles.first().weeks.first()
        val taper = applied.macrocycles.first().blocks.last().mesocycles.first().weeks.first()
        val peakMainPct = peak.sessions.flatMap { it.exercises.take(1) }
            .flatMap { it.sets }.mapNotNull { it.targetPercentageRM }.average()
        val taperMainPct = taper.sessions.flatMap { it.exercises.take(1) }
            .flatMap { it.sets }.mapNotNull { it.targetPercentageRM }.average()
        assertTrue("Taper debe reducir %RM respecto a Peak", taperMainPct < peakMainPct)

        val anchoredMain = firstWeek.sessions.first().exercises.first()
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
            .sessions.flatMap { it.exercises }
            .filter { it.isCompetitionLift }
        assertTrue(main.all { it.reference1RM != null && it.reference1RM in setOf(180.0, 108.0, 198.0) })
        val squat = main.first { it.catalogConfigurationId == "low_bar_back_squat__barbell" }
        assertEquals(180.0 * (squat.sets.first().targetPercentageRM ?: 0.0) / 100.0,
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
        val protocol = PROTOCOL_LIBRARY.first { it.id == "kpkn-native-sbd-4" }
        val applied = ProgramProtocolEngine.applyProtocol(
            Program(id = "p", name = "A", startDay = 5),
            protocol,
            SeqIds(),
        )
        val firstWeek = applied.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        assertEquals(5, firstWeek.sessions.first().dayOfWeek)
        val accessory = firstWeek.sessions
            .flatMap { it.parts }
            .flatMap { it.exercises }
            .first { !it.isCompetitionLift && it.sets.none { set -> set.targetPercentageRM != null } }
        assertNull(accessory.sets.first().targetPercentageRM)
        assertEquals(TrainingMode.REPS, accessory.trainingMode)
        assertNotNull("El accesorio debe conservar RPE", accessory.sets.first().targetRPE)
    }
}
