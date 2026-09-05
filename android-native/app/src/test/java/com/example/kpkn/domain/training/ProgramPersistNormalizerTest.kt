package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.isCompetitionMeet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramPersistNormalizerTest {

    @Test
    fun repairStaleRunCursor_moves_missing_week_to_existing_location() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            runState = ProgramRunState(
                runId = "run",
                weekId = "deleted",
                weekInstanceId = "deleted",
                blockId = "gone",
                mesocycleId = "gone",
                macrocycleId = "gone",
                status = ProgramRunStatus.ACTIVE,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m1",
                                    name = "Meso",
                                    weeks = listOf(ProgramWeek(id = "w1", name = "W1")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val repaired = ProgramPersistNormalizer.repairStaleRunCursor(program)

        assertEquals("w1", repaired.runState?.weekId)
        assertEquals("b1", repaired.runState?.blockId)
        assertEquals("m1", repaired.runState?.mesocycleId)
        assertEquals("mc", repaired.runState?.macrocycleId)
    }

    @Test
    fun normalize_syncs_loop_occurrences_for_simple_cyclic() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            loops = listOf(Loop(id = "l1", title = "Deload", type = LoopType.DELOAD, repeatEveryXLoops = 4)),
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m1",
                                    name = "Meso",
                                    weeks = listOf(ProgramWeek(id = "w1", name = "W1")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val normalized = ProgramPersistNormalizer.normalize(program)

        assertTrue(normalized.loopOccurrences.isNotEmpty())
        assertEquals("l1", normalized.loopOccurrences.first().loopId)
    }

    @Test
    fun normalize_strips_competition_meet_sessions() {
        val monday = Session(
            id = "mon",
            name = "Sesión Lunes",
            dayOfWeek = 1,
            exercises = listOf(
                com.example.kpkn.data.models.Exercise(
                    id = "sq",
                    name = "Sentadilla",
                    sets = listOf(com.example.kpkn.data.models.ExerciseSet(id = "s1", targetReps = 5, weight = 100.0)),
                ),
            ),
        )
        val meet = Session(
            id = "meet",
            name = "Técnica",
            dayOfWeek = 3,
            isMeetDay = true,
            isCompetitionSession = true,
            competitionKeyDateId = "comp",
            competitionDetails = com.example.kpkn.data.models.CompetitionDetails(competitionDate = "2026-09-02"),
        )
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            startDay = 1,
            timelineStartDate = "2026-08-24",
            calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
            keyDates = listOf(
                com.example.kpkn.data.models.ProgramKeyDate(
                    id = "comp",
                    title = "Meet",
                    type = com.example.kpkn.data.models.KeyDateType.COMPETITION,
                    startDate = "2026-09-02",
                    eventDate = "2026-09-02",
                ),
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque 1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m1",
                                    name = "Meso 1",
                                    weeks = listOf(
                                        ProgramWeek(id = "w1", name = "Semana 1", sessions = listOf(monday)),
                                        ProgramWeek(id = "w-new", name = "Semana extra"),
                                    ),
                                ),
                            ),
                        ),
                        Block(
                            id = "b2",
                            name = "Bloque 2",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m2",
                                    name = "Meso 2",
                                    weeks = listOf(
                                        ProgramWeek(id = "w-meet", name = "Semana meet", sessions = listOf(meet)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val normalized = ProgramPersistNormalizer.normalize(program)
        val sessions = normalized.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
        assertTrue(sessions.none { it.id == "meet" })
        assertTrue(sessions.none { it.isCompetitionMeet })
        assertTrue(sessions.any { it.id == "mon" })
    }
}
