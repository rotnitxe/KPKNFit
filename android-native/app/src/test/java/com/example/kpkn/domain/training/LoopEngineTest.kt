package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class LoopEngineTest {

    private fun makeProgram(
        loopState: LoopState = LoopState(),
        loops: List<Loop> = emptyList(),
        events: List<ProgramEvent> = emptyList(),
    ): Program = Program(
        id = "test",
        name = "Test Program",
        macrocycles = listOf(
            Macrocycle(
                id = "mc1",
                name = "Macro 1",
                blocks = listOf(
                    Block(
                        id = "b1",
                        name = "Block 1",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "m1",
                                name = "Meso 1",
                                weeks = listOf(
                                    ProgramWeek(id = "w1", name = "W1"),
                                    ProgramWeek(id = "w2", name = "W2"),
                                    ProgramWeek(id = "w3", name = "W3"),
                                    ProgramWeek(id = "w4", name = "W4"),
                                ),
                            ),
                        ),
                    )
                ),
            )
        ),
        loops = loops,
        loopState = loopState,
        events = events,
        weekDays = 7,
    )

    private val deloadLoop = Loop(
        id = "loop-deload",
        title = "Descarga",
        type = LoopType.DELOAD,
        repeatEveryXLoops = 4,
        priority = 10,
    )

    private val testLoop = Loop(
        id = "loop-test",
        title = "Test 1RM",
        type = LoopType.ONE_RM_TEST,
        repeatEveryXLoops = 3,
        priority = 5,
    )

    // ─── getCycleLength ───

    @Test
    fun getCycleLength_returns_sum_of_weeks() {
        val program = makeProgram()
        assertEquals(4, LoopEngine.getCycleLength(program))
    }

    @Test
    fun getCycleLength_empty_returns_1() {
        val program = Program(id = "e", name = "Empty")
        assertEquals(1, LoopEngine.getCycleLength(program))
    }

    // ─── getCurrentCycle ───

    @Test
    fun getCurrentCycle_default_zero() {
        val program = makeProgram()
        assertEquals(0, LoopEngine.getCurrentCycle(program))
    }

    @Test
    fun getCurrentCycle_from_state() {
        val program = makeProgram(loopState = LoopState(currentCycle = 7))
        assertEquals(7, LoopEngine.getCurrentCycle(program))
    }

    // ─── projectLoops ───

    @Test
    fun projectLoops_ordered_by_cycle_then_priority() {
        val program = makeProgram(loops = listOf(deloadLoop, testLoop))
        val projections = LoopEngine.projectLoops(program, fromCycle = 1, lookAheadCycles = 12)

        assertTrue(projections.isNotEmpty())

        // deload: cycle 1 is 1%4 != 0, cycle 2: 2%4 != 0, cycle 3: 3%4 != 0, cycle 4: 4%4 == 0
        // test: cycle 1: 1%3 != 0, cycle 2: 2%3 != 0, cycle 3: 3%3 == 0
        // So cycle 3 has test, cycle 4 has deload
        val first = projections.first()
        assertEquals(3, first.cycle)
        assertEquals("loop-test", first.loop.id)
    }

    @Test
    fun projectLoops_stacks_collisions() {
        val program = makeProgram(
            loops = listOf(
                deloadLoop.copy(repeatEveryXLoops = 3),
                testLoop, // also every 3
            )
        )
        val projections = LoopEngine.projectLoops(program, fromCycle = 1, lookAheadCycles = 6)
        val collisions = LoopEngine.detectLoopCollisions(projections)

        assertTrue(collisions.containsKey(3))
        assertEquals(2, collisions[3]!!.size)
    }

    @Test
    fun projectLoops_respects_cancelled() {
        val program = makeProgram(
            loops = listOf(testLoop),
            loopState = LoopState(cancelled = listOf("loop-test")),
        )
        val projections = LoopEngine.projectLoops(program, fromCycle = 1)
        assertTrue(projections.isEmpty())
    }

    @Test
    fun projectLoops_handles_postponement() {
        val program = makeProgram(
            loops = listOf(testLoop),
            loopState = LoopState(postponed = listOf(
                PostponedLoop(loopId = "loop-test", fromCycle = 3, toCycle = 4)
            )),
        )
        val projections = LoopEngine.projectLoops(program, fromCycle = 1, lookAheadCycles = 6)

        // No projection at cycle 3 (postponed), but there should be one at cycle 4
        val at3 = projections.filter { it.cycle == 3 }
        val at4 = projections.filter { it.cycle == 4 }
        assertTrue(at3.none { !it.isPostponed })
        assertTrue(at4.any { it.isPostponed })
    }

    // ─── detectLoopCollisions ───

    @Test
    fun detectLoopCollisions_no_collisions() {
        val program = makeProgram(loops = listOf(testLoop))
        val projections = LoopEngine.projectLoops(program, fromCycle = 1)
        val collisions = LoopEngine.detectLoopCollisions(projections)
        assertTrue(collisions.isEmpty())
    }

    // ─── postponeLoop ───

    @Test
    fun postponeLoop_adds_postponement() {
        val program = makeProgram(loops = listOf(testLoop))
        val updated = LoopEngine.postponeLoop(program, "loop-test", 3)

        assertEquals(1, updated.loopState!!.postponed.size)
        assertEquals(3, updated.loopState!!.postponed[0].fromCycle)
        assertEquals(4, updated.loopState!!.postponed[0].toCycle)
    }

    @Test
    fun postponeLoop_unknown_loop_creates_entry() {
        val program = makeProgram(loops = listOf(testLoop))
        val updated = LoopEngine.postponeLoop(program, "unknown", 3)
        // Implementation always adds the postponement
        assertEquals(1, updated.loopState!!.postponed.size)
        assertEquals("unknown", updated.loopState!!.postponed[0].loopId)
    }

    // ─── cancelLoop ───

    @Test
    fun cancelLoop_adds_to_cancelled() {
        val program = makeProgram(loops = listOf(testLoop))
        val updated = LoopEngine.cancelLoop(program, "loop-test")

        assertTrue("loop-test" in updated.loopState!!.cancelled)
    }

    @Test
    fun cancelLoop_idempotent() {
        val program = makeProgram(
            loops = listOf(testLoop),
            loopState = LoopState(cancelled = listOf("loop-test")),
        )
        val updated = LoopEngine.cancelLoop(program, "loop-test")
        assertEquals(1, updated.loopState!!.cancelled.size)
    }

    // ─── reactivateLoop ───

    @Test
    fun reactivateLoop_removes_from_cancelled() {
        val program = makeProgram(
            loops = listOf(testLoop),
            loopState = LoopState(cancelled = listOf("loop-test")),
        )
        val updated = LoopEngine.reactivateLoop(program, "loop-test")
        assertTrue("loop-test" !in updated.loopState!!.cancelled)
    }

    // ─── advanceCycle ───

    @Test
    fun advanceCycle_increments_and_cleans_expired() {
        val program = makeProgram(
            loopState = LoopState(
                currentCycle = 2,
                postponed = listOf(
                    PostponedLoop("loop-test", 2, 4), // survives: toCycle > 3
                    PostponedLoop("loop-test", 1, 3), // expired: toCycle == 3
                ),
            )
        )
        val updated = LoopEngine.advanceCycle(program)

        assertEquals(3, updated.loopState!!.currentCycle)
        assertEquals(1, updated.loopState!!.postponed.size)
        assertEquals(4, updated.loopState!!.postponed[0].toCycle)
    }

    // ─── migrateEventsToLoops ───

    @Test
    fun migrateEventsToLoops_converts_legacy_events() {
        val legacyEvent = ProgramEvent(
            id = "evt1",
            title = "Descarga mensual",
            type = "deload",
            date = "2026-01-01",
            calculatedWeek = 4,
            repeatEveryXCycles = 4,
        )
        val program = makeProgram(events = listOf(legacyEvent))
        val updated = LoopEngine.migrateEventsToLoops(program)

        assertEquals(1, updated.loops.size)
        assertEquals("Descarga mensual", updated.loops[0].title)
        assertEquals(4, updated.loops[0].repeatEveryXLoops)
        assertTrue(updated.events.isEmpty())
    }

    @Test
    fun migrateEventsToLoops_skips_already_migrated() {
        val existing = Loop(id = "existing", title = "Descarga mensual", repeatEveryXLoops = 4)
        val legacyEvent = ProgramEvent(
            title = "Descarga mensual",
            type = "deload",
            date = "2026-01-01",
            calculatedWeek = 4,
            repeatEveryXCycles = 4,
        )
        val program = makeProgram(loops = listOf(existing), events = listOf(legacyEvent))
        val updated = LoopEngine.migrateEventsToLoops(program)

        assertEquals(1, updated.loops.size)
    }

    @Test
    fun migrateEventsToLoops_no_legacy_returns_same() {
        val program = makeProgram(events = listOf(
            ProgramEvent(title = "Meet", type = "competition", date = "2026-01-01", calculatedWeek = 1)
        ))
        val updated = LoopEngine.migrateEventsToLoops(program)
        assertSame(program, updated)
    }

    // ─── formatLoopCountdown ───

    @Test
    fun formatLoopCountdown_now() {
        assertEquals("Ahora", LoopEngine.formatLoopCountdown(0))
    }

    @Test
    fun formatLoopCountdown_one_day() {
        assertEquals("1 día", LoopEngine.formatLoopCountdown(1))
    }

    @Test
    fun formatLoopCountdown_days() {
        assertEquals("5 días", LoopEngine.formatLoopCountdown(5))
    }

    @Test
    fun formatLoopCountdown_weeks() {
        assertEquals("2 sem", LoopEngine.formatLoopCountdown(14))
    }

    @Test
    fun formatLoopCountdown_weeks_and_days() {
        assertEquals("1s 3d", LoopEngine.formatLoopCountdown(10))
    }

    // ─── Emoji/Label ───

    @Test
    fun getLoopTypeEmoji() {
        assertEquals("\uD83C\uDFCB\uFE0F", LoopEngine.getLoopTypeEmoji(LoopType.ONE_RM_TEST))
        assertEquals("\uD83E\uDDD8", LoopEngine.getLoopTypeEmoji(LoopType.DELOAD))
        assertEquals("\uD83C\uDFC6", LoopEngine.getLoopTypeEmoji(LoopType.COMPETITION))
        assertEquals("\u26A1", LoopEngine.getLoopTypeEmoji(LoopType.CUSTOM))
    }

    @Test
    fun getLoopTypeLabel() {
        assertEquals("Test 1RM", LoopEngine.getLoopTypeLabel(LoopType.ONE_RM_TEST))
        assertEquals("Descarga", LoopEngine.getLoopTypeLabel(LoopType.DELOAD))
        assertEquals("Competición", LoopEngine.getLoopTypeLabel(LoopType.COMPETITION))
        assertEquals("Personalizado", LoopEngine.getLoopTypeLabel(LoopType.CUSTOM))
    }
}
