package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopStatus
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopOccurrenceOperationalTest {

    private fun simpleWithLoop(): Program {
        val loop = Loop(id = "l1", title = "Deload", type = LoopType.DELOAD, repeatEveryXLoops = 4)
        return Program(
            id = "p",
            name = "Simple",
            structure = ProgramStructure.SIMPLE,
            loops = listOf(loop),
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m",
                                    name = "M",
                                    weeks = listOf(
                                        ProgramWeek(id = "w1", name = "W1", sessions = listOf(Session(id = "s", name = "D"))),
                                        ProgramWeek(id = "loop_week_l1", name = "Deload", isLoopWeek = true, loopId = "l1"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun syncOccurrences_materializes_upcoming_occurrences() {
        val synced = LoopEngine.syncOccurrences(simpleWithLoop())
        assertTrue(synced.loopOccurrences.isNotEmpty())
        assertTrue(synced.loopOccurrences.any { it.loopId == "l1" && it.scheduledCycle == 4 })
    }

    @Test
    fun postponeLoop_updates_occurrence_status() {
        val base = LoopEngine.syncOccurrences(simpleWithLoop())
        val postponed = LoopEngine.postponeNextOccurrence(base, "l1")
        val fromFour = postponed.loopState?.postponed?.firstOrNull { it.loopId == "l1" }
        assertEquals(4, fromFour?.fromCycle)
        assertEquals(5, fromFour?.toCycle)
        val occ = postponed.loopOccurrences.firstOrNull { it.loopId == "l1" && it.scheduledCycle == 5 }
            ?: postponed.loopOccurrences.firstOrNull { it.loopId == "l1" && it.status == LoopStatus.POSTPONED }
        assertTrue(occ != null)
    }

    @Test
    fun cancelOccurrence_keeps_rule_and_skips_only_that_cycle() {
        val base = LoopEngine.syncOccurrences(simpleWithLoop())
        val occId = base.loopOccurrences.first { it.loopId == "l1" && it.scheduledCycle == 4 }.id
        val cancelled = LoopEngine.cancelOccurrence(base, occId)
        assertTrue(cancelled.loops.any { it.id == "l1" })
        assertTrue("l1" !in (cancelled.loopState?.cancelled ?: emptyList()))
        assertTrue(cancelled.loopOccurrences.any { it.id == occId && it.status == LoopStatus.CANCELLED })
        assertTrue(cancelled.loopOccurrences.any { it.loopId == "l1" && it.scheduledCycle == 8 })
    }

    @Test
    fun roadmap_markers_expose_next_occurrence_cycle() {
        val synced = LoopEngine.syncOccurrences(simpleWithLoop())
        val markers = ProgramDetailHelpers.buildSimpleRoadmapLoopMarkers(synced)
        assertEquals(1, markers.size)
        assertEquals(4, markers.first().nextCycle)
    }
}
