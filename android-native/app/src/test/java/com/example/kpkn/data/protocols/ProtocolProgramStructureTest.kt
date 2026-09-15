package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.ProgramProtocolEngine
import com.example.kpkn.domain.training.ProgramProgressEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ProtocolProgramStructureTest {
    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    companion object {
        private val cyclicMethodIds = setOf(
            "wendler-531-bbb",
            "wendler-531-fsl",
            "texas-method-3d",
            "texas-method-4d",
            "phul-verified",
            "phat-verified",
            "nsuns-531-lp-4d",
            "madcow-5x5",
            "gzclp",
            "westside-conjugate",
        )
        private val finiteSimpleIds = setOf(
            "coan-phillipi-dl",
            "smolov-jr",
            "cube-method",
        )
        private val oneWeekBlockWhitelist = setOf(
            "smolov" to "Taper",
            "kpkn-native-sbd-4" to "Taper",
            "kpkn-rp-style" to "Descarga",
        )

        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun visible() = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }

    private fun apply(id: String): Program =
        ProgramProtocolEngine.applyProtocol(Program(id = "p", name = "T"), visible().first { it.id == id }, SeqIds())

    @Test
    fun visible_protocols_match_simple_vs_complex_and_block_weeks() {
        visible().forEach { protocol ->
            val recipe = protocol.recipe!!
            val blockIndexes = recipe.weeks.map { it.blockIndex }.distinct().sorted()
            assertEquals("${protocol.id} ProtocolBlock vs receta", protocol.blocks.size, blockIndexes.size)
            assertEquals(
                "${protocol.id} semanas declaradas vs receta",
                protocol.blocks.sumOf { it.weeks },
                recipe.weeks.size,
            )
            protocol.blocks.forEachIndexed { index, block ->
                val weeksInBlock = recipe.weeks.count { it.blockIndex == blockIndexes[index] }
                assertEquals("${protocol.id} ${block.name} semanas", block.weeks, weeksInBlock)
                val allowOneWeek = oneWeekBlockWhitelist.any { it.first == protocol.id && it.second == block.name }
                if (weeksInBlock == 1) {
                    assertTrue("${protocol.id} bloque de 1 semana '${block.name}' no está en whitelist de taper/deload de autor", allowOneWeek)
                }
            }
            val applied = apply(protocol.id)
            if (blockIndexes.size > 1) {
                assertEquals("${protocol.id} debe ser COMPLEX", ProgramStructure.COMPLEX, applied.structure)
                assertTrue("${protocol.id} kind cíclico no debe forzar SIMPLE", !recipe.repeats)
            } else {
                assertEquals("${protocol.id} debe ser SIMPLE", ProgramStructure.SIMPLE, applied.structure)
                assertTrue(
                    "${protocol.id} de un bloque debe clasificarse como método cíclico o especialización finita",
                    protocol.id in cyclicMethodIds || protocol.id in finiteSimpleIds,
                )
                if (protocol.id in cyclicMethodIds) {
                    assertTrue("${protocol.id} debe repetir", recipe.repeats)
                    assertEquals(SimpleProgramKind.CYCLIC, applied.simpleProgramKind)
                    assertTrue(
                        "${protocol.id} kind honesto",
                        protocol.kind == ProtocolKind.METHOD || protocol.kind == ProtocolKind.WEEKLY_SPLIT,
                    )
                }
                if (protocol.id in finiteSimpleIds) {
                    assertTrue("${protocol.id} no debe ciclar", !recipe.repeats)
                    assertEquals(SimpleProgramKind.LINEAR, applied.simpleProgramKind)
                }
            }
        }
    }

    @Test
    fun wendler_is_simple_four_weeks_one_block_and_wraps() {
        val applied = apply("wendler-531-bbb")
        assertEquals(ProgramStructure.SIMPLE, applied.structure)
        assertEquals(SimpleProgramKind.CYCLIC, applied.simpleProgramKind)
        val weeks = applied.macrocycles.first().blocks.single().mesocycles.single().weeks
        assertEquals(4, weeks.size)
        assertEquals(listOf("5s", "3s", "1s", "Descarga"), weeks.map { it.name })
        val logs = weeks.flatMap { week ->
            week.sessions.map { session ->
                WorkoutLog(
                    id = "log_${session.id}",
                    programId = applied.id,
                    sessionId = session.id,
                    sessionName = session.name,
                    date = "2026-01-01T10:00:00.000Z",
                    durationMinutes = 45,
                    weekId = week.id,
                    cycleNumber = 1,
                    weekInstanceId = ProgramProgressEngine.instanceIdFor(1, week.id),
                )
            }
        }
        val withRun = applied.copy(
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_531",
                cycleNumber = 1,
                weekId = weeks.last().id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, weeks.last().id),
            ),
        )
        val result = ProgramProgressEngine.completeCycle(withRun, null, 1, logs)
        assertTrue(result.advancedCycle)
        assertEquals(2, result.program.runState?.cycleNumber)
        assertEquals(ProgramProgressEngine.instanceIdFor(2, weeks.first().id), result.program.runState?.weekInstanceId)
    }

    @Test
    fun phul_keeps_power_and_hypertrophy_in_the_same_week() {
        val protocol = visible().first { it.id == "phul-verified" }
        assertEquals(1, protocol.recipe!!.weeks.map { it.blockIndex }.distinct().size)
        protocol.recipe!!.weeks.forEach { week ->
            val labels = week.days.map { it.label }
            assertTrue("${week.weekNumber} power+hyp: $labels", labels.any { it.contains("Power") } && labels.any { it.contains("Hypertrophy") })
            assertEquals(4, week.days.size)
        }
        val applied = apply("phul-verified")
        assertEquals(ProgramStructure.SIMPLE, applied.structure)
        assertEquals(SimpleProgramKind.CYCLIC, applied.simpleProgramKind)
    }

    @Test
    fun texas_is_simple_with_a_single_block_index() {
        listOf("texas-method-3d", "texas-method-4d").forEach { id ->
            val protocol = visible().first { it.id == id }
            assertEquals(setOf(0), protocol.recipe!!.weeks.map { it.blockIndex }.toSet())
            val applied = apply(id)
            assertEquals(ProgramStructure.SIMPLE, applied.structure)
            assertEquals(1, applied.macrocycles.first().blocks.size)
        }
    }

    @Test
    fun juggernaut_is_complex_four_by_four_and_completes() {
        val protocol = visible().first { it.id == "juggernaut-2" }
        val byBlock = protocol.recipe!!.weeks.groupBy { it.blockIndex }
        assertEquals(4, byBlock.size)
        assertTrue(byBlock.values.all { it.size == 4 })
        val applied = apply("juggernaut-2")
        assertEquals(ProgramStructure.COMPLEX, applied.structure)
        val blocks = applied.macrocycles.first().blocks
        assertEquals(4, blocks.size)
        assertTrue(blocks.all { it.mesocycles.single().weeks.size == 4 })
        assertTrue(!applied.sourceRecipe!!.repeats)
    }

    @Test
    fun coan_and_smolov_jr_are_simple_linear_and_complete() {
        listOf("coan-phillipi-dl", "smolov-jr").forEach { id ->
            val applied = apply(id)
            assertEquals(id, ProgramStructure.SIMPLE, applied.structure)
            assertEquals(id, SimpleProgramKind.LINEAR, applied.simpleProgramKind)
            val weeks = applied.macrocycles.first().blocks.single().mesocycles.single().weeks
            val logs = weeks.flatMap { week ->
                week.sessions.map { session ->
                    WorkoutLog(
                        id = "log_${id}_${session.id}",
                        programId = applied.id,
                        sessionId = session.id,
                        sessionName = session.name,
                        date = "2026-01-01T10:00:00.000Z",
                        durationMinutes = 45,
                        weekId = week.id,
                    )
                }
            }
            val lastWeek = weeks.last()
            val result = ProgramProgressEngine.advanceAfterSessionComplete(
                program = applied.copy(
                    runState = com.example.kpkn.data.models.ProgramRunState(
                        runId = "run_$id",
                        weekId = lastWeek.id,
                        weekInstanceId = lastWeek.id,
                    ),
                ),
                activeState = com.example.kpkn.data.models.ActiveProgramState(
                    programId = applied.id,
                    status = ProgramStatus.ACTIVE,
                ),
                completedSession = lastWeek.sessions.first(),
                weekInstanceId = lastWeek.id,
                logs = logs,
            )
            assertEquals(id, ProgramRunStatus.COMPLETED, result.program.runState?.status)
            assertTrue(id, result.program.runState?.cycleNumber != 2)
        }
    }

    @Test
    fun applyProtocol_does_not_leave_complex_on_week_only_methods() {
        cyclicMethodIds.forEach { id ->
            val applied = apply(id)
            assertEquals(id, ProgramStructure.SIMPLE, applied.structure)
            assertEquals(id, 1, applied.macrocycles.first().blocks.size)
        }
    }

    @Test
    fun candito_groups_author_phases_not_one_week_blocks() {
        val protocol = visible().first { it.id == "candito-6" }
        val byBlock = protocol.recipe!!.weeks.groupBy { it.blockIndex }
        assertEquals(3, byBlock.size)
        assertTrue(byBlock.values.all { it.size == 2 })
        assertEquals(listOf("Hipertrofia", "Fuerza", "Pico-test"), protocol.blocks.map { it.name })
        val applied = apply("candito-6")
        assertEquals(ProgramStructure.COMPLEX, applied.structure)
        assertEquals(3, applied.macrocycles.first().blocks.size)
    }
}
